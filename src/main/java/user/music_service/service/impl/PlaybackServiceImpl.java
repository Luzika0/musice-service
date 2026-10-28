package user.music_service.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import user.music_service.constant.Constant;
import user.music_service.dto.GeneralResponse;
import user.music_service.dto.request.RequestHeaderData;
import user.music_service.enums.ErrorCode;
import user.music_service.handler.AppException;
import user.music_service.model.PlaybackSession;
import user.music_service.repository.PlaybackSessionRepository;
import user.music_service.service.PlaybackService;
import user.music_service.utils.DateTimeUtils;

@Service
@RequiredArgsConstructor
public class PlaybackServiceImpl implements PlaybackService {
    private final SecureRandom secureRandom = new SecureRandom();
    private final PlaybackSessionRepository playbackSessionRepository;
    private final RequestHeaderData requestHeaderData;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${playback.expiration}")
    private Long tokenLifeTimeSeacound;

    @Override
    @Transactional
    public GeneralResponse createSession(UUID songId) throws AppException {
        Integer userId = requestHeaderData.getUserId();

        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHURIZED, "User must be authenticated.");
        }

        
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(CAST(:userId AS BIGINT))")
                .setParameter("userId", userId.longValue())
                .getSingleResult();

        var previousSessions = playbackSessionRepository.findAllByUserId(userId.longValue());
        for (PlaybackSession previous : previousSessions) {
            if (!previous.isRevoked()) {
                previous.setRevoked(true);
            }
        }
        playbackSessionRepository.saveAll(previousSessions);

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        LocalDateTime expiresAt = DateTimeUtils.KHMER_LOCAL_DATE_TIME()
                .plusSeconds(tokenLifeTimeSeacound);

        PlaybackSession session = new PlaybackSession();
        session.setExpiresAt(expiresAt);
        session.setSongId(songId);
        session.setTokenHash(sha256(rawToken));
        session.setUserId(userId.longValue());

        PlaybackSession savedSession = playbackSessionRepository.save(session);

        var data = new InnerPlaybackServiceTokenResponse(
                rawToken,
                savedSession.getId(),
                savedSession.getExpiresAt(),
                savedSession.getSongId());

        return new GeneralResponse(data);
    }

    private record InnerPlaybackServiceTokenResponse(
            String token,
            UUID sessionId,
            @JsonFormat(pattern = "yyyy-MM-dd:HH:mm:ss", timezone = "UTC") LocalDateTime expiresAt,
            UUID songId) {
    }

    @Override
    public GeneralResponse validateToken(
            String rawToken,
            Integer userId,
            UUID requestedSongId) throws AppException {

        if (rawToken == null || rawToken.isBlank() || userId == null || requestedSongId == null) {
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    "Missing playback token, user, or song identity.");
        }

        PlaybackSession session = playbackSessionRepository
                .findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new AppException(
                        ErrorCode.UNAUTHURIZED,
                        "Invalid playback token."));

        if (session.isRevoked()) {
            throw new AppException(ErrorCode.REVOKED, "Playback session was replaced by another device.");
        }

        if (!DateTimeUtils.KHMER_LOCAL_DATE_TIME().isBefore(session.getExpiresAt())) {
            throw new AppException(ErrorCode.EXPIRED, "Playback token expired.");
        }

        if (!session.getSongId().equals(requestedSongId)) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Playback token is not valid for this song.");
        }

        if (!session.getUserId().equals(userId.longValue())) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Playback token does not belong to this user.");
        }

        var data = new InnerPlaybackServiceTokenResponse(
                null,
                session.getId(),
                session.getExpiresAt(),
                session.getSongId());

        return new GeneralResponse(data);
    }

    @Override
    public boolean isSessionActive(String rawToken, Integer userId, UUID requestedSongId) {
        if (rawToken == null || rawToken.isBlank() || userId == null || requestedSongId == null) {
            return false;
        }
        return playbackSessionRepository.findByTokenHash(sha256(rawToken))
                .filter(session -> !session.isRevoked())
                .filter(session -> DateTimeUtils.KHMER_LOCAL_DATE_TIME().isBefore(session.getExpiresAt()))
                .filter(session -> session.getUserId().equals(userId.longValue()))
                .filter(session -> session.getSongId().equals(requestedSongId))
                .isPresent();
    }

    @Override
    @Transactional
    public GeneralResponse revokeSession(Integer userId, UUID sessionId) throws AppException {
        var session = playbackSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.NOT_FOUND,
                        "Playback session" + Constant.NOT_FOUND));

        if (userId == null || !session.getUserId().equals(userId.longValue())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Not your session.");
        }

        session.setRevoked(true);
        playbackSessionRepository.save(session);
        return new GeneralResponse();
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
