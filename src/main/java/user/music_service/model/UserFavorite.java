package user.music_service.model;

import java.io.Serializable;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "user_favorites")
@IdClass(UserFavorite.FavoriteId.class)
@Data
@EqualsAndHashCode(callSuper = false)
public class UserFavorite {
    @Id
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Id
    @Column(name = "song_id", nullable = false)
    private UUID songId;

    @Data
    public static class FavoriteId implements Serializable {
        private Integer userId;
        private UUID songId;
    }
}
