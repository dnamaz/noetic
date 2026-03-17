package com.noetic.websearch.provider.fetcher;

import com.noetic.websearch.model.*;
import com.noetic.websearch.provider.ContentFetcher;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Fetches content from local file system paths.
 *
 * <p>Accepts absolute paths ({@code /path/to/file}), home-relative paths
 * ({@code ~/file}), relative paths ({@code ./file}), and {@code file://} URIs.
 * Detects PDFs by magic bytes and {@code .pdf} extension; parses HTML files
 * via Jsoup; treats everything else as plain text.</p>
 */
@Component
public class LocalFileFetcher implements ContentFetcher {

    private static final Logger log = LoggerFactory.getLogger(LocalFileFetcher.class);

    @Override
    public String type() {
        return "local";
    }

    @Override
    public FetcherCapabilities capabilities() {
        return new FetcherCapabilities(
                false,  // supportsJavaScript
                false,  // supportsWaitForSelector
                true,   // supportsMarkdownOutput
                false,  // supportsBotBypass
                false,  // supportsProxy
                false,  // supportsScreenshot
                false,  // requiresApiKey
                false   // requiresLocalBinary
        );
    }

    @Override
    public boolean supports(FetchRequest request) {
        return isLocalPath(request.url());
    }

    @Override
    public FetchResult fetch(FetchRequest request) {
        Instant start = Instant.now();
        String url = request.url();

        try {
            Path path = toPath(url);
            if (!Files.exists(path)) {
                throw new IOException("File not found: " + path);
            }

            byte[] bytes = Files.readAllBytes(path);
            String fileName = path.getFileName().toString().toLowerCase();

            if (fileName.endsWith(".pdf") || isPdfMagicBytes(bytes)) {
                return extractPdf(bytes, url, start);
            }

            String content;
            String title = path.getFileName().toString();

            if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
                Document doc = Jsoup.parse(new String(bytes, StandardCharsets.UTF_8));
                if (!doc.title().isBlank()) title = doc.title();
                List<String> links = request.includeLinks()
                        ? ContentExtractor.extractLinks(doc) : List.of();
                List<String> images = request.includeImages()
                        ? ContentExtractor.extractImages(doc) : List.of();
                content = ContentExtractor.extractMainContent(doc, request.outputFormat());

                return FetchResult.builder()
                        .url(url)
                        .title(title)
                        .content(content)
                        .rawHtml(doc.html())
                        .links(links)
                        .images(images)
                        .wordCount(ContentExtractor.countWords(content))
                        .statusCode(200)
                        .fetcherUsed("local")
                        .fetchTime(Duration.between(start, Instant.now()))
                        .providerMeta(Map.of())
                        .build();
            }

            // Plain text / unknown
            content = new String(bytes, StandardCharsets.UTF_8);

            return FetchResult.builder()
                    .url(url)
                    .title(title)
                    .content(content)
                    .wordCount(ContentExtractor.countWords(content))
                    .statusCode(200)
                    .fetcherUsed("local")
                    .fetchTime(Duration.between(start, Instant.now()))
                    .providerMeta(Map.of())
                    .build();

        } catch (Exception e) {
            log.error("Local file fetch failed for {}: {}", url, e.getMessage());
            return FetchResult.builder()
                    .url(url)
                    .content("")
                    .statusCode(0)
                    .fetcherUsed("local")
                    .fetchTime(Duration.between(start, Instant.now()))
                    .providerMeta(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Returns true if the given URL string is a local file path rather than
     * a network URL.
     */
    public static boolean isLocalPath(String url) {
        if (url == null) return false;
        return url.startsWith("file://")
                || url.startsWith("/")
                || url.startsWith("~/")
                || url.startsWith("./")
                || url.startsWith(".\\");
    }

    private static Path toPath(String url) {
        if (url.startsWith("file://")) {
            return Path.of(URI.create(url));
        }
        if (url.startsWith("~/")) {
            return Path.of(System.getProperty("user.home")).resolve(url.substring(2));
        }
        return Path.of(url);
    }

    private static boolean isPdfMagicBytes(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == '%' && bytes[1] == 'P'
                && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private FetchResult extractPdf(byte[] pdfBytes, String url, Instant start) {
        try {
            var doc = Loader.loadPDF(pdfBytes);
            var stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            int pages = doc.getNumberOfPages();
            doc.close();

            log.info("Extracted {} pages from local PDF: {}", pages, url);
            return FetchResult.builder()
                    .url(url)
                    .title("PDF Document")
                    .content(text)
                    .wordCount(ContentExtractor.countWords(text))
                    .statusCode(200)
                    .fetcherUsed("local")
                    .fetchTime(Duration.between(start, Instant.now()))
                    .providerMeta(Map.of("contentType", "application/pdf", "pages", pages))
                    .build();

        } catch (Exception e) {
            log.error("PDF extraction failed for {}: {}", url, e.getMessage());
            return FetchResult.builder()
                    .url(url)
                    .content("")
                    .statusCode(200)
                    .fetcherUsed("local")
                    .fetchTime(Duration.between(start, Instant.now()))
                    .providerMeta(Map.of("error", "PDF extraction failed: " + e.getMessage()))
                    .build();
        }
    }
}
