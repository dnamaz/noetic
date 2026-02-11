package com.noetic.websearch.provider;

import com.noetic.websearch.model.FetchRequest;
import com.noetic.websearch.model.FetchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Selects the appropriate ContentFetcher based on configuration, URL rules,
 * SPA heuristics, and capabilities-aware auto-detection.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>Explicit {@code fetchMode} (non-auto) -- selects directly.</li>
 *   <li>Domain rules -- URL pattern matches override auto-detection.</li>
 *   <li>Domain memory -- if a domain previously required dynamic fetching,
 *       skip straight to dynamic next time.</li>
 *   <li>Fallback chain with auto-detection -- tries each fetcher in order.
 *       If content is too short or shows SPA signals, tries the next fetcher.</li>
 * </ol>
 */
@Component
public class FetcherResolver {

    private static final Logger log = LoggerFactory.getLogger(FetcherResolver.class);
    private static final int MIN_CONTENT_LENGTH = 100;

    /**
     * HTML patterns that indicate a JavaScript-rendered SPA page.
     * If the static fetcher returns HTML matching these, the dynamic fetcher
     * is more likely to succeed.
     */
    private static final List<String> SPA_INDICATORS = List.of(
            "<div id=\"root\"></div>",
            "<div id=\"__next\"></div>",
            "<div id=\"__next\">",
            "<div id=\"app\"></div>",
            "<div id=\"__nuxt\"></div>",
            "<noscript>You need to enable JavaScript",
            "<noscript>Please enable JavaScript",
            "<noscript>This app works best with JavaScript enabled",
            "window.__INITIAL_STATE__",
            "window.__NEXT_DATA__"
    );

    private final Map<String, ContentFetcher> fetchersByType;
    private final List<String> fallbackChain;
    private final boolean autoDetectJs;
    private final List<DomainRule> domainRules;

    /**
     * Remembers domains that required dynamic fetching so subsequent
     * requests skip the static attempt. Keyed by hostname.
     */
    private final ConcurrentHashMap<String, String> domainMemory = new ConcurrentHashMap<>();

    public FetcherResolver(
            List<ContentFetcher> fetchers,
            @Value("${websearch.fetcher.fallback-chain:static,dynamic,api}") List<String> fallbackChain,
            @Value("${websearch.fetcher.auto-detect-js:true}") boolean autoDetectJs,
            @Value("${websearch.fetcher.rules:}") List<String> rules
    ) {
        this.fetchersByType = fetchers.stream()
                .collect(Collectors.toMap(ContentFetcher::type, Function.identity()));
        this.fallbackChain = fallbackChain;
        this.autoDetectJs = autoDetectJs;
        this.domainRules = parseRules(rules);

        log.info("FetcherResolver initialized with fetchers: {}, chain: {}, rules: {}",
                fetchersByType.keySet(), fallbackChain, domainRules.size());
    }

