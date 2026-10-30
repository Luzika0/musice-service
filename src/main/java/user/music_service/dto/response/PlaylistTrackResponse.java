package user.music_service.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Safe library-facing song metadata. Intentionally excludes the private R2 HLS object key.
 */
@Data
@Builder
public class PlaylistTrackResponse {
    private UUID songId;
    private Integer artistId;
    private String songName;
    private String artistName;
    private String songImage;
    private String songType;
    private Integer position;
}
