package user.music_service.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import user.music_service.dto.request.RequestHeaderData;
import user.music_service.enums.ErrorCode;
import user.music_service.handler.AppException;
import user.music_service.repository.SongRepository;
import user.music_service.service.PlaybackService;

/**
 * Delivers private, AES-128 encrypted HLS assets.
 * Every playlist, segment, and key request must carry the current playback token.
 */
@RestController
@RequestMapping("/hls/{songId}")
@RequiredArgsConstructor
public class HlsController {

    private final S3Client s3Client;
    private final SongRepository songRepository;
    private final PlaybackService playbackService;
    private final RequestHeaderData requestHeaderData;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @GetMapping("/{assetName:.+}")
    public ResponseEntity<StreamingResponseBody> getAsset(
            @PathVariable UUID songId,
            @PathVariable String assetName) throws AppException {

        if (!isAllowedAssetName(assetName)) {
            throw new AppException(ErrorCode.NOT_FOUND, "HLS asset not found.");
        }

        Integer userId = requestHeaderData.getUserId();
        String playbackToken = requestHeaderData.getPlayBackToken();

        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHURIZED, "User must be authenticated.");
        }

        if (!songRepository.existsById(songId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Song not found.");
        }

        playbackService.validateToken(playbackToken, userId, songId);

        String objectKey = "hls/" + songId + "/" + assetName;
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        MediaType contentType = contentTypeFor(assetName);
        StreamingResponseBody body = outputStream -> {
            try (ResponseInputStream<GetObjectResponse> input = s3Client.getObject(request)) {
                input.transferTo(outputStream);
                outputStream.flush();
            }
        };

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private boolean isAllowedAssetName(String assetName) {
        return "index.m3u8".equals(assetName)
                || "key".equals(assetName)
                || assetName.matches("segment_\\d+\\.ts");
    }

    private MediaType contentTypeFor(String assetName) {
        if (assetName.endsWith(".m3u8")) {
            return MediaType.parseMediaType("application/vnd.apple.mpegurl");
        }
        if (assetName.endsWith(".ts")) {
            return MediaType.parseMediaType("video/mp2t");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
