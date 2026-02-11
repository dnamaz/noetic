package com.noetic.websearch.provider.fetcher;

import com.noetic.websearch.model.*;
import com.noetic.websearch.provider.CaptchaSolver;
import com.noetic.websearch.provider.CaptchaSolver.CaptchaDetection;
import com.noetic.websearch.provider.CaptchaSolver.CaptchaSolveResult;
import com.noetic.websearch.provider.ContentFetcher;
import com.ruiyun.jvppeteer.core.Browser;
import com.ruiyun.jvppeteer.core.Page;
import com.ruiyun.jvppeteer.entities.GoToOptions;
import com.ruiyun.jvppeteer.entities.ScreenshotOptions;
import com.ruiyun.jvppeteer.entities.ImageType;
import com.ruiyun.jvppeteer.entities.Viewport;
import com.ruiyun.jvppeteer.entities.WaitForSelectorOptions;
import com.ruiyun.jvppeteer.entities.PuppeteerLifeCycle;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Fetches web pages using Jvppeteer (headless Chromium via CDP).
 * Supports JavaScript rendering, wait-for-selector, page actions, screenshots,
 * mobile emulation, proxy routing, and stealth mode.
 *
 * <p>When Chromium is available, renders pages fully before extracting content
 * via {@link ContentExtractor}. Falls back gracefully to Jsoup-based static
 * fetching if Chromium is not found.</p>
 *
 * <p>Manages a {@link BrowserPool} of reusable headless browser instances.</p>
 */
@Component
public class DynamicContentFetcher implements ContentFetcher {

    private static final Logger log = LoggerFactory.getLogger(DynamicContentFetcher.class);

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/128.0.6613.137 Safari/537.36";
    private static final String MOBILE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

    // Stealth launch arguments to reduce bot detection
    private static final List<String> STEALTH_ARGS = List.of(
            "--disable-blink-features=AutomationControlled",
            "--disable-infobars",
            "--disable-background-timer-throttling",
            "--disable-backgrounding-occluded-windows",
            "--disable-renderer-backgrounding",
            "--disable-features=TranslateUI",
            "--disable-ipc-flooding-protection",
            "--window-size=1920,1080"
    );

    // JavaScript to inject for stealth after page creation
    private static final String STEALTH_SCRIPT = """
            // Remove webdriver indicator
            Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
            // Spoof plugins (headless Chrome has empty plugins array)
            Object.defineProperty(navigator, 'plugins', {
                get: () => [1, 2, 3, 4, 5]
            });
            // Spoof languages
            Object.defineProperty(navigator, 'languages', {
                get: () => ['en-US', 'en']
            });
            // Add chrome object that real Chrome has
            if (!window.chrome) {
                window.chrome = { runtime: {} };
            }
            """;

    private final boolean enabled;
    private final String chromiumPath;
    private final int timeoutMs;
    private final int poolSize;
    private final boolean proxyEnabled;
    private final String proxyType;
    private final String proxyHost;
    private final int proxyPort;

    /** Optional CAPTCHA solver -- null if no solver is configured. */
    private final CaptchaSolver captchaSolver;

    /** Resolved path to the Chromium binary, or empty if not available. */
    private String resolvedChromiumPath;
    /** Whether Chromium is available and the dynamic fetcher can render JS. */
    private boolean chromiumAvailable;
    /** Pool of reusable browser instances. Null if Chromium is not available. */
    private BrowserPool browserPool;

    public DynamicContentFetcher(
            @Value("${websearch.fetcher.dynamic.enabled:true}") boolean enabled,
            @Value("${websearch.fetcher.dynamic.chromium-path:auto}") String chromiumPath,
            @Value("${websearch.fetcher.dynamic.timeout-ms:10000}") int timeoutMs,
            @Value("${websearch.fetcher.dynamic.pool-size:2}") int poolSize,
            @Value("${websearch.proxy.enabled:false}") boolean proxyEnabled,
            @Value("${websearch.proxy.type:NONE}") String proxyType,
            @Value("${websearch.proxy.host:127.0.0.1}") String proxyHost,
            @Value("${websearch.proxy.port:9050}") int proxyPort,
            @Autowired(required = false) CaptchaSolver captchaSolver
    ) {
        this.enabled = enabled;
        this.chromiumPath = chromiumPath;
        this.timeoutMs = timeoutMs;
        this.poolSize = poolSize;
        this.proxyEnabled = proxyEnabled;
        this.proxyType = proxyType;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.captchaSolver = captchaSolver;

        if (captchaSolver != null) {
            log.info("CAPTCHA solver configured: {}", captchaSolver.type());
        }
    }

