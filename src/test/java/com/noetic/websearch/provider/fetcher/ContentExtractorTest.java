package com.noetic.websearch.provider.fetcher;

import com.noetic.websearch.model.OutputFormat;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ContentExtractor}.
 */
class ContentExtractorTest {

    // ── Noise removal ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Noise removal")
    class NoiseRemoval {

        @Test
        @DisplayName("strips nav, header, footer, aside elements")
        void stripsStructuralNoise() {
            Document doc = Jsoup.parse("""
                    <html><body>
                        <nav>Navigation menu</nav>
                        <header>Site header</header>
                        <main><p>Main content here</p></main>
                        <aside>Sidebar ads</aside>
                        <footer>Footer links</footer>
                    </body></html>
                    """);

            String content = ContentExtractor.extractMainContent(doc, OutputFormat.TEXT);

            assertTrue(content.contains("Main content here"));
            assertFalse(content.contains("Navigation menu"));
            assertFalse(content.contains("Site header"));
            assertFalse(content.contains("Sidebar ads"));
            assertFalse(content.contains("Footer links"));
        }

        @Test
        @DisplayName("strips ad-related elements by class and data attribute")
        void stripsAds() {
            Document doc = Jsoup.parse("""
                    <html><body>
                        <article>
                            <p>Article content</p>
                            <div class="advertisement">Buy now!</div>
                            <div class="ad">Sponsored</div>
                            <div data-ad-slot="123">Ad slot</div>
                            <div class="adsbygoogle">Google ad</div>
                        </article>
                    </body></html>
                    """);

            String content = ContentExtractor.extractMainContent(doc, OutputFormat.TEXT);

            assertTrue(content.contains("Article content"));
            assertFalse(content.contains("Buy now!"));
            assertFalse(content.contains("Sponsored"));
            assertFalse(content.contains("Ad slot"));
            assertFalse(content.contains("Google ad"));
        }

        @Test
        @DisplayName("strips cookie banners and popups")
        void stripsCookiesAndPopups() {
            Document doc = Jsoup.parse("""
                    <html><body>
                        <div class="cookie-banner">Accept cookies</div>
                        <div class="popup">Subscribe now!</div>
                        <div class="modal">Login required</div>
                        <main><p>Real content</p></main>
                    </body></html>
                    """);

            String content = ContentExtractor.extractMainContent(doc, OutputFormat.TEXT);

            assertTrue(content.contains("Real content"));
            assertFalse(content.contains("Accept cookies"));
            assertFalse(content.contains("Subscribe now!"));
            assertFalse(content.contains("Login required"));
        }

        @Test
        @DisplayName("strips social, newsletter, and comment sections")
        void stripsSocialAndComments() {
            Document doc = Jsoup.parse("""
                    <html><body>
                        <article><p>Article text</p></article>
                        <div class="social-share">Share on Twitter</div>
                        <div class="newsletter-signup">Enter email</div>
                        <div class="comments">User comment 1</div>
                        <div id="comments">User comment 2</div>
                        <div class="related-posts">Related articles</div>
                    </body></html>
                    """);

            String content = ContentExtractor.extractMainContent(doc, OutputFormat.TEXT);

            assertTrue(content.contains("Article text"));
            assertFalse(content.contains("Share on Twitter"));
            assertFalse(content.contains("Enter email"));
            assertFalse(content.contains("User comment 1"));
            assertFalse(content.contains("Related articles"));
        }

        @Test
        @DisplayName("strips script, style, and noscript tags")
        void stripsScriptsAndStyles() {
            Document doc = Jsoup.parse("""
                    <html><body>
                        <script>alert('xss')</script>
                        <style>.hidden{display:none}</style>
                        <noscript>Enable JavaScript</noscript>
                        <main><p>Visible content</p></main>
                    </body></html>
                    """);

            String content = ContentExtractor.extractMainContent(doc, OutputFormat.TEXT);

            assertTrue(content.contains("Visible content"));
            assertFalse(content.contains("alert"));
            assertFalse(content.contains("display:none"));
        }
    }

    // ── Main content detection ───────────────────────────────────────────

    @Nested
    @DisplayName("Main content detection")
    class MainContentDetection {

        @Test
        @DisplayName("prefers <main> element")
        void prefersMainElement() {
            Document doc = Jsoup.parse("""
                    <html><body>
                        <div>Outside content</div>
                        <main><p>Inside main</p></main>
                    </body></html>
                    """);

            String content = ContentExtractor.extractMainContent(doc, OutputFormat.TEXT);
            assertTrue(content.contains("Inside main"));
        }

        @Test
        @DisplayName("prefers <article> element when no <main>")
        void prefersArticle() {
            Document doc = Jsoup.parse("""
                    <html><body>
                        <div>Outside</div>
                        <article><p>Article body</p></article>
                    </body></html>
                    """);

            String content = ContentExtractor.extractMainContent(doc, OutputFormat.TEXT);
            assertTrue(content.contains("Article body"));
        }

