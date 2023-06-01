package searchengine.dto.response;

import lombok.Data;
import searchengine.dto.response.CommonResponse;

@Data
public class SuccessResponse  implements CommonResponse {
    private boolean result = true;
}
