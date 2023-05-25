package searchengine.services.siteindexing;

import searchengine.model.SiteEntity;

public record InputLinksEntity(SiteEntity site, String link, String mask) {
}
