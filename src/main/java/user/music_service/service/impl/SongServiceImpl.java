package user.music_service.service.impl;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.EncryptionTypeMismatchException;
import software.amazon.awssdk.services.s3.model.InvalidRequestException;
import software.amazon.awssdk.services.s3.model.InvalidWriteOffsetException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.TooManyPartsException;
import user.music_service.constant.Constant;
import user.music_service.dto.GeneralResponse;
import user.music_service.dto.request.ArtistRequest;
import user.music_service.dto.request.RequestHeaderData;
import user.music_service.dto.request.SongRequest;
import user.music_service.dto.response.ArtistResponse;
import user.music_service.dto.response.GaneralPagination;
import user.music_service.enums.ErrorCode;
import user.music_service.handler.AppException;
import user.music_service.model.Artist;
import user.music_service.model.Song;
import user.music_service.repository.ArtistRepository;
import user.music_service.repository.SongRepository;
import user.music_service.repository.UserRepository;
import user.music_service.service.HlsService;
import user.music_service.service.SongService;

@Service
@RequiredArgsConstructor
@Slf4j
public class SongServiceImpl implements SongService {

    private final GeneralResponse generalResponse;
    private final HlsService hlsService;
    private final UserRepository userRepository;
    private final RequestHeaderData requestHeaderData;
    private final ArtistRepository artistRepository;
    private final S3Client s3Client;
    private final SongRepository songRepository;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @Override
    public GeneralResponse createArtist(ArtistRequest request) throws AppException {
        var existArtistEntity = artistRepository.findByArtistName(request.getArtistName());
        Integer userId = requestHeaderData.getUserId();
        var permission = userRepository.findById(userId);

        if (permission.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, Constant.NOT_FOUND);
        }

        if (permission.get().getPermission() == null
                || !List.of(Constant.PERMISSION_ADMIN)
                        .contains(permission.get().getPermission().getPermission())) {
            throw new AppException(
                    ErrorCode.ACCESS_DENIED,
                    Constant.NOT_ENOUGH_PERMISSION_PERMISSION);
        }

