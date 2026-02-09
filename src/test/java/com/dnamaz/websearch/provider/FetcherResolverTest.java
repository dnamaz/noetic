package com.dnamaz.websearch.provider;

import com.dnamaz.websearch.model.FetchRequest;
import com.dnamaz.websearch.model.FetchResult;
import com.dnamaz.websearch.model.FetcherCapabilities;
import com.dnamaz.websearch.model.OutputFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FetcherResolver}.
 */
class FetcherResolverTest {

    // ── Test helpers ─────────────────────────────────────────────────────

    /** A fake fetcher that returns configurable content. */
    static class FakeFetcher implements ContentFetcher {
        private final String type;
        private final String content;
        private final String rawHtml;

        FakeFetcher(String type, String content) {
            this(type, content, "<html>" + content + "</html>");
        }

        FakeFetcher(String type, String content, String rawHtml) {
            this.type = type;
            this.content = content;
            this.rawHtml = rawHtml;
        }

        @Override public String type() { return type; }
        @Override public FetcherCapabilities capabilities() {
            return new FetcherCapabilities(false, false, false, false, false, false, false, false);
        }
        @Override public boolean supports(FetchRequest request) { return true; }
        @Override public FetchResult fetch(FetchRequest request) {
            return FetchResult.builder()
                    .url(request.url())
                    .content(content)
                    .rawHtml(rawHtml)
                    .statusCode(200)
                    .fetcherUsed(type)
                    .fetchTime(Duration.ofMillis(10))
                    .build();
        }
    }

    private FetchRequest request(String url) {
        return FetchRequest.builder().url(url).outputFormat(OutputFormat.MARKDOWN).build();
    }

    // ── Explicit mode ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Explicit fetch mode")
    class ExplicitMode {

        @Test
        @DisplayName("explicit mode selects the specified fetcher directly")
        void selectsExplicitFetcher() {
            var resolver = new FetcherResolver(
                    List.of(new FakeFetcher("static", "static content"),
                            new FakeFetcher("dynamic", "dynamic content")),
                    List.of("static", "dynamic"), true, List.of());

            FetchResult result = resolver.resolve(request("https://example.com"), "dynamic");

            assertEquals("dynamic", result.fetcherUsed());
            assertEquals("dynamic content", result.content());
        }

        @Test
        @DisplayName("explicit mode throws for unknown fetcher")
        void throwsForUnknownFetcher() {
            var resolver = new FetcherResolver(
                    List.of(new FakeFetcher("static", "content")),
                    List.of("static"), true, List.of());

            assertThrows(IllegalArgumentException.class, () ->
                    resolver.resolve(request("https://example.com"), "nonexistent"));
        }
    }

    // ── Domain rules ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Domain rules")
    class DomainRules {

        @Test
        @DisplayName("domain rule overrides auto-detection")
        void domainRuleOverridesAuto() {
            var resolver = new FetcherResolver(
                    List.of(new FakeFetcher("static", "static content"),
                            new FakeFetcher("dynamic", "dynamic content")),
                    List.of("static", "dynamic"), true,
                    List.of("*.notion.so=dynamic"));

            FetchResult result = resolver.resolve(
                    request("https://www.notion.so/page/123"), "auto");

            assertEquals("dynamic", result.fetcherUsed());
        }

        @Test
        @DisplayName("non-matching domain falls through to chain")
        void nonMatchingDomainUsesChain() {
            var resolver = new FetcherResolver(
                    List.of(new FakeFetcher("static",
                            "Static content that is well above the minimum content length threshold of one hundred characters for the fetcher resolver auto detection"),
                            new FakeFetcher("dynamic", "dynamic content")),
                    List.of("static", "dynamic"), true,
                    List.of("*.notion.so=dynamic"));

            FetchResult result = resolver.resolve(
                    request("https://example.com/page"), "auto");

            assertEquals("static", result.fetcherUsed());
        }
    }

    // ── SPA detection ────────────────────────────────────────────────────

    @Nested
    @DisplayName("SPA auto-detection")
    class SpaDetection {

