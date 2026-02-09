package com.dnamaz.websearch.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses robots.txt and sitemap XML files to discover crawlable URLs.
 * Handles sitemap index files ({@code <sitemapindex>}) recursively.
 */
@Component
public class SitemapParser {

    private static final Logger log = LoggerFactory.getLogger(SitemapParser.class);
    private static final String USER_AGENT = "WebSearchBot/1.0";

    /**
     * Discover URLs from a domain by checking robots.txt and parsing sitemaps.
     *
     * @param domain     the domain to discover (e.g. "example.com")
     * @param maxUrls    maximum number of URLs to return
     * @param pathFilter optional regex to filter URLs by path
     * @return list of discovered URLs
     */
    public SitemapResult discover(String domain, int maxUrls, String pathFilter) {
        String baseUrl = domain.startsWith("http") ? domain : "https://" + domain;
        List<String> sitemapUrls = new ArrayList<>();
        List<String> discoveredUrls = new ArrayList<>();

        // Step 1: Check robots.txt for Sitemap directives
        try {
            String robotsUrl = baseUrl + "/robots.txt";
            String robotsContent = Jsoup.connect(robotsUrl)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .ignoreContentType(true)
                    .execute()
                    .body();

            for (String line : robotsContent.split("\n")) {
                if (line.toLowerCase().startsWith("sitemap:")) {
                    String sitemapUrl = line.substring("sitemap:".length()).trim();
                    sitemapUrls.add(sitemapUrl);
                }
            }
        } catch (Exception e) {
            log.debug("Could not fetch robots.txt for {}: {}", domain, e.getMessage());
        }

        // If no sitemaps found in robots.txt, try common locations
        if (sitemapUrls.isEmpty()) {
            sitemapUrls.add(baseUrl + "/sitemap.xml");
            sitemapUrls.add(baseUrl + "/sitemap_index.xml");
        }

        // Step 2: Parse each sitemap
        Pattern filter = pathFilter != null ? Pattern.compile(pathFilter) : null;

        for (String sitemapUrl : sitemapUrls) {
            if (discoveredUrls.size() >= maxUrls) break;
            parseSitemap(sitemapUrl, discoveredUrls, maxUrls, filter, 0);
        }

        int totalFound = discoveredUrls.size();
        if (discoveredUrls.size() > maxUrls) {
            discoveredUrls = discoveredUrls.subList(0, maxUrls);
        }

        log.info("Discovered {} URLs from {} (filtered to {})", totalFound, domain, discoveredUrls.size());

        return new SitemapResult(domain, sitemapUrls, discoveredUrls, totalFound,
                totalFound - discoveredUrls.size());
    }

    private void parseSitemap(String url, List<String> urls, int maxUrls,
                               Pattern filter, int depth) {
        if (depth > 3 || urls.size() >= maxUrls) return;

        try {
            String xml = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .ignoreContentType(true)
                    .execute()
                    .body();

            Document doc = Jsoup.parse(xml, url, Parser.xmlParser());

            // Check if this is a sitemap index
            for (Element sitemap : doc.select("sitemapindex > sitemap > loc")) {
                if (urls.size() >= maxUrls) break;
                parseSitemap(sitemap.text().trim(), urls, maxUrls, filter, depth + 1);
            }

            // Parse URL entries
            for (Element loc : doc.select("urlset > url > loc")) {
                if (urls.size() >= maxUrls) break;
                String pageUrl = loc.text().trim();

                if (filter != null && !filter.matcher(pageUrl).find()) {
                    continue;
                }

                urls.add(pageUrl);
            }
        } catch (Exception e) {
            log.debug("Failed to parse sitemap {}: {}", url, e.getMessage());
        }
    }

    /**
     * Result of sitemap discovery.
     */
    public record SitemapResult(
            String domain,
            List<String> sitemapUrls,
            List<String> discoveredUrls,
            int totalFound,
            int filtered
    ) {}
}
