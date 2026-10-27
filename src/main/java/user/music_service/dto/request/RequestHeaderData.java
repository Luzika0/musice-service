package user.music_service.dto.request;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class RequestHeaderData {

    private Integer userId;
    private String playBackToken;

    public Integer getUserId() {
        return userId;
    }

    public String getPlayBackToken() {
        return playBackToken;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public void setPlayBackToken(String playBackToken) {
        this.playBackToken = playBackToken;
    }
}