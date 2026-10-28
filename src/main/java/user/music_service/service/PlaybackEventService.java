package user.music_service.service;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaybackEventService {

    private static final long HEARTBEAT_INTERVAL_MILLIS = 15_000L;

    private final PlaybackService playbackService;
    private final TaskScheduler playbackEventTaskScheduler;

    public SseEmitter subscribe(UUID songId, Integer userId, String token) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<ScheduledFuture<?>> scheduled = new AtomicReference<>();
        AtomicLong lastHeartbeat = new AtomicLong(System.currentTimeMillis());

        Runnable cancel = () -> {
            ScheduledFuture<?> future = scheduled.get();
            if (future != null) {
                future.cancel(false);
            }
        };

        emitter.onCompletion(cancel);
        emitter.onTimeout(() -> {
            cancel.run();
            emitter.complete();
        });
        emitter.onError(error -> cancel.run());

        if (!playbackService.isSessionActive(token, userId, songId)) {
            sendAndComplete(emitter, cancel);
            return emitter;
        }

        ScheduledFuture<?> future = playbackEventTaskScheduler.scheduleAtFixedRate(() -> {
            try {
                if (!playbackService.isSessionActive(token, userId, songId)) {
                    sendAndComplete(emitter, cancel);
                    return;
                }

                long now = System.currentTimeMillis();
                if (now - lastHeartbeat.get() >= HEARTBEAT_INTERVAL_MILLIS) {
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data(Map.of("active", true)));
                    lastHeartbeat.set(now);
                }
            } catch (IOException | IllegalStateException exception) {
                cancel.run();
                emitter.completeWithError(exception);
            }
        }, Duration.ofSeconds(1));

        scheduled.set(future);
        return emitter;
    }

    private void sendAndComplete(SseEmitter emitter, Runnable cancel) {
        try {
            emitter.send(SseEmitter.event()
                    .name("playback_taken_over")
                    .data(Map.of("active", false, "reason", "SESSION_REPLACED")));
            emitter.complete();
        } catch (IOException | IllegalStateException ignored) {
            emitter.complete();
        } finally {
            cancel.run();
        }
    }
}
