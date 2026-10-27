package user.music_service.service;

import java.util.UUID;

import user.music_service.dto.GeneralResponse;
import user.music_service.handler.AppException;

public interface PlaybackService {

    GeneralResponse createSession(UUID songId) throws AppException;

    GeneralResponse validateToken(
            String rawToken,
            Integer userId,
            UUID requestedSongId) throws AppException;

    GeneralResponse revokeSession(Integer userId, UUID seasionId) throws AppException;

}