        @Test
        @DisplayName("falls back to body when no semantic element found")
        void fallsBackToBody() {
            Document doc = Jsoup.parse("""
                    <html><body>
                        <div><p>Body content only</p></div>
                    </body></html>
                    """);

            String content = ContentExtractor.extractMainContent(doc, OutputFormat.TEXT);
            assertTrue(content.contains("Body content only"));
        }
    }

    // ── Markdown conversion ──────────────────────────────────────────────

    @Nested
    @DisplayName("Markdown conversion")
    class MarkdownConversion {

        @Test
        @DisplayName("converts headings h1-h6")
        void convertsHeadings() {
            Document doc = Jsoup.parse("""
                    <html><body>
                        <main>
                            <h1>Title</h1>
                            <h2>Subtitle</h2>
                            <h3>Section</h3>
                        </main>
                    </body></html>
                    """);

            String md = ContentExtractor.extractMainContent(doc, OutputFormat.MARKDOWN);

            assertTrue(md.contains("# Title"));
            assertTrue(md.contains("## Subtitle"));
            assertTrue(md.contains("### Section"));
        }

        @Test
        @DisplayName("converts paragraphs")
        void convertsParagraphs() {
            Document doc = Jsoup.parse("""
                    <html><body><main>
                        <p>First paragraph</p>
                        <p>Second paragraph</p>
                    </main></body></html>
                    """);

            String md = ContentExtractor.extractMainContent(doc, OutputFormat.MARKDOWN);

            assertTrue(md.contains("First paragraph"));
            assertTrue(md.contains("Second paragraph"));
        }

        @Test
        @DisplayName("converts unordered lists")
        void convertsUnorderedLists() {
            Document doc = Jsoup.parse("""
                    <html><body><main>
                        <ul>
                            <li>Apple</li>
                            <li>Banana</li>
                            <li>Cherry</li>
                        </ul>
                    </main></body></html>
                    """);

            String md = ContentExtractor.extractMainContent(doc, OutputFormat.MARKDOWN);

            assertTrue(md.contains("- Apple"));
            assertTrue(md.contains("- Banana"));
            assertTrue(md.contains("- Cherry"));
        }

        @Test
        @DisplayName("converts ordered lists with numbering")
        void convertsOrderedLists() {
            Document doc = Jsoup.parse("""
                    <html><body><main>
                        <ol>
                            <li>First</li>
                            <li>Second</li>
                            <li>Third</li>
                        </ol>
                    </main></body></html>
                    """);

            String md = ContentExtractor.extractMainContent(doc, OutputFormat.MARKDOWN);

            assertTrue(md.contains("1. First"));
            assertTrue(md.contains("2. Second"));
            assertTrue(md.contains("3. Third"));
        }

        @Test
        @DisplayName("converts code blocks with language detection")
        void convertsCodeBlocks() {
            Document doc = Jsoup.parse("""
                    <html><body><main>
                        <pre><code class="language-java">public void main() {}</code></pre>
                    </main></body></html>
                    """);

            String md = ContentExtractor.extractMainContent(doc, OutputFormat.MARKDOWN);

            assertTrue(md.contains("```java"));
            assertTrue(md.contains("public void main()"));
            assertTrue(md.contains("```"));
        }

        @Test
        @DisplayName("converts blockquotes")
        void convertsBlockquotes() {
            Document doc = Jsoup.parse("""
                    <html><body><main>
                        <blockquote>Wise words here</blockquote>
                    </main></body></html>
                    """);

            String md = ContentExtractor.extractMainContent(doc, OutputFormat.MARKDOWN);

            assertTrue(md.contains("> Wise words here"));
        }

        @Test
        @DisplayName("converts tables to markdown tables")
        void convertsTables() {
            Document doc = Jsoup.parse("""
                    <html><body><main>
                        <table>
                            <thead><tr><th>Name</th><th>Age</th></tr></thead>
                            <tbody><tr><td>Alice</td><td>30</td></tr></tbody>
                        </table>
                    </main></body></html>
                    """);

            String md = ContentExtractor.extractMainContent(doc, OutputFormat.MARKDOWN);

            assertTrue(md.contains("Name"));
            assertTrue(md.contains("Age"));
            assertTrue(md.contains("---"));
            assertTrue(md.contains("Alice"));
            assertTrue(md.contains("30"));
        }

        @Test
        @DisplayName("converts inline links to markdown links")
        void convertsInlineLinks() {
            Document doc = Jsoup.parse("""
                    <html><body><main>
                        <p>Visit <a href="https://example.com">Example</a> for more.</p>
                    </main></body></html>
                    """, "https://base.com");

            String md = ContentExtractor.extractMainContent(doc, OutputFormat.MARKDOWN);

            assertTrue(md.contains("[Example](https://example.com)"));
        }

