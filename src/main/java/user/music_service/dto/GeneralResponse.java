package user.music_service.dto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;
import user.music_service.constant.Constant;

@Data
@Component
@JsonPropertyOrder({
        "statusCode",
        "message",
        "data"
})
public class GeneralResponse {
    private Integer statusCode;
    private String message;
    private Object data;

    @Autowired
    public GeneralResponse() {
        this.statusCode = HttpStatus.OK.value();
        this.message = Constant.SUCESS;
        this.data = null;
    }

    public GeneralResponse(Object object) {
        this.statusCode = HttpStatus.OK.value();
        this.message = Constant.SUCESS;
        this.data = object;
    }

    public GeneralResponse(HttpStatus statusCode, String message) {
        this.statusCode = statusCode.value();
        this.message = message;
        this.data = null;
    }
}
