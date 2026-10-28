package user.music_service.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import lombok.RequiredArgsConstructor;
import user.music_service.handler.AppException;
import user.music_service.service.HlsService;
import user.music_service.service.PlaybackService;

@RestController
@RequestMapping("/song/{songId}")
@RequiredArgsConstructor
public class SongController {

    private final PlaybackService playbackService;
    private final HlsService hlsService;

    @GetMapping("/playback-token")
    public ResponseEntity<?> getPlaybackToken(
            @PathVariable UUID songId) throws AppException {

        return ResponseEntity.ok()
                .body(playbackService.createSession(songId));
    }

    @GetMapping("/hls/{assetName:.+}")
    public ResponseEntity<StreamingResponseBody> getHlsAsset(
            @PathVariable UUID songId,
            @PathVariable String assetName) throws AppException {

        return hlsService.getAsset(songId, assetName);
    }
}
