package com.noetic.websearch.provider.fetcher;

import com.noetic.websearch.model.*;
import com.noetic.websearch.provider.ContentFetcher;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Fetches web pages using Jsoup (static HTTP, no JavaScript rendering).
 * Supports proxy (HTTP/SOCKS4/SOCKS5), custom headers/cookies,
 * mobile user-agent, TLS skip, PDF detection, and enhanced ad blocking.
 */
@Component
public class StaticContentFetcher implements ContentFetcher {

    private static final Logger log = LoggerFactory.getLogger(StaticContentFetcher.class);

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final String MOBILE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

    private final ProxyConfig proxyConfig;

    public StaticContentFetcher(
            @Value("${websearch.proxy.enabled:false}") boolean proxyEnabled,
            @Value("${websearch.proxy.type:NONE}") String proxyType,
            @Value("${websearch.proxy.host:127.0.0.1}") String proxyHost,
            @Value("${websearch.proxy.port:9050}") int proxyPort,
            @Value("${websearch.proxy.username:}") String proxyUser,
            @Value("${websearch.proxy.password:}") String proxyPass,
            @Value("${websearch.proxy.use-onion-services:false}") boolean useOnion
    ) {
        this.proxyConfig = new ProxyConfig(proxyEnabled,
                proxyEnabled ? ProxyType.valueOf(proxyType) : ProxyType.NONE,
                proxyHost, proxyPort,
                proxyUser.isBlank() ? null : proxyUser,
                proxyPass.isBlank() ? null : proxyPass,
                useOnion);

        if (proxyEnabled) {
            log.info("StaticContentFetcher proxy enabled: {}://{}:{}", proxyType, proxyHost, proxyPort);
        }
    }

    @Override
    public String type() {
        return "static";
    }

    @Override
    public FetcherCapabilities capabilities() {
        return new FetcherCapabilities(
                false,  // supportsJavaScript
                false,  // supportsWaitForSelector
                true,   // supportsMarkdownOutput
                false,  // supportsBotBypass
                proxyConfig.enabled(),  // supportsProxy
                false,  // supportsScreenshot
                false,  // requiresApiKey
                false   // requiresLocalBinary
        );
    }

    @Override
    public boolean supports(FetchRequest request) {
        return true;
    }

    @Override
    public FetchResult fetch(FetchRequest request) {
        Instant start = Instant.now();
        try {
            String userAgent = request.mobile() ? MOBILE_UA : DESKTOP_UA;

            Connection connection = Jsoup.connect(request.url())
                    .userAgent(userAgent)
                    .timeout((int) request.timeout().toMillis())
                    .followRedirects(true);

            // Proxy support
            if (proxyConfig.enabled()) {
                connection.proxy(proxyConfig.toJavaProxy());
            }

            // Custom headers
            if (!request.headers().isEmpty()) {
                connection.headers(request.headers());
            }

            // Custom cookies
            if (!request.cookies().isEmpty()) {
                connection.cookies(request.cookies());
            }

            // Skip TLS verification
            if (request.skipTlsVerification()) {
                connection.sslSocketFactory(createInsecureSslFactory());
            }

            // Execute request -- ignore content type validation for binary (PDF) support
            Connection.Response response = connection
                    .ignoreContentType(true)
                    .maxBodySize(10 * 1024 * 1024) // 10MB max
                    .execute();
            String contentType = response.contentType();

            // PDF detection
            if (contentType != null && contentType.contains("application/pdf")) {
                return extractPdf(response.bodyAsBytes(), request.url(), start);
            }

            Document doc = response.parse();
            String title = doc.title();
            String content = ContentExtractor.extractMainContent(doc, request.outputFormat());

            List<String> links = request.includeLinks()
                    ? ContentExtractor.extractLinks(doc) : List.of();
            List<String> images = request.includeImages()
                    ? ContentExtractor.extractImages(doc) : List.of();

            int wordCount = ContentExtractor.countWords(content);
            Duration fetchTime = Duration.between(start, Instant.now());

            return FetchResult.builder()
                    .url(doc.location())
                    .title(title)
                    .content(content)
                    .rawHtml(doc.html())
                    .links(links)
                    .images(images)
                    .wordCount(wordCount)
                    .statusCode(response.statusCode())
                    .fetcherUsed("static")
                    .fetchTime(fetchTime)
                    .providerMeta(Map.of())
                    .build();

        } catch (Exception e) {
            log.error("Static fetch failed for {}: {}", request.url(), e.getMessage());
            Duration fetchTime = Duration.between(start, Instant.now());
            return FetchResult.builder()
                    .url(request.url())
                    .content("")
                    .statusCode(0)
                    .fetcherUsed("static")
                    .fetchTime(fetchTime)
                    .build();
        }
    }

    private FetchResult extractPdf(byte[] pdfBytes, String url, Instant start) {
        try {
            // Use PDFBox for text extraction
            var doc = Loader.loadPDF(pdfBytes);
            var stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            int pages = doc.getNumberOfPages();
            doc.close();

            Duration fetchTime = Duration.between(start, Instant.now());
            log.info("Extracted {} pages from PDF: {}", pages, url);

            return FetchResult.builder()
                    .url(url)
                    .title("PDF Document")
                    .content(text)
                    .wordCount(ContentExtractor.countWords(text))
                    .statusCode(200)
                    .fetcherUsed("static")
                    .fetchTime(fetchTime)
                    .providerMeta(Map.of("contentType", "application/pdf", "pages", pages))
                    .build();
        } catch (Exception e) {
            log.error("PDF extraction failed for {}: {}", url, e.getMessage());
            return FetchResult.builder()
                    .url(url)
                    .content("")
                    .statusCode(200)
                    .fetcherUsed("static")
                    .fetchTime(Duration.between(start, Instant.now()))
                    .providerMeta(Map.of("error", "PDF extraction failed: " + e.getMessage()))
                    .build();
        }
    }

    private SSLSocketFactory createInsecureSslFactory() {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new java.security.SecureRandom());
            return sc.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create insecure SSL factory", e);
        }
    }
}
