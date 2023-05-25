package searchengine.services.siteindexing;

import searchengine.dto.common.CommonResponse;

public interface SiteIndexingService {
    CommonResponse indexAllSites();
    CommonResponse stopIndexing() throws InterruptedException;

    CommonResponse indexPageByPath(String url) throws Exception;
}
