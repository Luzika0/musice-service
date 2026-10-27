package user.music_service.service;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import user.music_service.handler.AppException;

public interface HlsService {

    void processSong(UUID songId, String sourceObjectKey) throws IOException;

    ResponseEntity<StreamingResponseBody> getAsset(UUID songId, String assetName) throws AppException;
}
