package com.noetic.websearch.model;

import java.util.Map;

/**
 * A result from a vector similarity search.
 */
public record VectorMatch(
        String id,
        float score,
        String content,
        Map<String, String> metadata
) {}
