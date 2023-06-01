package searchengine.data.siteindexing;

import searchengine.model.SiteEntity;

public record InputSiteIndexingLink(SiteEntity site, String link, String mask) {
}
