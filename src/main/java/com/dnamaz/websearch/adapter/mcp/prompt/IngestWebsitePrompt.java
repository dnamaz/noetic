package com.dnamaz.websearch.adapter.mcp.prompt;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP Prompt: ingest_website
 * Teaches the LLM to discover all pages from a domain via sitemap and ingest into cache.
 */
@Component
public class IngestWebsitePrompt {

    @McpTool(name = "prompt_ingest_website",
            description = "Get instructions for ingesting an entire website into the vector "
                    + "cache via sitemap discovery and batch crawling.")
    public Map<String, String> ingestWebsite(
            @McpToolParam(description = "Domain to ingest (e.g. docs.example.com)") String domain,
            @McpToolParam(description = "Regex to filter URLs by path (e.g. /docs/.*)", required = false) String pathFilter,
            @McpToolParam(description = "Max pages to ingest (default 100)", required = false) Integer maxPages
    ) {
        int max = maxPages != null ? maxPages : 100;
        String filterNote = pathFilter != null
                ? "Apply path filter: " + pathFilter
                : "No path filter -- ingest all discovered pages.";

        String instructions = """
                You are a website ingestion assistant. Follow these steps:

                1. Call `discover_sitemap` with domain="%s" and maxUrls=%d
                   %s
                2. Review the discovered URLs and confirm with the user if the list looks correct.
                3. Call `batch_crawl` with the domain="%s", maxUrls=%d,
                   chunkStrategy="semantic" to crawl and cache all pages.
                4. Report the results: pages crawled, chunks stored, any failures.
                5. The website content is now available via `cache_query` for future retrieval.
                """.formatted(domain, max, filterNote, domain, max);

        return Map.of(
                "role", "system",
                "instructions", instructions,
                "domain", domain,
                "maxPages", String.valueOf(max)
        );
    }
}
