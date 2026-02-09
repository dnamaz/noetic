package com.dnamaz.websearch.adapter.mcp;

import com.dnamaz.websearch.model.FetchResult;
import com.dnamaz.websearch.model.OutputFormat;
import com.dnamaz.websearch.service.CrawlService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tool: crawl_page
 * Fetches and extracts content from a web page.
 */
@Component
public class CrawlPageMcpTool {

    private final CrawlService crawlService;

    public CrawlPageMcpTool(CrawlService crawlService) {
        this.crawlService = crawlService;
    }

    @McpTool(name = "crawl_page", description = "Fetch and extract content from a web page. Returns "
            + "clean text/markdown content. Supports static HTML fetching and dynamic JavaScript "
            + "rendering. Use fetchMode 'auto' for automatic detection, 'static' for fast HTML-only, "
            + "or 'dynamic' for JavaScript-rendered pages.")
    public FetchResult crawlPage(
            @McpToolParam(description = "The URL to crawl") String url,
            @McpToolParam(description = "Fetch mode: auto, static, dynamic, api", required = false) String fetchMode,
            @McpToolParam(description = "Output format: html, markdown, text", required = false) String outputFormat,
            @McpToolParam(description = "Include links found on the page", required = false) Boolean includeLinks,
            @McpToolParam(description = "Include image URLs found on the page", required = false) Boolean includeImages,
            @McpToolParam(description = "CSS selector to wait for (dynamic mode)", required = false) String waitForSelector
    ) {
        OutputFormat format = null;
        if (outputFormat != null) {
            try {
                format = OutputFormat.valueOf(outputFormat.toUpperCase());
            } catch (IllegalArgumentException e) {
                format = OutputFormat.MARKDOWN;
            }
        }

        return crawlService.crawl(
                url,
                fetchMode != null ? fetchMode : "auto",
                format,
                includeLinks != null && includeLinks,
                includeImages != null && includeImages,
                waitForSelector
        );
    }
}
