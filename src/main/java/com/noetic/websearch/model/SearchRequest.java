package com.noetic.websearch.model;

import java.util.List;
import java.util.Map;

/**
 * Universal search request model across all SearchProvider implementations.
 */
public record SearchRequest(
        String query,
        int maxResults,
        Freshness freshness,
        String language,
        String country,
        List<String> includeDomains,
        List<String> excludeDomains,
        boolean safeSearch,
        SearchDepth searchDepth,
        boolean skipCache,
        Map<String, Object> extra
) {
    public SearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query is required");
        }
        if (maxResults <= 0) maxResults = 10;
        if (freshness == null) freshness = Freshness.NONE;
        if (searchDepth == null) searchDepth = SearchDepth.BASIC;
        if (includeDomains == null) includeDomains = List.of();
        if (excludeDomains == null) excludeDomains = List.of();
        if (extra == null) extra = Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String query;
        private int maxResults = 10;
        private Freshness freshness = Freshness.NONE;
        private String language;
        private String country;
        private List<String> includeDomains = List.of();
        private List<String> excludeDomains = List.of();
        private boolean safeSearch;
        private SearchDepth searchDepth = SearchDepth.BASIC;
        private boolean skipCache;
        private Map<String, Object> extra = Map.of();

        public Builder query(String v) { this.query = v; return this; }
        public Builder maxResults(Integer v) { if (v != null) this.maxResults = v; return this; }
        public Builder freshness(Freshness v) { if (v != null) this.freshness = v; return this; }
        public Builder language(String v) { this.language = v; return this; }
        public Builder country(String v) { this.country = v; return this; }
        public Builder includeDomains(List<String> v) { if (v != null) this.includeDomains = v; return this; }
        public Builder excludeDomains(List<String> v) { if (v != null) this.excludeDomains = v; return this; }
        public Builder safeSearch(boolean v) { this.safeSearch = v; return this; }
        public Builder searchDepth(SearchDepth v) { if (v != null) this.searchDepth = v; return this; }
        public Builder skipCache(boolean v) { this.skipCache = v; return this; }
        public Builder extra(Map<String, Object> v) { if (v != null) this.extra = v; return this; }

        public SearchRequest build() {
            return new SearchRequest(query, maxResults, freshness, language, country,
                    includeDomains, excludeDomains, safeSearch, searchDepth, skipCache, extra);
        }
    }
}
