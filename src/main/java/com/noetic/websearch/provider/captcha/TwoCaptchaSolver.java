package com.noetic.websearch.provider.captcha;

import com.noetic.websearch.provider.CaptchaSolver;
import com.ruiyun.jvppeteer.core.Page;
import com.twocaptcha.TwoCaptcha;
import com.twocaptcha.captcha.HCaptcha;
import com.twocaptcha.captcha.ReCaptcha;
import com.twocaptcha.captcha.Turnstile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Solves CAPTCHAs using the 2Captcha API service.
 *
 * <p>Supports reCAPTCHA v2/v3, hCaptcha, and Cloudflare Turnstile.
 * Requires a paid API key from <a href="https://2captcha.com">2captcha.com</a>.</p>
 *
 * <p>Only activated when {@code websearch.captcha.active=2captcha} and a valid
 * API key is provided. No external calls are made otherwise.</p>
 */
@Component
@ConditionalOnProperty(name = "websearch.captcha.active", havingValue = "2captcha")
public class TwoCaptchaSolver implements CaptchaSolver {

    private static final Logger log = LoggerFactory.getLogger(TwoCaptchaSolver.class);

    private final TwoCaptcha solver;
    private final int pollingIntervalMs;

    public TwoCaptchaSolver(
            @Value("${websearch.captcha.2captcha.api-key}") String apiKey,
            @Value("${websearch.captcha.2captcha.polling-interval-ms:5000}") int pollingIntervalMs
    ) {
        this.solver = new TwoCaptcha(apiKey);
        this.pollingIntervalMs = pollingIntervalMs;
        this.solver.setPollingInterval(pollingIntervalMs);
        log.info("TwoCaptchaSolver initialized (polling interval: {}ms)", pollingIntervalMs);
    }

    @Override
    public String type() {
        return "2captcha";
    }

    @Override
    public CaptchaDetection detect(Page page) {
        String pageUrl;
        try {
            Object urlResult = page.evaluate("window.location.href");
            pageUrl = urlResult != null ? urlResult.toString() : "";
        } catch (Exception e) {
            pageUrl = "";
        }
        return CaptchaDetector.detect(page, pageUrl);
    }

    @Override
    public CaptchaSolveResult solve(Page page, CaptchaDetection detection) {
        if (!detection.detected()) {
            return CaptchaSolveResult.failure("No CAPTCHA detected", 0);
        }

        long startTime = System.currentTimeMillis();

        try {
            String token = switch (detection.captchaType()) {
                case RECAPTCHA_V2 -> solveRecaptchaV2(detection);
                case RECAPTCHA_V3 -> solveRecaptchaV3(detection);
                case HCAPTCHA -> solveHCaptcha(detection);
                case CLOUDFLARE_TURNSTILE -> solveTurnstile(detection);
                default -> throw new CaptchaSolveException(
                        "Unsupported CAPTCHA type: " + detection.captchaType());
            };

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("CAPTCHA solved in {}ms: type={}, url={}", elapsed,
                    detection.captchaType(), detection.pageUrl());

            // Inject the solution token into the page
            injectToken(page, detection, token);

            return CaptchaSolveResult.success(token, elapsed);

        } catch (CaptchaSolveException e) {
            throw e;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("CAPTCHA solve failed after {}ms: {}", elapsed, e.getMessage());
            return CaptchaSolveResult.failure(e.getMessage(), elapsed);
        }
    }

    // ── Solver methods per CAPTCHA type ──────────────────────────────────

    private String solveRecaptchaV2(CaptchaDetection detection) throws Exception {
        ReCaptcha captcha = new ReCaptcha();
        captcha.setSiteKey(detection.sitekey());
        captcha.setUrl(detection.pageUrl());
        solver.solve(captcha);
        return captcha.getCode();
    }

    private String solveRecaptchaV3(CaptchaDetection detection) throws Exception {
        ReCaptcha captcha = new ReCaptcha();
        captcha.setSiteKey(detection.sitekey());
        captcha.setUrl(detection.pageUrl());
        captcha.setVersion("v3");
        captcha.setAction("verify");
        captcha.setScore(0.9);
        solver.solve(captcha);
        return captcha.getCode();
    }

    private String solveHCaptcha(CaptchaDetection detection) throws Exception {
        HCaptcha captcha = new HCaptcha();
        captcha.setSiteKey(detection.sitekey());
        captcha.setUrl(detection.pageUrl());
        solver.solve(captcha);
        return captcha.getCode();
    }

    private String solveTurnstile(CaptchaDetection detection) throws Exception {
        Turnstile captcha = new Turnstile();
        captcha.setSiteKey(detection.sitekey());
        captcha.setUrl(detection.pageUrl());
        solver.solve(captcha);
        return captcha.getCode();
    }

    // ── Token injection ──────────────────────────────────────────────────

    /**
     * Injects the CAPTCHA solution token into the page and triggers any callback.
     */
    private void injectToken(Page page, CaptchaDetection detection, String token) {
        try {
            String escapedToken = token.replace("'", "\\'");

            switch (detection.captchaType()) {
                case RECAPTCHA_V2, RECAPTCHA_V3 -> {
                    // Set the g-recaptcha-response textarea
                    page.evaluate("""
                            document.getElementById('g-recaptcha-response').value = '%s';
                            """.formatted(escapedToken));

                    // Trigger callback if one exists
                    if (detection.callbackFunction() != null && !detection.callbackFunction().isBlank()) {
                        page.evaluate("%s('%s')".formatted(detection.callbackFunction(), escapedToken));
                    }
                }
                case HCAPTCHA -> {
                    // Set the h-captcha-response textarea
                    page.evaluate("""
                            const textarea = document.querySelector('[name="h-captcha-response"], textarea[name="g-recaptcha-response"]');
                            if (textarea) textarea.value = '%s';
                            """.formatted(escapedToken));
                }
                case CLOUDFLARE_TURNSTILE -> {
                    // Set the cf-turnstile-response hidden input
                    page.evaluate("""
                            const input = document.querySelector('[name="cf-turnstile-response"]');
                            if (input) input.value = '%s';
                            """.formatted(escapedToken));

                    if (detection.callbackFunction() != null && !detection.callbackFunction().isBlank()) {
                        page.evaluate("%s('%s')".formatted(detection.callbackFunction(), escapedToken));
                    }
                }
                default -> log.warn("Token injection not supported for type: {}", detection.captchaType());
            }

            log.debug("Injected CAPTCHA token into page");
        } catch (Exception e) {
            log.warn("Failed to inject CAPTCHA token: {}", e.getMessage());
        }
    }
}
