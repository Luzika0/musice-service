package user.music_service.dto.request;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RequestHeaderInterceptor implements HandlerInterceptor {

    private final RequestHeaderData requestHeaderData;

    public RequestHeaderInterceptor(RequestHeaderData requestHeaderData) {
        this.requestHeaderData = requestHeaderData;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        String userIdHeader = request.getHeader("X-User-Id");
        String playBackTokenHeader = request.getHeader("playBack-token");

        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                int userId = Integer.parseInt(userIdHeader);
                if (userId <= 0) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return false;
                }
                requestHeaderData.setUserId(userId);
            } catch (NumberFormatException exception) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            }
        }

        if (playBackTokenHeader != null && !playBackTokenHeader.isBlank()) {
            requestHeaderData.setPlayBackToken(playBackTokenHeader);
        }

        return true;
    }
}
