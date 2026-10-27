package user.music_service.dto.request;

import org.springframework.web.multipart.MultipartFile;

import lombok.Data;
import user.music_service.enums.SongType;

@Data
public class SongRequest {

    private Integer artistId;

    private String songName;

    private SongType songType;

    private MultipartFile file;
}
