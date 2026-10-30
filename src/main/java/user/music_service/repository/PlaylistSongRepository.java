package user.music_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import user.music_service.model.PlaylistSong;

public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, Long> {
    List<PlaylistSong> findAllByPlaylistIdOrderByPositionAsc(Long playlistId);
    Optional<PlaylistSong> findByPlaylistIdAndSongId(Long playlistId, UUID songId);
    boolean existsByPlaylistIdAndSongId(Long playlistId, UUID songId);
    void deleteByPlaylistIdAndSongId(Long playlistId, UUID songId);
    void deleteAllByPlaylistId(Long playlistId);
}
