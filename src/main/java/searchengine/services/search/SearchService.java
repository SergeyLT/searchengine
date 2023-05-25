package searchengine.services.search;

import searchengine.dto.SearchRequest;
import searchengine.dto.common.CommonResponse;

import java.io.IOException;

public interface SearchService {
    CommonResponse search(SearchRequest request) throws IOException;
}
