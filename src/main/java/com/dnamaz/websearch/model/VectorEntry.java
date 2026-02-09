package com.dnamaz.websearch.model;

import java.time.Instant;
import java.util.Map;

/**
 * An entry stored in the VectorStore.
 * {@code createdAt} and {@code entryType} are first-class fields
 * because the TTL eviction system depends on them directly.
 * {@code namespace} scopes entries to a project for isolation.
 */
public record VectorEntry(
        String id,
        float[] vector,
        String content,
        String entryType,
        String namespace,
        Instant createdAt,
        Map<String, String> metadata
) {
    public VectorEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID is required");
        }
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Vector is required");
        }
        if (entryType == null) entryType = "unknown";
        if (namespace == null) namespace = "default";
        if (createdAt == null) createdAt = Instant.now();
        if (metadata == null) metadata = Map.of();
    }
}
