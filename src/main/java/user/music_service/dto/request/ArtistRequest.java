package user.music_service.dto.request;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ArtistRequest {

    @NotNull(message = "artistName is required")
    private String artistName;

    @NotNull(message = "albumName is required")
    private String albumName;

    private MultipartFile artistImage;

    private MultipartFile albumIamge;

}
