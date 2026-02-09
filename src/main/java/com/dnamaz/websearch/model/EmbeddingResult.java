package com.dnamaz.websearch.model;

import java.util.Map;

/**
 * Result of an embedding operation.
 */
public record EmbeddingResult(
        float[] vector,
        int dimensions,
        int tokenCount,
        String model,
        Map<String, Object> providerMeta
) {
    public static EmbeddingResult of(float[] vector, String model) {
        return new EmbeddingResult(vector, vector.length, 0, model, Map.of());
    }
}
