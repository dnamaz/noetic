package com.noetic.websearch.provider.embedding;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure Java BERT WordPiece tokenizer.
 *
 * <p>Implements the same tokenization pipeline as the HuggingFace BERT tokenizer:
 * normalize, basic tokenize (whitespace + punctuation), WordPiece sub-tokenize,
 * then encode with special tokens [CLS]/[SEP] and padding.</p>
 *
 * <p>This avoids the Rust JNI dependency ({@code ai.djl.huggingface:tokenizers})
 * which crashes in GraalVM native image.</p>
 *
 * <p>Compatible with all BERT-based models using uncased WordPiece vocabularies
 * (all-MiniLM-L6-v2, BERT-base-uncased, etc.).</p>
 */
public class BertWordPieceTokenizer {

    private static final String UNK_TOKEN = "[UNK]";
    private static final String CLS_TOKEN = "[CLS]";
    private static final String SEP_TOKEN = "[SEP]";
    private static final String PAD_TOKEN = "[PAD]";
    private static final String WORDPIECE_PREFIX = "##";
    private static final int MAX_WORD_LENGTH = 200;

    private final Map<String, Integer> vocab;
    private final int clsId;
    private final int sepId;
    private final int padId;
    private final int unkId;
    private final int maxLength;

    /**
     * Create a tokenizer from a vocab.txt file.
     *
     * @param vocabPath path to vocab.txt (one token per line, ID = line number)
     * @param maxLength maximum sequence length (including [CLS] and [SEP])
     */
    public BertWordPieceTokenizer(Path vocabPath, int maxLength) throws IOException {
        this.vocab = loadVocab(vocabPath);
        this.maxLength = maxLength;
        this.clsId = vocab.getOrDefault(CLS_TOKEN, 101);
        this.sepId = vocab.getOrDefault(SEP_TOKEN, 102);
        this.padId = vocab.getOrDefault(PAD_TOKEN, 0);
        this.unkId = vocab.getOrDefault(UNK_TOKEN, 100);
    }

    /**
     * Encode text into BERT input tensors.
     *
     * @param text the input text
     * @return encoding with inputIds, attentionMask, and tokenTypeIds
     */
    public Encoding encode(String text) {
        // 1. Normalize: lowercase + strip accents
        String normalized = normalize(text);

        // 2. Basic tokenize: whitespace + punctuation split
        List<String> basicTokens = basicTokenize(normalized);

        // 3. WordPiece sub-tokenize
        List<Integer> tokenIds = new ArrayList<>();
        tokenIds.add(clsId); // [CLS]

        for (String token : basicTokens) {
            List<Integer> subIds = wordPieceTokenize(token);
            // Check if adding these would exceed maxLength - 1 (reserve for [SEP])
            if (tokenIds.size() + subIds.size() >= maxLength - 1) {
                // Add as many as fit
                int remaining = maxLength - 1 - tokenIds.size();
                for (int i = 0; i < remaining && i < subIds.size(); i++) {
                    tokenIds.add(subIds.get(i));
                }
                break;
            }
            tokenIds.addAll(subIds);
        }

        tokenIds.add(sepId); // [SEP]

        int seqLen = tokenIds.size();

        // 4. Pad to maxLength
        long[] inputIds = new long[maxLength];
        long[] attentionMask = new long[maxLength];
        long[] tokenTypeIds = new long[maxLength]; // all zeros for single segment

        for (int i = 0; i < seqLen; i++) {
            inputIds[i] = tokenIds.get(i);
            attentionMask[i] = 1;
        }
        for (int i = seqLen; i < maxLength; i++) {
            inputIds[i] = padId;
            attentionMask[i] = 0;
        }

        return new Encoding(inputIds, attentionMask, tokenTypeIds);
    }

    // ---- Normalization ----

    private String normalize(String text) {
        // Lowercase
        String lower = text.toLowerCase();
        // Strip accents: NFD decompose, then remove combining marks
        String nfd = Normalizer.normalize(lower, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(nfd.length());
        for (int i = 0; i < nfd.length(); i++) {
            char c = nfd.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ---- Basic Tokenization ----

    /**
     * Split on whitespace and punctuation. Punctuation characters become
     * separate tokens. Whitespace is consumed.
     */
    private List<String> basicTokenize(String text) {
        // Clean: replace control chars and zero-width chars with space
        StringBuilder cleaned = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0 || c == 0xFFFD || Character.isISOControl(c)) {
                cleaned.append(' ');
            } else if (Character.isWhitespace(c)) {
                cleaned.append(' ');
            } else {
                cleaned.append(c);
            }
        }

        // Add whitespace around punctuation and CJK characters
        StringBuilder spaced = new StringBuilder(cleaned.length() * 2);
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (isPunctuation(c) || isCjkCharacter(c)) {
                spaced.append(' ').append(c).append(' ');
            } else {
                spaced.append(c);
            }
        }

        // Split on whitespace
        List<String> tokens = new ArrayList<>();
        for (String token : spaced.toString().split("\\s+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    // ---- WordPiece Tokenization ----

    /**
     * Apply the WordPiece algorithm to a single basic token.
     * Greedily matches the longest prefix in the vocabulary.
     */
    private List<Integer> wordPieceTokenize(String token) {
        if (token.length() > MAX_WORD_LENGTH) {
            return List.of(unkId);
        }

        List<Integer> ids = new ArrayList<>();
        int start = 0;
        while (start < token.length()) {
            int end = token.length();
            Integer foundId = null;

            while (start < end) {
                String substr = token.substring(start, end);
                if (start > 0) {
                    substr = WORDPIECE_PREFIX + substr;
                }
                Integer id = vocab.get(substr);
                if (id != null) {
                    foundId = id;
                    break;
                }
                end--;
            }

            if (foundId == null) {
                // Character not in vocab at all
                ids.add(unkId);
                start++;
            } else {
                ids.add(foundId);
                start = end;
            }
        }
        return ids;
    }

    // ---- Character Classification ----

    private boolean isPunctuation(char c) {
        int type = Character.getType(c);
        // ASCII punctuation range
        if ((c >= 33 && c <= 47) || (c >= 58 && c <= 64) ||
                (c >= 91 && c <= 96) || (c >= 123 && c <= 126)) {
            return true;
        }
        // Unicode punctuation categories
        return type == Character.DASH_PUNCTUATION ||
                type == Character.START_PUNCTUATION ||
                type == Character.END_PUNCTUATION ||
                type == Character.CONNECTOR_PUNCTUATION ||
                type == Character.OTHER_PUNCTUATION ||
                type == Character.INITIAL_QUOTE_PUNCTUATION ||
                type == Character.FINAL_QUOTE_PUNCTUATION;
    }

    private boolean isCjkCharacter(char c) {
        // CJK Unified Ideographs and common CJK ranges
        return (c >= 0x4E00 && c <= 0x9FFF) ||
                (c >= 0x3400 && c <= 0x4DBF) ||
                (c >= 0xF900 && c <= 0xFAFF) ||
                (c >= 0x2F800 && c <= 0x2FA1F);
    }

    // ---- Vocabulary Loading ----

    private static Map<String, Integer> loadVocab(Path vocabPath) throws IOException {
        Map<String, Integer> vocab = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(vocabPath)) {
            String line;
            int id = 0;
            while ((line = reader.readLine()) != null) {
                vocab.put(line.trim(), id++);
            }
        }
        return vocab;
    }

    // ---- Result Record ----

    /**
     * Tokenization result containing the three tensors needed for BERT inference.
     */
    public record Encoding(long[] inputIds, long[] attentionMask, long[] tokenTypeIds) {
    }
}
