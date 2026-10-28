package user.music_service.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import user.music_service.model.PlaybackSession;

public interface PlaybackSessionRepository extends JpaRepository<PlaybackSession, UUID> {
    Optional<PlaybackSession> findByTokenHash(String tokenHash);

    List<PlaybackSession> findAllByUserId(Long userId);

    void deleteAllByUserIdAndExpiresAtBefore(Long userId, LocalDateTime expiresAt);
}
