package com.dnamaz.websearch.adapter.mcp;

import com.dnamaz.websearch.service.MapService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tool: map_site
 * Discover all reachable URLs from a starting URL via BFS link crawling.
 */
@Component
public class MapSiteMcpTool {

    private final MapService mapService;

    public MapSiteMcpTool(MapService mapService) {
        this.mapService = mapService;
    }

    @McpTool(name = "map_site", description = "Discover all reachable URLs from a starting URL "
            + "by following links. Uses BFS crawling with same-domain filtering. Faster than "
            + "discover_sitemap for sites without sitemaps. Returns a flat list of discovered URLs.")
    public MapService.MapResult mapSite(
            @McpToolParam(description = "Starting URL or domain (e.g. https://example.com)") String startUrl,
            @McpToolParam(description = "Maximum link depth to crawl (default 3)", required = false) Integer maxDepth,
            @McpToolParam(description = "Maximum URLs to discover (default 100)", required = false) Integer maxUrls,
            @McpToolParam(description = "Regex to filter URLs by path (e.g. /docs/.*)", required = false) String pathFilter
    ) {
        return mapService.map(startUrl, maxDepth, maxUrls, pathFilter);
    }
}
