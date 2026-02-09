package com.dnamaz.websearch.adapter.mcp;

import com.dnamaz.websearch.service.BatchCrawlService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP tool: batch_crawl
 * Crawls multiple URLs with optional sitemap discovery.
 */
@Component
public class BatchCrawlMcpTool {

    private final BatchCrawlService batchCrawlService;

    public BatchCrawlMcpTool(BatchCrawlService batchCrawlService) {
        this.batchCrawlService = batchCrawlService;
    }

    @McpTool(name = "batch_crawl", description = "Crawl multiple URLs or an entire domain. Provide "
            + "either a list of URLs or a domain (for automatic sitemap discovery). Each page is "
            + "crawled, chunked, and cached into the vector store. Use for bulk site ingestion.")
    public BatchCrawlService.BatchCrawlResult batchCrawl(
            @McpToolParam(description = "List of URLs to crawl", required = false) List<String> urls,
            @McpToolParam(description = "Domain for sitemap discovery (e.g. example.com)", required = false) String domain,
            @McpToolParam(description = "Fetch mode: auto, static, dynamic", required = false) String fetchMode,
            @McpToolParam(description = "Chunking strategy: sentence, token, semantic", required = false) String chunkStrategy,
            @McpToolParam(description = "Max concurrent crawls", required = false) Integer maxConcurrency,
            @McpToolParam(description = "Delay between requests in ms", required = false) Long rateLimitMs,
            @McpToolParam(description = "Regex to filter URLs by path", required = false) String pathFilter,
            @McpToolParam(description = "Max URLs to crawl", required = false) Integer maxUrls
    ) {
        return batchCrawlService.batchCrawl(urls, domain, fetchMode, chunkStrategy,
                maxConcurrency, rateLimitMs, pathFilter, maxUrls);
    }
}
