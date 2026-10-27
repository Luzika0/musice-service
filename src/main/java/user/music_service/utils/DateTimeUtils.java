package user.music_service.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class DateTimeUtils {
    private DateTimeUtils() {

    }

    public static LocalDateTime KHMER_LOCAL_DATE_TIME() {
        return LocalDateTime.now(ZoneId.of("Asia/Phnom_Penh"));
    }
}
