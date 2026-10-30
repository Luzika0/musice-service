package user.music_service.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import user.music_service.dto.GeneralResponse;
import user.music_service.dto.request.PlaylistRequest;
import user.music_service.dto.response.PlaylistResponse;
import user.music_service.enums.ErrorCode;
import user.music_service.handler.AppException;
import user.music_service.model.PlaylistSong;
import user.music_service.model.Song;
import user.music_service.model.UserFavorite;
import user.music_service.model.UserPlaylist;
import user.music_service.repository.PlaylistSongRepository;
import user.music_service.repository.SongRepository;
import user.music_service.repository.UserFavoriteRepository;
import user.music_service.repository.UserPlaylistRepository;
import user.music_service.repository.UserRepository;
import user.music_service.service.LibraryService;

@Service
@RequiredArgsConstructor
@Transactional
public class LibraryServiceImpl implements LibraryService {
    private final UserRepository userRepository;
    private final SongRepository songRepository;
    private final UserFavoriteRepository favoriteRepository;
    private final UserPlaylistRepository playlistRepository;
    private final PlaylistSongRepository playlistSongRepository;

    private void requireUser(Integer userId) throws AppException {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new AppException(ErrorCode.UNAUTHURIZED, "A valid signed-in account is required.");
        }
    }

    private Song requireSong(UUID songId) throws AppException {
        if (songId == null) throw new AppException(ErrorCode.BAD_REQUEST, "Song ID is required.");
        return songRepository.findById(songId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Song not found."));
    }

    private UserPlaylist requirePlaylist(Integer userId, Long playlistId) throws AppException {
        if (playlistId == null) throw new AppException(ErrorCode.BAD_REQUEST, "Playlist ID is required.");
        return playlistRepository.findByPlaylistIdAndUserId(playlistId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Playlist not found."));
    }

    private PlaylistResponse response(UserPlaylist playlist) {
        List<UUID> ids = playlistSongRepository.findAllByPlaylistIdOrderByPositionAsc(playlist.getPlaylistId())
                .stream().map(PlaylistSong::getSongId).toList();
        return PlaylistResponse.builder()
                .playlistId(playlist.getPlaylistId())
                .name(playlist.getName())
                .description(playlist.getDescription())
                .coverImage(playlist.getCoverImage())
                .createdAt(playlist.getCreateAt())
                .songIds(ids)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public GeneralResponse listFavorites(Integer userId) throws AppException {
        requireUser(userId);
        List<Song> songs = favoriteRepository.findAllByUserIdOrderBySongIdAsc(userId).stream()
                .map(f -> songRepository.findById(f.getSongId()).orElse(null))
                .filter(java.util.Objects::nonNull).toList();
        return new GeneralResponse(songs);
    }

    @Override
    public GeneralResponse addFavorite(Integer userId, UUID songId) throws AppException {
        requireUser(userId);
        requireSong(songId);
        if (!favoriteRepository.existsById(new UserFavorite.FavoriteId(userId, songId))) {
            UserFavorite favorite = new UserFavorite();
            favorite.setUserId(userId);
            favorite.setSongId(songId);
            favoriteRepository.save(favorite);
        }
        return new GeneralResponse();
    }

    @Override
    public GeneralResponse removeFavorite(Integer userId, UUID songId) throws AppException {
        requireUser(userId);
        favoriteRepository.deleteByUserIdAndSongId(userId, songId);
        return new GeneralResponse();
    }

    @Override
    @Transactional(readOnly = true)
    public GeneralResponse listPlaylists(Integer userId) throws AppException {
        requireUser(userId);
        return new GeneralResponse(playlistRepository.findAllByUserIdOrderByCreateAtDesc(userId).stream()
                .map(this::response).toList());
    }

    @Override
    public GeneralResponse createPlaylist(Integer userId, PlaylistRequest request) throws AppException {
        requireUser(userId);
        UserPlaylist playlist = new UserPlaylist();
        playlist.setUserId(userId);
        playlist.setName(request.getName().trim());
        playlist.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        return new GeneralResponse(response(playlistRepository.save(playlist)));
    }

    @Override
    @Transactional(readOnly = true)
    public GeneralResponse getPlaylist(Integer userId, Long playlistId) throws AppException {
        requireUser(userId);
        return new GeneralResponse(response(requirePlaylist(userId, playlistId)));
    }

    @Override
    public GeneralResponse updatePlaylist(Integer userId, Long playlistId, PlaylistRequest request) throws AppException {
        requireUser(userId);
        UserPlaylist playlist = requirePlaylist(userId, playlistId);
        playlist.setName(request.getName().trim());
        playlist.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        return new GeneralResponse(response(playlistRepository.save(playlist)));
    }

    @Override
    public GeneralResponse deletePlaylist(Integer userId, Long playlistId) throws AppException {
        requireUser(userId);
        UserPlaylist playlist = requirePlaylist(userId, playlistId);
        playlistSongRepository.deleteAllByPlaylistId(playlistId);
        playlistRepository.delete(playlist);
        return new GeneralResponse();
    }

    @Override
    public GeneralResponse addSong(Integer userId, Long playlistId, UUID songId) throws AppException {
        requireUser(userId);
        requirePlaylist(userId, playlistId);
        requireSong(songId);
        if (!playlistSongRepository.existsByPlaylistIdAndSongId(playlistId, songId)) {
            int position = playlistSongRepository.findAllByPlaylistIdOrderByPositionAsc(playlistId).size();
            PlaylistSong row = new PlaylistSong();
            row.setPlaylistId(playlistId);
            row.setSongId(songId);
            row.setPosition(position);
            playlistSongRepository.save(row);
        }
        return new GeneralResponse(response(requirePlaylist(userId, playlistId)));
    }

    @Override
    public GeneralResponse removeSong(Integer userId, Long playlistId, UUID songId) throws AppException {
        requireUser(userId);
        requirePlaylist(userId, playlistId);
        playlistSongRepository.deleteByPlaylistIdAndSongId(playlistId, songId);
        List<PlaylistSong> remaining = playlistSongRepository.findAllByPlaylistIdOrderByPositionAsc(playlistId);
        for (int i = 0; i < remaining.size(); i++) remaining.get(i).setPosition(i);
        playlistSongRepository.saveAll(remaining);
        return new GeneralResponse(response(requirePlaylist(userId, playlistId)));
    }

    @Override
    public GeneralResponse reorderSongs(Integer userId, Long playlistId, List<UUID> songIds) throws AppException {
        requireUser(userId);
        requirePlaylist(userId, playlistId);
        if (songIds == null || new HashSet<>(songIds).size() != songIds.size()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Song order must contain unique song IDs.");
        }
        List<PlaylistSong> current = playlistSongRepository.findAllByPlaylistIdOrderByPositionAsc(playlistId);
        if (current.size() != songIds.size()
                || !new HashSet<>(current.stream().map(PlaylistSong::getSongId).toList()).equals(new HashSet<>(songIds))) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Reorder must contain every song in this playlist exactly once.");
        }
        var bySong = current.stream().collect(java.util.stream.Collectors.toMap(PlaylistSong::getSongId, row -> row));
        for (int i = 0; i < songIds.size(); i++) bySong.get(songIds.get(i)).setPosition(i);
        playlistSongRepository.saveAll(current);
        return new GeneralResponse(response(requirePlaylist(userId, playlistId)));
    }
}
