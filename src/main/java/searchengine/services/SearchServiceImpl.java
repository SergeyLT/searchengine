package searchengine.services;

import lombok.RequiredArgsConstructor;
import net.sf.saxon.ma.trie.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import searchengine.config.SearchResultSettings;
import searchengine.data.search.PageRank;
import searchengine.dto.request.SearchRequest;
import searchengine.dto.response.CommonResponse;
import searchengine.dto.response.ErrorResponse;
import searchengine.data.search.SearchData;
import searchengine.dto.response.SearchResponse;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.LemmaParser;
import searchengine.utils.WebPageParser;
import searchengine.utils.SnippetCreator;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    Logger logger = LoggerFactory.getLogger(SearchServiceImpl.class);

    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SearchResultSettings searchResultSettings;

    @Override
    public CommonResponse search(SearchRequest request) throws IOException {
        List<SiteEntity> sitesList = new ArrayList<>();
        String checkRequestString = checkRequest(request, sitesList);
        if (!checkRequestString.isEmpty()) {
            ErrorResponse errorResponse = new ErrorResponse();
            errorResponse.setError(checkRequestString);
            return errorResponse;
        }

        Map<String,Set<String>> mapLemmas = LemmaParser.getInstance()
                .getLemmaSet(request.getQuery());
        Set<String> lemmas = getLemmaSetByMap(mapLemmas);

        Tuple2<Integer,List<SearchData>> searchDataInfo = getSearchResult(lemmas, request, sitesList);
        List<SearchData> searchData = searchDataInfo._2;
        int countResults = searchDataInfo._1;

        SearchResponse response = new SearchResponse();
        response.setResult(true);
        response.setCount(countResults);
        response.setData(searchData);
        return response;
    }

    private Set<String> getLemmaSetByMap(Map<String, Set<String>> mapLemmas) {
        Set<String> lemmaSet = new HashSet<>();
        for (Map.Entry<String, Set<String>> wordLemmas : mapLemmas.entrySet()) {
            Set<String> tempSet = wordLemmas.getValue();
            String lemma;

            if (tempSet.size() > 1) {
                List<String> lemmas= lemmaRepository.findLemmaByLemmasOrderByFrequency(tempSet);
                lemma = lemmas.isEmpty() ? tempSet.iterator().next() : lemmas.get(0);
            } else {
                lemma = tempSet.iterator().next();
            }

            lemmaSet.add(lemma);
        }
        return lemmaSet;
    }


    private Tuple2<Integer,List<SearchData>>  getSearchResult(Set<String> searchLemmas, SearchRequest request
            , List<SiteEntity> sitesList) {
        List<LemmaEntity> lemmas = lemmaRepository.findLemmaByLemmasAndSites(searchLemmas, sitesList);

        deleteNotFullLemmasSites(sitesList, lemmas, searchLemmas.size());

        if(lemmas.isEmpty()) {
            return new Tuple2<>(0, new ArrayList<>());
        }
        lemmas.forEach(l -> logger.info(l.getLemma() + ":" + l.getFrequency()+":"+l.getSite().getUrl()));

        Map<SiteEntity, List<Integer>> pagesBySite = getPagesBySiteMap(lemmas);
        if (pagesBySite.isEmpty()) {
            return new Tuple2<>(0, new ArrayList<>());
        }

        List<Integer> pagesId = pagesBySite.values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        List<PageRank> pagesWithRank = indexRepository
                .findPagesWithSumRankByLemmasAndPagesId(lemmas, pagesId);
        pagesWithRank.forEach(pr -> logger.info(pr.getPage().getPath() + ":" + pr.getRank()));

        double maxRank = pagesWithRank.get(0).getRank();
        pagesWithRank.forEach(pr -> pr.setRank(pr.getRank() / maxRank));
        pagesWithRank.forEach(pr -> logger.info(pr.getPage().getPath() + ":" + pr.getRank()));
        int countPages = pagesWithRank.size();

        return new Tuple2<>(countPages
                , createSearchDataList(getPagesSubListByLimits(pagesWithRank, request), lemmas));
    }

    private void deleteNotFullLemmasSites(List<SiteEntity> sitesList, List<LemmaEntity> lemmas, int searchLemmasCount) {
        if(lemmas.isEmpty()) {
            return;
        }

        for (SiteEntity siteEntity : sitesList) {
            Set<LemmaEntity> lemmasBySite = lemmas.stream().filter(l -> l.getSite().getId() == siteEntity.getId())
                    .collect(Collectors.toSet());
            if (lemmasBySite.size() < searchLemmasCount) {
                lemmas.removeIf(l -> lemmasBySite.contains(l));
            }
        }
    }

    private Map<SiteEntity, List<Integer>> getPagesBySiteMap(List<LemmaEntity> lemmas) {
        Map<SiteEntity, List<Integer>> pagesBySite = new LinkedHashMap<>();
        List<Integer> newPagesId = new ArrayList<>();
        newPagesId.add(0);
        int stopCheckedSiteId = 0;
        int countLemmasBySite = 0;
        for (LemmaEntity lemma : lemmas) {
            SiteEntity site = lemma.getSite();
            if (stopCheckedSiteId == site.getId()) {
                continue;
            }

            boolean isSiteExistInMap = pagesBySite.containsKey(site);
            List<Integer> pagesId = isSiteExistInMap? pagesBySite.get(site) : newPagesId;
            int searchNewPages = isSiteExistInMap? 0 : 1;
            countLemmasBySite = isSiteExistInMap? ++countLemmasBySite : 1;

            pagesId = findPagesId(new PagesIdProperty(pagesId, pagesBySite, site), lemma, searchNewPages);

            if (pagesId.isEmpty()) {
                stopCheckedSiteId = site.getId();
                continue;
            }

            if (isEnoughPages(new PagesIdProperty(pagesId, pagesBySite, site), countLemmasBySite, lemma)) {
                stopCheckedSiteId = site.getId();
                continue;
            }

            pagesBySite.put(site, pagesId);
            logger.info(lemma.getLemma() + ":" + pagesId);
        }

        return pagesBySite;
    }

    private List<Integer> findPagesId(PagesIdProperty pagesIdProperty, LemmaEntity lemma, int searchNewPages) {
        List<Integer> pagesId = indexRepository
                .findPageIdByLemmaAndPages(lemma, pagesIdProperty.pagesId, searchNewPages);
        if (pagesId.isEmpty()) {
            pagesIdProperty.pagesBySite.remove(pagesIdProperty.site);
            logger.info(pagesIdProperty.site.getUrl() + " is removed cause not found " + lemma.getLemma());
        }
        return pagesId;
    }

    private boolean isEnoughPages(PagesIdProperty pagesIdProperty, int countLemmasBySite, LemmaEntity lemma) {
        boolean isSiteExistInMap = pagesIdProperty.pagesBySite.containsKey(pagesIdProperty.site);

        int searchPageStartCountLimit = searchResultSettings.getSearchPageStartCountLimit();
        boolean isTrimPageList = !isSiteExistInMap && pagesIdProperty.pagesId.size() > searchPageStartCountLimit;
        while (isTrimPageList && pagesIdProperty.pagesId.size() > searchPageStartCountLimit) {
            pagesIdProperty.pagesId.remove(pagesIdProperty.pagesId.size() - 1);
        }

        boolean isSameCountPages = isSiteExistInMap
                && pagesIdProperty.pagesBySite.get(pagesIdProperty.site).size() == pagesIdProperty.pagesId.size();
        boolean isManyPages = countLemmasBySite > searchResultSettings.getSearchLemmasCountForPageLimit()
                && lemma.getFrequency() > searchResultSettings.getSearchPageCountLimit();

        return pagesIdProperty.pagesId.isEmpty() || isSameCountPages || isManyPages;
    }

    private List<PageRank> getPagesSubListByLimits(List<PageRank> pagesWithRank, SearchRequest request) {
        return new ArrayList<>(pagesWithRank.stream()
                .skip(request.getOffset())
                .limit(request.getLimit())
                .collect(Collectors.toList()));
    }

    private List<SearchData> createSearchDataList(List<PageRank> pagesWithRank, List<LemmaEntity> lemmas) {
        List<SearchData> searchDataList = new ArrayList<>();

        pagesWithRank.forEach(pr -> {
            PageEntity page = pr.getPage();
            SearchData data = new SearchData();
            data.setRelevance(pr.getRank());
            data.setUri(page.getPath());
            data.setTitle(WebPageParser.getTitleFromHTML(page.getContent()));

            String snippet = "";
            try{
                snippet = createSnippetForPage(page, lemmas);
            } catch (Exception e) {
            }
            data.setSnippet(snippet);

            data.setSite(page.getSite().getUrl());
            data.setSiteName(page.getSite().getName());
            searchDataList.add(data);
        });

        return searchDataList;
    }

    private String createSnippetForPage(PageEntity page, List<LemmaEntity> lemmas) throws IOException {
        Set<String> lemmasSet = lemmas.stream().map(LemmaEntity::getLemma)
                .collect(Collectors.toSet());

        String snippet = "";
        String pageText = WebPageParser.getTextFromHTML(page.getContent());

        return new SnippetCreator(searchResultSettings).getSnippetWithParts(pageText, lemmasSet, snippet);
    }

    private String checkRequest(SearchRequest request, List<SiteEntity> sitesList) {
        if (request.getQuery() == null || request.getQuery().isEmpty()) {
            return "Задан пустой поисковый запрос";
        }

        int checkAllSites = request.getSite() == null
                || request.getSite().isEmpty() ? 1 : 0;
        List<SiteEntity> indexedSites = siteRepository.findIndexedSites(request.getSite(),checkAllSites);

        if (indexedSites.isEmpty()) {
            return "Сайты для поиска не проиндексированы";
        }

        if (request.getLimit() == 0) {
            request.setLimit(searchResultSettings.getDefaultSearchResultLimit());
        }

        sitesList.addAll(indexedSites);
        return "";
    }

    private record PagesIdProperty(List<Integer> pagesId , Map<SiteEntity
            , List<Integer>> pagesBySite, SiteEntity site) {
    }
}
