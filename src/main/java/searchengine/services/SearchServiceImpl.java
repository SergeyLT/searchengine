package searchengine.services;

import lombok.RequiredArgsConstructor;
import net.sf.saxon.ma.trie.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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
    private final int DEFAULT_SEARCH_RESULT_LIMIT = 20;
    private final int SEARCH_PAGE_COUNT_LIMIT = 2500;
    private final int SEARCH_PAGE_START_COUNT_LIMIT = 4000;
    private final int SEARCH_LEMMAS_COUNT_FOR_PAGE_LIMIT = 3;

    @Override
    public CommonResponse search(SearchRequest request) throws IOException {
        List<SiteEntity> sitesList = new ArrayList<>();
        String checkRequestString = checkRequest(request, sitesList);
        if (!checkRequestString.isEmpty()) {
            ErrorResponse errorResponse = new ErrorResponse();
            errorResponse.setError(checkRequestString);
            return errorResponse;
        }

        Set<String> lemmas = LemmaParser.getInstance()
                .getLemmaSet(request.getQuery());

        Tuple2<Integer,List<SearchData>> searchDataInfo = getSearchResult(lemmas, request, sitesList);
        List<SearchData> searchData = searchDataInfo._2;
        int countResults = searchDataInfo._1;

        SearchResponse response = new SearchResponse();
        response.setResult(true);
        response.setCount(countResults);
        response.setData(searchData);
        return response;
    }

    private Tuple2<Integer,List<SearchData>>  getSearchResult(Set<String> searchLemmas, SearchRequest request
            , List<SiteEntity> sitesList) {
        List<LemmaEntity> lemmas = lemmaRepository.findLemmaByLemmasAndSites(searchLemmas, sitesList);
        if(lemmas.isEmpty()) {
            return new Tuple2<>(0, new ArrayList<>());
        }
        lemmas.forEach(l -> logger.info(l.getLemma() + ":" + l.getFrequency()+":"+l.getSite().getUrl()));

        Map<SiteEntity, List<Integer>> pagesBySite = getPagesBySiteMap(lemmas);

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

            pagesId = indexRepository.findPageIdByLemmaAndPages(lemma, pagesId, searchNewPages);
            boolean isTrimPageList = !isSiteExistInMap && pagesId.size() > SEARCH_PAGE_START_COUNT_LIMIT;
            pagesId = isTrimPageList ? pagesId.subList(0, SEARCH_PAGE_START_COUNT_LIMIT) : pagesId;
            boolean isSameCountPages = isSiteExistInMap && pagesBySite.get(site).size() == pagesId.size();
            boolean isManyPages = countLemmasBySite > SEARCH_LEMMAS_COUNT_FOR_PAGE_LIMIT
                    && lemma.getFrequency() > SEARCH_PAGE_COUNT_LIMIT;
            if (pagesId.isEmpty() || isSameCountPages || isManyPages) {
                stopCheckedSiteId = site.getId();
                continue;
            }

            pagesBySite.put(site, pagesId);
            logger.info(lemma.getLemma() + ":" + pagesId);
        }

        return pagesBySite;
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
        String description = WebPageParser.getDescriptionFromHTML(page.getContent());

        Set<String> lemmasSet = lemmas.stream().map(LemmaEntity::getLemma)
                .collect(Collectors.toSet());

        String snippet = "";
        if (description.length() <= SnippetCreator.SNIPPET_MAX_SIZE) {
            snippet = SnippetCreator.getSnippetFullText(description, lemmasSet);
        }
        if (!snippet.isEmpty()) {
            return snippet;
        }

        snippet = SnippetCreator.getSnippetWithParts(description, lemmasSet, "");
        int snippetLength = snippet.length();
        if (snippetLength >= SnippetCreator.SNIPPET_MAX_SIZE) {
            return snippet;
        }

        String pageText = WebPageParser.getTextFromHTML(page.getContent());

        return SnippetCreator.getSnippetWithParts(pageText, lemmasSet, snippet);
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
            request.setLimit(DEFAULT_SEARCH_RESULT_LIMIT);
        }

        sitesList.addAll(indexedSites);
        return "";
    }
}
