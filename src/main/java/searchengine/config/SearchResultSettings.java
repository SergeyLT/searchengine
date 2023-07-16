package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "search-result-settings")
public class SearchResultSettings {
    private int defaultSearchResultLimit;
    private int searchPageCountLimit;
    private int searchPageStartCountLimit;
    private int searchLemmasCountForPageLimit;
    private int snippetMaxSize;
    private int snippetPartSize;
}
