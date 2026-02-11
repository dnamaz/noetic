package com.noetic.websearch.provider;

import com.noetic.websearch.model.SearchCapabilities;
import com.noetic.websearch.model.SearchRequest;
import com.noetic.websearch.model.SearchResponse;

/**
 * Provider interface for performing web searches.
 *
 * <p>Implementations include scraping (DuckDuckGo) and API-based
 * providers (Brave, SerpAPI, Tavily).</p>
 */
public interface SearchProvider {

    /** Provider type identifier (e.g. "scraping", "brave", "tavily"). */
    String type();

    /** Declares what this provider supports. */
    SearchCapabilities capabilities();

    /** Execute a web search. */
    SearchResponse search(SearchRequest request);
}
