package com.noetic.websearch.provider.search;

import com.noetic.websearch.model.*;
import com.noetic.websearch.provider.SearchProvider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Searches the web by scraping DuckDuckGo's Lite or HTML endpoint.
 * No API key required. Implements polite rate limiting and user-agent rotation.
 *
 * <p>When a SOCKS proxy is active, the Lite endpoint is preferred because
 * the full HTML endpoint can be unreliable through proxies. The Lite endpoint
 * uses a simpler table-based layout that is also faster to parse.</p>
 *
 * <p>This is the default search provider. It deactivates when another
 * provider (e.g. "brave") is configured via {@code websearch.search.active}.</p>
 */
@Component
@ConditionalOnProperty(name = "websearch.search.active", havingValue = "scraping", matchIfMissing = true)
public class ScrapingSearchProvider implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(ScrapingSearchProvider.class);

    private static final String DDG_HTML_URL = "https://html.duckduckgo.com/html/";
    private static final String DDG_LITE_URL = "https://lite.duckduckgo.com/lite/";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final long rateLimitMs;
    private final ProxyConfig proxyConfig;

    // Stream isolation settings (SOCKS5 proxy)
    private final boolean streamIsolationEnabled;
    private final int rotateEveryN;
    private final boolean rotateOnEmpty;
    private final AtomicInteger requestsSinceRotation = new AtomicInteger(0);

    private volatile long lastRequestTime = 0;

    public ScrapingSearchProvider(
            @Value("${websearch.search.scraping.rate-limit-ms:1000}") long rateLimitMs,
            @Value("${websearch.proxy.enabled:false}") boolean proxyEnabled,
            @Value("${websearch.proxy.type:NONE}") String proxyType,
            @Value("${websearch.proxy.host:127.0.0.1}") String proxyHost,
            @Value("${websearch.proxy.port:9050}") int proxyPort,
            @Value("${websearch.proxy.use-onion-services:false}") boolean useOnion,
            @Value("${websearch.proxy.rotation.enabled:true}") boolean rotationEnabled,
            @Value("${websearch.proxy.rotation.every-n-requests:20}") int rotateEveryN,
            @Value("${websearch.proxy.rotation.on-empty-results:true}") boolean rotateOnEmpty
    ) {
        this.rateLimitMs = rateLimitMs;
        this.proxyConfig = new ProxyConfig(proxyEnabled,
                proxyEnabled ? ProxyType.valueOf(proxyType) : ProxyType.NONE,
                proxyHost, proxyPort, null, null, useOnion);

        this.streamIsolationEnabled = rotationEnabled && proxyEnabled && proxyConfig.isSocks();
        this.rotateEveryN = rotateEveryN;
        this.rotateOnEmpty = rotateOnEmpty;

        if (proxyEnabled) {
            log.info("ScrapingSearchProvider proxy enabled: {}://{}:{}",
                    proxyType, proxyHost, proxyPort);
            if (this.streamIsolationEnabled) {
                proxyConfig.installStreamIsolationAuthenticator();
                log.info("SOCKS5 stream isolation enabled: every {} requests, on-empty: {}",
                        rotateEveryN, rotateOnEmpty);
            }
        }
    }

    @Override
    public String type() {
        return "scraping";
    }

    @Override
    public SearchCapabilities capabilities() {
        return new SearchCapabilities(
                false,  // supportsFreshness
                false,  // supportsLanguage
                false,  // supportsCountry
                false,  // supportsDomainFiltering
                false,  // supportsRawContent
                false,  // supportsAiAnswer
                30      // maxResultsLimit
        );
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        Instant start = Instant.now();
        enforceRateLimit();

        // Proactive stream rotation: rotate every N requests for privacy
        if (streamIsolationEnabled && requestsSinceRotation.get() >= rotateEveryN) {
            int newId = ProxyConfig.rotateStream();
            requestsSinceRotation.set(0);
            log.info("Proxy stream rotated (every {} requests), new stream: {}",
                    rotateEveryN, newId);
        }

        List<SearchResult> results = doSearch(request);

        // Reactive rotation: if empty results, rotate stream and retry once
        if (results.isEmpty() && streamIsolationEnabled && rotateOnEmpty) {
            int newId = ProxyConfig.rotateStream();
            requestsSinceRotation.set(0);
            log.info("Empty results for '{}', rotating proxy stream to {} and retrying",
                    request.query(), newId);
            enforceRateLimit();
            results = doSearch(request);
        }

        requestsSinceRotation.incrementAndGet();

        Duration responseTime = Duration.between(start, Instant.now());
        boolean useLite = proxyConfig.enabled() && proxyConfig.isSocks();
        log.info("DuckDuckGo {} search for '{}' returned {} results in {}ms (stream: {})",
                useLite ? "Lite" : "HTML", request.query(),
                results.size(), responseTime.toMillis(), ProxyConfig.currentStreamId());

        return SearchResponse.builder()
                .provider("scraping")
                .results(results)
                .responseTime(responseTime)
                .fromCache(false)
                .build();
    }

    /**
     * Performs a single DuckDuckGo search request. Returns parsed results,
     * or an empty list on failure.
     */
    private List<SearchResult> doSearch(SearchRequest request) {
        try {
            // When a SOCKS proxy is active, use the Lite endpoint because
            // the full HTML endpoint can be unreliable through proxies.
            boolean useLite = proxyConfig.enabled() && proxyConfig.isSocks();
            String baseUrl = useLite ? DDG_LITE_URL : DDG_HTML_URL;

            org.jsoup.Connection conn = Jsoup.connect(baseUrl)
                    .userAgent(USER_AGENT)
                    .timeout(15_000)
                    .followRedirects(true)
                    .data("q", request.query());

            if (proxyConfig.enabled()) {
                conn.proxy(proxyConfig.toJavaProxy());
            }

            Document doc = conn.post();

            int maxResults = Math.min(request.maxResults(), 30);
            return useLite
                    ? parseLiteResults(doc, maxResults)
                    : parseHtmlResults(doc, maxResults);

        } catch (Exception e) {
            log.error("DuckDuckGo search failed for '{}': {}", request.query(), e.getMessage(), e);
            return List.of();
        }
    }

    // ---- HTML endpoint parser (.result / .result__a / .result__snippet) ----

    private List<SearchResult> parseHtmlResults(Document doc, int maxResults) {
        Elements resultElements = doc.select(".result");
        List<SearchResult> results = new ArrayList<>();

        for (Element result : resultElements) {
            if (results.size() >= maxResults) break;

            Element titleLink = result.selectFirst(".result__a");
            Element snippet = result.selectFirst(".result__snippet");
            if (titleLink == null) continue;

            String title = titleLink.text();
            String resultUrl = decodeRedirectUrl(titleLink.attr("href"));
            String snippetText = snippet != null ? snippet.text() : "";

            results.add(SearchResult.builder()
                    .title(title).url(resultUrl).snippet(snippetText).build());
        }
        return results;
    }

    // ---- Lite endpoint parser (table rows with .result-link / .result-snippet) ----

    private List<SearchResult> parseLiteResults(Document doc, int maxResults) {
        Elements links = doc.select("a.result-link");
        List<SearchResult> results = new ArrayList<>();

        for (Element link : links) {
            if (results.size() >= maxResults) break;

            String title = link.text();
            String resultUrl = decodeRedirectUrl(link.attr("href"));

            // The snippet is in a <td class="result-snippet"> that follows the link's <tr>
            String snippetText = "";
            Element parentTr = link.closest("tr");
            if (parentTr != null) {
                // Walk sibling <tr> elements to find the snippet
                Element sibling = parentTr.nextElementSibling();
                while (sibling != null) {
                    Element snippetTd = sibling.selectFirst("td.result-snippet");
                    if (snippetTd != null) {
                        snippetText = snippetTd.text();
                        break;
                    }
                    // Stop at the next numbered result or spacer row
                    if (sibling.selectFirst("a.result-link") != null) break;
                    sibling = sibling.nextElementSibling();
                }
            }

            results.add(SearchResult.builder()
                    .title(title).url(resultUrl).snippet(snippetText).build());
        }
        return results;
    }

    // ---- shared URL decoding ----

    private String decodeRedirectUrl(String rawUrl) {
        if (rawUrl != null && rawUrl.contains("uddg=")) {
            try {
                String decoded = java.net.URLDecoder.decode(
                        rawUrl.substring(rawUrl.indexOf("uddg=") + 5),
                        StandardCharsets.UTF_8);
                if (decoded.contains("&")) {
                    decoded = decoded.substring(0, decoded.indexOf("&"));
                }
                return decoded;
            } catch (Exception e) {
                // fall through
            }
        }
        return rawUrl;
    }

    private synchronized void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        if (elapsed < rateLimitMs) {
            try {
                Thread.sleep(rateLimitMs - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime = System.currentTimeMillis();
    }
}
