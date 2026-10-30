package user.music_service.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlaylistResponse {
    private Long playlistId;
    private String name;
    private String description;
    private String coverImage;
    private LocalDateTime createdAt;
    private List<UUID> songIds;
    private List<PlaylistTrackResponse> songs;
}
