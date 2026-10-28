package user.music_service.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({ "artistId", "artistName", "albumName", "artistImage", "albumImage" })
public class ArtistResponse {

    private Integer artistId;
    private String artistName;
    private String albumName;
    private String artistImage;
    private String albumImage;
}
