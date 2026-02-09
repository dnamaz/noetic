package com.dnamaz.websearch.service;

import com.dnamaz.websearch.model.*;
import com.dnamaz.websearch.provider.ChunkingStrategy;
import com.dnamaz.websearch.provider.EmbeddingProvider;
import com.dnamaz.websearch.provider.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates content chunking, embedding, and storage.
 */
@Service
public class ChunkService {

    private static final Logger log = LoggerFactory.getLogger(ChunkService.class);

    private final Map<String, ChunkingStrategy> strategies;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final String defaultStrategy;
    private final int defaultMaxChunkSize;
    private final int defaultOverlap;

    public ChunkService(
            List<ChunkingStrategy> strategies,
            EmbeddingProvider embeddingProvider,
            VectorStore vectorStore,
            @Value("${websearch.chunking.default-strategy:sentence}") String defaultStrategy,
            @Value("${websearch.chunking.max-chunk-size:512}") int defaultMaxChunkSize,
            @Value("${websearch.chunking.overlap:50}") int defaultOverlap
    ) {
        this.strategies = strategies.stream()
                .collect(Collectors.toMap(ChunkingStrategy::type, Function.identity()));
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.defaultStrategy = defaultStrategy;
        this.defaultMaxChunkSize = defaultMaxChunkSize;
        this.defaultOverlap = defaultOverlap;
    }

    public List<ContentChunk> chunk(String content, String strategy, Integer maxChunkSize,
                                      Integer overlap, String sourceUrl, String namespace) {

        String strat = strategy != null ? strategy : defaultStrategy;
        int chunkSize = maxChunkSize != null ? maxChunkSize : defaultMaxChunkSize;
        int ovlp = overlap != null ? overlap : defaultOverlap;

        ChunkingStrategy chunkingStrategy = strategies.get(strat);
        if (chunkingStrategy == null) {
            throw new IllegalArgumentException("Unknown chunking strategy: " + strat
                    + ". Available: " + strategies.keySet());
        }

        ChunkRequest request = new ChunkRequest(content, strat, chunkSize, ovlp);
        List<ContentChunk> chunks = chunkingStrategy.chunk(request);

        // Embed and store each chunk
        List<ContentChunk> storedChunks = new ArrayList<>();
        for (ContentChunk chunk : chunks) {
            try {
                EmbeddingResult embedding = embeddingProvider.embed(
                        EmbeddingRequest.of(chunk.text(), InputType.DOCUMENT));

                Map<String, String> metadata = new java.util.HashMap<>();
                metadata.put("strategy", strat);
                if (sourceUrl != null) {
                    metadata.put("sourceUrl", sourceUrl);
                }

                VectorEntry entry = new VectorEntry(
                        chunk.chunkId(),
                        embedding.vector(),
                        chunk.text(),
                        "crawl_chunk",
                        namespace,
                        Instant.now(),
                        metadata
                );

                vectorStore.upsert(entry);

                storedChunks.add(new ContentChunk(
                        chunk.chunkId(), chunk.text(), chunk.tokenCount(), true));

            } catch (Exception e) {
                log.warn("Failed to embed/store chunk {}: {}", chunk.chunkId(), e.getMessage());
                storedChunks.add(chunk);
            }
        }

        log.info("Chunked content into {} chunks (strategy={}, stored={})",
                storedChunks.size(), strat,
                storedChunks.stream().filter(ContentChunk::embeddingStored).count());

        return storedChunks;
    }
}
