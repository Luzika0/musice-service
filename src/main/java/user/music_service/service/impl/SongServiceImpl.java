package user.music_service.service.impl;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
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
import user.music_service.model.PlaylistSong;
import user.music_service.repository.ArtistRepository;
import user.music_service.repository.SongRepository;
import user.music_service.repository.PlaylistSongRepository;
import user.music_service.repository.UserFavoriteRepository;
import user.music_service.repository.UserRepository;
import user.music_service.service.HlsService;
import user.music_service.service.SongService;

@Service
@RequiredArgsConstructor
@Slf4j
public class SongServiceImpl implements SongService {

    private static final long MAX_AUDIO_BYTES = 300L * 1024 * 1024;
    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;

    private final GeneralResponse generalResponse;
    private final HlsService hlsService;
    private final UserRepository userRepository;
    private final RequestHeaderData requestHeaderData;
    private final ArtistRepository artistRepository;
    private final S3Client s3Client;
    private final SongRepository songRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final PlaylistSongRepository playlistSongRepository;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @Override
    public GeneralResponse createArtist(ArtistRequest request) throws AppException {
        requireAdmin();

        final var existArtist = artistRepository.findByArtistNameAndAlbumName(request.getArtistName(),
                request.getAlbumName());

        if (existArtist != null) {
            throw new AppException(ErrorCode.ARTIST_EXISTS,
                    "Artist " + request.getArtistName() + "with this album " + request.getAlbumName()
                            + "already exists");
        }

        validateOptionalImage(request.getArtistImage(), "Artist image");
        validateOptionalImage(request.getAlbumIamge(), "Album image");

        Artist artist = new Artist();
        artist.setArtistName(request.getArtistName());
        artist.setStatus(Constant.STATUS_ACT);
        artist.setAlbumName(request.getAlbumName());

        // Save first because the database generates artistId for the R2 prefixes.
        Artist saved = artistRepository.save(artist);
        try {
            if (hasFile(request.getArtistImage())) {
                saved.setArtistImage(uploadImage(
                        request.getArtistImage(),
                        "music/artistImage/" + saved.getArtistId()));
            }
            if (hasFile(request.getAlbumIamge())) {
                saved.setAlbumImage(uploadImage(
                        request.getAlbumIamge(),
                        "music/albumnImage/" + saved.getArtistId()));
            }
            saved = artistRepository.save(saved);
            return new GeneralResponse(toArtistResponse(saved));
        } catch (RuntimeException | IOException exception) {
            log.error("Failed to store artist images for artistId={}", saved.getArtistId(), exception);
            deleteIfPresent(saved.getArtistImage());
            deleteIfPresent(saved.getAlbumImage());
            artistRepository.delete(saved);
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Could not store artist or album image. No artist was created.");
        }
    }

    @Override
    public GeneralResponse updateArtist(ArtistRequest request) throws AppException {
        requireAdmin();
        Artist artist = artistRepository.findByArtistNameAndAlbumName(request.getArtistName(), request.getAlbumName());
        if (artist == null) {
            throw new AppException(ErrorCode.ARTIST_EXISTS,
                    Constant.THIS_ARTIST_WITH_NAME + request.getArtistName() + Constant.NOT_FOUND);
        }

        validateOptionalImage(request.getArtistImage(), "Artist image");
        validateOptionalImage(request.getAlbumIamge(), "Album image");

        String oldArtistImage = artist.getArtistImage();
        String oldAlbumImage = artist.getAlbumImage();
        try {
            if (hasFile(request.getArtistImage())) {
                artist.setArtistImage(uploadImage(
                        request.getArtistImage(), "music/artistImage/" + artist.getArtistId()));
            }
            if (hasFile(request.getAlbumIamge())) {
                artist.setAlbumImage(uploadImage(
                        request.getAlbumIamge(), "music/albumnImage/" + artist.getArtistId()));
            }
            artist.setAlbumName(request.getAlbumName());
            Artist saved = artistRepository.save(artist);

            if (hasFile(request.getArtistImage())) {
                deleteIfPresent(oldArtistImage);
            }
            if (hasFile(request.getAlbumIamge())) {
                deleteIfPresent(oldAlbumImage);
            }
            return new GeneralResponse(toArtistResponse(saved));
        } catch (IOException | RuntimeException exception) {
            log.error("Failed to update artist {}", artist.getArtistId(), exception);
            // Restore old object references if persistence/update fails.
            artist.setArtistImage(oldArtistImage);
            artist.setAlbumImage(oldAlbumImage);
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Could not update artist images. Please retry.");
        }
    }

