package com.dnamaz.websearch.model;

/**
 * Declares what an EmbeddingProvider supports.
 */
public record EmbeddingCapabilities(
        boolean supportsInputType,
        boolean supportsDimensionOverride,
        boolean supportsBatch,
        int maxBatchSize,
        int maxTokensPerText,
        int defaultDimensions,
        AuthType authType
) {}