    /**
     * Resolve and fetch using the appropriate ContentFetcher.
     *
     * @param request   the fetch request
     * @param fetchMode "auto", "static", "dynamic", or "api"
     * @return the fetch result
     */
    public FetchResult resolve(FetchRequest request, String fetchMode) {
        // 1. Explicit mode -- select directly
        if (fetchMode != null && !fetchMode.equalsIgnoreCase("auto")) {
            ContentFetcher fetcher = fetchersByType.get(fetchMode.toLowerCase());
            if (fetcher == null) {
                throw new IllegalArgumentException("Unknown fetch mode: " + fetchMode);
            }
            return fetcher.fetch(request);
        }

        // 2. Domain rules -- check if a rule matches this URL
        String matchedFetcher = matchDomainRule(request.url());
        if (matchedFetcher != null) {
            ContentFetcher fetcher = fetchersByType.get(matchedFetcher);
            if (fetcher != null && fetcher.supports(request)) {
                log.debug("Domain rule matched for {}: using '{}'", request.url(), matchedFetcher);
                return fetcher.fetch(request);
            }
        }

        // 3. Domain memory -- if we know this domain needs dynamic, skip static
        String hostname = extractHostname(request.url());
        String remembered = hostname != null ? domainMemory.get(hostname) : null;
        if (remembered != null) {
            ContentFetcher fetcher = fetchersByType.get(remembered);
            if (fetcher != null && fetcher.supports(request)) {
                log.debug("Domain memory hit for {}: using '{}'", hostname, remembered);
                try {
                    FetchResult result = fetcher.fetch(request);
                    if (result.content() != null && result.content().length() >= MIN_CONTENT_LENGTH) {
                        return result;
                    }
                } catch (Exception e) {
                    log.debug("Remembered fetcher '{}' failed for {}: {}", remembered, hostname, e.getMessage());
                }
                // Fall through to chain if remembered fetcher fails
            }
        }

        // 4. Auto mode: walk the fallback chain with SPA detection
        for (int i = 0; i < fallbackChain.size(); i++) {
            String type = fallbackChain.get(i);
            ContentFetcher fetcher = fetchersByType.get(type);
            if (fetcher == null) {
                log.debug("Fetcher '{}' not available, skipping", type);
                continue;
            }

            if (!fetcher.supports(request)) {
                log.debug("Fetcher '{}' does not support this request, skipping", type);
                continue;
            }

            try {
                FetchResult result = fetcher.fetch(request);
                boolean isLastInChain = i == fallbackChain.size() - 1;

                if (autoDetectJs && !isLastInChain) {
                    // Check if content is too short
                    if (result.content() != null && result.content().length() < MIN_CONTENT_LENGTH) {
                        log.info("Fetcher '{}' returned minimal content ({} chars), trying next in chain",
                                type, result.content().length());
                        rememberDomain(hostname, fallbackChain.get(i + 1));
                        continue;
                    }

                    // Check for SPA indicators in the raw HTML
                    if (result.rawHtml() != null && hasSpaSignals(result.rawHtml())) {
                        log.info("Fetcher '{}' returned SPA shell for {}, trying next in chain",
                                type, request.url());
                        rememberDomain(hostname, fallbackChain.get(i + 1));
                        continue;
                    }
                }

                return result;
            } catch (Exception e) {
                log.warn("Fetcher '{}' failed for {}: {}", type, request.url(), e.getMessage());
            }
        }

        throw new RuntimeException("All fetchers in chain failed for: " + request.url());
    }

    /** Get a specific fetcher by type. */
    public ContentFetcher getFetcher(String type) {
        return fetchersByType.get(type);
    }

    // ── Domain rules ─────────────────────────────────────────────────────

    /**
     * A domain rule mapping a URL pattern to a fetcher type.
     * Pattern supports glob-style wildcards: {@code *.example.com}, {@code docs.google.com/*}.
     */
    record DomainRule(Pattern pattern, String fetcherType) {}

    /**
     * Parses rule strings like "*.notion.so=dynamic" or "docs.google.com/*=dynamic".
     */
    private static List<DomainRule> parseRules(List<String> rules) {
        if (rules == null || rules.isEmpty()) return List.of();

        return rules.stream()
                .filter(r -> r != null && r.contains("="))
                .map(rule -> {
                    String[] parts = rule.split("=", 2);
                    String glob = parts[0].trim();
                    String fetcher = parts[1].trim();

                    // Convert glob to regex: * -> [^/]*, ** -> .*, . -> \.
                    String regex = glob
                            .replace(".", "\\.")
                            .replace("**", "<<DOUBLESTAR>>")
                            .replace("*", "[^/]*")
                            .replace("<<DOUBLESTAR>>", ".*");

                    return new DomainRule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), fetcher);
                })
                .toList();
    }

    /**
     * Checks if the URL matches any domain rule.
     *
     * @return the fetcher type to use, or null if no rule matches
     */
    private String matchDomainRule(String url) {
        if (domainRules.isEmpty() || url == null) return null;

        for (DomainRule rule : domainRules) {
            if (rule.pattern().matcher(url).find()) {
                return rule.fetcherType();
            }
        }
        return null;
    }

    // ── SPA detection ────────────────────────────────────────────────────

    /**
     * Checks raw HTML for signals that the page is a JavaScript-rendered SPA.
     */
    private boolean hasSpaSignals(String rawHtml) {
        for (String indicator : SPA_INDICATORS) {
            if (rawHtml.contains(indicator)) {
                return true;
            }
        }
        return false;
    }

    // ── Domain memory ────────────────────────────────────────────────────

    /**
     * Remember that a domain should skip straight to a specific fetcher.
     */
    private void rememberDomain(String hostname, String fetcherType) {
        if (hostname != null && fetcherType != null) {
            domainMemory.put(hostname, fetcherType);
            log.debug("Remembered domain '{}' -> fetcher '{}'", hostname, fetcherType);
        }
    }

    private static String extractHostname(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
