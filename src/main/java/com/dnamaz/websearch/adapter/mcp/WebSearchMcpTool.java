package com.dnamaz.websearch.adapter.mcp;

import com.dnamaz.websearch.model.*;
import com.dnamaz.websearch.service.NamespaceResolver;
import com.dnamaz.websearch.service.WebSearchService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP tool: web_search
 * Searches the internet for a query and returns ranked results.
 */
@Component
public class WebSearchMcpTool {

    private final WebSearchService webSearchService;
    private final NamespaceResolver namespaceResolver;

    public WebSearchMcpTool(WebSearchService webSearchService, NamespaceResolver namespaceResolver) {
        this.webSearchService = webSearchService;
        this.namespaceResolver = namespaceResolver;
    }

    @McpTool(name = "web_search", description = "Search the internet for a query. Returns a ranked "
            + "list of results with titles, URLs, and snippets. Results are cached -- subsequent "
            + "similar queries may return cached results. Use 'freshness' to filter by recency. "
            + "Use 'includeDomains' to restrict to specific sites.")
    public SearchResponse webSearch(
            @McpToolParam(description = "The search query") String query,
            @McpToolParam(description = "Max results to return (1-100)", required = false) Integer maxResults,
            @McpToolParam(description = "Recency filter: day, week, month, year", required = false) String freshness,
            @McpToolParam(description = "ISO language code (e.g. en, fr)", required = false) String language,
            @McpToolParam(description = "Only include results from these domains", required = false) List<String> includeDomains,
            @McpToolParam(description = "Project namespace for cache isolation", required = false) String namespace
    ) {
        String ns = namespaceResolver.resolve(namespace);
        return webSearchService.search(SearchRequest.builder()
                .query(query)
                .maxResults(maxResults)
                .freshness(Freshness.parse(freshness))
                .language(language)
                .includeDomains(includeDomains)
                .build(), ns);
    }
}
