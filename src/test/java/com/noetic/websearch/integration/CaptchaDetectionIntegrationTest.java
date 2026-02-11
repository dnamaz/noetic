package com.noetic.websearch.integration;

import com.noetic.websearch.provider.CaptchaSolver.CaptchaDetection;
import com.noetic.websearch.provider.CaptchaSolver.CaptchaType;
import com.noetic.websearch.provider.captcha.CaptchaDetector;
import com.noetic.websearch.provider.fetcher.BrowserPool;
import com.noetic.websearch.provider.fetcher.ChromiumDetector;
import com.ruiyun.jvppeteer.core.Browser;
import com.ruiyun.jvppeteer.core.Page;
import com.ruiyun.jvppeteer.entities.GoToOptions;
import com.ruiyun.jvppeteer.entities.PuppeteerLifeCycle;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for CAPTCHA detection against real demo pages.
 *
 * <p>Requires Chromium to be installed. Tests are skipped automatically if not available.</p>
 *
 * <p>Test targets:</p>
 * <ul>
 *   <li><a href="https://www.google.com/recaptcha/api2/demo">Google reCAPTCHA v2 demo</a></li>
 *   <li><a href="https://accounts.hcaptcha.com/demo">hCaptcha demo</a></li>
 *   <li>A page with no CAPTCHA (example.com) as negative control</li>
 * </ul>
 */
@Tag("integration")
@DisplayName("CAPTCHA detection integration tests")
class CaptchaDetectionIntegrationTest {

    private static BrowserPool pool;
    private static boolean chromiumAvailable;
    private static String chromiumPath;

    @BeforeAll
    static void setup() {
        Optional<String> detected = ChromiumDetector.resolve("auto");
        chromiumAvailable = detected.isPresent();

        if (chromiumAvailable) {
            chromiumPath = detected.get();
            pool = new BrowserPool(chromiumPath, 1, List.of(
                    "--no-sandbox",
                    "--disable-blink-features=AutomationControlled",
                    "--disable-infobars"
            ));
        }
    }

    @AfterAll
    static void teardown() {
        if (pool != null) {
            pool.close();
        }
    }

    private Page navigateTo(Browser browser, String url) throws Exception {
        Page page = browser.newPage();
        GoToOptions options = new GoToOptions();
        options.setTimeout(20000);
        options.setWaitUntil(List.of(PuppeteerLifeCycle.NETWORKIDLE));
        page.goTo(url, options);
        return page;
    }

    // ── reCAPTCHA v2 detection ───────────────────────────────────────────

    @Test
    @DisplayName("detects reCAPTCHA v2 on Google's official demo page")
    void detectsRecaptchaV2OnGoogleDemo() throws Exception {
        assumeTrue(chromiumAvailable, "Chromium not available, skipping");

        Browser browser = pool.acquire(20000);
        try {
            Page page = navigateTo(browser, "https://www.google.com/recaptcha/api2/demo");

            // Wait a moment for iframes and scripts to load
            Thread.sleep(3000);

            // Debug: dump what the page looks like
            String debugScript = "() => { const rc = document.querySelector('.g-recaptcha, [data-sitekey]'); const iframe = document.querySelector('iframe[src*=\"recaptcha\"]'); return JSON.stringify({ hasDiv: !!rc, sitekey: rc ? rc.getAttribute('data-sitekey') : null, hasIframe: !!iframe }); }";
            Object debugHtml = page.evaluate(debugScript);
            System.out.println("reCAPTCHA debug: " + debugHtml);

            CaptchaDetection detection = CaptchaDetector.detect(page,
                    "https://www.google.com/recaptcha/api2/demo");

            System.out.println("Detection result: detected=" + detection.detected()
                    + ", type=" + detection.captchaType() + ", sitekey=" + detection.sitekey());

            assertTrue(detection.detected(),
                    "Should detect reCAPTCHA on Google's demo page. Debug: " + debugHtml);
            assertEquals(CaptchaType.RECAPTCHA_V2, detection.captchaType(),
                    "Should identify as reCAPTCHA v2");
            assertNotNull(detection.sitekey(),
                    "Should extract the sitekey");
            assertFalse(detection.sitekey().isBlank(),
                    "Sitekey should not be blank");

            page.close();
        } finally {
            pool.release(browser);
        }
    }

    // ── hCaptcha detection ───────────────────────────────────────────────

    @Test
    @DisplayName("detects hCaptcha on hCaptcha's official demo page")
    void detectsHCaptchaOnDemo() throws Exception {
        assumeTrue(chromiumAvailable, "Chromium not available, skipping");

        Browser browser = pool.acquire(20000);
        try {
            Page page = navigateTo(browser, "https://accounts.hcaptcha.com/demo");

            // Wait for hCaptcha widget to load
            Thread.sleep(3000);

            // Debug: check DOM state
            String debugScript = "() => { const hc = document.querySelector('.h-captcha, [data-hcaptcha-sitekey], [data-sitekey]'); const iframe = document.querySelector('iframe[src*=\"hcaptcha\"]'); return JSON.stringify({ hasDiv: !!hc, sitekey: hc ? (hc.getAttribute('data-sitekey') || hc.getAttribute('data-hcaptcha-sitekey')) : null, hasIframe: !!iframe }); }";
            Object debugHtml = page.evaluate(debugScript);
            System.out.println("hCaptcha debug: " + debugHtml);

            CaptchaDetection detection = CaptchaDetector.detect(page,
                    "https://accounts.hcaptcha.com/demo");

            System.out.println("Detection result: detected=" + detection.detected()
                    + ", type=" + detection.captchaType() + ", sitekey=" + detection.sitekey());

            assertTrue(detection.detected(),
                    "Should detect hCaptcha on hCaptcha's demo page. Debug: " + debugHtml);
            assertEquals(CaptchaType.HCAPTCHA, detection.captchaType(),
                    "Should identify as hCaptcha");
            assertNotNull(detection.sitekey(),
                    "Should extract the sitekey");
            assertFalse(detection.sitekey().isBlank(),
                    "Sitekey should not be blank");

            page.close();
        } finally {
            pool.release(browser);
        }
    }

    // ── Negative test (no CAPTCHA) ───────────────────────────────────────

    @Test
    @DisplayName("does NOT detect CAPTCHA on example.com (negative control)")
    void noCaptchaOnExampleCom() throws Exception {
        assumeTrue(chromiumAvailable, "Chromium not available, skipping");

        Browser browser = pool.acquire(20000);
        try {
            Page page = navigateTo(browser, "https://example.com");

            CaptchaDetection detection = CaptchaDetector.detect(page, "https://example.com");

            assertFalse(detection.detected(),
                    "Should NOT detect CAPTCHA on example.com");
            assertEquals(CaptchaType.NONE, detection.captchaType());

            page.close();
        } finally {
            pool.release(browser);
        }
    }

    // ── Browser pool health check ────────────────────────────────────────

    @Test
    @DisplayName("browser pool acquires and releases correctly")
    void browserPoolWorks() {
        assumeTrue(chromiumAvailable, "Chromium not available, skipping");

        Browser browser = pool.acquire(15000);
        assertNotNull(browser, "Should acquire a browser");
        assertTrue(browser.connected(), "Browser should be connected");

        pool.release(browser);
        // No exception means success
    }
}
