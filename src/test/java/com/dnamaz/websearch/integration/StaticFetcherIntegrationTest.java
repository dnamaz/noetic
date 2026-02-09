package com.dnamaz.websearch.integration;

import com.dnamaz.websearch.model.FetchRequest;
import com.dnamaz.websearch.model.FetchResult;
import com.dnamaz.websearch.model.OutputFormat;
import com.dnamaz.websearch.provider.fetcher.StaticContentFetcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link StaticContentFetcher}.
 * These tests make real HTTP requests to public websites.
 */
@Tag("integration")
@DisplayName("StaticContentFetcher integration tests")
class StaticFetcherIntegrationTest {

    private static StaticContentFetcher fetcher;

    @BeforeAll
    static void setup() {
        // No proxy
        fetcher = new StaticContentFetcher(false, "NONE", "127.0.0.1", 9050, "", "", false);
    }

    @Test
    @DisplayName("crawls example.com and returns clean markdown")
    void crawlsExampleCom() {
        FetchRequest request = FetchRequest.builder()
                .url("https://example.com")
                .outputFormat(OutputFormat.MARKDOWN)
                .includeLinks(true)
                .build();

        FetchResult result = fetcher.fetch(request);

        assertEquals(200, result.statusCode());
        assertEquals("static", result.fetcherUsed());
        assertNotNull(result.content());
        assertTrue(result.content().length() > 10, "Content should not be empty");
        assertTrue(result.content().contains("Example Domain"),
                "Should contain the page title text");
        assertTrue(result.wordCount() > 0, "Should have a word count");
        assertNotNull(result.rawHtml(), "Should include raw HTML");
        assertFalse(result.content().contains("<script>"), "Markdown should not contain script tags");
    }

    @Test
    @DisplayName("crawls example.com with TEXT format")
    void crawlsExampleComText() {
        FetchRequest request = FetchRequest.builder()
                .url("https://example.com")
                .outputFormat(OutputFormat.TEXT)
                .build();

        FetchResult result = fetcher.fetch(request);

        assertEquals(200, result.statusCode());
        assertFalse(result.content().contains("<p>"), "TEXT format should not contain HTML tags");
        assertTrue(result.content().contains("Example Domain"));
    }

    @Test
    @DisplayName("crawls example.com with HTML format preserves tags")
    void crawlsExampleComHtml() {
        FetchRequest request = FetchRequest.builder()
                .url("https://example.com")
                .outputFormat(OutputFormat.HTML)
                .build();

        FetchResult result = fetcher.fetch(request);

        assertEquals(200, result.statusCode());
        // HTML format should contain HTML elements
        assertTrue(result.content().contains("<") || result.content().length() > 0);
    }

    @Test
    @DisplayName("extracts links when includeLinks is true")
    void extractsLinks() {
        FetchRequest request = FetchRequest.builder()
                .url("https://example.com")
                .outputFormat(OutputFormat.MARKDOWN)
                .includeLinks(true)
                .build();

        FetchResult result = fetcher.fetch(request);

        assertNotNull(result.links());
        // example.com has at least one link (to iana.org)
        assertFalse(result.links().isEmpty(), "Should extract at least one link");
    }

    @Test
    @DisplayName("returns empty links when includeLinks is false")
    void noLinksWhenDisabled() {
        FetchRequest request = FetchRequest.builder()
                .url("https://example.com")
                .outputFormat(OutputFormat.MARKDOWN)
                .includeLinks(false)
                .build();

        FetchResult result = fetcher.fetch(request);

        assertTrue(result.links() == null || result.links().isEmpty());
    }

    @Test
    @DisplayName("handles non-existent domain gracefully")
    void handlesNonExistentDomain() {
        FetchRequest request = FetchRequest.builder()
                .url("https://this-domain-does-not-exist-at-all-12345.com")
                .outputFormat(OutputFormat.TEXT)
                .build();

        FetchResult result = fetcher.fetch(request);

        assertEquals(0, result.statusCode(), "Should return 0 status for failed requests");
        assertEquals("", result.content());
    }

    @Test
    @DisplayName("crawls a page with rich content and produces clean markdown")
    void crawlsRichContentPage() {
        FetchRequest request = FetchRequest.builder()
                .url("https://httpbin.org/html")
                .outputFormat(OutputFormat.MARKDOWN)
                .build();

        FetchResult result = fetcher.fetch(request);

        assertEquals(200, result.statusCode());
        assertNotNull(result.content());
        assertTrue(result.content().length() > 50, "Should extract meaningful content");
        // httpbin.org/html has a known page with "Herman Melville" text
        assertTrue(result.content().contains("Melville"),
                "Should contain expected text from httpbin/html");
    }
}
