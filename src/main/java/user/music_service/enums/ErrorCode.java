package user.music_service.enums;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT(400, "The provided request data is invalid or malformed.", HttpStatus.BAD_REQUEST),
    NOT_FOUND(404, "This Artist not found.", HttpStatus.NOT_FOUND),
    UNAUTHURIZED(401, "unauthorized.", HttpStatus.UNAUTHORIZED),
    REVOKED(403, "Token revoked.", HttpStatus.FORBIDDEN),
    EXPIRED(401, "Token expired.", HttpStatus.UNAUTHORIZED),
    BAD_REQUEST(400, "The request could not be understood or was missing required parameters.", HttpStatus.BAD_REQUEST),
    ACCESS_DENIED(403, "Access is denied for this resource.", HttpStatus.FORBIDDEN),
    FILE_SIZE_LIMIT_EXCEEDED(413, "The uploaded file exceeds the maximum allowed size limit.",
            HttpStatus.CONTENT_TOO_LARGE),
    ARTIST_EXISTS(409, "This Artist already exists.", HttpStatus.CONFLICT);

    private final Integer code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(Integer code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getStatus() {
        return status;
    }

}
