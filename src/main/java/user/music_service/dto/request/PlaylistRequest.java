package user.music_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlaylistRequest {
    @NotBlank
    @Size(max = 80)
    private String name;

    @Size(max = 500)
    private String description;
}