    @PostConstruct
    public void initialize() {
        if (!enabled) {
            log.info("DynamicContentFetcher disabled by configuration");
            chromiumAvailable = false;
            return;
        }

        // Detect Chromium binary
        Optional<String> detected = ChromiumDetector.resolve(chromiumPath);
        if (detected.isEmpty()) {
            log.warn("DynamicContentFetcher enabled but no Chromium found. "
                    + "Will fall back to Jsoup for dynamic requests.");
            chromiumAvailable = false;
            return;
        }

        resolvedChromiumPath = detected.get();
        chromiumAvailable = true;

        // Build launch args including proxy if configured
        List<String> extraArgs = new ArrayList<>(STEALTH_ARGS);
        if (proxyEnabled && proxyHost != null && proxyPort > 0) {
            String proxyArg = "--proxy-server=" + proxyHost + ":" + proxyPort;
            extraArgs.add(proxyArg);
            log.info("DynamicContentFetcher proxy: {}", proxyArg);
        }

        browserPool = new BrowserPool(resolvedChromiumPath, poolSize, extraArgs);
        log.info("DynamicContentFetcher ready: chromium={}, poolSize={}", resolvedChromiumPath, poolSize);
    }

    @PreDestroy
    public void shutdown() {
        if (browserPool != null) {
            browserPool.close();
        }
    }

    @Override
    public String type() {
        return "dynamic";
    }

    @Override
    public FetcherCapabilities capabilities() {
        return new FetcherCapabilities(
                true,   // supportsJavaScript
                true,   // supportsWaitForSelector
                true,   // supportsMarkdownOutput
                false,  // supportsBotBypass
                proxyEnabled,  // supportsProxy
                chromiumAvailable,  // supportsScreenshot (only with Chromium)
                false,  // requiresApiKey
                true    // requiresLocalBinary
        );
    }

    @Override
    public boolean supports(FetchRequest request) {
        return enabled;
    }

    @Override
    public FetchResult fetch(FetchRequest request) {
        if (!enabled) {
            throw new IllegalStateException("Dynamic fetcher is disabled");
        }

        if (chromiumAvailable) {
            return fetchWithChromium(request);
        } else {
            return fetchWithJsoupFallback(request);
        }
    }

    // ── Chromium-based fetch (real JavaScript rendering) ──────────────────

