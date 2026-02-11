package com.noetic.websearch.model;

import java.util.List;
import java.util.Map;

/**
 * Batch embedding request for multiple texts.
 */
public record EmbeddingBatchRequest(
        List<String> texts,
        InputType inputType,
        Integer outputDimensions,
        Map<String, Object> extra
) {
    public EmbeddingBatchRequest {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("At least one text is required");
        }
        if (inputType == null) inputType = InputType.DOCUMENT;
        if (extra == null) extra = Map.of();
    }

    public static EmbeddingBatchRequest of(List<String> texts, InputType inputType) {
        return new EmbeddingBatchRequest(texts, inputType, null, Map.of());
    }
}
