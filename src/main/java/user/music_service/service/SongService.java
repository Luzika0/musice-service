package user.music_service.service;

import java.io.IOException;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.EncryptionTypeMismatchException;
import software.amazon.awssdk.services.s3.model.InvalidRequestException;
import software.amazon.awssdk.services.s3.model.InvalidWriteOffsetException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.TooManyPartsException;
import user.music_service.dto.GeneralResponse;
import user.music_service.dto.request.ArtistRequest;
import user.music_service.dto.request.SongRequest;
import user.music_service.handler.AppException;

public interface SongService {
    GeneralResponse createArtist(ArtistRequest request) throws AppException;

    GeneralResponse updateArtist(ArtistRequest request) throws AppException;

    GeneralResponse viewArtist(Integer page, Integer size, String searchText) throws AppException;

    GeneralResponse deleteArtist(Integer artistId) throws AppException;

    GeneralResponse deleteSong(Integer songId)
            throws AppException, InvalidRequestException, InvalidWriteOffsetException, TooManyPartsException,
            EncryptionTypeMismatchException, S3Exception, AwsServiceException, SdkClientException, IOException;

    GeneralResponse listSong(Integer page, Integer size, String searchText)
            throws AppException;

    GeneralResponse uploadSongs(SongRequest request)
            throws AppException, InvalidRequestException, InvalidWriteOffsetException, TooManyPartsException,
            EncryptionTypeMismatchException, S3Exception, AwsServiceException, SdkClientException, IOException;

}