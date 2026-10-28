package user.music_service.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
@JsonPropertyOrder({ "totalPage", "totalElement", "data" })
public class GaneralPagination<T> {

    private Integer totalPage;

    private Long totalElement;

    private Object content;

}
