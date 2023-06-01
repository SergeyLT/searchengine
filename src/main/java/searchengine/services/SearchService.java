package searchengine.services;

import searchengine.dto.request.SearchRequest;
import searchengine.dto.response.CommonResponse;

import java.io.IOException;

public interface SearchService {
    CommonResponse search(SearchRequest request) throws IOException;
}
