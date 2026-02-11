package com.noetic.websearch.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Universal search result model across all providers.
 */
public record SearchResult(
        String title,
        String url,
        String snippet,
        List<String> extraSnippets,
        String rawContent,
        Double score,
        Instant publishedDate,
        Map<String, Object> providerMeta
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String title = "";
        private String url = "";
        private String snippet = "";
        private List<String> extraSnippets = List.of();
        private String rawContent;
        private Double score;
        private Instant publishedDate;
        private Map<String, Object> providerMeta = Map.of();

        public Builder title(String v) { this.title = v; return this; }
        public Builder url(String v) { this.url = v; return this; }
        public Builder snippet(String v) { this.snippet = v; return this; }
        public Builder extraSnippets(List<String> v) { this.extraSnippets = v; return this; }
        public Builder rawContent(String v) { this.rawContent = v; return this; }
        public Builder score(Double v) { this.score = v; return this; }
        public Builder publishedDate(Instant v) { this.publishedDate = v; return this; }
        public Builder providerMeta(Map<String, Object> v) { this.providerMeta = v; return this; }

        public SearchResult build() {
            return new SearchResult(title, url, snippet, extraSnippets,
                    rawContent, score, publishedDate, providerMeta);
        }
    }
}
