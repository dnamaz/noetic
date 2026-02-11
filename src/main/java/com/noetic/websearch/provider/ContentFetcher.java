package com.noetic.websearch.provider;

import com.noetic.websearch.model.FetchRequest;
import com.noetic.websearch.model.FetchResult;
import com.noetic.websearch.model.FetcherCapabilities;

/**
 * Provider interface for fetching web page content.
 *
 * <p>Implementations include static HTTP fetching (Jsoup), dynamic
 * JS-rendering (Jvppeteer/CDP), and external API services (Firecrawl,
 * Browserless, etc.).</p>
 */
public interface ContentFetcher {

    /** Provider type identifier (e.g. "static", "dynamic", "firecrawl"). */
    String type();

    /** Declares what this fetcher supports. */
    FetcherCapabilities capabilities();

    /** Whether this fetcher can handle the given request. */
    boolean supports(FetchRequest request);

    /** Fetch the page content. */
    FetchResult fetch(FetchRequest request);
}
