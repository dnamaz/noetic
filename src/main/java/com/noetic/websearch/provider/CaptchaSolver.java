package com.noetic.websearch.provider;

import com.ruiyun.jvppeteer.core.Page;

/**
 * Provider interface for solving CAPTCHAs encountered during dynamic page fetching.
 *
 * <p>When the {@link com.noetic.websearch.provider.fetcher.DynamicContentFetcher}
 * detects a CAPTCHA on a rendered page, it delegates to the active {@code CaptchaSolver}
 * to resolve the challenge. After solving, the fetcher retries content extraction.</p>
 *
 * <p>Implementations typically interact with external CAPTCHA-solving services
 * (2Captcha, CapSolver, etc.) and inject the solution token into the page via CDP.</p>
 *
 * <p>The default configuration ({@code active: none}) disables CAPTCHA solving entirely.
 * No external API calls are made unless a solver is explicitly configured with an API key.</p>
 */
public interface CaptchaSolver {

    /** Provider type identifier (e.g. "2captcha", "capsolver"). */
    String type();

    /**
     * Checks whether the given page contains a CAPTCHA challenge.
     *
     * <p>Examines the rendered DOM for known CAPTCHA indicators:
     * reCAPTCHA iframes, hCaptcha divs, Cloudflare Turnstile widgets, etc.</p>
     *
     * @param page the Jvppeteer page after navigation
     * @return detection result with CAPTCHA type and sitekey if found
     */
    CaptchaDetection detect(Page page);

    /**
     * Attempts to solve a detected CAPTCHA and inject the solution into the page.
     *
     * @param page      the Jvppeteer page containing the CAPTCHA
     * @param detection the detection result from {@link #detect(Page)}
     * @return result indicating success/failure and any solution token
     * @throws CaptchaSolveException if the solving service fails or times out
     */
    CaptchaSolveResult solve(Page page, CaptchaDetection detection);

    // ── Nested types ─────────────────────────────────────────────────────

    /** Type of CAPTCHA detected on a page. */
    enum CaptchaType {
        NONE,
        RECAPTCHA_V2,
        RECAPTCHA_V3,
        HCAPTCHA,
        CLOUDFLARE_TURNSTILE,
        UNKNOWN
    }

    /** Result of CAPTCHA detection on a page. */
    record CaptchaDetection(
            boolean detected,
            CaptchaType captchaType,
            String sitekey,
            String pageUrl,
            String callbackFunction
    ) {
        public static CaptchaDetection none() {
            return new CaptchaDetection(false, CaptchaType.NONE, null, null, null);
        }

        public static CaptchaDetection found(CaptchaType type, String sitekey, String pageUrl) {
            return new CaptchaDetection(true, type, sitekey, pageUrl, null);
        }

        public static CaptchaDetection found(CaptchaType type, String sitekey, String pageUrl, String callback) {
            return new CaptchaDetection(true, type, sitekey, pageUrl, callback);
        }
    }

    /** Result of a CAPTCHA solve attempt. */
    record CaptchaSolveResult(
            boolean solved,
            String token,
            String errorMessage,
            long solveTimeMs
    ) {
        public static CaptchaSolveResult success(String token, long solveTimeMs) {
            return new CaptchaSolveResult(true, token, null, solveTimeMs);
        }

        public static CaptchaSolveResult failure(String errorMessage, long solveTimeMs) {
            return new CaptchaSolveResult(false, null, errorMessage, solveTimeMs);
        }
    }

    /** Thrown when CAPTCHA solving fails. */
    class CaptchaSolveException extends RuntimeException {
        public CaptchaSolveException(String message) { super(message); }
        public CaptchaSolveException(String message, Throwable cause) { super(message, cause); }
    }
}
