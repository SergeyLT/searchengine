package searchengine.dto.response;

import lombok.Data;
import searchengine.data.search.SearchData;

import java.util.List;

@Data
public class SearchResponse implements CommonResponse {
    private boolean result;
    private int count;
    private List<SearchData> data;
}
