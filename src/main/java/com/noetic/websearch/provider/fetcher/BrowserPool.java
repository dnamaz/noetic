package com.noetic.websearch.provider.fetcher;

import com.ruiyun.jvppeteer.core.Browser;
import com.ruiyun.jvppeteer.core.Puppeteer;
import com.ruiyun.jvppeteer.entities.LaunchOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages a pool of headless Chromium browser instances for reuse across requests.
 *
 * <p>Browsers are launched lazily on first acquire. When released, they are returned
 * to the pool for reuse. If a browser has crashed or is disconnected, it is discarded
 * and a fresh one is created.</p>
 *
 * <p>Thread-safe. Implements {@link AutoCloseable} for clean shutdown.</p>
 */
public class BrowserPool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BrowserPool.class);

    private final LaunchOptions launchOptions;
    private final int poolSize;
    private final BlockingQueue<Browser> available;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Creates a browser pool.
     *
     * @param executablePath path to Chrome/Chromium binary
     * @param poolSize       maximum number of browser instances
     * @param stealthArgs    additional launch args (stealth flags, proxy, etc.)
     */
    public BrowserPool(String executablePath, int poolSize, List<String> stealthArgs) {
        this.poolSize = poolSize;
        this.available = new LinkedBlockingQueue<>(poolSize);

        List<String> args = new ArrayList<>();
        args.add("--no-sandbox");
        args.add("--disable-setuid-sandbox");
        args.add("--disable-dev-shm-usage");
        args.add("--disable-gpu");
        args.addAll(stealthArgs);

        this.launchOptions = new LaunchOptions();
        this.launchOptions.setExecutablePath(executablePath);
        this.launchOptions.setHeadless(true);
        this.launchOptions.setArgs(args);
        this.launchOptions.setDefaultViewport(null);
        this.launchOptions.setProtocolTimeout(60_000);

        log.info("BrowserPool created: poolSize={}, executable={}", poolSize, executablePath);
    }

    /**
     * Acquires a browser from the pool. If none are available, launches a new one
     * (up to pool size). Blocks briefly if the pool is at capacity.
     *
     * @param timeoutMs maximum time to wait for an available browser
     * @return a browser instance
     * @throws RuntimeException if the pool is closed or timed out
     */
    public Browser acquire(long timeoutMs) {
        if (closed.get()) {
            throw new IllegalStateException("BrowserPool is closed");
        }

        // Try to get one from the pool immediately
        Browser browser = available.poll();
        if (browser != null && isHealthy(browser)) {
            return browser;
        }

        // Pool empty or unhealthy browser -- launch a new one
        try {
            return launchBrowser();
        } catch (Exception e) {
            log.error("Failed to launch browser: {}", e.getMessage());
            // Last resort: wait for a browser to be returned
            try {
                browser = available.poll(timeoutMs, TimeUnit.MILLISECONDS);
                if (browser != null && isHealthy(browser)) {
                    return browser;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Could not acquire browser from pool", e);
        }
    }

    /**
     * Returns a browser to the pool for reuse. If the browser is unhealthy
     * or the pool is full, it is closed instead.
     *
     * @param browser the browser to release
     */
    public void release(Browser browser) {
        if (browser == null) return;

        if (closed.get() || !isHealthy(browser)) {
            closeBrowser(browser);
            return;
        }

        // Try to return to pool; if full, close it
        if (!available.offer(browser)) {
            closeBrowser(browser);
        }
    }

    /**
     * Checks if a browser instance is still usable.
     */
    private boolean isHealthy(Browser browser) {
        try {
            return browser.connected();
        } catch (Exception e) {
            return false;
        }
    }

    private Browser launchBrowser() throws Exception {
        log.debug("Launching new Chromium instance");
        return Puppeteer.launch(launchOptions);
    }

    private void closeBrowser(Browser browser) {
        try {
            browser.close();
        } catch (Exception e) {
            log.debug("Error closing browser: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        log.info("Shutting down BrowserPool");
        Browser browser;
        while ((browser = available.poll()) != null) {
            closeBrowser(browser);
        }
    }
}