        @Test
        @DisplayName("detects React SPA shell and falls through to next fetcher")
        void detectsReactSpa() {
            var staticFetcher = new FakeFetcher("static",
                    "Some short content but enough chars to pass the length check padding padding padding padding",
                    "<html><body><div id=\"root\"></div><script src=\"bundle.js\"></script></body></html>");
            var dynamicFetcher = new FakeFetcher("dynamic", "Rendered React content with lots of text");

            var resolver = new FetcherResolver(
                    List.of(staticFetcher, dynamicFetcher),
                    List.of("static", "dynamic"), true, List.of());

            FetchResult result = resolver.resolve(request("https://react-app.com"), "auto");

            assertEquals("dynamic", result.fetcherUsed());
        }

        @Test
        @DisplayName("detects Next.js SPA shell")
        void detectsNextJsSpa() {
            var staticFetcher = new FakeFetcher("static",
                    "Some content that is long enough to pass the minimum content length check",
                    "<html><body><div id=\"__next\"></div></body></html>");
            var dynamicFetcher = new FakeFetcher("dynamic", "Server-rendered Next.js content");

            var resolver = new FetcherResolver(
                    List.of(staticFetcher, dynamicFetcher),
                    List.of("static", "dynamic"), true, List.of());

            FetchResult result = resolver.resolve(request("https://nextjs-app.com"), "auto");

            assertEquals("dynamic", result.fetcherUsed());
        }

        @Test
        @DisplayName("short content triggers fallthrough to next fetcher")
        void shortContentTriggersNext() {
            var staticFetcher = new FakeFetcher("static", "short"); // < 100 chars
            var dynamicFetcher = new FakeFetcher("dynamic",
                    "Dynamic content that is longer than the minimum threshold for content length");

            var resolver = new FetcherResolver(
                    List.of(staticFetcher, dynamicFetcher),
                    List.of("static", "dynamic"), true, List.of());

            FetchResult result = resolver.resolve(request("https://js-app.com"), "auto");

            assertEquals("dynamic", result.fetcherUsed());
        }
    }

    // ── Domain memory ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Domain memory")
    class DomainMemory {

        @Test
        @DisplayName("remembers domain that needed dynamic and skips static on second request")
        void remembersDomain() {
            var staticFetcher = new FakeFetcher("static", "tiny"); // too short, triggers fallthrough
            var dynamicFetcher = new FakeFetcher("dynamic",
                    "Dynamic content that is well above the minimum content length threshold for fetcher resolver");

            var resolver = new FetcherResolver(
                    List.of(staticFetcher, dynamicFetcher),
                    List.of("static", "dynamic"), true, List.of());

            // First request: static fails (short), falls through to dynamic
            FetchResult first = resolver.resolve(request("https://spa-app.com/page1"), "auto");
            assertEquals("dynamic", first.fetcherUsed());

            // Second request to same domain: should go directly to dynamic
            FetchResult second = resolver.resolve(request("https://spa-app.com/page2"), "auto");
            assertEquals("dynamic", second.fetcherUsed());
        }
    }

    // ── Fallback chain ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Fallback chain")
    class FallbackChain {

        @Test
        @DisplayName("first successful fetcher in chain wins")
        void firstSuccessWins() {
            var resolver = new FetcherResolver(
                    List.of(new FakeFetcher("static",
                            "Good content from static fetcher that is well above minimum content length. This needs to be at least one hundred characters.")),
                    List.of("static", "dynamic"), true, List.of());

            FetchResult result = resolver.resolve(request("https://example.com"), "auto");

            assertEquals("static", result.fetcherUsed());
        }

        @Test
        @DisplayName("throws when all fetchers fail")
        void throwsWhenAllFail() {
            // No fetchers available
            var resolver = new FetcherResolver(
                    List.of(), List.of("static", "dynamic"), true, List.of());

            assertThrows(RuntimeException.class, () ->
                    resolver.resolve(request("https://example.com"), "auto"));
        }
    }
}
