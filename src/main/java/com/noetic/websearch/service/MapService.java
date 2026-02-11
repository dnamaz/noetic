package com.noetic.websearch.service;

import com.noetic.websearch.model.FetchRequest;
import com.noetic.websearch.model.FetchResult;
import com.noetic.websearch.provider.FetcherResolver;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Discovers all reachable URLs from a starting URL via BFS link crawling.
 * Like Firecrawl's map endpoint -- quickly finds all URLs on a domain.
 */
@Service
public class MapService {

    private static final Logger log = LoggerFactory.getLogger(MapService.class);
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    /**
     * BFS crawl starting from a URL, discovering all same-domain links.
     *
     * @param startUrl    the starting URL
     * @param maxDepth    maximum BFS depth (default 3)
     * @param maxUrls     maximum URLs to discover (default 100)
     * @param pathFilter  optional regex to filter URLs by path
     * @return discovered URLs
     */
    public MapResult map(String startUrl, Integer maxDepth, Integer maxUrls, String pathFilter) {
        int depth = maxDepth != null ? maxDepth : 3;
        int max = maxUrls != null ? maxUrls : 100;
        Pattern filter = pathFilter != null ? Pattern.compile(pathFilter) : null;

        String baseUrl = startUrl.startsWith("http") ? startUrl : "https://" + startUrl;
        String domain;
        try {
            domain = URI.create(baseUrl).getHost();
        } catch (Exception e) {
            domain = baseUrl;
        }

        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();
        Map<String, Integer> depthMap = new HashMap<>();

        queue.add(baseUrl);
        depthMap.put(baseUrl, 0);

        while (!queue.isEmpty() && visited.size() < max) {
            String url = queue.poll();
            if (visited.contains(url)) continue;

            int currentDepth = depthMap.getOrDefault(url, 0);
            if (currentDepth > depth) continue;

            visited.add(url);

            // Apply path filter
            if (filter != null && !filter.matcher(url).find()) {
                continue;
            }

            // Don't fetch links beyond max depth
            if (currentDepth >= depth) continue;

            try {
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(5000)
                        .followRedirects(true)
                        .get();

                for (var link : doc.select("a[href]")) {
                    String href = link.absUrl("href");
                    if (href.isBlank()) continue;

                    // Same-domain filter
                    try {
                        String linkDomain = URI.create(href).getHost();
                        if (linkDomain == null || !linkDomain.equals(domain)) continue;
                    } catch (Exception e) {
                        continue;
                    }

                    // Strip fragments and query params for dedup
                    String cleanUrl = href.split("[#?]")[0];
                    if (cleanUrl.isBlank() || visited.contains(cleanUrl)) continue;

                    if (!depthMap.containsKey(cleanUrl)) {
                        depthMap.put(cleanUrl, currentDepth + 1);
                        queue.add(cleanUrl);
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to fetch links from {}: {}", url, e.getMessage());
            }
        }

        List<String> urls = visited.stream()
                .filter(u -> filter == null || filter.matcher(u).find())
                .limit(max)
                .toList();

        log.info("Map discovered {} URLs from {} (depth={}, max={})",
                urls.size(), baseUrl, depth, max);

        return new MapResult(baseUrl, domain, urls, urls.size(), depth);
    }

    public record MapResult(
            String startUrl,
            String domain,
            List<String> urls,
            int totalFound,
            int maxDepth
    ) {}
}
