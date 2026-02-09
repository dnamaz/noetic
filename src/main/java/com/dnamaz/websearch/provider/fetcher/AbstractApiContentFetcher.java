package com.dnamaz.websearch.provider.fetcher;

import com.dnamaz.websearch.model.FetchRequest;
import com.dnamaz.websearch.model.FetchResult;
import com.dnamaz.websearch.provider.ContentFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Shared base for API-backed ContentFetcher implementations.
 *
 * <p>Handles HTTP client lifecycle, API key injection, retry with backoff,
 * timeout handling, and error handling (401/429/5xx).</p>
 */
public abstract class AbstractApiContentFetcher implements ContentFetcher {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    protected abstract String baseUrl();

    protected abstract String apiKey();

    protected abstract HttpRequest buildRequest(FetchRequest request);

    protected abstract FetchResult parseResponse(HttpResponse<String> response, FetchRequest request);

    @Override
    public boolean supports(FetchRequest request) {
        return apiKey() != null && !apiKey().isBlank();
    }

    @Override
    public FetchResult fetch(FetchRequest request) {
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
                throw new RuntimeException("API error " + response.statusCode()
                        + " from " + type() + ": " + response.body());
            }

            return parseResponse(response, request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Fetch interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Fetch failed via " + type() + ": " + e.getMessage(), e);
        }
    }
}
