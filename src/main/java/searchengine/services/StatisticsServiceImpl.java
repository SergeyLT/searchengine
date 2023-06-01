package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.data.config.Site;
import searchengine.config.SitesList;
import searchengine.data.statistics.DetailedStatisticsItem;
import searchengine.data.statistics.StatisticsData;
import searchengine.dto.response.StatisticsResponse;
import searchengine.data.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.data.siteindexing.SiteIndexingStatus;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.SiteIndexingServiceImpl;
import searchengine.services.StatisticsService;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    Logger logger = LoggerFactory.getLogger(SiteIndexingServiceImpl.class);

    private final String[] STATUSES = { "INDEXED", "FAILED", "INDEXING" };
    private final String[] ERRORS = {
            "Ошибка индексации: главная страница сайта не доступна",
            "Ошибка индексации: сайт не доступен",
            ""
    };
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    @Value("${localTimeZone}")
    private String localTimeZone;

    @Override
    public StatisticsResponse getStatistics() {
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = getStatisticsData();
        response.setStatistics(data);
        response.setResult(data != null);
        return response;
    }

    private StatisticsData getStatisticsData() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();

        if (sitesList == null) {return null;}

        sitesList.forEach(site -> {
            DetailedStatisticsItem item = createDefaultStatisticItem(site);
            detailed.add(item);
            total.setIndexing(false);
        });

        Map<String, SiteEntity> sitesMap;
        try{
            sitesMap = findSiteEntitiesByConfigSites();

            detailed.forEach(i -> {
                if (sitesMap.containsKey(i.getUrl())) {
                    SiteEntity site = sitesMap.get(i.getUrl());
                    updateStatisticItemFromSiteEntity(i, site, total);

                    updatePagesCount(i, site, total);
                    updateLemmasCount(i, site, total);
                }
            });
        } catch (Exception e) {
            logger.error("Error with getting  statistics: " + e.getClass() + " - " + e.getMessage());
        }

        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);

        return data;
    }

    private DetailedStatisticsItem createDefaultStatisticItem(Site site) {
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setName(site.getName());
        item.setUrl(site.getUrl());
        item.setError(null);
        item.setStatus("");
        return item;
    }

    private Map<String, SiteEntity> findSiteEntitiesByConfigSites(){
        return siteRepository.findSitesByURLList(
                sites.getSites().stream().map(Site::getUrl).toList()
        ).stream().collect(Collectors.toMap(SiteEntity::getUrl,s -> s));
    }

    private void updateStatisticItemFromSiteEntity(DetailedStatisticsItem item, SiteEntity site
            , TotalStatistics total) {
        item.setStatusTime(site.getStatusTime().toEpochSecond(ZoneOffset.of(localTimeZone)));
        item.setStatus(site.getStatus().toString());
        item.setError(site.getLastError());

        if(site.getStatus() == SiteIndexingStatus.INDEXING) {
            total.setIndexing(true);
        }
    }

    private void updatePagesCount(DetailedStatisticsItem item, SiteEntity site, TotalStatistics total) {
        int pagesCount = pageRepository.countBySite(site);
        item.setPages(pagesCount);
        total.setPages(total.getPages() + pagesCount);
    }

    private void updateLemmasCount(DetailedStatisticsItem item, SiteEntity site, TotalStatistics total) {
        int lemmasCount = lemmaRepository.countBySite(site);
        item.setLemmas(lemmasCount);
        total.setLemmas(total.getLemmas() + lemmasCount);
    }

}