    private FetchResult fetchWithChromium(FetchRequest request) {
        Instant start = Instant.now();
        Browser browser = null;

        try {
            browser = browserPool.acquire(timeoutMs);
            Page page = browser.newPage();

            try {
                // Set user-agent
                String userAgent = request.mobile() ? MOBILE_UA : DESKTOP_UA;
                page.setUserAgent(userAgent);

                // Mobile emulation viewport
                if (request.mobile()) {
                    Viewport viewport = new Viewport();
                    viewport.setWidth(375);
                    viewport.setHeight(812);
                    viewport.setDeviceScaleFactor(3.0);
                    viewport.setIsMobile(true);
                    viewport.setHasTouch(true);
                    page.setViewport(viewport);
                }

                // Custom headers
                if (!request.headers().isEmpty()) {
                    page.setExtraHTTPHeaders(request.headers());
                }

                // Inject stealth script before navigation
                page.evaluateOnNewDocument(STEALTH_SCRIPT);

                // Navigate with wait conditions
                GoToOptions goToOptions = new GoToOptions();
                goToOptions.setTimeout(timeoutMs);
                if (request.waitForNetworkIdle()) {
                    goToOptions.setWaitUntil(List.of(PuppeteerLifeCycle.NETWORKIDLE));
                } else {
                    goToOptions.setWaitUntil(List.of(PuppeteerLifeCycle.DOMCONTENT_LOADED));
                }

                page.goTo(request.url(), goToOptions);

                // Wait for specific selector if requested
                if (request.waitForSelector() != null && !request.waitForSelector().isBlank()) {
                    WaitForSelectorOptions selectorOptions = new WaitForSelectorOptions();
                    selectorOptions.setTimeout(timeoutMs);
                    selectorOptions.setVisible(true);
                    page.waitForSelector(request.waitForSelector(), selectorOptions);
                }

                // Detect and solve CAPTCHAs if solver is configured
                if (captchaSolver != null) {
                    CaptchaDetection detection = captchaSolver.detect(page);
                    if (detection.detected()) {
                        log.info("CAPTCHA detected on {}: {}", request.url(), detection.captchaType());
                        CaptchaSolveResult result = captchaSolver.solve(page, detection);
                        if (result.solved()) {
                            log.info("CAPTCHA solved in {}ms, waiting for page reload", result.solveTimeMs());
                            // Wait for the page to process the CAPTCHA solution
                            Thread.sleep(2000);
                            // Re-wait for network idle after CAPTCHA solve
                            GoToOptions reloadOptions = new GoToOptions();
                            reloadOptions.setTimeout(timeoutMs);
                            reloadOptions.setWaitUntil(List.of(PuppeteerLifeCycle.NETWORKIDLE));
                            try {
                                page.goTo(request.url(), reloadOptions);
                            } catch (Exception e) {
                                log.debug("Post-CAPTCHA navigation: {}", e.getMessage());
                            }
                        } else {
                            log.warn("CAPTCHA solve failed: {}", result.errorMessage());
                        }
                    }
                }

                // Execute page actions
                executeActions(page, request.actions());

                // Capture screenshot if requested
                String screenshotBase64 = null;
                if (request.captureScreenshot()) {
                    ScreenshotOptions screenshotOpts = new ScreenshotOptions();
                    screenshotOpts.setType(ImageType.PNG);
                    screenshotOpts.setFullPage(false);
                    screenshotBase64 = page.screenshot(screenshotOpts);
                }

                // Extract the fully rendered DOM
                String renderedHtml = page.content();

                // Parse with Jsoup + ContentExtractor
                Document doc = Jsoup.parse(renderedHtml, request.url());
                String title = doc.title();
                String content = ContentExtractor.extractMainContent(doc, request.outputFormat());
                List<String> links = request.includeLinks()
                        ? ContentExtractor.extractLinks(doc) : List.of();
                List<String> images = request.includeImages()
                        ? ContentExtractor.extractImages(doc) : List.of();

                Duration fetchTime = Duration.between(start, Instant.now());

                return FetchResult.builder()
                        .url(request.url())
                        .title(title)
                        .content(content)
                        .rawHtml(renderedHtml)
                        .links(links)
                        .images(images)
                        .wordCount(ContentExtractor.countWords(content))
                        .statusCode(200)
                        .fetcherUsed("dynamic")
                        .fetchTime(fetchTime)
                        .screenshot(screenshotBase64)
                        .providerMeta(Map.of("renderer", "chromium", "chromiumPath", resolvedChromiumPath))
                        .build();

            } finally {
                // Close the page but keep the browser for reuse
                try {
                    page.close();
                } catch (Exception e) {
                    log.debug("Error closing page: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Chromium fetch failed for {}: {}. Falling back to Jsoup.", request.url(), e.getMessage());
            // Return browser to pool even on error
            if (browser != null) {
                browserPool.release(browser);
                browser = null; // prevent double-release below
            }
            return fetchWithJsoupFallback(request);
        } finally {
            if (browser != null) {
                browserPool.release(browser);
            }
        }
    }

    /**
     * Executes page actions (click, type, scroll, wait) on the rendered page.
     */
    private void executeActions(Page page, List<PageAction> actions) {
        for (PageAction action : actions) {
            try {
                switch (action.type()) {
                    case CLICK -> {
                        if (action.selector() != null) {
                            page.click(action.selector());
                            if (action.delayMs() > 0) {
                                Thread.sleep(action.delayMs());
                            }
                        }
                    }
                    case TYPE -> {
                        if (action.selector() != null && action.value() != null) {
                            page.type(action.selector(), action.value());
                        }
                    }
                    case SCROLL -> {
                        int pixels = action.value() != null ? Integer.parseInt(action.value()) : 500;
                        page.evaluate("window.scrollBy(0, " + pixels + ")");
                        Thread.sleep(Math.max(action.delayMs(), 200)); // allow content to load
                    }
                    case WAIT -> {
                        int ms = action.value() != null ? Integer.parseInt(action.value()) : 1000;
                        Thread.sleep(ms);
                    }
                    case WAIT_FOR_SELECTOR -> {
                        if (action.selector() != null) {
                            WaitForSelectorOptions opts = new WaitForSelectorOptions();
                            opts.setTimeout(timeoutMs);
                            opts.setVisible(true);
                            page.waitForSelector(action.selector(), opts);
                        }
                    }
                }
                log.debug("Executed action: {} on {}", action.type(), action.selector());
            } catch (Exception e) {
                log.warn("Page action {} failed: {}", action.type(), e.getMessage());
            }
        }
    }

    // ── Jsoup fallback (no JavaScript rendering) ─────────────────────────

    private FetchResult fetchWithJsoupFallback(FetchRequest request) {
        Instant start = Instant.now();

        log.info("DynamicContentFetcher: using Jsoup fallback for: {}", request.url());

        try {
            String userAgent = request.mobile() ? MOBILE_UA : DESKTOP_UA;

            org.jsoup.Connection conn = org.jsoup.Jsoup.connect(request.url())
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .maxBodySize(10 * 1024 * 1024);

            if (!request.headers().isEmpty()) {
                conn.headers(request.headers());
            }
            if (!request.cookies().isEmpty()) {
                conn.cookies(request.cookies());
            }

            org.jsoup.Connection.Response response = conn.execute();
            String contentType = response.contentType();

            // PDF detection
            if (contentType != null && contentType.contains("application/pdf")) {
                return extractPdf(response.bodyAsBytes(), request.url(), start);
            }

            Document doc = response.parse();
            String content = ContentExtractor.extractMainContent(doc, request.outputFormat());
            List<String> links = request.includeLinks()
                    ? ContentExtractor.extractLinks(doc) : List.of();
            List<String> images = request.includeImages()
                    ? ContentExtractor.extractImages(doc) : List.of();

            Duration fetchTime = Duration.between(start, Instant.now());

            return FetchResult.builder()
                    .url(doc.location())
                    .title(doc.title())
                    .content(content)
                    .rawHtml(doc.html())
                    .links(links)
                    .images(images)
                    .wordCount(ContentExtractor.countWords(content))
                    .statusCode(response.statusCode())
                    .fetcherUsed("dynamic")
                    .fetchTime(fetchTime)
                    .providerMeta(Map.of("renderer", "jsoup-fallback",
                            "reason", chromiumAvailable ? "chromium-error" : "chromium-not-found"))
                    .build();
        } catch (Exception e) {
            log.error("Jsoup fallback also failed for {}: {}", request.url(), e.getMessage());
            Duration fetchTime = Duration.between(start, Instant.now());
            return FetchResult.builder()
                    .url(request.url())
                    .content("")
                    .statusCode(0)
                    .fetcherUsed("dynamic")
                    .fetchTime(fetchTime)
                    .providerMeta(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    // ── PDF extraction ───────────────────────────────────────────────────

    private FetchResult extractPdf(byte[] pdfBytes, String url, Instant start) {
        try {
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
                    .fetcherUsed("dynamic")
                    .fetchTime(fetchTime)
                    .providerMeta(Map.of("contentType", "application/pdf", "pages", pages))
                    .build();
        } catch (Exception e) {
            log.error("PDF extraction failed for {}: {}", url, e.getMessage());
            return FetchResult.builder()
                    .url(url)
                    .content("")
                    .statusCode(200)
                    .fetcherUsed("dynamic")
                    .fetchTime(Duration.between(start, Instant.now()))
                    .providerMeta(Map.of("error", "PDF extraction failed: " + e.getMessage()))
                    .build();
        }
    }
}
