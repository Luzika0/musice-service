package user.music_service.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;

@Data
@JsonPropertyOrder({ "artistId", "artistName", "albumName" })
public class ArtistResponse {

    private Integer artistId;

    private String artistName;

    private String albumName;
}
