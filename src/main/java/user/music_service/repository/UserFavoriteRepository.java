package user.music_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import user.music_service.model.UserFavorite;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, UserFavorite.FavoriteId> {
    List<UserFavorite> findAllByUserIdOrderBySongIdAsc(Integer userId);
    Optional<UserFavorite> findByUserIdAndSongId(Integer userId, UUID songId);
    void deleteByUserIdAndSongId(Integer userId, UUID songId);
}
