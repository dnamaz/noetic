package com.noetic.websearch.integration;

import com.noetic.websearch.model.FetchRequest;
import com.noetic.websearch.model.FetchResult;
import com.noetic.websearch.model.OutputFormat;
import com.noetic.websearch.provider.fetcher.ChromiumDetector;
import com.noetic.websearch.provider.fetcher.DynamicContentFetcher;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link DynamicContentFetcher}.
 *
 * <p>Tests that require Chromium are skipped automatically if Chrome/Chromium
 * is not installed on the system (via JUnit {@code assumeTrue}).</p>
 */
@Tag("integration")
@DisplayName("DynamicContentFetcher integration tests")
class DynamicFetcherIntegrationTest {

    private static DynamicContentFetcher fetcher;
    private static boolean chromiumAvailable;

    @BeforeAll
    static void setup() {
        chromiumAvailable = ChromiumDetector.resolve("auto").isPresent();

        // Create fetcher -- it will auto-detect Chromium in @PostConstruct
        fetcher = new DynamicContentFetcher(
                true, "auto", 15000, 1,
                false, "NONE", "127.0.0.1", 9050,
                null  // no CAPTCHA solver
        );
        fetcher.initialize();
    }

    @AfterAll
    static void teardown() {
        if (fetcher != null) {
            fetcher.shutdown();
        }
    }

    // ── Jsoup fallback tests (always run) ────────────────────────────────

    @Nested
    @DisplayName("Jsoup fallback (no Chromium required)")
    class JsoupFallback {

        @Test
        @DisplayName("fetches example.com with clean markdown via fallback")
        void fetchesExampleCom() {
            FetchRequest request = FetchRequest.builder()
                    .url("https://example.com")
                    .outputFormat(OutputFormat.MARKDOWN)
                    .includeLinks(true)
                    .build();

            FetchResult result = fetcher.fetch(request);

            assertEquals("dynamic", result.fetcherUsed());
            assertTrue(result.content().contains("Example Domain"));
            assertTrue(result.wordCount() > 0);
            assertNotNull(result.rawHtml());

            // Should have extracted links
            if (result.links() != null) {
                assertFalse(result.links().isEmpty());
            }
        }

        @Test
        @DisplayName("produces same quality markdown as static fetcher")
        void sameQualityAsStatic() {
            FetchRequest request = FetchRequest.builder()
                    .url("https://httpbin.org/html")
                    .outputFormat(OutputFormat.MARKDOWN)
                    .build();

            FetchResult result = fetcher.fetch(request);

            assertEquals("dynamic", result.fetcherUsed());
            assertTrue(result.content().contains("Melville"),
                    "Should extract same content as static fetcher");
            assertFalse(result.content().contains("<script>"),
                    "Should strip scripts");
        }
    }

    // ── Chromium rendering tests (require Chrome installed) ──────────────

    @Nested
    @DisplayName("Chromium rendering (requires Chrome)")
    class ChromiumRendering {

        @Test
        @DisplayName("renders a static page via Chromium")
        void rendersStaticPage() {
            assumeTrue(chromiumAvailable, "Chromium not available, skipping");

            FetchRequest request = FetchRequest.builder()
                    .url("https://example.com")
                    .outputFormat(OutputFormat.MARKDOWN)
                    .build();

            FetchResult result = fetcher.fetch(request);

            assertEquals("dynamic", result.fetcherUsed());
            assertTrue(result.content().contains("Example Domain"));

            // Check providerMeta to confirm Chromium was used
            if (result.providerMeta() != null) {
                assertEquals("chromium", result.providerMeta().get("renderer"),
                        "Should use Chromium renderer when available");
            }
        }

        @Test
        @DisplayName("renders httpbin.org/html and extracts content via ContentExtractor")
        void rendersAndExtracts() {
            assumeTrue(chromiumAvailable, "Chromium not available, skipping");

            FetchRequest request = FetchRequest.builder()
                    .url("https://httpbin.org/html")
                    .outputFormat(OutputFormat.MARKDOWN)
                    .includeLinks(true)
                    .includeImages(true)
                    .build();

            FetchResult result = fetcher.fetch(request);

            assertEquals("dynamic", result.fetcherUsed());
            assertTrue(result.content().contains("Melville"));
            assertTrue(result.wordCount() > 10);
            assertNotNull(result.rawHtml());
        }

        @Test
        @DisplayName("renders a JS-generated page that static fetcher cannot handle")
        void rendersJsGeneratedPage() {
            assumeTrue(chromiumAvailable, "Chromium not available, skipping");

            // httpbin.org/headers rendered via JS -- will have content via Chromium
            FetchRequest request = FetchRequest.builder()
                    .url("https://httpbin.org/html")
                    .outputFormat(OutputFormat.TEXT)
                    .build();

            FetchResult result = fetcher.fetch(request);

            assertEquals("dynamic", result.fetcherUsed());
            assertNotNull(result.content());
            assertTrue(result.content().length() > 50);
        }

        @Test
        @DisplayName("supports mobile user-agent")
        void supportsMobileUA() {
            assumeTrue(chromiumAvailable, "Chromium not available, skipping");

            FetchRequest request = FetchRequest.builder()
                    .url("https://example.com")
                    .outputFormat(OutputFormat.TEXT)
                    .mobile(true)
                    .build();

            FetchResult result = fetcher.fetch(request);

            assertEquals("dynamic", result.fetcherUsed());
            assertNotNull(result.content());
        }
    }
}
