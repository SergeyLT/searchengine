package searchengine.dto.request;

import lombok.Data;

@Data
public class SearchRequest {
    private String query;
    private int offset;
    private int limit;
    private String site;
}
