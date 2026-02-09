package com.dnamaz.websearch.provider.search;

import com.dnamaz.websearch.model.SearchRequest;
import com.dnamaz.websearch.model.SearchResponse;
import com.dnamaz.websearch.provider.SearchProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Shared base for API-backed SearchProvider implementations.
 * Handles HTTP client, API key, rate limiting, retry, and error handling.
 */
public abstract class AbstractApiSearchProvider implements SearchProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    protected abstract String baseUrl();
    protected abstract String apiKey();
    protected abstract HttpRequest buildRequest(SearchRequest request);
    protected abstract SearchResponse parseResponse(HttpResponse<String> response);

    @Override
    public SearchResponse search(SearchRequest request) {
        try {
            HttpRequest httpRequest = buildRequest(request);
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                log.warn("Rate limited by {}, retrying after delay", type());
                Thread.sleep(2000);
                response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            }

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Search API error " + response.statusCode()
                        + " from " + type() + ": " + response.body());
            }

            return parseResponse(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Search interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Search failed via " + type() + ": " + e.getMessage(), e);
        }
    }
}
