package user.music_service.controller;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.EncryptionTypeMismatchException;
import software.amazon.awssdk.services.s3.model.InvalidRequestException;
import software.amazon.awssdk.services.s3.model.InvalidWriteOffsetException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.TooManyPartsException;
import user.music_service.dto.request.ArtistRequest;
import user.music_service.dto.request.SongRequest;
import user.music_service.handler.AppException;
import user.music_service.service.SongService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminSongController {
    private final SongService songService;

    @PostMapping(value = "/create-artist", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createArtist(@Valid @ModelAttribute ArtistRequest request) {
        return ResponseEntity.ok(songService.createArtist(request));
    }

    @DeleteMapping("/delete-song/{songId}")
    public ResponseEntity<?> deleteSong(@PathVariable UUID songId)
            throws InvalidRequestException, InvalidWriteOffsetException, TooManyPartsException,
            EncryptionTypeMismatchException, S3Exception, AwsServiceException, SdkClientException,
            AppException, IOException {
        return ResponseEntity.ok(songService.deleteSong(songId));
    }

    @PostMapping(value = "/upload-song", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadSong(@Valid @ModelAttribute SongRequest request)
            throws AppException, IOException {
        return ResponseEntity.ok(songService.uploadSongs(request));
    }
}
