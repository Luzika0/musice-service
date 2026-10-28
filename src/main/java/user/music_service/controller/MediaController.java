package user.music_service.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import user.music_service.dto.request.RequestHeaderData;
import user.music_service.enums.ErrorCode;
import user.music_service.handler.AppException;
import user.music_service.service.SongService;

@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final S3Client s3Client;
    private final SongService songService;
    private final RequestHeaderData requestHeaderData;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @GetMapping("/artist")
    public ResponseEntity<?> getArtist(@RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String searchText) throws AppException {
        return ResponseEntity.ok().body(songService.viewArtist(page, size, searchText));
    }

    @GetMapping("/image")
    public ResponseEntity<StreamingResponseBody> getImage(@RequestParam String r2Imagekey)
            throws AppException {
        if (requestHeaderData.getUserId() == null) {
            throw new AppException(ErrorCode.UNAUTHURIZED, "User must be authenticated.");
        }
        if (!isAllowedImageKey(r2Imagekey)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Image not found.");
        }

        HeadObjectResponse metadata = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(r2Imagekey)
                .build());

        String contentType = metadata.contentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new AppException(ErrorCode.NOT_FOUND, "Image not found.");
        }

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(r2Imagekey)
                .build();

        StreamingResponseBody body = output -> {
            try (ResponseInputStream<GetObjectResponse> input = s3Client.getObject(request)) {
                input.transferTo(output);
                output.flush();
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private boolean isAllowedImageKey(String key) {
        return key != null
                && !key.contains("..")
                && !key.contains("\\")
                && (key.startsWith("music/songImage/")
                        || key.startsWith("music/artistImage/")
                        || key.startsWith("music/albumnImage/"));
    }

    @GetMapping("/list-song")
    public ResponseEntity<?> listSongs(@RequestParam Integer page, @RequestParam Integer size,
            @RequestParam String searchText) throws AppException {
        return ResponseEntity.ok().body(songService.listSong(page, size, searchText));
    }

}
