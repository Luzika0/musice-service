package user.music_service.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import lombok.RequiredArgsConstructor;
import user.music_service.dto.request.RequestHeaderData;
import user.music_service.enums.ErrorCode;
import user.music_service.handler.AppException;
import user.music_service.service.HlsService;
import user.music_service.service.PlaybackService;

@RestController
@RequestMapping("/song/{songId}")
@RequiredArgsConstructor
public class SongController {

    private final PlaybackService playbackService;
    private final HlsService hlsService;
    private final RequestHeaderData requestHeaderData;

    @GetMapping("/playback-token")
    public ResponseEntity<?> getPlaybackToken(@PathVariable UUID songId) throws AppException {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(playbackService.createSession(songId));
    }

    @GetMapping("/playback-status")
    public ResponseEntity<?> getPlaybackStatus(@PathVariable UUID songId) throws AppException {
        Integer userId = requestHeaderData.getUserId();
        String token = requestHeaderData.getPlayBackToken();

        if (userId == null || token == null || token.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHURIZED,
                    "Authenticated user and playback token are required.");
        }

        boolean active = playbackService.isSessionActive(token, userId, songId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("active", active));
    }

    @GetMapping("/hls/{assetName:.+}")
    public ResponseEntity<StreamingResponseBody> getHlsAsset(
            @PathVariable UUID songId,
            @PathVariable String assetName) throws AppException {
        return hlsService.getAsset(songId, assetName);
    }
}
