package user.music_service.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Generates AES-128 encrypted HLS assets from an audio object in private R2.
 *
 * Requires FFmpeg to be installed in the runtime/container and available at
 * hls.ffmpeg-path. This is HLS AES encryption, not a commercial DRM system.
 */
@Service
@RequiredArgsConstructor
public class HlsProcessingService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final S3Client s3Client;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @Value("${hls.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    public void processSong(UUID songId, String sourceObjectKey) throws IOException {
        Path workDir = Files.createTempDirectory("music-hls-");
        Path sourceFile = workDir.resolve("source.audio");
        Path outputDir = workDir.resolve("output");
        Path keyFile = workDir.resolve("hls.key");
        Path keyInfoFile = workDir.resolve("key-info.txt");
        Path ffmpegLog = workDir.resolve("ffmpeg.log");
        Files.createDirectories(outputDir);

        byte[] encryptionKey = new byte[16];
        byte[] initializationVector = new byte[16];
        SECURE_RANDOM.nextBytes(encryptionKey);
        SECURE_RANDOM.nextBytes(initializationVector);
        Files.write(keyFile, encryptionKey);

        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(sourceObjectKey)
                    .build();
            try (ResponseInputStream<GetObjectResponse> input = s3Client.getObject(getRequest)) {
                Files.copy(input, sourceFile);
            }

            // The relative URI is resolved by HlsController at /hls/{songId}/key.
            Files.writeString(keyInfoFile,
                    "key\n"
                            + keyFile.toAbsolutePath() + "\n"
                            + HexFormat.of().formatHex(initializationVector) + "\n");

            Path playlist = outputDir.resolve("index.m3u8");
            Path segmentsPattern = outputDir.resolve("segment_%05d.ts");

            Process process = new ProcessBuilder(
                    ffmpegPath,
                    "-hide_banner", "-loglevel", "error", "-y",
                    "-i", sourceFile.toString(),
                    "-vn",
                    "-c:a", "aac",
                    "-b:a", "128k",
                    "-ac", "2",
                    "-ar", "44100",
                    "-f", "hls",
                    "-hls_time", "6",
                    "-hls_playlist_type", "vod",
                    "-hls_key_info_file", keyInfoFile.toString(),
                    "-hls_segment_filename", segmentsPattern.toString(),
                    playlist.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ffmpegLog.toFile())
                    .start();

            final int exitCode;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException exception) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while generating HLS audio.", exception);
            }

            if (exitCode != 0 || !Files.isRegularFile(playlist)) {
                String detail = Files.exists(ffmpegLog) ? Files.readString(ffmpegLog) : "";
                throw new IOException("FFmpeg failed to generate HLS audio (exit "
                        + exitCode + "): " + detail);
            }

            String prefix = "hls/" + songId + "/";
            // Upload the encryption key and segments first. Publish the playlist last.
            putFile(prefix + "key", keyFile, "application/octet-stream");
            try (Stream<Path> files = Files.list(outputDir)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".ts"))
                        .toList()) {
                    putFile(prefix + file.getFileName(), file, "video/mp2t");
                }
            }
            Path masterPlaylist = outputDir.resolve("master.m3u8");
            Files.writeString(masterPlaylist,
                    "#EXTM3U\\n"
                            + "#EXT-X-VERSION:3\\n"
                            + "#EXT-X-STREAM-INF:BANDWIDTH=160000,CODECS=\\"mp4a.40.2\\"\\n"
                            + "index.m3u8\\n");
            putFile(prefix + "index.m3u8", playlist, "application/vnd.apple.mpegurl");
            putFile(prefix + "master.m3u8", masterPlaylist, "application/vnd.apple.mpegurl");
        } finally {
            deleteTemporaryFiles(workDir);
        }
    }

    private void putFile(String objectKey, Path file, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromFile(file));
    }

    private void deleteTemporaryFiles(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Best-effort cleanup; never mask the original processing error.
                        }
                    });
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }
}
