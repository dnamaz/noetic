package com.noetic.websearch.service;

import com.noetic.websearch.model.*;
import com.noetic.websearch.provider.EmbeddingProvider;
import com.noetic.websearch.provider.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates semantic search over cached content.
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;

    public CacheService(EmbeddingProvider embeddingProvider, VectorStore vectorStore) {
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
    }

    public List<VectorMatch> query(String queryText, Integer topK, Float similarityThreshold,
                                    String namespace) {
        int k = topK != null ? topK : 5;
        float threshold = similarityThreshold != null ? similarityThreshold : 0.0f;

        EmbeddingResult queryEmbedding = embeddingProvider.embed(
                EmbeddingRequest.of(queryText, InputType.QUERY));

        List<VectorMatch> matches = vectorStore.search(
                VectorSearchRequest.of(queryEmbedding.vector(), k, threshold, namespace));

        log.info("Cache query for '{}' (ns={}) returned {} matches", queryText, namespace, matches.size());
        return matches;
    }
}
