package com.dnamaz.websearch.provider.embedding;

import com.dnamaz.websearch.model.*;
import com.dnamaz.websearch.provider.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared base for enterprise cloud SDK embedding providers (Bedrock, Azure, Vertex).
 *
 * <p>Unlike {@link AbstractApiEmbeddingProvider}, this base does NOT manage its own
 * HTTP client -- each cloud SDK handles its own credential chain, token refresh,
 * regional endpoints, and TLS internally. This base handles batch splitting,
 * InputType mapping, and usage tracking.</p>
 */
public abstract class AbstractCloudEmbeddingProvider implements EmbeddingProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected abstract Object buildSdkClient();
    protected abstract EmbeddingResult invokeSdk(String text, InputType inputType);
    protected abstract List<EmbeddingResult> invokeSdkBatch(List<String> texts, InputType inputType);

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        return invokeSdk(request.text(), request.inputType());
    }

    @Override
    public List<EmbeddingResult> embedBatch(EmbeddingBatchRequest request) {
        int maxBatch = capabilities().maxBatchSize();
        if (maxBatch <= 0 || request.texts().size() <= maxBatch) {
            return invokeSdkBatch(request.texts(), request.inputType());
        }

        // Split into chunks respecting maxBatchSize
        List<EmbeddingResult> allResults = new ArrayList<>();
        for (int i = 0; i < request.texts().size(); i += maxBatch) {
            int end = Math.min(i + maxBatch, request.texts().size());
            List<String> batch = request.texts().subList(i, end);
            allResults.addAll(invokeSdkBatch(batch, request.inputType()));
        }
        return allResults;
    }
}
