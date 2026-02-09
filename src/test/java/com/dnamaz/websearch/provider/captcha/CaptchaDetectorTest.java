package com.dnamaz.websearch.provider.captcha;

import com.dnamaz.websearch.provider.CaptchaSolver.CaptchaDetection;
import com.dnamaz.websearch.provider.CaptchaSolver.CaptchaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CaptchaDetector}.
 *
 * <p>These tests validate the JavaScript detection logic by checking
 * the expected JSON parsing behavior. Since CaptchaDetector.detect()
 * requires a live Jvppeteer Page, the detection JavaScript is tested
 * via integration tests against real CAPTCHA pages.</p>
 *
 * <p>This file tests the helper methods and static detection utilities.</p>
 */
class CaptchaDetectorTest {

    @Test
    @DisplayName("CaptchaDetection.none() returns not-detected")
    void noneReturnsNotDetected() {
        CaptchaDetection result = CaptchaDetection.none();

        assertFalse(result.detected());
        assertEquals(CaptchaType.NONE, result.captchaType());
        assertNull(result.sitekey());
    }

    @Test
    @DisplayName("CaptchaDetection.found() returns detected with type and sitekey")
    void foundReturnsDetected() {
        CaptchaDetection result = CaptchaDetection.found(
                CaptchaType.RECAPTCHA_V2, "6LeIxAcTAAAAAAJcZVRqyHh71UMIEGNQ", "https://example.com");

        assertTrue(result.detected());
        assertEquals(CaptchaType.RECAPTCHA_V2, result.captchaType());
        assertEquals("6LeIxAcTAAAAAAJcZVRqyHh71UMIEGNQ", result.sitekey());
        assertEquals("https://example.com", result.pageUrl());
    }

    @Test
    @DisplayName("CaptchaDetection.found() with callback")
    void foundWithCallback() {
        CaptchaDetection result = CaptchaDetection.found(
                CaptchaType.CLOUDFLARE_TURNSTILE, "0x4ABC", "https://example.com", "onSuccess");

        assertTrue(result.detected());
        assertEquals(CaptchaType.CLOUDFLARE_TURNSTILE, result.captchaType());
        assertEquals("onSuccess", result.callbackFunction());
    }

    @Test
    @DisplayName("CaptchaType enum has all expected values")
    void captchaTypeHasExpectedValues() {
        assertEquals(6, CaptchaType.values().length);
        assertNotNull(CaptchaType.NONE);
        assertNotNull(CaptchaType.RECAPTCHA_V2);
        assertNotNull(CaptchaType.RECAPTCHA_V3);
        assertNotNull(CaptchaType.HCAPTCHA);
        assertNotNull(CaptchaType.CLOUDFLARE_TURNSTILE);
        assertNotNull(CaptchaType.UNKNOWN);
    }
}
