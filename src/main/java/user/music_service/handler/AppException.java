package user.music_service.handler;

import lombok.Getter;
import user.music_service.enums.ErrorCode;

@Getter
public class AppException extends RuntimeException {
    private final ErrorCode errorCodes;

    public AppException(ErrorCode errorCodes, String message) {
        super(message);
        this.errorCodes = errorCodes;
    }
}
