package com.dnamaz.websearch.provider;

import com.dnamaz.websearch.model.SearchCapabilities;
import com.dnamaz.websearch.model.SearchRequest;
import com.dnamaz.websearch.model.SearchResponse;

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
