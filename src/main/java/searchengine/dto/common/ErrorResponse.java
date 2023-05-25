package searchengine.dto.common;

import lombok.Data;

@Data
public class ErrorResponse implements CommonResponse {
    private boolean result;
    private String error;
}
