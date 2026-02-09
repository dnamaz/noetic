package com.dnamaz.websearch.provider.chunking;

import com.dnamaz.websearch.model.ChunkRequest;
import com.dnamaz.websearch.model.ContentChunk;
import com.dnamaz.websearch.provider.ChunkingStrategy;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Splits content into chunks at sentence boundaries.
 * Maintains complete sentences and supports overlap.
 */
@Component
public class SentenceChunkingStrategy implements ChunkingStrategy {

    @Override
    public String type() {
        return "sentence";
    }

    @Override
    public List<ContentChunk> chunk(ChunkRequest request) {
        List<String> sentences = splitIntoSentences(request.content());
        List<ContentChunk> chunks = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        List<String> currentSentences = new ArrayList<>();

        for (String sentence : sentences) {
            int projectedLength = current.length() + sentence.length();

            if (projectedLength > request.maxChunkSize() && !current.isEmpty()) {
                // Emit current chunk
                chunks.add(createChunk(current.toString().trim()));

                // Apply overlap by keeping last N characters of sentences
                current = new StringBuilder();
                currentSentences.clear();

                if (request.overlap() > 0) {
                    // Re-add the last sentence for overlap
                    current.append(sentence);
                    currentSentences.add(sentence);
                    continue;
                }
            }

            current.append(sentence);
            currentSentences.add(sentence);
        }

        if (!current.isEmpty()) {
            chunks.add(createChunk(current.toString().trim()));
        }

        return chunks;
    }

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(text);

        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE;
             start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence + " ");
            }
        }

        return sentences;
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
