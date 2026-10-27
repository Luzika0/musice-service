package user.music_service.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "playback_sessions", indexes = {
        @Index(name = "idx_playback_token_hash", columnList = "token_hash"),
        @Index(name = "idx_playback_expires_at", columnList = "expires_at"),
        @Index(name = "idx_playback_hls_session_hash", columnList = "hls_session_hash")
})
public class PlaybackSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "hls_session_hash", unique = true, length = 64)
    private String hlsSessionHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "song_id", nullable = false)
    private UUID songId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;
}
