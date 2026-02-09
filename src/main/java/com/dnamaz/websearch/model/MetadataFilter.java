package com.dnamaz.websearch.model;

import java.time.Instant;
import java.util.Map;

/**
 * Composable filter for metadata-constrained search and bulk delete.
 */
public record MetadataFilter(
        Map<String, String> equals,
        Map<String, String> contains,
        Instant createdAfter,
        Instant createdBefore
) {
    public MetadataFilter {
        if (equals == null) equals = Map.of();
        if (contains == null) contains = Map.of();
    }

    public static MetadataFilter byType(String entryType) {
        return new MetadataFilter(Map.of("entryType", entryType), Map.of(), null, null);
    }

    public static MetadataFilter olderThan(Instant cutoff) {
        return new MetadataFilter(Map.of(), Map.of(), null, cutoff);
    }

    public static MetadataFilter byTypeOlderThan(String entryType, Instant cutoff) {
        return new MetadataFilter(Map.of("entryType", entryType), Map.of(), null, cutoff);
    }
}
