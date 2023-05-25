package searchengine.services.siteindexing;

import lombok.Data;

import java.util.Set;

@Data
public class PageParsingInfo {
    private Set<String> subLinks;
    private int status;
    private String content;
}
