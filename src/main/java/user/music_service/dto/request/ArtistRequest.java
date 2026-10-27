package user.music_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ArtistRequest {

    @NotNull(message = "artistName is required")
    private String artistName;

    @NotNull(message = "albumName is required")
    private String albumName;

}
