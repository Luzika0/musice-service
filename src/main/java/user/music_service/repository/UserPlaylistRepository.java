package user.music_service.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import user.music_service.model.UserPlaylist;

public interface UserPlaylistRepository extends JpaRepository<UserPlaylist, Long> {
    List<UserPlaylist> findAllByUserIdOrderByCreateAtDesc(Integer userId);
    Optional<UserPlaylist> findByPlaylistIdAndUserId(Long playlistId, Integer userId);
}
