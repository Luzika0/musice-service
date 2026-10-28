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
        var permission = userRepository
                .findById(userId);

        if (permission.isEmpty()) {
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    Constant.NOT_FOUND);
        }

        if (permission.get().getPermission() == null
                || !List.of(
                        Constant.PERMISSION_ADMIN).contains(permission.get().getPermission().getPermission())) {

            throw new AppException(
                    ErrorCode.ACCESS_DENIED,
                    Constant.NOT_ENOUGH_PERMISSION_PERMISSION);
        }
        if (existArtistEntity != null) {
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    Constant.THIS_ARTIST_WITH_NAME
                            + request.getArtistName()
                            + Constant.EXIST);
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
            throw new AppException(ErrorCode.ARTIST_EXISTS,
                    Constant.THIS_ARTIST_WITH_NAME + request.getArtistName() + Constant.NOT_FOUND);
        }
        artistEntity.setArtistName(request.getArtistName());
        artistEntity.setAlbumName(request.getAlbumName());
        artistRepository.save(artistEntity);
        return generalResponse;
    }

    @Override
    public GeneralResponse viewArtist(Integer page, Integer size, String searchText) throws AppException {
        final var pageable = PageRequest.of(page, size);
        final var responsePage = artistRepository.findAllOrByArtistNameContainingIgnoreCase(searchText, pageable);

        final var data = responsePage.stream()
                .map(t -> {
                    final var artistResponse = new ArtistResponse();
                    artistResponse.setArtistId(t.getArtistId());
                    artistResponse.setArtistName(t.getArtistName());
                    artistResponse.setAlbumName(t.getAlbumName());

                    return artistResponse;
                });
        final var pagination = GaneralPagination.builder().totalPage(responsePage.getTotalPages())
                .totalElement(responsePage.getTotalElements()).data(data).build();

        return new GeneralResponse(pagination);
    }

    @Override
    public GeneralResponse deleteArtist(Integer artistId) throws AppException {

        final var artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
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
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    Constant.NOT_FOUND);
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
                    Constant.THIS_ARTIST_WITH_ID
                            + request.getArtistId()
                            + Constant.NOT_FOUND);
        }

        var file = request.getFile();

        if (file == null || file.isEmpty()) {
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    Constant.REQUEST_FILE);
        }

        if (file.getSize() > 300L * 1024 * 1024) {
            throw new AppException(
                    ErrorCode.FILE_SIZE_LIMIT_EXCEEDED,
                    Constant.FILE_SIZE_LIMIT_EXCEEDED);
        }

        String contentType = file.getContentType();

        if (contentType == null || !contentType.startsWith("audio/")) {
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    "Only audio files are allowed");
        }

        UUID uuid = UUID.randomUUID();
        String objectKey = "songs/" + uuid;

        
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        s3Client.putObject(
                putRequest,
                RequestBody.fromInputStream(
                        file.getInputStream(),
                        file.getSize()));

        log.info(
                "Original audio uploaded to R2. songId={}, objectKey={}",
                uuid,
                objectKey);


        try {
            hlsService.processSong(uuid, objectKey);
        } catch (IOException | RuntimeException exception) {
            log.error("HLS processing failed. songId={}", uuid, exception);

            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    "HLS processing failed. Please try uploading again.");
        } finally {
            try {
                s3Client.deleteObject(builder -> builder
                        .bucket(bucket)
                        .key(objectKey));
                log.info("Temporary original audio deleted. songId={}", uuid);
            } catch (RuntimeException cleanupException) {
                log.error(
                        "Failed to delete temporary original audio. songId={}, key={}",
                        uuid,
                        objectKey,
                        cleanupException);
            }
        }

        Song songEntity = new Song();
        songEntity.setSongId(uuid);
        songEntity.setArtistId(request.getArtistId());
        songEntity.setSongName(request.getSongName());
        songEntity.setSongType(request.getSongType());
        songEntity.setUrlSong(objectKey);

        songRepository.save(songEntity);

        log.info("Song uploaded and HLS generated successfully: {}", uuid);

        return new GeneralResponse();
    }

}
