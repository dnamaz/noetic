package com.noetic.websearch.model;

/**
 * Request to split content into chunks.
 */
public record ChunkRequest(
        String content,
        String strategy,
        int maxChunkSize,
        int overlap
) {
    public ChunkRequest {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content is required");
        }
        if (strategy == null || strategy.isBlank()) strategy = "sentence";
        if (maxChunkSize <= 0) maxChunkSize = 512;
        if (overlap < 0) overlap = 50;
    }
}
