package searchengine.dto.response;

import lombok.Data;
import searchengine.dto.response.CommonResponse;

@Data
public class ErrorResponse implements CommonResponse {
    private boolean result;
    private String error;
}
