package user.music_service.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/** Safe library-facing song metadata; private HLS object keys are never exposed. */
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
