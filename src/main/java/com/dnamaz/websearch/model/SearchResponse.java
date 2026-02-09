package com.dnamaz.websearch.model;

import java.time.Duration;
import java.util.List;

/**
 * Rich search response envelope.
 */
public record SearchResponse(
        String provider,
        List<SearchResult> results,
        String aiAnswer,
        Duration responseTime,
        boolean fromCache
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String provider = "";
        private List<SearchResult> results = List.of();
        private String aiAnswer;
        private Duration responseTime = Duration.ZERO;
        private boolean fromCache;

        public Builder provider(String v) { this.provider = v; return this; }
        public Builder results(List<SearchResult> v) { this.results = v; return this; }
        public Builder aiAnswer(String v) { this.aiAnswer = v; return this; }
        public Builder responseTime(Duration v) { this.responseTime = v; return this; }
        public Builder fromCache(boolean v) { this.fromCache = v; return this; }

        public SearchResponse build() {
            return new SearchResponse(provider, results, aiAnswer, responseTime, fromCache);
        }
    }
}
