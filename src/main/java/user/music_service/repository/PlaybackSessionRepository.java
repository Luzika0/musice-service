package user.music_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import user.music_service.model.PlaybackSession;

public interface PlaybackSessionRepository extends JpaRepository<PlaybackSession, UUID> {
    Optional<PlaybackSession> findByTokenHash(String tokenHash);

    Optional<PlaybackSession> findByHlsSessionHash(String hlsSessionHash);

    Optional<PlaybackSession> findByUserId(Integer userId);

    void deleteAllByUserId(long userId);
    
}
