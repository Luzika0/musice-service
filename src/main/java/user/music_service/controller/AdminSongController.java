package user.music_service.controller;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import user.music_service.dto.request.ArtistRequest;
import user.music_service.dto.request.SongRequest;
import user.music_service.handler.AppException;
import user.music_service.service.SongService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminSongController {
    private final SongService songService;

    @PostMapping("/create-artist")
    public ResponseEntity<?> createArtist(@RequestBody ArtistRequest req) {

        return ResponseEntity.ok().body(songService.createArtist(req));
    }

    @PostMapping(value = "/upload-song", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadSong(@ModelAttribute SongRequest request) throws AppException, IOException {
        return ResponseEntity.ok().body(songService.uploadSongs(request));
    }
    

}
