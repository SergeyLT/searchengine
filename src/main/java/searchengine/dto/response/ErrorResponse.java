package searchengine.dto.response;

import lombok.Data;

@Data
public class ErrorResponse implements CommonResponse {
    private boolean result;
    private String error;
}
