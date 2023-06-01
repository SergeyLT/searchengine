package searchengine.utils;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import searchengine.config.JsoupSettings;
import searchengine.data.siteindexing.InputSiteIndexingLink;
import searchengine.data.siteindexing.PageParsingInfo;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class WebPageParser {
    private final InputSiteIndexingLink inputLinks;
    private final JsoupSettings jsoupSettings;
    private String domainLink;

    public PageParsingInfo getPageInfo() throws IOException, InterruptedException {
        PageParsingInfo pageParsingInfo = new PageParsingInfo();
        LinkedHashMap<String, Boolean> subLinks = new LinkedHashMap<>();
        domainLink = getDomainLink(inputLinks.link());

        Document htmlPageDocument = null;
        int status;
        try {
            Connection.Response response = getResponseByLink();
            htmlPageDocument = response.parse();
            status = response.statusCode();
        } catch(HttpStatusException e) {
            status = e.getStatusCode();
        }

        pageParsingInfo.setStatus(status);
        pageParsingInfo.setContent(htmlPageDocument != null ? htmlPageDocument.outerHtml() : "");

        if(htmlPageDocument == null || status != 200){
            return pageParsingInfo;
        }

        Elements linkElements = htmlPageDocument.select("a[href]");

        linkElements.forEach(l -> {
            StringBuilder subLink = new StringBuilder(l.attr("abs:href"));
            subLink = closeLinkSlash(subLink);
            checkSubLink(subLink.toString(), subLinks);
        });

        pageParsingInfo.setSubLinks(subLinks.entrySet().stream().filter(l -> l.getValue())
                .collect(Collectors.toMap(l -> l.getKey(), l -> l.getValue()))
                .keySet()
        );

        return pageParsingInfo;
    }

    private boolean checkSubLink(String subLink, LinkedHashMap<String, Boolean> subLinks) {
        if (subLink.equals(inputLinks.link())) {
            return false;
        }

        if (subLinks.containsKey(subLink)) {
            return false;
        }

        if ((domainLink + '/').contains(subLink)) {
            subLinks.put(subLink, false);
            return false;
        }

        if (!subLink.matches(inputLinks.mask())) {
            return false;
        }

        subLinks.put(subLink, true);
        return true;
    }

    private Connection.Response getResponseByLink() throws InterruptedException, IOException {
        Thread.sleep(150);
        return Jsoup.connect(inputLinks.link()).timeout(5000)
                .userAgent(jsoupSettings.getUserAgent())
                .referrer(jsoupSettings.getReferer()).execute();
    }

    public static StringBuilder closeLinkSlash(StringBuilder link) {
        return link.charAt(link.length()-1) == '/' ? link : link.append('/');
    }

    public static String getDomainLink(String link) {
        Matcher matcher =  Pattern.compile("^.*[\\/]{2}[^\\/]*").matcher(link);
        return matcher.find() ? matcher.group(0) : "";
    }

    public static String getRelativeLink(String link){
        return link.replace(getDomainLink(link),"");
    }

    public static String createDomainPageLinkMask(String link){
        String maskLink = closeLinkSlash(new StringBuilder(link)).toString()
                .replace("/","\\/")
                .replace(".","\\.");
        StringBuilder domainPageLinkMask = new StringBuilder("^").append(maskLink)
                .append("([^\\?\\#\\.]*|[^\\?\\#]*\\.html[\\/]{0,1})$");
        return domainPageLinkMask.toString();
    }

    public static String getTextFromHTML(String html) {
        return Jsoup.parse(html).body().text();
    }

    public static String getTitleFromHTML(String html) {
        return Jsoup.parse(html).title();
    }

    public static String getDescriptionFromHTML(String html) {
        String description = "";
        try {
            description = Jsoup.parse(html).select("meta[name=description]")
                    .get(0).attr("content");
        } catch (Exception e) {
        }

        return description;
    }
}
