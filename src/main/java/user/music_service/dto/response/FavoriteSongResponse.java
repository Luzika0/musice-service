package user.music_service.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/** Song metadata safe to expose in a signed-in user's favorites collection. */
@Data
@Builder
public class FavoriteSongResponse {
    private UUID songId;
    private Integer artistId;
    private String songName;
    private String artistName;
    private String songImage;
    private String songType;
}
