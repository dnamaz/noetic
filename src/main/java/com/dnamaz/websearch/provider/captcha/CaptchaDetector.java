package com.dnamaz.websearch.provider.captcha;

import com.dnamaz.websearch.provider.CaptchaSolver.CaptchaDetection;
import com.dnamaz.websearch.provider.CaptchaSolver.CaptchaType;
import com.ruiyun.jvppeteer.core.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects CAPTCHAs on rendered pages by examining the DOM via CDP evaluation.
 *
 * <p>Supports detection of reCAPTCHA v2/v3, hCaptcha, and Cloudflare Turnstile.
 * Each detection returns the CAPTCHA type, sitekey, and page URL needed for
 * submission to a solving service.</p>
 */
public final class CaptchaDetector {

    private static final Logger log = LoggerFactory.getLogger(CaptchaDetector.class);

    private CaptchaDetector() {}

    /**
     * JavaScript executed in the page context to detect CAPTCHAs.
     * Returns a JSON object: { type, sitekey, callback }
     */
    /**
     * Note: Jvppeteer v2.1.0 evaluate() expects a function expression, NOT an IIFE.
     * The function is wrapped and called by the library internally.
     */
    /**
     * Detection order matters: check specific providers (hCaptcha, Turnstile) BEFORE
     * the generic [data-sitekey] selector, since hCaptcha also uses data-sitekey.
     *
     * <p>Order: hCaptcha class/iframe -> Turnstile class -> reCAPTCHA class/iframe/script -> Cloudflare challenge</p>
     *
     * <p>Note: Jvppeteer v2.1.0 evaluate() expects a function expression, NOT an IIFE.</p>
     */
    private static final String DETECT_SCRIPT =
            "() => { "
            // hCaptcha (check FIRST -- it also uses data-sitekey)
            + "var hc = document.querySelector('.h-captcha'); "
            + "if (hc) { var sk = hc.getAttribute('data-sitekey') || ''; return JSON.stringify({type:'HCAPTCHA',sitekey:sk,callback:''}); } "
            + "var hi = document.querySelector('iframe[src*=\"hcaptcha.com\"]'); "
            + "if (hi) { var s = hi.getAttribute('src') || ''; var m = s.match(/sitekey=([^&]+)/); var sk2 = m ? m[1] : ''; return JSON.stringify({type:'HCAPTCHA',sitekey:sk2,callback:''}); } "
            // Cloudflare Turnstile
            + "var ts = document.querySelector('.cf-turnstile, [data-turnstile-sitekey]'); "
            + "if (ts) { var sk3 = ts.getAttribute('data-sitekey') || ts.getAttribute('data-turnstile-sitekey') || ''; var cb3 = ts.getAttribute('data-callback') || ''; return JSON.stringify({type:'CLOUDFLARE_TURNSTILE',sitekey:sk3,callback:cb3}); } "
            // reCAPTCHA v2 -- class-based detection
            + "var rc = document.querySelector('.g-recaptcha'); "
            + "if (rc) { var sk4 = rc.getAttribute('data-sitekey') || ''; var cb4 = rc.getAttribute('data-callback') || ''; return JSON.stringify({type:'RECAPTCHA_V2',sitekey:sk4,callback:cb4}); } "
            // reCAPTCHA v2 via iframe
            + "var ri = document.querySelector('iframe[src*=\"recaptcha\"]'); "
            + "if (ri) { var s5 = ri.getAttribute('src') || ''; var m5 = s5.match(/[?&]k=([^&]+)/); var sk5 = m5 ? m5[1] : ''; return JSON.stringify({type:'RECAPTCHA_V2',sitekey:sk5,callback:''}); } "
            // reCAPTCHA v3 via script tag
            + "var rv3 = document.querySelector('script[src*=\"recaptcha/api.js?render=\"]'); "
            + "if (rv3) { var s6 = rv3.getAttribute('src') || ''; var m6 = s6.match(/render=([^&]+)/); var sk6 = m6 ? m6[1] : ''; return JSON.stringify({type:'RECAPTCHA_V3',sitekey:sk6,callback:''}); } "
            // Generic data-sitekey (catch-all for reCAPTCHA without .g-recaptcha class)
            + "var gs = document.querySelector('[data-sitekey]'); "
            + "if (gs) { var sk7 = gs.getAttribute('data-sitekey') || ''; return JSON.stringify({type:'RECAPTCHA_V2',sitekey:sk7,callback:''}); } "
            // Cloudflare challenge page (interstitial)
            + "if (document.querySelector('#challenge-form, #challenge-running, .cf-browser-verification')) { return JSON.stringify({type:'CLOUDFLARE_TURNSTILE',sitekey:'',callback:''}); } "
            + "return JSON.stringify({type:'NONE',sitekey:'',callback:''}); "
            + "}";

    /**
     * Detects CAPTCHAs on the given page by executing JavaScript in the page context.
     *
     * @param page    the Jvppeteer page after navigation
     * @param pageUrl the URL of the page (for logging and solve submission)
     * @return detection result
     */
    public static CaptchaDetection detect(Page page, String pageUrl) {
        try {
            Object result = page.evaluate(DETECT_SCRIPT);
            if (result == null) {
                return CaptchaDetection.none();
            }

            String json = result.toString();

            // Parse the simple JSON manually to avoid adding a dependency
            String typeStr = extractJsonValue(json, "type");
            String sitekey = extractJsonValue(json, "sitekey");
            String callback = extractJsonValue(json, "callback");

            CaptchaType type = parseCaptchaType(typeStr);
            if (type == CaptchaType.NONE) {
                return CaptchaDetection.none();
            }

            log.info("CAPTCHA detected on {}: type={}, sitekey={}", pageUrl, type,
                    sitekey != null && sitekey.length() > 10
                            ? sitekey.substring(0, 10) + "..." : sitekey);

            return CaptchaDetection.found(type, sitekey, pageUrl, callback);

        } catch (Exception e) {
            log.debug("CAPTCHA detection failed for {}: {}", pageUrl, e.getMessage());
            return CaptchaDetection.none();
        }
    }

    private static CaptchaType parseCaptchaType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) return CaptchaType.NONE;
        try {
            return CaptchaType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return CaptchaType.UNKNOWN;
        }
    }

    /**
     * Extracts a value from a simple flat JSON string like {"key":"value"}.
     */
    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return "";
        return json.substring(start, end);
    }
}
