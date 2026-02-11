package com.noetic.websearch.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Universal request model for all ContentFetcher implementations.
 * Providers ignore fields they don't support.
 */
public record FetchRequest(
        String url,
        boolean renderJavaScript,
        Duration timeout,
        boolean waitForNetworkIdle,
        String waitForSelector,
        boolean includeLinks,
        boolean includeImages,
        OutputFormat outputFormat,
        Map<String, String> headers,
        Map<String, String> cookies,
        boolean mobile,
        boolean skipTlsVerification,
        boolean captureScreenshot,
        List<PageAction> actions,
        Map<String, Object> extra
) {
    public FetchRequest {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }
        if (timeout == null) timeout = Duration.ofSeconds(10);
        if (outputFormat == null) outputFormat = OutputFormat.MARKDOWN;
        if (headers == null) headers = Map.of();
        if (cookies == null) cookies = Map.of();
        if (actions == null) actions = List.of();
        if (extra == null) extra = Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String url;
        private boolean renderJavaScript;
        private Duration timeout = Duration.ofSeconds(10);
        private boolean waitForNetworkIdle;
        private String waitForSelector;
        private boolean includeLinks;
        private boolean includeImages;
        private OutputFormat outputFormat = OutputFormat.MARKDOWN;
        private Map<String, String> headers = Map.of();
        private Map<String, String> cookies = Map.of();
        private boolean mobile;
        private boolean skipTlsVerification;
        private boolean captureScreenshot;
        private List<PageAction> actions = List.of();
        private Map<String, Object> extra = Map.of();

        public Builder url(String url) { this.url = url; return this; }
        public Builder renderJavaScript(boolean v) { this.renderJavaScript = v; return this; }
        public Builder timeout(Duration v) { this.timeout = v; return this; }
        public Builder waitForNetworkIdle(boolean v) { this.waitForNetworkIdle = v; return this; }
        public Builder waitForSelector(String v) { this.waitForSelector = v; return this; }
        public Builder includeLinks(boolean v) { this.includeLinks = v; return this; }
        public Builder includeImages(boolean v) { this.includeImages = v; return this; }
        public Builder outputFormat(OutputFormat v) { this.outputFormat = v; return this; }
        public Builder headers(Map<String, String> v) { if (v != null) this.headers = v; return this; }
        public Builder cookies(Map<String, String> v) { if (v != null) this.cookies = v; return this; }
        public Builder mobile(boolean v) { this.mobile = v; return this; }
        public Builder skipTlsVerification(boolean v) { this.skipTlsVerification = v; return this; }
        public Builder captureScreenshot(boolean v) { this.captureScreenshot = v; return this; }
        public Builder actions(List<PageAction> v) { if (v != null) this.actions = v; return this; }
        public Builder extra(Map<String, Object> v) { if (v != null) this.extra = v; return this; }

        public FetchRequest build() {
            return new FetchRequest(url, renderJavaScript, timeout, waitForNetworkIdle,
                    waitForSelector, includeLinks, includeImages, outputFormat,
                    headers, cookies, mobile, skipTlsVerification, captureScreenshot,
                    actions, extra);
        }
    }
}
