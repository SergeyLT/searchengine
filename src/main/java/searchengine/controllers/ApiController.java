package searchengine.controllers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.IndexPageRequest;
import searchengine.dto.SearchRequest;
import searchengine.dto.common.CommonResponse;
import searchengine.dto.common.ErrorResponse;
import searchengine.dto.common.SuccessResponse;
import searchengine.dto.common.search.SearchResponse;
import searchengine.services.search.SearchService;
import searchengine.services.siteindexing.SiteIndexingService;
import searchengine.services.statistics.StatisticsService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final SiteIndexingService siteIndexingService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<String> statistics() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        String serialized = "";
        try {
            serialized = mapper.writeValueAsString(statisticsService.getStatistics());
        } catch (Exception e) {
        }

        return ResponseEntity.ok(serialized);
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<CommonResponse> startIndexing() {
        CommonResponse response;

        try{
            response = siteIndexingService.indexAllSites();
        }catch (Exception e){
            response = new ErrorResponse();
            ((ErrorResponse)response).setError("Ошибка индексации сайтов");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        boolean isSuccess = response instanceof SuccessResponse;
        return new ResponseEntity<>(response, isSuccess ? HttpStatus.OK : HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<CommonResponse> stopIndexing() {
        CommonResponse response;

        try{
            response = siteIndexingService.stopIndexing();
        }catch (Exception e){
            response = new ErrorResponse();
            ((ErrorResponse)response).setError("Ошибка остановки индексации сайтов");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        boolean isSuccess = response instanceof SuccessResponse;
        return new ResponseEntity<>(response, isSuccess ? HttpStatus.OK : HttpStatus.BAD_REQUEST);
    }

    @PostMapping(value = "/indexPage"
            ,consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<CommonResponse> indexPage(IndexPageRequest request) {
        CommonResponse response;
        try{
            response = siteIndexingService.indexPageByPath(request.getUrl());
        }catch (Exception e){
            response = new ErrorResponse();
            ((ErrorResponse)response).setError("Ошибка индексации страницы");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        boolean isSuccess = response instanceof SuccessResponse;
        return new ResponseEntity<>(response, isSuccess ? HttpStatus.OK : HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/search")
    public ResponseEntity<CommonResponse> search(SearchRequest request) {
        CommonResponse response;

        try{
            response = searchService.search(request);
        }catch (Exception e){
            response = new ErrorResponse();
            ((ErrorResponse)response).setError("Ошибка поиска");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        boolean isSuccess = response instanceof SearchResponse;
        return new ResponseEntity<>(response, isSuccess ? HttpStatus.OK : HttpStatus.BAD_REQUEST);
    }
}
