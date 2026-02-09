package com.dnamaz.websearch.service;

import com.dnamaz.websearch.model.*;
import com.dnamaz.websearch.provider.EmbeddingProvider;
import com.dnamaz.websearch.provider.SearchProvider;
import com.dnamaz.websearch.provider.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates web search with semantic caching.
 *
 * <p>Before hitting the search provider, embeds the query and checks the
 * VectorStore for semantically similar cached queries (cosine similarity
 * above threshold). On a miss, delegates to the active SearchProvider,
 * caches the results, and returns.</p>
 */
@Service
public class WebSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);

    private final SearchProvider searchProvider;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final float similarityThreshold;

    public WebSearchService(
            SearchProvider searchProvider,
            EmbeddingProvider embeddingProvider,
            VectorStore vectorStore,
            @Value("${websearch.store.similarity-threshold:0.92}") float similarityThreshold
    ) {
        this.searchProvider = searchProvider;
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.similarityThreshold = similarityThreshold;
    }

    public SearchResponse search(SearchRequest request, String namespace) {
        Instant start = Instant.now();

        // Check cache first (unless explicitly skipped)
        if (!request.skipCache()) {
            try {
                EmbeddingResult queryEmbedding = embeddingProvider.embed(
                        EmbeddingRequest.of(request.query(), InputType.QUERY));

                List<VectorMatch> cached = vectorStore.search(
                        VectorSearchRequest.of(queryEmbedding.vector(), request.maxResults(),
                                similarityThreshold, namespace));

                if (!cached.isEmpty()) {
                    log.info("Cache hit for query '{}' (ns={}): {} results",
                            request.query(), namespace, cached.size());
                    List<SearchResult> results = cached.stream()
                            .map(match -> SearchResult.builder()
                                    .title(match.metadata().getOrDefault("title", ""))
                                    .url(match.metadata().getOrDefault("url", ""))
                                    .snippet(match.content())
                                    .build())
                            .toList();

                    return SearchResponse.builder()
                            .provider("cache")
                            .results(results)
                            .responseTime(Duration.between(start, Instant.now()))
                            .fromCache(true)
                            .build();
                }
            } catch (Exception e) {
                log.debug("Cache check failed, proceeding with live search: {}", e.getMessage());
            }
        } else {
            log.debug("Cache skipped for query '{}'", request.query());
        }

        // Cache miss -- perform live search
        SearchResponse response = searchProvider.search(request);

        // Cache the results
        try {
            for (SearchResult result : response.results()) {
                String text = result.title() + " " + result.snippet();
                EmbeddingResult embedding = embeddingProvider.embed(
                        EmbeddingRequest.of(text, InputType.DOCUMENT));

                VectorEntry entry = new VectorEntry(
                        UUID.randomUUID().toString(),
                        embedding.vector(),
                        result.snippet(),
                        "search_result",
                        namespace,
                        Instant.now(),
                        Map.of("title", result.title(),
                                "url", result.url(),
                                "query", request.query())
                );
                vectorStore.upsert(entry);
            }
        } catch (Exception e) {
            log.warn("Failed to cache search results: {}", e.getMessage());
        }

        return SearchResponse.builder()
                .provider(response.provider())
                .results(response.results())
                .aiAnswer(response.aiAnswer())
                .responseTime(Duration.between(start, Instant.now()))
                .fromCache(false)
                .build();
    }
}
