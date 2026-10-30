package user.music_service.dto.request;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import user.music_service.enums.SongType;

@Data
public class SongRequest {

    @NotNull(message = "artistId is required")
    private Integer artistId;

    @NotBlank(message = "songName is required")
    private String songName;

    private MultipartFile songImage;

    @NotNull(message = "songType is required")
    private SongType songType;

    private MultipartFile file;
}
