package user.music_service.service;

import java.util.List;
import java.util.UUID;
import user.music_service.dto.GeneralResponse;
import user.music_service.dto.request.PlaylistRequest;
import user.music_service.handler.AppException;

public interface LibraryService {
    GeneralResponse listFavorites(Integer userId) throws AppException;
    GeneralResponse addFavorite(Integer userId, UUID songId) throws AppException;
    GeneralResponse removeFavorite(Integer userId, UUID songId) throws AppException;
    GeneralResponse listPlaylists(Integer userId) throws AppException;
    GeneralResponse createPlaylist(Integer userId, PlaylistRequest request) throws AppException;
    GeneralResponse getPlaylist(Integer userId, Long playlistId) throws AppException;
    GeneralResponse updatePlaylist(Integer userId, Long playlistId, PlaylistRequest request) throws AppException;
    GeneralResponse deletePlaylist(Integer userId, Long playlistId) throws AppException;
    GeneralResponse addSong(Integer userId, Long playlistId, UUID songId) throws AppException;
    GeneralResponse removeSong(Integer userId, Long playlistId, UUID songId) throws AppException;
    GeneralResponse reorderSongs(Integer userId, Long playlistId, List<UUID> songIds) throws AppException;
}
