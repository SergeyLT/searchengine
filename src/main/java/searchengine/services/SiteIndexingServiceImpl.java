package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.jsoup.HttpStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import searchengine.config.JsoupSettings;
import searchengine.data.config.Site;
import searchengine.config.SitesList;
import searchengine.data.siteindexing.InputSiteIndexingLink;
import searchengine.data.siteindexing.PageParsingInfo;
import searchengine.data.siteindexing.SiteIndexingStatus;
import searchengine.dto.response.CommonResponse;
import searchengine.dto.response.ErrorResponse;
import searchengine.dto.response.SuccessResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.LemmaParser;
import searchengine.utils.WebPageParser;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SiteIndexingServiceImpl implements SiteIndexingService {
    Logger logger = LoggerFactory.getLogger(SiteIndexingServiceImpl.class);
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SitesList sites;
    private final JsoupSettings jsoupSettings;
    private List<Thread> siteIndexingThreads;
    private AtomicBoolean allowIndexing = new AtomicBoolean();
    private int MAX_RETRY_INSERT_COUNT = 20;
    @Override
    public CommonResponse indexAllSites() {
        if(isIndexingActive()) {
            return getErrorResponse("Индексация уже запущена");
        }

        allowIndexing.set(true);
        clearDb();

        siteIndexingThreads = new ArrayList<>();
        sites.getSites().forEach(s -> siteIndexingThreads.add(new Thread(() -> {
            SiteEntity site = createSiteEntity(s);
            try {
                indexSite(site);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        })));

        siteIndexingThreads.forEach(Thread::start);

        return new SuccessResponse();
    }

    private ErrorResponse getErrorResponse(String errorText) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setError(errorText);
        return errorResponse;
    }

    private void clearDb() {
        indexRepository.deleteAllIndices();
        lemmaRepository.deleteAllLemmas();
        pageRepository.deleteAllPages();
        siteRepository.deleteAllSites();
    }

    @Override
    public CommonResponse stopIndexing() throws InterruptedException {
        allowIndexing.set(false);

        if (!isIndexingActive() && isSiteInfoUpdateEnd()) {
            return getErrorResponse("Индексация не запущена");
        }

        while(isIndexingActive()) {
            Thread.sleep(10);
        }

        siteRepository.updateStoppedIndexingSite(SiteIndexingStatus.FAILED,"Индексация остановлена пользователем");

        return new SuccessResponse();
    }

    private boolean isSiteInfoUpdateEnd() {
        List<SiteIndexingStatus> statusList = siteRepository.getAllStatus();
        return !statusList.stream()
                .filter(s -> s == SiteIndexingStatus.INDEXING)
                .findFirst().isPresent();
    }

    @Override
    public CommonResponse indexPageByPath(String url) throws Exception {
        url = java.net.URLDecoder.decode(url, StandardCharsets.UTF_8);

        SiteEntity site = null;
        String siteURL = WebPageParser.getDomainLink(url);
        Optional<Site> siteConfInfo = sites.getSites().stream().filter(s -> s.getUrl().equals(siteURL)).findFirst();

        if (siteConfInfo.isEmpty()) {
            return getErrorResponse("Данная страница находится за пределами сайтов,"
                    + " указанных в конфигурационном файле");
        }

        try{
            site = siteRepository.findByUrl(siteURL);
            if (site == null) {
                site = createSiteEntity(siteConfInfo.get());
                siteRepository.save(site);
            }

            InputSiteIndexingLink inputLinks = new InputSiteIndexingLink(site, url, "");

            indexPage(inputLinks, false);
        } catch(Exception e) {
            return processPageIndexingError (e, site, url);
        }

        updateSiteEntityStatus(site, SiteIndexingStatus.INDEXED, null);
        return new SuccessResponse();
    }

    private ErrorResponse processPageIndexingError (Exception e, SiteEntity site, String url) throws Exception {
        boolean statusError = e instanceof HttpStatusException ? true : false;
        String errorText = statusError ? "Ошибка запроса страницы: "
                + ((HttpStatusException)e).getStatusCode() : "Ошибка обработки страницы";

        if (site != null) {
            updateSiteEntityStatus(site, SiteIndexingStatus.FAILED, errorText + " - " + url);
        }

        if (statusError) {
            return getErrorResponse(errorText);
        } else {
            throw e;
        }
    }

    private void indexSite(SiteEntity site) throws InterruptedException {
        siteRepository.save(site);
        String error;

        int coreCount = Runtime.getRuntime().availableProcessors();
        RecursiveSiteParser loaderFork = new RecursiveSiteParser(new InputSiteIndexingLink(site,site.getUrl()
                ,WebPageParser.createDomainPageLinkMask(site.getUrl())),0);
        try {
            new ForkJoinPool(coreCount).invoke(loaderFork);
        } catch (Exception e) {
            logger.error("Error of fork execute: " + e.getMessage());
            allowIndexing.set(false);
            updateSiteEntityStatus(site,SiteIndexingStatus.FAILED,"Ошибка индексации");
            return;
        }

        if (site.getStatus() != SiteIndexingStatus.INDEXING) {
            return;
        }

        error = !allowIndexing.get() ? "Индексация остановлена пользователем" : site.getLastError();

        SiteIndexingStatus status = error == null ? SiteIndexingStatus.INDEXED
                : SiteIndexingStatus.FAILED;
        updateSiteEntityStatus(site,status,error);
    }

    private boolean isIndexingActive() {
        return !(siteIndexingThreads == null || (siteIndexingThreads.stream().filter(Thread::isAlive).count() == 0));
    }

    private Set<String> indexPage(InputSiteIndexingLink inputLinks, boolean isSiteIndexing)
            throws IOException, InterruptedException {
        logger.info("Start indexing page " + inputLinks.link());

        PageParsingInfo pageInfo;
        String pageLink = WebPageParser.getRelativeLink(WebPageParser
                .closeLinkSlash(new StringBuilder(inputLinks.link())).toString());

        PageEntity page = getPageForIndexing(pageLink,inputLinks, isSiteIndexing);
        if (page == null) {
            return null;
        }

        WebPageParser webPageParser = new WebPageParser(inputLinks, jsoupSettings);
        pageInfo = webPageParser.getPageInfo();

        page.setContent(pageInfo.getContent());
        page.setCode(pageInfo.getStatus());

        if (pageInfo.getStatus() != 200 && !isSiteIndexing) {
            throw new HttpStatusException("Error in indexing page",pageInfo.getStatus(),inputLinks.link());
        }

        try{
            pageRepository.save(page);
        } catch(DataIntegrityViolationException | ConstraintViolationException e) {
            logger.error("Save page: " + e.getMessage());
            return null;
        }

        if (isSiteIndexing) {
            getNewLinks(pageInfo.getSubLinks());
        }

        saveLemmas(page, pageInfo.getContent(), isSiteIndexing);

        logger.info("End indexing page " + inputLinks.link());
        return pageInfo.getSubLinks();
    }

    private void getNewLinks(Set<String> links) {
        if (links == null) {return;}

        Set<String> existedLinks = pageRepository.findPagesByPathList(links.stream()
                        .map(l -> WebPageParser.getRelativeLink(l)).toList()).stream()
                 .map(PageEntity::getPath).collect(Collectors.toSet());
        links.removeIf(l -> existedLinks.contains(WebPageParser.getRelativeLink(l)));
    }

    private SiteEntity createSiteEntity(Site siteInfo) {
        SiteEntity site = new SiteEntity();
        site.setName(siteInfo.getName())
                .setUrl(siteInfo.getUrl())
                .setStatus(SiteIndexingStatus.INDEXING)
                .setStatusTime(LocalDateTime.now());
        return site;
    }

    private void updateSiteEntityStatus(SiteEntity site, SiteIndexingStatus status, String error) {
        site.setStatus(status)
                .setLastError(error)
                .setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }

    private PageEntity getPageForIndexing(String pageLink, InputSiteIndexingLink inputLinks, boolean unique) {
        PageEntity page = pageRepository.findByPathAndSite(pageLink, inputLinks.site());
        if (page != null && unique) {
            return null;
        }

        if (page != null) {
            List<Integer> lemmasId = indexRepository.findLemmasIdByPage(page.getId());
            if (!lemmasId.isEmpty()) {
                lemmaRepository.decreaseFrequencyById(lemmasId);
                lemmaRepository.deleteZeroLemmas(lemmasId);
            }
            indexRepository.deleteByPage(page);
            pageRepository.delete(page);
        }

        page = new PageEntity();
        page.setSite(inputLinks.site());
        page.setPath(pageLink);

        return page;
    }

    private void saveLemmas(PageEntity page, String content, boolean isSiteIndexing)
            throws IOException, InterruptedException {
        if (page == null || content == null) {
            return;
        }

        Map<String, Integer> lemmas = LemmaParser.getInstance()
                .collectLemmas(WebPageParser.getTextFromHTML(content));

        if (lemmas.isEmpty()) {
            return;
        }

        if (!insertLemmas(page, lemmas, isSiteIndexing)) {
            return;
        }

        insertIndices(page, lemmas);
    }

    private boolean insertLemmas(PageEntity page, Map<String, Integer> lemmas, boolean isSiteIndexing)
            throws InterruptedException {
        boolean isLemmasSaved;
        List<String> queryLemmas;
        if (isSiteIndexing) {
            queryLemmas = lemmaRepository.getSaveLemmasQueriesMasInsert(lemmas.keySet(), page.getSite());
        } else {
            lemmaRepository.saveLemmas(lemmas.keySet(), page.getSite());
            return true;
        }

        for (String queryLemma : queryLemmas) {
            isLemmasSaved = false;
            int tryCount = 0;
            while (!isLemmasSaved && tryCount < MAX_RETRY_INSERT_COUNT) {
                if(!allowIndexing.get()) {
                    return false;
                }

                isLemmasSaved = insertLemma(page, tryCount, queryLemma);
                tryCount = !isLemmasSaved ? ++tryCount : tryCount;
            }

            if (!isLemmasSaved) {
                logger.error("Lemmas not saved: "  + page.getPath());
                return isLemmasSaved;
            }
        }

        return true;
    }

    private boolean insertLemma(PageEntity page, int tryCount, String queryLemma) throws InterruptedException {
        try {
            lemmaRepository.saveLemmasByProcedure(queryLemma);

            if (tryCount != 0) {
                logger.info("Lemma add with " + tryCount + " try");
            }
        } catch (Exception e) {
            logger.error("Lemmas save: " + e.getClass() + ": " + page.getPath() + ": try: " + tryCount);
            Thread.sleep(1000);
            return false;
        }

        return true;
    }

    private boolean insertIndices(PageEntity page, Map<String, Integer> lemmas) throws InterruptedException {
        int tryCount = 0;
        boolean isIndexSaved = false;
        while (!isIndexSaved && tryCount < MAX_RETRY_INSERT_COUNT) {
            try {
                if(!allowIndexing.get()) {
                    return false;
                }

                indexRepository.saveIndices(lemmas, page);
                isIndexSaved = true;

                if (tryCount != 0) {
                    logger.info("Index add with " + tryCount + " try");
                }
            } catch (Exception e) {
                logger.error("Indices save: " + e.getClass() + ": " + page.getPath() + ": try: " + tryCount);
                tryCount++;
                Thread.sleep(1000);
            }
        }
        if (!isIndexSaved) {
            logger.error("Index not saved: "  + page.getPath());
            return isIndexSaved;
        }

        return true;
    }

    private boolean isContinueJobAfterSetSiteStatus(InputSiteIndexingLink inputLinks, String error) {
        try {
            if (error != null) {
                updateSiteEntityStatus(inputLinks.site(), SiteIndexingStatus.INDEXING, error);
                return false;
            } else {
                inputLinks.site().setStatusTime(LocalDateTime.now());
                siteRepository.save(inputLinks.site());
            }
        } catch (Exception e) {
            logger.error("Site info update error: ", e);
        }

        return true;
    }

    private class RecursiveSiteParser extends RecursiveAction {
        private InputSiteIndexingLink inputLinks;
        private int level;

        public RecursiveSiteParser(InputSiteIndexingLink inputLinks, int level) {
            this.inputLinks = inputLinks;
            this.level = level;
        }

        @Override
        protected void compute() {

            if (!allowIndexing.get()) {
                return;
            }

            String error = null;
            Set<String> subLinks = null;
            try {
                subLinks = indexPage(inputLinks, true);
            } catch (SocketTimeoutException e) {
                error = e.getMessage() + ": " + inputLinks.link();
            } catch (Exception e) {
                error = "Internal error: " + e.getClass() + ": " + inputLinks.link();
                logger.error("Page indexing error: ", e);
            }

            if (!isContinueJobAfterSetSiteStatus(inputLinks, error)) {
                return;
            }

            if (subLinks == null) {
                return;
            }

            List<RecursiveSiteParser> taskList = new ArrayList<>();
            for (String subLink : subLinks) {
                RecursiveSiteParser task = new RecursiveSiteParser(
                        new InputSiteIndexingLink(inputLinks.site(), subLink, inputLinks.mask()), level + 1);
                taskList.add(task);
            }

            invokeAll(taskList);
            inputLinks = null;
        }
    }
}
