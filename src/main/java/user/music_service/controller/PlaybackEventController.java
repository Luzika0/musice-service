package user.music_service.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.RequiredArgsConstructor;
import user.music_service.dto.request.RequestHeaderData;
import user.music_service.enums.ErrorCode;
import user.music_service.handler.AppException;
import user.music_service.service.PlaybackEventService;

@RestController
@RequestMapping("/song/{songId}")
@RequiredArgsConstructor
public class PlaybackEventController {

    private final PlaybackEventService playbackEventService;
    private final RequestHeaderData requestHeaderData;

    @GetMapping(value = "/playback-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(@PathVariable UUID songId) throws AppException {
        Integer userId = requestHeaderData.getUserId();
        String token = requestHeaderData.getPlayBackToken();

        if (userId == null || token == null || token.isBlank()) {
            throw new AppException(
                    ErrorCode.UNAUTHURIZED,
                    "Authenticated user and playback token are required.");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(playbackEventService.subscribe(songId, userId, token));
    }
}
