package com.noetic.websearch.model;

/**
 * Semantic hint for embedding generation that improves retrieval quality.
 *
 * <p>Providers like Cohere, Voyage, and Google Vertex produce different
 * embeddings depending on whether the text is content being stored or
 * a query being searched. Local models that don't distinguish input
 * types simply ignore this field.</p>
 */
public enum InputType {
    /** Content being stored (Cohere: search_document, Voyage: document, Google: retrieval_document) */
    DOCUMENT,
    /** Search query being run (Cohere: search_query, Voyage: query, Google: retrieval_query) */
    QUERY,
    /** Text for classification tasks */
    CLASSIFICATION,
    /** Text for clustering tasks */
    CLUSTERING
}
