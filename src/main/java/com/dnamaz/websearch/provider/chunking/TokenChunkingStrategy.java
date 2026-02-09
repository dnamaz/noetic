package com.dnamaz.websearch.provider.chunking;

import com.dnamaz.websearch.model.ChunkRequest;
import com.dnamaz.websearch.model.ContentChunk;
import com.dnamaz.websearch.provider.ChunkingStrategy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Splits content into chunks based on approximate token count (word-based).
 * Simple and predictable chunking strategy.
 */
@Component
public class TokenChunkingStrategy implements ChunkingStrategy {

    @Override
    public String type() {
        return "token";
    }

    @Override
    public List<ContentChunk> chunk(ChunkRequest request) {
        String[] words = request.content().split("\\s+");
        List<ContentChunk> chunks = new ArrayList<>();

        int maxTokens = request.maxChunkSize();
        int overlapTokens = request.overlap();

        int i = 0;
        while (i < words.length) {
            int end = Math.min(i + maxTokens, words.length);
            String chunkText = String.join(" ", java.util.Arrays.copyOfRange(words, i, end));

            chunks.add(new ContentChunk(
                    UUID.randomUUID().toString(),
                    chunkText,
                    end - i,
                    false
            ));

            i += maxTokens - overlapTokens;
            if (i >= words.length) break;
        }

        return chunks;
    }
}
