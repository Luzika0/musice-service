package user.music_service.dto.request;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ArtistRequest {

    @NotBlank(message = "artistName is required")
    private String artistName;

    @NotBlank(message = "albumName is required")
    private String albumName;

    private MultipartFile artistImage;

    // Preserve the multipart field name used by the existing Angular client.
    private MultipartFile albumIamge;
}
