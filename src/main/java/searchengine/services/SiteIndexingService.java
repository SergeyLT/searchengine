package searchengine.services;

import searchengine.dto.response.CommonResponse;

public interface SiteIndexingService {
    CommonResponse indexAllSites();
    CommonResponse stopIndexing() throws InterruptedException;

    CommonResponse indexPageByPath(String url) throws Exception;
}
