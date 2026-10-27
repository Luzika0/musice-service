package user.music_service.service.impl;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
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
import user.music_service.service.HlsService;
import user.music_service.service.PlaybackService;

@Service
@RequiredArgsConstructor
public class HlsServiceImpl implements HlsService {

    private final HlsProcessingService hlsProcessingService;
    private final S3Client s3Client;
    private final SongRepository songRepository;
    private final PlaybackService playbackService;
    private final RequestHeaderData requestHeaderData;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @Override
    public void processSong(UUID songId, String sourceObjectKey) throws java.io.IOException {
        hlsProcessingService.processSong(songId, sourceObjectKey);
    }

    @Override
    public ResponseEntity<StreamingResponseBody> getAsset(UUID songId, String assetName) throws AppException {
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

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key("hls/" + songId + "/" + assetName)
                .build();

        StreamingResponseBody body = outputStream -> {
            try (ResponseInputStream<GetObjectResponse> input = s3Client.getObject(request)) {
                input.transferTo(outputStream);
                outputStream.flush();
            }
        };

        return ResponseEntity.ok()
                .contentType(contentTypeFor(assetName))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private boolean isAllowedAssetName(String assetName) {
        return "master.m3u8".equals(assetName)
                || "index.m3u8".equals(assetName)
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
