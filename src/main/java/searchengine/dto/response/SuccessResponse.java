package searchengine.dto.response;

import lombok.Data;

@Data
public class SuccessResponse  implements CommonResponse {
    private boolean result = true;
}