    @Override
    public GeneralResponse viewArtist(Integer page, Integer size, String searchText)
            throws AppException {
        if (page == null || page < 0 || size == null || size < 1 || size > 100) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Page must be >= 0 and size must be between 1 and 100.");
        }
        final var responsePage = artistRepository.searchArtistsAndAlbums(
                searchText, PageRequest.of(page, size));

        final var data = responsePage.stream().map(this::toArtistResponse).toList();
        final var pagination = GaneralPagination.builder()
                .totalPage(responsePage.getTotalPages())
                .totalElement(responsePage.getTotalElements())
                .content(data)
                .build();
        return new GeneralResponse(pagination);
    }

    @Override
    public GeneralResponse deleteArtist(Integer artistId) throws AppException {
        requireAdmin();
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

        requireAdmin();

        if (request.getArtistId() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Artist ID is required.");
        }
        Artist selectedArtist = artistRepository.findById(request.getArtistId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        Constant.THIS_ARTIST_WITH_ID + request.getArtistId() + Constant.NOT_FOUND));
        if (Constant.STATUS_DEL.equals(selectedArtist.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Songs can only be uploaded to an active artist.");
        }
        if (request.getSongName() == null || request.getSongName().isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Song name is required.");
        }
        if (request.getSongType() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Song type is required.");
        }

        MultipartFile audio = request.getFile();
        if (audio == null || audio.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Audio file is required.");
        }
        if (audio.getSize() > MAX_AUDIO_BYTES) {
            throw new AppException(ErrorCode.FILE_SIZE_LIMIT_EXCEEDED,
                    "Audio file must not exceed 300 MB.");
        }
        if (audio.getContentType() == null || !audio.getContentType().toLowerCase().startsWith("audio/")) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Only audio files are allowed.");
        }

        MultipartFile image = request.getSongImage();
        validateRequiredImage(image, "Song cover image");
        String imageExtension = imageExtension(image.getContentType());

        UUID songId = UUID.randomUUID();
        String imageKey = "music/songImage/" + songId + imageExtension;

        // The original audio stays on local temporary storage only. HLS service
        // uploads encrypted HLS output to music/hls/{songId}/.
        try {
            hlsService.processSong(songId, audio);
        } catch (IOException | RuntimeException exception) {
            cleanupHlsPrefix(songId);
            log.error("Local audio processing/HLS upload failed for songId={}", songId, exception);
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "HLS processing failed. Check server logs and try again.");
        }

        try {
            uploadImageAtKey(image, imageKey);
            Song song = new Song();
            song.setSongId(songId);
            song.setArtistId(request.getArtistId());
            song.setSongName(request.getSongName());
            song.setSongType(request.getSongType());
            song.setUrlSong("music/hls/" + songId + "/master.m3u8");
            song.setSongImage(imageKey);
            songRepository.save(song);
            log.info("Song saved. songId={}, hlsKey={}, imageKey={}",
                    songId, song.getUrlSong(), imageKey);
            return new GeneralResponse(song);
        } catch (IOException | RuntimeException exception) {
            deleteIfPresent(imageKey);
            cleanupHlsPrefix(songId);
            log.error("Failed to save song metadata/image for songId={}", songId, exception);
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Song metadata or cover image could not be saved.");
        }
    }

    private void requireAdmin() {
        Integer userId = requestHeaderData.getUserId();
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHURIZED, "User must be authenticated.");
        }
        var user = userRepository.findById(userId);
        if (user.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, Constant.NOT_FOUND);
        }
        if (user.get().getPermission() == null
                || !Constant.PERMISSION_ADMIN.equals(user.get().getPermission().getPermission())) {
            throw new AppException(ErrorCode.ACCESS_DENIED,
                    Constant.NOT_ENOUGH_PERMISSION_PERMISSION);
        }
    }

    private ArtistResponse toArtistResponse(Artist artist) {
        ArtistResponse response = new ArtistResponse();
        response.setArtistId(artist.getArtistId());
        response.setArtistName(artist.getArtistName());
        response.setAlbumName(artist.getAlbumName());
        response.setArtistImage(artist.getArtistImage());
        response.setAlbumImage(artist.getAlbumImage());
        return response;
    }

    private boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private void validateOptionalImage(MultipartFile file, String label) {
        if (hasFile(file)) {
            validateRequiredImage(file, label);
            imageExtension(file.getContentType());
        }
    }

    private void validateRequiredImage(MultipartFile file, String label) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, label + " is required.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new AppException(ErrorCode.FILE_SIZE_LIMIT_EXCEEDED,
                    label + " must not exceed 20 MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new AppException(ErrorCode.BAD_REQUEST, label + " must be an image.");
        }
    }

    private String uploadImage(MultipartFile image, String prefix) throws IOException {
        String key = prefix + "/" + UUID.randomUUID() + imageExtension(image.getContentType());
        uploadImageAtKey(image, key);
        return key;
    }

    private void uploadImageAtKey(MultipartFile image, String key) throws IOException {
        String contentType = image.getContentType().toLowerCase();
        try (var input = image.getInputStream()) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromInputStream(input, image.getSize()));
        }
    }

    private String imageExtension(String contentType) {
        if (contentType == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Image content type is required.");
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/avif" -> ".avif";
            default -> throw new AppException(ErrorCode.BAD_REQUEST,
                    "Supported image formats: JPEG, PNG, WebP, GIF, AVIF.");
        };
    }

    private void deleteIfPresent(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException exception) {
            log.warn("Could not clean up R2 object {}", key, exception);
        }
    }

    @Override
    public GeneralResponse deleteSong(UUID songId)
            throws AppException, InvalidRequestException, InvalidWriteOffsetException, TooManyPartsException,
            EncryptionTypeMismatchException, S3Exception, AwsServiceException, SdkClientException, IOException {
        requireAdmin();

        if (songId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Song ID is required.");
        }

        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.NOT_FOUND,
                        Constant.THIS_SONG_WITH_ID + songId + Constant.NOT_FOUND));

        
        deleteR2Prefix("music/hls/" + songId + "/");
        deleteIfPresent(song.getSongImage());

        // Remove account-library references before deleting the catalog record.
        userFavoriteRepository.deleteAllBySongId(songId);
        List<PlaylistSong> affectedPlaylistRows = playlistSongRepository.findAllBySongId(songId);
        List<Long> affectedPlaylistIds = affectedPlaylistRows.stream()
                .map(PlaylistSong::getPlaylistId)
                .distinct()
                .toList();
        playlistSongRepository.deleteAllBySongId(songId);
        for (Long playlistId : affectedPlaylistIds) {
            List<PlaylistSong> remaining = playlistSongRepository
                    .findAllByPlaylistIdOrderByPositionAsc(playlistId);
            for (int index = 0; index < remaining.size(); index++) {
                remaining.get(index).setPosition(index);
            }
            playlistSongRepository.saveAll(remaining);
        }
        songRepository.delete(song);

        log.info("Song deleted. songId={}", songId);
        return new GeneralResponse();
    }

    private void cleanupHlsPrefix(UUID songId) {
        try {
            deleteR2Prefix("music/hls/" + songId + "/");
        } catch (RuntimeException cleanupException) {
            log.warn("Could not clean up incomplete HLS objects for songId={}", songId, cleanupException);
        }
    }

    private void deleteR2Prefix(String prefix) {
        String continuationToken = null;
        do {
            var requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .maxKeys(1000);
            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }

            var result = s3Client.listObjectsV2(requestBuilder.build());
            if (!result.contents().isEmpty()) {
                var objectIdentifiers = result.contents().stream()
                        .map(object -> ObjectIdentifier.builder().key(object.key()).build())
                        .toList();

                s3Client.deleteObjects(builder -> builder
                        .bucket(bucket)
                        .delete(Delete.builder().objects(objectIdentifiers).build()));
            }
            continuationToken = Boolean.TRUE.equals(result.isTruncated())
                    ? result.nextContinuationToken()
                    : null;
        } while (continuationToken != null);
    }

    @Override
    public GeneralResponse listSong(Integer page, Integer size, String searchText)
            throws AppException {
        if (page == null || page < 0 || size == null || size < 1 || size > 100) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Page must be >= 0 and size must be between 1 and 100.");
        }

        String normalizedSearch = searchText == null || searchText.isBlank()
                ? null
                : searchText.trim();

        PageRequest pageable = PageRequest.of(page, size);

        // Do not pass null into a native query parameter. Use the standard
        // paginated repository query when no search text was supplied.
        Page<Song> responsePage = normalizedSearch == null
                ? songRepository.findAll(pageable)
                
                : songRepository.searchSongs(normalizedSearch, pageable);

        var pagination = GaneralPagination.<Song>builder()
                .totalPage(responsePage.getTotalPages())
                .totalElement(responsePage.getTotalElements())
                .content(responsePage.getContent())
                .build();

        return new GeneralResponse(pagination);
    }
}
