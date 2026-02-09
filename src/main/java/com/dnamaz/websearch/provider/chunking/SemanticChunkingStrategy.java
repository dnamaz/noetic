package com.dnamaz.websearch.provider.chunking;

import com.dnamaz.websearch.model.ChunkRequest;
import com.dnamaz.websearch.model.ContentChunk;
import com.dnamaz.websearch.provider.ChunkingStrategy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Splits content at semantic boundaries (paragraphs, headings, section breaks).
 * Falls back to sentence splitting for very long sections.
 */
@Component
public class SemanticChunkingStrategy implements ChunkingStrategy {

    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\n\\s*\\n");
    private static final Pattern HEADING_SPLIT = Pattern.compile("(?m)^#{1,6}\\s");

    @Override
    public String type() {
        return "semantic";
    }

    @Override
    public List<ContentChunk> chunk(ChunkRequest request) {
        // Split on paragraph boundaries first
        String[] paragraphs = PARAGRAPH_SPLIT.split(request.content());
        List<ContentChunk> chunks = new ArrayList<>();

        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            // If adding this paragraph would exceed max, emit current and start new
            if (current.length() + trimmed.length() > request.maxChunkSize()
                    && !current.isEmpty()) {
                chunks.add(createChunk(current.toString().trim()));
                current = new StringBuilder();
            }

            // If a single paragraph exceeds max, split it further
            if (trimmed.length() > request.maxChunkSize()) {
                if (!current.isEmpty()) {
                    chunks.add(createChunk(current.toString().trim()));
                    current = new StringBuilder();
                }
                // Split long paragraph by sentences
                String[] sentences = trimmed.split("(?<=[.!?])\\s+");
                StringBuilder sentBuf = new StringBuilder();
                for (String sent : sentences) {
                    if (sentBuf.length() + sent.length() > request.maxChunkSize()
                            && !sentBuf.isEmpty()) {
                        chunks.add(createChunk(sentBuf.toString().trim()));
                        sentBuf = new StringBuilder();
                    }
                    sentBuf.append(sent).append(" ");
                }
                if (!sentBuf.isEmpty()) {
                    current.append(sentBuf);
                }
            } else {
                current.append(trimmed).append("\n\n");
            }
        }

        if (!current.isEmpty()) {
            chunks.add(createChunk(current.toString().trim()));
        }

        return chunks;
    }

    private ContentChunk createChunk(String text) {
        int tokenEstimate = text.split("\\s+").length;
        return new ContentChunk(
                UUID.randomUUID().toString(),
                text,
                tokenEstimate,
                false
        );
    }
}
