package com.noetic.websearch.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Rich response from a ContentFetcher.
 */
public record FetchResult(
        String url,
        String title,
        String content,
        String rawHtml,
        List<String> links,
        List<String> images,
        int wordCount,
        int statusCode,
        String fetcherUsed,
        Duration fetchTime,
        String screenshot,
        Map<String, Object> providerMeta
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String url;
        private String title = "";
        private String content = "";
        private String rawHtml;
        private List<String> links = List.of();
        private List<String> images = List.of();
        private int wordCount;
        private int statusCode = 200;
        private String fetcherUsed = "";
        private Duration fetchTime = Duration.ZERO;
        private String screenshot;
        private Map<String, Object> providerMeta = Map.of();

        public Builder url(String v) { this.url = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public Builder content(String v) { this.content = v; return this; }
        public Builder rawHtml(String v) { this.rawHtml = v; return this; }
        public Builder links(List<String> v) { this.links = v; return this; }
        public Builder images(List<String> v) { this.images = v; return this; }
        public Builder wordCount(int v) { this.wordCount = v; return this; }
        public Builder statusCode(int v) { this.statusCode = v; return this; }
        public Builder fetcherUsed(String v) { this.fetcherUsed = v; return this; }
        public Builder fetchTime(Duration v) { this.fetchTime = v; return this; }
        public Builder screenshot(String v) { this.screenshot = v; return this; }
        public Builder providerMeta(Map<String, Object> v) { this.providerMeta = v; return this; }

        public FetchResult build() {
            return new FetchResult(url, title, content, rawHtml, links, images,
                    wordCount, statusCode, fetcherUsed, fetchTime, screenshot, providerMeta);
        }
    }
}
