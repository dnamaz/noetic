package com.noetic.websearch.provider;

import com.noetic.websearch.model.EmbeddingBatchRequest;
import com.noetic.websearch.model.EmbeddingCapabilities;
import com.noetic.websearch.model.EmbeddingRequest;
import com.noetic.websearch.model.EmbeddingResult;

import java.util.List;

/**
 * Provider interface for generating vector embeddings.
 *
 * <p>Implementations include local ONNX models, direct API providers
 * (OpenAI, Cohere, Voyage), and enterprise cloud SDKs (Bedrock, Azure, Vertex).</p>
 */
public interface EmbeddingProvider {

    /** Provider type identifier (e.g. "onnx", "openai", "bedrock"). */
    String type();

    /** Declares what this provider supports. */
    EmbeddingCapabilities capabilities();

    /** Generate embedding for a single text. */
    EmbeddingResult embed(EmbeddingRequest request);

    /** Generate embeddings for multiple texts. */
    List<EmbeddingResult> embedBatch(EmbeddingBatchRequest request);

    /** Output vector dimensions. */
    int dimensions();

    /** Active model name. */
    String model();
}
