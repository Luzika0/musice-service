package user.music_service.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class HlsProcessingService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration FFMPEG_TIMEOUT = Duration.ofMinutes(10);

    private final S3Client s3Client;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @Value("${hls.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    /**
     * New flow: the uploaded original audio is written to temporary local
     * storage, converted by FFmpeg, then deleted. It is never uploaded to R2.
     */
    public void processSong(UUID songId, MultipartFile audioFile)
            throws IOException {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new IOException("Uploaded audio file is empty.");
        }

        Path workDir = Files.createTempDirectory("music-hls-");
        try {
            String extension = getSafeExtension(audioFile.getOriginalFilename());
            Path sourceFile = Files.createTempFile(workDir, "source-", extension);
            audioFile.transferTo(sourceFile);
            processLocalFile(songId, sourceFile, workDir);
        } finally {
            deleteTemporaryFiles(workDir);
        }
    }

    /**
     * Legacy flow retained for callers that still pass an existing R2 object.
     */
    public void processSong(UUID songId, String sourceObjectKey)
            throws IOException {
        Path workDir = Files.createTempDirectory("music-hls-");
        try {
            Path sourceFile = Files.createTempFile(workDir, "source-", ".audio");
            downloadSource(sourceObjectKey, sourceFile);
            processLocalFile(songId, sourceFile, workDir);
        } finally {
            deleteTemporaryFiles(workDir);
        }
    }

    private void processLocalFile(UUID songId, Path sourceFile, Path workDir)
            throws IOException {
        Path outputDir = Files.createDirectories(workDir.resolve("output"));
        Path keyFile = workDir.resolve("hls.key");
        Path keyInfoFile = workDir.resolve("key-info.txt");
        Path ffmpegLog = workDir.resolve("ffmpeg.log");

        byte[] encryptionKey = new byte[16];
        byte[] initializationVector = new byte[16];
        SECURE_RANDOM.nextBytes(encryptionKey);
        SECURE_RANDOM.nextBytes(initializationVector);
        Files.write(keyFile, encryptionKey);

        Files.writeString(
                keyInfoFile,
                "key\n" + keyFile.toAbsolutePath() + "\n"
                        + HexFormat.of().formatHex(initializationVector) + "\n");

        Path playlist = outputDir.resolve("index.m3u8");
        Path segmentsPattern = outputDir.resolve("segment_%05d.ts");
        runFfmpeg(sourceFile, playlist, segmentsPattern, keyInfoFile, ffmpegLog);
        validateHlsOutput(playlist, keyFile, outputDir);

        String prefix = "hls/" + songId + "/";

        // Upload key and segments first; publish playlists last.
        putFile(prefix + "key", keyFile, "application/octet-stream");

        List<Path> segments;
        try (Stream<Path> files = Files.list(outputDir)) {
            segments = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".ts"))
                    .sorted()
                    .toList();
        }

        for (Path segment : segments) {
            putFile(prefix + segment.getFileName(), segment, "video/mp2t");
        }

        putFile(prefix + "index.m3u8", playlist,
                "application/vnd.apple.mpegurl");

        Path masterPlaylist = outputDir.resolve("master.m3u8");
        Files.writeString(masterPlaylist,
                "#EXTM3U\n"
                + "#EXT-X-VERSION:3\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=160000,CODECS=\"mp4a.40.2\"\n"
                + "index.m3u8\n");

        putFile(prefix + "master.m3u8", masterPlaylist,
                "application/vnd.apple.mpegurl");

        log.info("HLS processing succeeded for song {}: {} segments",
                songId, segments.size());
    }

    private void downloadSource(String objectKey, Path destination)
            throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket).key(objectKey).build();
        try (ResponseInputStream<GetObjectResponse> input = s3Client.getObject(request)) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void runFfmpeg(Path sourceFile, Path playlist, Path segmentsPattern,
            Path keyInfoFile, Path ffmpegLog)
            throws IOException {
        Process process = new ProcessBuilder(
                ffmpegPath,
                "-hide_banner", "-loglevel", "error", "-y",
                "-i", sourceFile.toString(),
                "-vn", "-c:a", "aac", "-b:a", "128k", "-ac", "2", "-ar", "44100",
                "-f", "hls", "-hls_time", "6", "-hls_playlist_type", "vod",
                "-hls_key_info_file", keyInfoFile.toString(),
                "-hls_segment_filename", segmentsPattern.toString(),
                playlist.toString())
                .redirectErrorStream(true)
                .redirectOutput(ffmpegLog.toFile())
                .start();

        boolean completed;
        try {
            completed = process.waitFor(FFMPEG_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg processing was interrupted.", e);
        }

        if (!completed) {
            process.destroyForcibly();
            throw new IOException("FFmpeg timed out after " + FFMPEG_TIMEOUT);
        }

        if (process.exitValue() != 0) {
            String details = Files.exists(ffmpegLog)
                    ? Files.readString(ffmpegLog) : "No FFmpeg log available";
            throw new IOException("FFmpeg exited with code "
                    + process.exitValue() + ": " + details);
        }
    }

    private void validateHlsOutput(Path playlist, Path keyFile, Path outputDir)
            throws IOException {
        if (!Files.isRegularFile(playlist)) {
            throw new IOException("FFmpeg did not generate index.m3u8");
        }
        if (Files.size(keyFile) != 16) {
            throw new IOException("Invalid AES-128 key size");
        }

        String text = Files.readString(playlist);
        if (!text.contains("#EXTM3U")
                || !text.contains("#EXT-X-ENDLIST")
                || !text.contains("#EXT-X-KEY:METHOD=AES-128")) {
            throw new IOException("Generated HLS playlist is incomplete or not encrypted");
        }

        try (Stream<Path> files = Files.list(outputDir)) {
            if (files.noneMatch(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().endsWith(".ts"))) {
                throw new IOException("FFmpeg did not generate HLS segments");
            }
        }
    }

    private void putFile(String objectKey, Path file, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket).key(objectKey).contentType(contentType).build(),
                RequestBody.fromFile(file));
    }

    private String getSafeExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return ".audio";
        }
        String name = Path.of(filename).getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return ".audio";
        }
        String extension = name.substring(dot).toLowerCase();
        return extension.matches("\\.[a-z0-9]{1,10}") ? extension : ".audio";
    }

    private void deleteTemporaryFiles(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("Could not delete temporary file {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("Could not clean temporary directory {}", root, e);
        }
    }
}
