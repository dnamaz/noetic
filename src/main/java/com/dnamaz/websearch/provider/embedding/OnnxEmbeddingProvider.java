package com.dnamaz.websearch.provider.embedding;

import com.dnamaz.websearch.model.*;
import com.dnamaz.websearch.provider.EmbeddingProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Local embedding provider using Spring AI's TransformersEmbeddingModel
 * backed by ONNX Runtime with the all-MiniLM-L6-v2 model.
 *
 * <p>The model and tokenizer are automatically downloaded from Hugging Face
 * on first startup (~23MB) and cached locally. Subsequent startups use
 * the cached files.</p>
 *
 * <p>Produces 384-dimensional vectors. InputType is ignored since the
 * local model doesn't distinguish between document and query embeddings.</p>
 */
@Component
public class OnnxEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingProvider.class);
    private static final int DIMENSIONS = 384;
    private static final String MODEL_NAME = "all-MiniLM-L6-v2";

    private final EmbeddingModel embeddingModel;

    public OnnxEmbeddingProvider(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @PostConstruct
    public void initialize() {
        log.info("ONNX embedding provider initialized with Spring AI TransformersEmbeddingModel");
        log.info("Model: {}, dimensions: {}", MODEL_NAME, DIMENSIONS);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down ONNX embedding provider");
    }

    @Override
    public String type() {
        return "onnx";
    }

    @Override
    public EmbeddingCapabilities capabilities() {
        return new EmbeddingCapabilities(
                false,      // supportsInputType
                false,      // supportsDimensionOverride
                true,       // supportsBatch
                64,         // maxBatchSize
                512,        // maxTokensPerText
                DIMENSIONS, // defaultDimensions
                AuthType.NONE
        );
    }

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        float[] vector = embeddingModel.embed(request.text());
        return new EmbeddingResult(vector, vector.length, 0, MODEL_NAME, Map.of());
    }

    @Override
    public List<EmbeddingResult> embedBatch(EmbeddingBatchRequest request) {
        List<EmbeddingResult> results = new ArrayList<>();
        // Spring AI's EmbeddingModel supports batch via embed(List<String>),
        // but the interface returns EmbeddingResponse. Use individual calls
        // for simplicity and compatibility.
        for (String text : request.texts()) {
            float[] vector = embeddingModel.embed(text);
            results.add(new EmbeddingResult(vector, vector.length, 0, MODEL_NAME, Map.of()));
        }
        return results;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public String model() {
        return MODEL_NAME;
    }
}
