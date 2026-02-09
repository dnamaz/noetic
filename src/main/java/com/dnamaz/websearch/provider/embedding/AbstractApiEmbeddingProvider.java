package com.dnamaz.websearch.provider.embedding;

import com.dnamaz.websearch.model.*;
import com.dnamaz.websearch.provider.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Shared base for API-key authenticated embedding providers (OpenAI, Cohere, Voyage).
 * Handles HTTP client, API key header, rate limiting, retry, and batch splitting.
 */
public abstract class AbstractApiEmbeddingProvider implements EmbeddingProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    protected abstract String baseUrl();
    protected abstract String apiKey();
    protected abstract HttpRequest buildRequest(EmbeddingBatchRequest request);
    protected abstract List<EmbeddingResult> parseResponse(HttpResponse<String> response);
    protected abstract String mapInputType(InputType inputType);

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        var batchRequest = new EmbeddingBatchRequest(
                List.of(request.text()), request.inputType(),
                request.outputDimensions(), request.extra());
        List<EmbeddingResult> results = embedBatch(batchRequest);
        return results.getFirst();
    }

    @Override
    public List<EmbeddingResult> embedBatch(EmbeddingBatchRequest request) {
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
                throw new RuntimeException("Embedding API error " + response.statusCode()
                        + " from " + type() + ": " + response.body());
            }

            return parseResponse(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Embedding interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Embedding failed via " + type() + ": " + e.getMessage(), e);
        }
    }
}
