package user.music_service.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import user.music_service.enums.ErrorCode;
import user.music_service.handler.AppException;
import user.music_service.model.PlaybackSession;
import user.music_service.repository.PlaybackSessionRepository;
import user.music_service.repository.SongRepository;
import user.music_service.utils.DateTimeUtils;

@Service
public class HlsPlaybackService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern SAFE_FILE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private final S3Client s3Client;
    private final PlaybackSessionRepository sessions;
    private final SongRepository songs;
    private final String bucket;

    public HlsPlaybackService(
            S3Client s3Client,
            PlaybackSessionRepository sessions,
            SongRepository songs,
            @Value("${cloudflare.r2.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.sessions = sessions;
        this.songs = songs;
        this.bucket = bucket;
    }

    public HlsSession createBrowserSession(String rawPlaybackToken, Integer userId, UUID songId) {
        if (rawPlaybackToken == null || rawPlaybackToken.isBlank() || userId == null || songId == null) {
            throw unauthorized();
        }
        PlaybackSession session = sessions.findByTokenHash(sha256(rawPlaybackToken))
                .orElseThrow(this::unauthorized);
        if (!isValid(session, userId, songId)) {
            throw unauthorized();
        }
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String rawCookie = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        session.setHlsSessionHash(sha256(rawCookie));
        sessions.save(session);
        long seconds = Math.max(1, Duration.between(
                DateTimeUtils.KHMER_LOCAL_DATE_TIME(), session.getExpiresAt()).getSeconds());
        return new HlsSession(rawCookie, seconds);
    }

    public byte[] readAsset(UUID songId, String cookie, Integer userId, String fileName) throws IOException {
        if (fileName == null || !SAFE_FILE.matcher(fileName).matches()
                || fileName.contains("..") || fileName.endsWith(".")) {
            throw new AppException(ErrorCode.NOT_FOUND, "HLS asset not found.");
        }
        PlaybackSession session = findCookieSession(cookie);
        if (userId == null || !isValid(session, userId, songId)) {
            throw unauthorized();
        }
        if (!songs.existsById(songId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Song not found.");
        }
        String key = "hls/" + songId + "/" + fileName;
        try (ResponseInputStream<GetObjectResponse> input = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            throw new AppException(ErrorCode.NOT_FOUND, "HLS asset not found.");
        }
    }

    public void uploadPackage(UUID songId, java.util.Map<String, byte[]> files) {
        if (!songs.existsById(songId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Song not found.");
        }
        byte[] playlist = files.get("audio.m3u8");
        if (playlist == null || playlist.length == 0 || playlist.length > 1024 * 1024) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Package must contain a valid audio.m3u8 playlist.");
        }
        String text = new String(playlist, StandardCharsets.UTF_8);
        if (!text.startsWith("#EXTM3U") || text.contains("://") || text.contains("../")
                || text.contains("..\\")) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Playlist must use relative local HLS filenames only.");
        }
        for (String line : text.split("\\R")) {
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#")) {
                if (value.startsWith("#EXT-X-KEY") && !value.contains("METHOD=NONE")) {
                    throw new AppException(ErrorCode.INVALID_INPUT,
                            "Encrypted HLS packages are not enabled in this first integration step.");
                }
                continue;
            }
            if (!SAFE_FILE.matcher(value).matches() || value.contains("..")) {
                throw new AppException(ErrorCode.INVALID_INPUT, "Unsafe playlist filename: " + value);
            }
            if (!files.containsKey(value)) {
                throw new AppException(ErrorCode.INVALID_INPUT, "Missing playlist segment: " + value);
            }
        }
        if (files.size() > 1000) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Too many HLS files.");
        }
        files.forEach((name, bytes) -> {
            if (!SAFE_FILE.matcher(name).matches() || name.contains("..")
                    || !(name.equals("audio.m3u8") || name.endsWith(".ts") || name.endsWith(".m4s"))) {
                throw new AppException(ErrorCode.INVALID_INPUT, "Unsupported HLS package file: " + name);
            }
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket)
                            .key("hls/" + songId + "/" + name)
                            .contentType(name.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t")
                            .build(),
                    RequestBody.fromBytes(bytes));
        });
    }

    private PlaybackSession findCookieSession(String cookie) {
        if (cookie == null || cookie.isBlank()) {
            throw unauthorized();
        }
        return sessions.findByHlsSessionHash(sha256(cookie)).orElseThrow(this::unauthorized);
    }

    private boolean isValid(PlaybackSession session, Integer userId, UUID songId) {
        return session != null && !session.isRevoked()
                && session.getUserId().equals(userId.longValue())
                && session.getSongId().equals(songId)
                && DateTimeUtils.KHMER_LOCAL_DATE_TIME().isBefore(session.getExpiresAt());
    }

    private AppException unauthorized() {
        return new AppException(ErrorCode.UNAUTHURIZED, "Invalid or expired HLS playback session.");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record HlsSession(String cookieValue, long maxAgeSeconds) {}
}
