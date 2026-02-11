package com.noetic.websearch.provider.embedding;

import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import com.noetic.websearch.model.*;
import com.noetic.websearch.provider.EmbeddingProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Local embedding provider using DJL with ONNX Runtime for the all-MiniLM-L6-v2 model.
 *
 * <p>Uses the manual PassthroughTranslator approach with explicit tokenization,
 * mean pooling, and L2 normalization -- the same proven pattern from the
 * Symmetry embedding library.</p>
 *
 * <p>The model and tokenizer are downloaded from Hugging Face on first use (~23MB)
 * and cached at {@code ~/.websearch/models/}. Subsequent startups use the cached files.</p>
 *
 * <p>Produces 384-dimensional L2-normalized vectors.</p>
 */
@Component
public class OnnxEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingProvider.class);
    private static final int DIMENSIONS = 384;
    private static final String MODEL_NAME = "all-MiniLM-L6-v2";

    private static final String DEFAULT_MODEL_URL =
            "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx";
    private static final String DEFAULT_VOCAB_URL =
            "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/vocab.txt";

    @Value("${websearch.embedding.onnx.cache-dir:#{null}}")
    private String cacheDir;

    private BertWordPieceTokenizer tokenizer;
    private ZooModel<NDList, NDList> model;

    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing ONNX embedding provider (model={}, dimensions={})", MODEL_NAME, DIMENSIONS);

            Path cache = resolveCacheDirectory();

            // Initialize pure Java WordPiece tokenizer
            Path vocabPath = resolveFile(cache, "vocab.txt", DEFAULT_VOCAB_URL);
            this.tokenizer = new BertWordPieceTokenizer(vocabPath, 512);

            // Load ONNX model via DJL
            Path modelPath = resolveFile(cache, "model.onnx", DEFAULT_MODEL_URL);
            Criteria<NDList, NDList> criteria = Criteria.builder()
                    .setTypes(NDList.class, NDList.class)
                    .optModelUrls(modelPath.toUri().toString())
                    .optEngine("OnnxRuntime")
                    .optTranslator(new PassthroughTranslator())
                    .build();
            this.model = criteria.loadModel();

            log.info("ONNX embedding provider initialized successfully");

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ONNX embedding provider", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (model != null) {
            model.close();
        }
        log.info("ONNX embedding provider closed");
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
        float[] vector = computeEmbedding(request.text());
        return new EmbeddingResult(vector, vector.length, 0, MODEL_NAME, Map.of());
    }

    @Override
    public List<EmbeddingResult> embedBatch(EmbeddingBatchRequest request) {
        List<EmbeddingResult> results = new ArrayList<>();
        for (String text : request.texts()) {
            float[] vector = computeEmbedding(text);
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

    // ---- Core embedding logic (Symmetry pattern) ----

    private float[] computeEmbedding(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }

        try (NDManager manager = NDManager.newBaseManager();
             Predictor<NDList, NDList> predictor = model.newPredictor()) {

            // Tokenize using pure Java WordPiece tokenizer
            var encoding = tokenizer.encode(text);
            long[] inputIds = encoding.inputIds();
            long[] attentionMask = encoding.attentionMask();

            // Create input tensors with correct shape directly (ORT NDArray has limited op support)
            var inputIdsTensor = manager.create(new long[][]{inputIds});
            var attentionMaskTensor = manager.create(new long[][]{attentionMask});
            var tokenTypeIdsTensor = manager.create(new long[][]{encoding.tokenTypeIds()});

            NDList input = new NDList(inputIdsTensor, attentionMaskTensor, tokenTypeIdsTensor);

            // Run inference
            NDList output = predictor.predict(input);

            // Extract raw output: [1, seq_len, hidden_size] -> float[]
            float[] rawOutput = output.get(0).toFloatArray();
            int seqLen = inputIds.length;

            // Mean pooling + L2 normalization in pure Java (ORT NDArray doesn't support expandDims/broadcast)
            float[] embedding = meanPoolJava(rawOutput, attentionMask, seqLen, DIMENSIONS);
            return normalize(embedding);

        } catch (Exception e) {
            log.error("Failed to generate embedding for text (length={}): {}", text.length(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    /**
     * Apply mean pooling in pure Java.
     *
     * <p>The raw output from ONNX is a flat float[] representing [1, seqLen, hiddenSize].
     * We average all token embeddings weighted by the attention mask to ignore padding.</p>
     *
     * @param rawOutput flattened model output [1 * seqLen * hiddenSize]
     * @param attentionMask attention mask (1 for real tokens, 0 for padding)
     * @param seqLen sequence length
     * @param hiddenSize embedding dimensions (384)
     */
    private float[] meanPoolJava(float[] rawOutput, long[] attentionMask, int seqLen, int hiddenSize) {
        float[] sum = new float[hiddenSize];
        float maskSum = 0f;

        for (int t = 0; t < seqLen; t++) {
            float mask = attentionMask[t];
            if (mask == 0f) continue;
            maskSum += mask;
            int offset = t * hiddenSize;
            for (int d = 0; d < hiddenSize; d++) {
                sum[d] += rawOutput[offset + d] * mask;
            }
        }

        // Average
        if (maskSum > 0f) {
            for (int d = 0; d < hiddenSize; d++) {
                sum[d] /= maskSum;
            }
        }
        return sum;
    }

    /**
     * L2-normalize the embedding vector so cosine similarity equals dot product.
     */
    private float[] normalize(float[] embedding) {
        float norm = 0.0f;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm > 0) {
            float[] normalized = new float[embedding.length];
            for (int i = 0; i < embedding.length; i++) {
                normalized[i] = embedding[i] / norm;
            }
            return normalized;
        }
        return embedding;
    }

    // ---- File resolution ----

    private Path resolveCacheDirectory() {
        Path dir;
        if (cacheDir != null && !cacheDir.isBlank()) {
            dir = Path.of(cacheDir);
        } else {
            dir = Path.of(System.getProperty("user.home"), ".websearch", "models", MODEL_NAME);
        }
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create model cache directory: " + dir, e);
        }
        return dir;
    }

    private Path resolveFile(Path cacheDirectory, String filename, String downloadUrl) {
        Path targetPath = cacheDirectory.resolve(filename);

        // Already cached
        if (Files.exists(targetPath)) {
            log.debug("Using cached file: {}", targetPath);
            return targetPath;
        }

        // Download from URL
        log.info("Downloading {} from {}...", filename, downloadUrl);
        try (InputStream in = URI.create(downloadUrl).toURL().openStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Downloaded {} ({} bytes)", filename, Files.size(targetPath));
        } catch (Exception e) {
            throw new RuntimeException("Failed to download " + filename + " from " + downloadUrl, e);
        }

        return targetPath;
    }

    // ---- Passthrough translator ----

    /**
     * Passthrough translator for raw ONNX model input/output.
     * No pre/post processing -- we handle tokenization and pooling ourselves.
     */
    private static class PassthroughTranslator implements Translator<NDList, NDList> {

        @Override
        public NDList processInput(TranslatorContext ctx, NDList input) {
            return input;
        }

        @Override
        public NDList processOutput(TranslatorContext ctx, NDList output) {
            return output;
        }

        @Override
        public Batchifier getBatchifier() {
            return null;
        }
    }
}