        @Test
        @DisplayName("converts bold and italic inline elements")
        void convertsBoldAndItalic() {
            Document doc = Jsoup.parse("""
                    <html><body><main>
                        <p>This is <strong>bold</strong> and <em>italic</em> and <code>code</code>.</p>
                    </main></body></html>
                    """);

            String md = ContentExtractor.extractMainContent(doc, OutputFormat.MARKDOWN);

            assertTrue(md.contains("**bold**"));
            assertTrue(md.contains("*italic*"));
            assertTrue(md.contains("`code`"));
        }

        @Test
        @DisplayName("converts horizontal rules")
        void convertsHorizontalRules() {
            Document doc = Jsoup.parse("""
                    <html><body><main>
                        <p>Before</p>
                        <hr>
                        <p>After</p>
                    </main></body></html>
                    """);

            String md = ContentExtractor.extractMainContent(doc, OutputFormat.MARKDOWN);

            assertTrue(md.contains("---"));
        }
    }

    // ── Output formats ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Output formats")
    class OutputFormats {

        @Test
        @DisplayName("HTML format returns raw HTML")
        void htmlFormat() {
            Document doc = Jsoup.parse("""
                    <html><body><main><p>Hello <strong>world</strong></p></main></body></html>
                    """);

            String html = ContentExtractor.extractMainContent(doc, OutputFormat.HTML);

            assertTrue(html.contains("<p>"));
            assertTrue(html.contains("<strong>"));
        }

        @Test
        @DisplayName("TEXT format returns plain text")
        void textFormat() {
            Document doc = Jsoup.parse("""
                    <html><body><main><p>Hello <strong>world</strong></p></main></body></html>
                    """);

            String text = ContentExtractor.extractMainContent(doc, OutputFormat.TEXT);

            assertTrue(text.contains("Hello world"));
            assertFalse(text.contains("<p>"));
            assertFalse(text.contains("<strong>"));
        }
    }

    // ── Link and image extraction ────────────────────────────────────────

    @Nested
    @DisplayName("Link and image extraction")
    class LinkAndImageExtraction {

        @Test
        @DisplayName("extracts deduplicated absolute links")
        void extractsLinks() {
            Document doc = Jsoup.parse("""
                    <html><body>
                        <a href="https://example.com/a">Link A</a>
                        <a href="https://example.com/b">Link B</a>
                        <a href="https://example.com/a">Duplicate A</a>
                    </body></html>
                    """, "https://base.com");

            List<String> links = ContentExtractor.extractLinks(doc);

            // Jsoup absUrl resolves relative to base URL
            assertTrue(links.size() >= 2, "Should extract at least 2 unique links, got: " + links);
            assertTrue(links.stream().anyMatch(l -> l.contains("example.com/a")),
                    "Should contain link to /a");
            assertTrue(links.stream().anyMatch(l -> l.contains("example.com/b")),
                    "Should contain link to /b");
            // Duplicates should be removed
            long uniqueCount = links.stream().filter(l -> l.contains("example.com/a")).count();
            assertEquals(1, uniqueCount, "Duplicate links should be removed");
        }

        @Test
        @DisplayName("extracts deduplicated absolute image URLs")
        void extractsImages() {
            Document doc = Jsoup.parse("""
                    <html><body>
                        <img src="https://example.com/1.png">
                        <img src="https://example.com/2.jpg">
                        <img src="https://example.com/1.png">
                    </body></html>
                    """, "https://base.com");

            List<String> images = ContentExtractor.extractImages(doc);

            assertTrue(images.size() >= 2, "Should extract at least 2 unique images, got: " + images);
            assertTrue(images.stream().anyMatch(i -> i.contains("1.png")),
                    "Should contain image 1.png");
            assertTrue(images.stream().anyMatch(i -> i.contains("2.jpg")),
                    "Should contain image 2.jpg");
            long uniqueCount = images.stream().filter(i -> i.contains("1.png")).count();
            assertEquals(1, uniqueCount, "Duplicate images should be removed");
        }
    }

    // ── Word counting ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Word counting")
    class WordCounting {

        @Test
        @DisplayName("counts words correctly")
        void countsWords() {
            assertEquals(3, ContentExtractor.countWords("hello world today"));
            assertEquals(1, ContentExtractor.countWords("single"));
        }

        @Test
        @DisplayName("returns 0 for null or blank content")
        void returnsZeroForEmpty() {
            assertEquals(0, ContentExtractor.countWords(null));
            assertEquals(0, ContentExtractor.countWords(""));
            assertEquals(0, ContentExtractor.countWords("   "));
        }
    }
}
