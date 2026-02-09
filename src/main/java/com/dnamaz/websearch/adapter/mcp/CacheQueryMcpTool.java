package com.dnamaz.websearch.adapter.mcp;

import com.dnamaz.websearch.model.VectorMatch;
import com.dnamaz.websearch.service.CacheService;
import com.dnamaz.websearch.service.NamespaceResolver;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP tool: cache_query
 * Semantic search over previously cached content.
 */
@Component
public class CacheQueryMcpTool {

    private final CacheService cacheService;
    private final NamespaceResolver namespaceResolver;

    public CacheQueryMcpTool(CacheService cacheService, NamespaceResolver namespaceResolver) {
        this.cacheService = cacheService;
        this.namespaceResolver = namespaceResolver;
    }

    @McpTool(name = "cache_query", description = "Search the local vector cache for content similar "
            + "to your query. Returns previously crawled and cached content ranked by semantic "
            + "similarity. Use after crawl_page or chunk_content to retrieve stored information.")
    public List<VectorMatch> cacheQuery(
            @McpToolParam(description = "Search query text") String query,
            @McpToolParam(description = "Number of results to return (default 5)", required = false) Integer topK,
            @McpToolParam(description = "Minimum similarity score (0.0-1.0)", required = false) Float similarityThreshold,
            @McpToolParam(description = "Project namespace for cache isolation", required = false) String namespace
    ) {
        String ns = namespaceResolver.resolve(namespace);
        return cacheService.query(query, topK, similarityThreshold, ns);
    }
}
