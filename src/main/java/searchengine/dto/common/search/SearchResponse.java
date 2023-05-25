package searchengine.dto.common.search;

import lombok.Data;
import searchengine.dto.common.CommonResponse;

import java.util.List;

@Data
public class SearchResponse implements CommonResponse {
    private boolean result;
    private int count;
    private List<SearchData> data;
}
