package com.noetic.websearch.model;

/**
 * A chunk of content produced by a ChunkingStrategy.
 */
public record ContentChunk(
        String chunkId,
        String text,
        int tokenCount,
        boolean embeddingStored
) {}
