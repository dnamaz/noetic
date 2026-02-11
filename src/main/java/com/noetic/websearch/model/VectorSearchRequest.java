package com.noetic.websearch.model;

/**
 * Rich vector search request with optional metadata filtering.
 */
public record VectorSearchRequest(
        float[] queryVector,
        int topK,
        float similarityThreshold,
        MetadataFilter filter,
        String namespace
) {
    public VectorSearchRequest {
        if (queryVector == null || queryVector.length == 0) {
            throw new IllegalArgumentException("Query vector is required");
        }
        if (topK <= 0) topK = 5;
    }

    public static VectorSearchRequest of(float[] queryVector, int topK, float threshold) {
        return new VectorSearchRequest(queryVector, topK, threshold, null, null);
    }

    public static VectorSearchRequest of(float[] queryVector, int topK, float threshold, String namespace) {
        return new VectorSearchRequest(queryVector, topK, threshold, null, namespace);
    }
}
