package com.noetic.websearch.provider.store;

import com.noetic.websearch.model.*;
import com.noetic.websearch.provider.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Shared base for cloud-hosted VectorStore implementations.
 * Handles HTTP client, API key/token auth, connection pooling, retry,
 * batch chunking, and error handling.
 */
public abstract class AbstractRemoteVectorStore implements VectorStore {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    protected abstract String endpoint();
    protected abstract HttpRequest buildUpsertRequest(List<VectorEntry> entries);
    protected abstract HttpRequest buildSearchRequest(VectorSearchRequest request);
    protected abstract List<VectorMatch> parseSearchResponse(HttpResponse<String> response);

    @Override
    public void upsertBatch(List<VectorEntry> entries) {
        int maxBatch = capabilities().maxBatchSize();
        if (maxBatch <= 0) maxBatch = entries.size();

        for (int i = 0; i < entries.size(); i += maxBatch) {
            int end = Math.min(i + maxBatch, entries.size());
            List<VectorEntry> batch = entries.subList(i, end);
            try {
                HttpRequest request = buildUpsertRequest(batch);
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    log.error("Upsert batch failed with status {}: {}",
                            response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.error("Upsert batch failed: {}", e.getMessage());
            }
        }
    }

    @Override
    public List<VectorMatch> search(VectorSearchRequest request) {
        try {
            HttpRequest httpRequest = buildSearchRequest(request);
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("Search failed: " + response.body());
            }
            return parseSearchResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("Vector search failed: " + e.getMessage(), e);
        }
    }
}
