package user.music_service.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import user.music_service.dto.request.RequestHeaderData;
import user.music_service.model.PlaybackSession;
import user.music_service.repository.PlaybackSessionRepository;
import user.music_service.repository.SongRepository;
import user.music_service.utils.DateTimeUtils;

@ExtendWith(MockitoExtension.class)
class PlaybackServiceImplTest {

    private static final String TOKEN = "test-playback-token";
    private static final Integer USER_ID = 42;
    private static final UUID SONG_ID = UUID.fromString("f0a1b2c3-d4e5-4678-9012-abcdef123456");

    @Mock
    private PlaybackSessionRepository playbackSessionRepository;

    @Mock
    private SongRepository songRepository;

    @Mock
    private RequestHeaderData requestHeaderData;

    @InjectMocks
    private PlaybackServiceImpl playbackService;

    private PlaybackSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = new PlaybackSession();
        session.setTokenHash(sha256(TOKEN));
        session.setUserId(USER_ID.longValue());
        session.setSongId(SONG_ID);
        session.setExpiresAt(DateTimeUtils.KHMER_LOCAL_DATE_TIME().plusMinutes(1));
        session.setRevoked(false);

        when(playbackSessionRepository.findByTokenHash(anyString()))
                .thenAnswer(invocation -> {
                    String hash = invocation.getArgument(0);
                    return hash.equals(session.getTokenHash())
                            ? Optional.of(session)
                            : Optional.empty();
                });
    }

    @Test
    void activeTokenForMatchingUserAndSongIsActive() {
        assertTrue(playbackService.isSessionActive(TOKEN, USER_ID, SONG_ID));
    }

    @Test
    void revokedTokenIsInactive() {
        session.setRevoked(true);
        assertFalse(playbackService.isSessionActive(TOKEN, USER_ID, SONG_ID));
    }

    @Test
    void expiredTokenIsInactive() {
        session.setExpiresAt(DateTimeUtils.KHMER_LOCAL_DATE_TIME().minusSeconds(1));
        assertFalse(playbackService.isSessionActive(TOKEN, USER_ID, SONG_ID));
    }

    @Test
    void tokenCannotBeUsedByAnotherUserOrForAnotherSong() {
        assertFalse(playbackService.isSessionActive(TOKEN, USER_ID + 1, SONG_ID));
        assertFalse(playbackService.isSessionActive(TOKEN, USER_ID, UUID.randomUUID()));
    }

    @Test
    void missingTokenIsInactive() {
        assertFalse(playbackService.isSessionActive(null, USER_ID, SONG_ID));
        assertFalse(playbackService.isSessionActive(TOKEN, null, SONG_ID));
        assertFalse(playbackService.isSessionActive(TOKEN, USER_ID, null));
    }

    private static String sha256(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
