
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
     * Original method retained for existing callers.
     * FFmpeg detects the input format from the file contents.
     */
    public void processSong(UUID songId, String sourceObjectKey)
            throws IOException {
        processSong(songId, sourceObjectKey, null);
    }

    /**
     * Processes an audio object in private R2 into AES-128 encrypted HLS.
     *
     * @param songId           song UUID
     * @param sourceObjectKey  existing original audio object in R2
     * @param originalFilename optional original filename, used for its extension
     */
    public void processSong(
            UUID songId,
            String sourceObjectKey,
            String originalFilename) throws IOException {

        Path workDir = Files.createTempDirectory("music-hls-");
        Path sourceFile = null;
        Path outputDir = workDir.resolve("output");
        Path keyFile = workDir.resolve("hls.key");
        Path keyInfoFile = workDir.resolve("key-info.txt");
        Path ffmpegLog = workDir.resolve("ffmpeg.log");

        byte[] encryptionKey = new byte[16];
        byte[] initializationVector = new byte[16];

        try {
            Files.createDirectories(outputDir);

            // Generate a fresh AES-128 key and IV for this song.
            SECURE_RANDOM.nextBytes(encryptionKey);
            SECURE_RANDOM.nextBytes(initializationVector);
            Files.write(keyFile, encryptionKey);

            // Preserve the uploaded file extension when available.
            // FFmpeg still detects the actual media format automatically.
            String extension = getSafeExtension(originalFilename);
            sourceFile = Files.createTempFile(
                    workDir, "source-", extension);

            downloadSource(sourceObjectKey, sourceFile);

            // The playlist references the protected key endpoint relatively.
            Files.writeString(
                    keyInfoFile,
                    "key\n"
                            + keyFile.toAbsolutePath() + "\n"
                            + HexFormat.of().formatHex(initializationVector)
                            + "\n");

            Path playlist = outputDir.resolve("index.m3u8");
            Path segmentsPattern = outputDir.resolve("segment_%05d.ts");

            runFfmpeg(
                    sourceFile,
                    playlist,
                    segmentsPattern,
                    keyInfoFile,
                    ffmpegLog);

            // Validate generated output before uploading it.
            validateHlsOutput(playlist, keyFile, outputDir);

            String prefix = "hls/" + songId + "/";

            // Upload the key and segments before publishing playlists.
            putFile(
                    prefix + "key",
                    keyFile,
                    "application/octet-stream");

            List<Path> segments;
            try (Stream<Path> files = Files.list(outputDir)) {
                segments = files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName()
                                .toString().endsWith(".ts"))
                        .sorted()
                        .toList();
            }

            for (Path segment : segments) {
                putFile(
                        prefix + segment.getFileName(),
                        segment,
                        "video/mp2t");
            }

            // Publish the media playlist after all segments are uploaded.
            putFile(
                    prefix + "index.m3u8",
                    playlist,
                    "application/vnd.apple.mpegurl");

            Path masterPlaylist = outputDir.resolve("master.m3u8");

            Files.writeString(
                    masterPlaylist,
                    "#EXTM3U\n"
                            + "#EXT-X-VERSION:3\n"
                            + "#EXT-X-STREAM-INF:"
                            + "BANDWIDTH=160000,"
                            + "CODECS=\"mp4a.40.2\"\n"
                            + "index.m3u8\n");

            // Publish master.m3u8 last so it doesn't appear before
            // its referenced media playlist has been uploaded.
            putFile(
                    prefix + "master.m3u8",
                    masterPlaylist,
                    "application/vnd.apple.mpegurl");

            log.info(
                    "HLS processing succeeded for song {}: {} segments",
                    songId,
                    segments.size());

            // Delete the source only after all HLS uploads succeeded.
            deleteOriginalIfConfigured(sourceObjectKey);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "HLS processing was interrupted for song " + songId,
                    exception);

        } finally {
            deleteTemporaryFiles(workDir);
        }
    }

    private void downloadSource(String objectKey, Path destination)
            throws IOException {

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        try (ResponseInputStream<GetObjectResponse> input = s3Client.getObject(request)) {

            Files.copy(
                    input,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void runFfmpeg(
            Path sourceFile,
            Path playlist,
            Path segmentsPattern,
            Path keyInfoFile,
            Path ffmpegLog)
            throws IOException, InterruptedException {

        Process process = new ProcessBuilder(
                ffmpegPath,
                "-hide_banner",
                "-loglevel", "error",
                "-y",
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

        boolean completed;

        try {
            completed = process.waitFor(
                    FFMPEG_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            throw exception;
        }

        if (!completed) {
            process.destroyForcibly();
            process.waitFor();

            throw new IOException(
                    "FFmpeg timed out after " + FFMPEG_TIMEOUT);
        }

        int exitCode = process.exitValue();

        if (exitCode != 0) {
            String details = Files.exists(ffmpegLog)
                    ? Files.readString(ffmpegLog)
                    : "No FFmpeg log available";

            throw new IOException(
                    "FFmpeg exited with code " + exitCode + ": "
                            + details);
        }
    }

    private void validateHlsOutput(
            Path playlist,
            Path keyFile,
            Path outputDir) throws IOException {

        if (!Files.isRegularFile(playlist)) {
            throw new IOException("FFmpeg did not generate index.m3u8");
        }

        if (Files.size(keyFile) != 16) {
            throw new IOException("Invalid AES-128 key size");
        }

        String playlistText = Files.readString(playlist);

        if (!playlistText.contains("#EXTM3U")
                || !playlistText.contains("#EXT-X-ENDLIST")) {
            throw new IOException("Generated HLS playlist is incomplete");
        }

        if (!playlistText.contains("#EXT-X-KEY:METHOD=AES-128")) {
            throw new IOException(
                    "HLS playlist does not declare AES-128 encryption");
        }

        try (Stream<Path> files = Files.list(outputDir)) {
            boolean hasSegment = files
                    .anyMatch(path -> Files.isRegularFile(path)
                            && path.getFileName().toString()
                                    .endsWith(".ts"));

            if (!hasSegment) {
                throw new IOException(
                        "FFmpeg did not generate any HLS segments");
            }
        }
    }

    private void putFile(
            String objectKey,
            Path file,
            String contentType) {

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromFile(file));
    }

    private void deleteOriginalIfConfigured(String sourceObjectKey) {
        // Off by default. Enable only after testing the complete pipeline.
        if (!deleteOriginalAfterProcessing) {
            return;
        }

        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(sourceObjectKey)
                            .build());

            log.info("Deleted original audio object {}", sourceObjectKey);

        } catch (RuntimeException exception) {
            // HLS has already been published. Report the cleanup failure
            // without marking successful HLS generation as failed.
            log.warn(
                    "HLS succeeded, but original audio could not be deleted: {}",
                    sourceObjectKey,
                    exception);
        }
    }

    @Value("${hls.delete-original-after-processing:true}")
    private boolean deleteOriginalAfterProcessing;

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

        // Accept ordinary extensions only; do not use the uploaded
        // filename as a filesystem path.
        if (extension.matches("\\.[a-z0-9]{1,10}")) {
            return extension;
        }

        return ".audio";
    }

    private void deleteTemporaryFiles(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            log.warn(
                                    "Could not delete temporary file {}",
                                    path);
                        }
                    });
        } catch (IOException exception) {
            log.warn("Could not clean temporary directory {}", root);
        }
    }
}
