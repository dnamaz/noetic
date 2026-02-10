package com.dnamaz.websearch.adapter.mcp.prompt;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP Prompt: ingest_website
 * Teaches the LLM to discover all pages from a domain via sitemap and ingest into cache.
 */
@Configuration
public class IngestWebsitePrompt {

    @Bean
    McpServerFeatures.SyncPromptSpecification ingestWebsitePromptSpec() {
        return new McpServerFeatures.SyncPromptSpecification(
                new McpSchema.Prompt("ingest_website",
                        "Get instructions for ingesting an entire website into the vector "
                                + "cache via sitemap discovery and batch crawling.",
                        List.of(
                                new McpSchema.PromptArgument("domain", "Domain to ingest (e.g. docs.example.com)", true),
                                new McpSchema.PromptArgument("pathFilter", "Regex to filter URLs by path (e.g. /docs/.*)", false),
                                new McpSchema.PromptArgument("maxPages", "Max pages to ingest (default 100)", false)
                        )),
                (exchange, request) -> {
                    String domain = (String) request.arguments().get("domain");
                    String pathFilter = (String) request.arguments().get("pathFilter");
                    String maxPagesStr = (String) request.arguments().get("maxPages");
                    int max = maxPagesStr != null ? Integer.parseInt(maxPagesStr) : 100;
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

                    return new GetPromptResult(
                            "Ingest website: " + domain,
                            List.of(new PromptMessage(Role.ASSISTANT, new TextContent(instructions)))
                    );
                }
        );
    }
}
