package com.dnamaz.websearch.adapter.mcp;

import com.dnamaz.websearch.service.BatchCrawlService;
import com.dnamaz.websearch.service.SitemapParser;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tool: discover_sitemap
 * Finds crawlable URLs from a domain by parsing robots.txt and sitemap XML.
 */
@Component
public class DiscoverSitemapMcpTool {

    private final BatchCrawlService batchCrawlService;

    public DiscoverSitemapMcpTool(BatchCrawlService batchCrawlService) {
        this.batchCrawlService = batchCrawlService;
    }

    @McpTool(name = "discover_sitemap", description = "Find crawlable URLs from a domain by parsing "
            + "robots.txt and sitemap XML. Returns a list of discovered URLs. Use pathFilter to "
            + "restrict to specific sections (e.g. '/blog/.*' for blog posts only).")
    public SitemapParser.SitemapResult discoverSitemap(
            @McpToolParam(description = "Domain to discover (e.g. example.com)") String domain,
            @McpToolParam(description = "Max URLs to return", required = false) Integer maxUrls,
            @McpToolParam(description = "Regex to filter URLs by path (e.g. /blog/.*)", required = false) String pathFilter
    ) {
        return batchCrawlService.discoverSitemap(domain, maxUrls, pathFilter);
    }
}
