package user.music_service.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import user.music_service.dto.GeneralResponse;

@ControllerAdvice
public class GloubleHanlder {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<GeneralResponse> appException(AppException ex) {
        final var errorCodes = ex.getErrorCodes();
        final var response = new GeneralResponse(errorCodes.getStatus(), errorCodes.getMessage());
        return ResponseEntity.status(errorCodes.getStatus()).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GeneralResponse> exception(Exception e){
        final var response=new GeneralResponse(HttpStatus.INTERNAL_SERVER_ERROR,e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

}
