package com.noetic.websearch.model;

/**
 * Declares what a VectorStore implementation supports.
 */
public record StoreCapabilities(
        boolean supportsNamespaces,
        boolean supportsMetadataFiltering,
        boolean supportsBatchOperations,
        boolean supportsGet,
        boolean supportsNativeTtl,
        boolean requiresExplicitDimensions,
        int maxBatchSize
) {}
