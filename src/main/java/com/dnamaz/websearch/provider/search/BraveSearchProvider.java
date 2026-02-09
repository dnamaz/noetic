package com.dnamaz.websearch.provider.search;

import com.dnamaz.websearch.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SearchProvider backed by the Brave Search API.
 *
 * <p>Uses HTTP GET with the {@code X-Subscription-Token} header for auth.
 * Supports freshness filtering, language, country, and extra snippets.</p>
 *
 * <p>Free tier: 2,000 queries/month, 1 req/sec rate limit.</p>
 *
 * <p>Activated when {@code websearch.search.active=brave}.</p>
 */
@Component
@ConditionalOnProperty(name = "websearch.search.active", havingValue = "brave")
public class BraveSearchProvider extends AbstractApiSearchProvider {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${websearch.search.brave.api-key:}")
    private String apiKey;

    @Value("${websearch.search.brave.base-url:https://api.search.brave.com/res/v1/web/search}")
    private String baseUrl;

    @Value("${websearch.search.brave.extra-snippets:true}")
    private boolean extraSnippets;

    @Value("${websearch.search.brave.timeout-ms:5000}")
    private int timeoutMs;

    @Override
    public String type() {
        return "brave";
    }

    @Override
    public SearchCapabilities capabilities() {
        return new SearchCapabilities(
                true,   // supportsFreshness
                true,   // supportsLanguage
                true,   // supportsCountry
                false,  // supportsDomainFiltering
                false,  // supportsRawContent
                false,  // supportsAiAnswer (not on free tier)
                20      // maxResultsLimit
        );
    }

    @Override
    protected String baseUrl() {
        return baseUrl;
    }

    @Override
    protected String apiKey() {
        return apiKey;
    }

    @Override
    protected HttpRequest buildRequest(SearchRequest request) {
        StringBuilder url = new StringBuilder(baseUrl);
        url.append("?q=").append(URLEncoder.encode(request.query(), StandardCharsets.UTF_8));
        url.append("&count=").append(Math.min(request.maxResults(), 20));

        // Map freshness
        if (request.freshness() != null && request.freshness() != Freshness.NONE) {
            String freshness = switch (request.freshness()) {
                case DAY -> "pd";
                case WEEK -> "pw";
                case MONTH -> "pm";
                case YEAR -> "py";
                default -> null;
            };
            if (freshness != null) {
                url.append("&freshness=").append(freshness);
            }
        }

        // Language
        if (request.language() != null && !request.language().isBlank()) {
            url.append("&search_lang=").append(URLEncoder.encode(request.language(), StandardCharsets.UTF_8));
        }

        // Country
        if (request.country() != null && !request.country().isBlank()) {
            url.append("&country=").append(URLEncoder.encode(request.country(), StandardCharsets.UTF_8));
        }

        // Extra snippets
        if (extraSnippets) {
            url.append("&extra_snippets=true");
        }

        return HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Accept", "application/json")
                .header("X-Subscription-Token", apiKey)
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
    }

    @Override
    protected SearchResponse parseResponse(HttpResponse<String> response) {
        try {
            JsonNode root = mapper.readTree(response.body());
            JsonNode webNode = root.path("web");
            JsonNode resultsNode = webNode.path("results");

            List<SearchResult> results = new ArrayList<>();

            if (resultsNode.isArray()) {
                for (JsonNode result : resultsNode) {
                    String title = result.path("title").asText("");
                    String resultUrl = result.path("url").asText("");
                    String description = result.path("description").asText("");

                    // Parse extra snippets
                    List<String> snippets = new ArrayList<>();
                    JsonNode extraSnippetsNode = result.path("extra_snippets");
                    if (extraSnippetsNode.isArray()) {
                        for (JsonNode snippet : extraSnippetsNode) {
                            snippets.add(snippet.asText());
                        }
                    }

                    results.add(SearchResult.builder()
                            .title(title)
                            .url(resultUrl)
                            .snippet(description)
                            .extraSnippets(snippets)
                            .providerMeta(Map.of("source", "brave"))
                            .build());
                }
            }

            log.info("Brave Search returned {} results", results.size());

            return SearchResponse.builder()
                    .provider("brave")
                    .results(results)
                    .fromCache(false)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Brave Search response: {}", e.getMessage());
            return SearchResponse.builder()
                    .provider("brave")
                    .results(List.of())
                    .fromCache(false)
                    .build();
        }
    }
}
