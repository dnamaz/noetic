package com.noetic.websearch.adapter.mcp;

import com.noetic.websearch.service.BatchCrawlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP tool: batch_crawl
 * Crawls multiple URLs with optional sitemap discovery.
 */
@Configuration
public class BatchCrawlMcpTool {

    @Bean
    McpServerFeatures.SyncToolSpecification batchCrawlTool(
            BatchCrawlService batchCrawlService,
            ObjectMapper objectMapper) {

        var schema = """
                {
                  "type": "object",
                  "properties": {
                    "urls": { "type": "array", "items": { "type": "string" }, "description": "List of URLs to crawl" },
                    "domain": { "type": "string", "description": "Domain for sitemap discovery (e.g. example.com)" },
                    "fetchMode": { "type": "string", "description": "Fetch mode: auto, static, dynamic" },
                    "chunkStrategy": { "type": "string", "description": "Chunking strategy: sentence, token, semantic" },
                    "maxConcurrency": { "type": "integer", "description": "Max concurrent crawls" },
                    "rateLimitMs": { "type": "integer", "description": "Delay between requests in ms" },
                    "pathFilter": { "type": "string", "description": "Regex to filter URLs by path" },
                    "maxUrls": { "type": "integer", "description": "Max URLs to crawl" }
                  }
                }
                """;

        return new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("batch_crawl")
                        .description("Crawl multiple URLs or an entire domain. Provide either a list of URLs "
                                + "or a domain (for automatic sitemap discovery). Each page is crawled, "
                                + "chunked, and cached into the vector store. Use for bulk site ingestion.")
                        .inputSchema(McpToolHelper.parseSchema(schema))
                        .build(),
                (exchange, args) -> {
                    @SuppressWarnings("unchecked")
                    var urls = (List<String>) args.get("urls");
                    var domain = (String) args.get("domain");
                    var fetchMode = (String) args.get("fetchMode");
                    var chunkStrategy = (String) args.get("chunkStrategy");
                    var maxConcurrency = args.get("maxConcurrency") instanceof Number n ? n.intValue() : null;
                    var rateLimitMs = args.get("rateLimitMs") instanceof Number n ? n.longValue() : null;
                    var pathFilter = (String) args.get("pathFilter");
                    var maxUrls = args.get("maxUrls") instanceof Number n ? n.intValue() : null;

                    var result = batchCrawlService.batchCrawl(urls, domain, fetchMode, chunkStrategy,
                            maxConcurrency, rateLimitMs, pathFilter, maxUrls);

                    return McpToolHelper.toResult(objectMapper, result);
                }
        );
    }
}
