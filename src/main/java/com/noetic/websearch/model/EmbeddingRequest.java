package com.noetic.websearch.model;

import java.util.Map;

/**
 * Universal single-text embedding request.
 */
public record EmbeddingRequest(
        String text,
        InputType inputType,
        Integer outputDimensions,
        Map<String, Object> extra
) {
    public EmbeddingRequest {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text is required");
        }
        if (inputType == null) inputType = InputType.DOCUMENT;
        if (extra == null) extra = Map.of();
    }

    public static EmbeddingRequest of(String text, InputType inputType) {
        return new EmbeddingRequest(text, inputType, null, Map.of());
    }
}