        if (existArtistEntity != null) {
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    Constant.THIS_ARTIST_WITH_NAME
                            + request.getArtistName() + Constant.EXIST);
        }

        Artist newArtist = new Artist();
        newArtist.setArtistName(request.getArtistName());
        newArtist.setAlbumName(request.getAlbumName());

        Artist savedArtist = artistRepository.save(newArtist);
        return new GeneralResponse(savedArtist);
    }

    @Override
    public GeneralResponse updateArtist(ArtistRequest request) throws AppException {
        final var artistEntity = artistRepository.findByArtistName(request.getArtistName());

        if (artistEntity == null) {
            throw new AppException(
                    ErrorCode.ARTIST_EXISTS,
                    Constant.THIS_ARTIST_WITH_NAME + request.getArtistName() + Constant.NOT_FOUND);
        }

        artistEntity.setArtistName(request.getArtistName());
        artistEntity.setAlbumName(request.getAlbumName());
        artistRepository.save(artistEntity);
        return generalResponse;
    }

    @Override
    public GeneralResponse viewArtist(Integer page, Integer size, String searchText)
            throws AppException {
        final var pageable = PageRequest.of(page, size);
        final var responsePage =
                artistRepository.findAllOrByArtistNameContainingIgnoreCase(searchText, pageable);

        final var data = responsePage.stream().map(t -> {
            final var artistResponse = new ArtistResponse();
            artistResponse.setArtistId(t.getArtistId());
            artistResponse.setArtistName(t.getArtistName());
            artistResponse.setAlbumName(t.getAlbumName());
            return artistResponse;
        });

        final var pagination = GaneralPagination.builder()
                .totalPage(responsePage.getTotalPages())
                .totalElement(responsePage.getTotalElements())
                .data(data)
                .build();

        return new GeneralResponse(pagination);
    }

    @Override
    public GeneralResponse deleteArtist(Integer artistId) throws AppException {
        final var artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.NOT_FOUND,
                        Constant.THIS_ARTIST_WITH_NAME + artistId + Constant.NOT_FOUND));

        artist.setStatus(Constant.STATUS_DEL);
        artistRepository.save(artist);
        return generalResponse;
    }

    @Override
    public GeneralResponse uploadSongs(SongRequest request)
            throws AppException, InvalidRequestException,
            InvalidWriteOffsetException, TooManyPartsException,
            EncryptionTypeMismatchException, S3Exception,
            AwsServiceException, SdkClientException, IOException {

        Integer userId = requestHeaderData.getUserId();
        var permission = userRepository.findById(userId);

        if (permission.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, Constant.NOT_FOUND);
        }

        if (permission.get().getPermission() == null
                || !List.of(Constant.PERMISSION_ADMIN)
                        .contains(permission.get().getPermission().getPermission())) {
            throw new AppException(
                    ErrorCode.ACCESS_DENIED,
                    Constant.NOT_ENOUGH_PERMISSION_PERMISSION);
        }

        if (request.getArtistId() == null
                || artistRepository.findById(request.getArtistId()).isEmpty()) {
            throw new AppException(
                    ErrorCode.NOT_FOUND,
                    Constant.THIS_ARTIST_WITH_ID + request.getArtistId() + Constant.NOT_FOUND);
        }

        var audioFile = request.getFile();
        if (audioFile == null || audioFile.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Audio file is required.");
        }
        if (audioFile.getSize() > 300L * 1024 * 1024) {
            throw new AppException(
                    ErrorCode.FILE_SIZE_LIMIT_EXCEEDED,
                    Constant.FILE_SIZE_LIMIT_EXCEEDED);
        }
        String audioContentType = audioFile.getContentType();
        if (audioContentType == null || !audioContentType.startsWith("audio/")) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Only audio files are allowed.");
        }

        // Cover image is required by this upload endpoint.
        var imageFile = request.getSongImage();
        if (imageFile == null || imageFile.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Song cover image is required.");
        }
        if (imageFile.getSize() > 10L * 1024 * 1024) {
            throw new AppException(ErrorCode.FILE_SIZE_LIMIT_EXCEEDED,
                    "Song cover image must not exceed 10 MB.");
        }

        String imageContentType = imageFile.getContentType();
        if (imageContentType == null || !imageContentType.startsWith("image/")) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Song cover must be an image.");
        }

        UUID uuid = UUID.randomUUID();

        // Convert audio from local temporary storage. Original audio is never uploaded to R2.
        try {
            hlsService.processSong(uuid, audioFile);
        } catch (IOException | RuntimeException exception) {
            log.error("HLS processing failed. songId={}", uuid, exception);
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    "HLS processing failed. Check server logs and try again.");
        }

        // Store the original image bytes in private R2 as a separate object.
        String imageExtension = getImageExtension(imageContentType);
        String imageObjectKey = "song-images/" + uuid + imageExtension;

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(imageObjectKey)
                        .contentType(imageContentType)
                        .build(),
                RequestBody.fromBytes(imageFile.getBytes()));

        Song songEntity = new Song();
        songEntity.setSongId(uuid);
        songEntity.setArtistId(request.getArtistId());
        songEntity.setSongName(request.getSongName());
        songEntity.setSongType(request.getSongType());
        songEntity.setUrlSong("hls/" + uuid + "/master.m3u8");
        songEntity.setSongImage(imageObjectKey);

        songRepository.save(songEntity);

        log.info("Song uploaded successfully. songId={}, imageObjectKey={}",
                uuid, imageObjectKey);

        return new GeneralResponse();
    }

    private String getImageExtension(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/avif" -> ".avif";
            default -> throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    "Supported cover formats: JPEG, PNG, WebP, GIF, and AVIF.");
        };
    }
}
