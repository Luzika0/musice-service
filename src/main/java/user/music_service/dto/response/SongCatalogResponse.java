package user.music_service.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/** Public catalog metadata for songs; never includes private HLS storage keys. */
@Data
@Builder
public class SongCatalogResponse {
    private UUID songId;
    private Integer artistId;
    private String songName;
    private String artistName;
    private String songImage;
    private String songType;
}
