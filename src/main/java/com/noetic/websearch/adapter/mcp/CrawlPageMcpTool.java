package com.noetic.websearch.adapter.mcp;

import com.noetic.websearch.model.OutputFormat;
import com.noetic.websearch.service.CrawlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP tool: crawl_page
 * Fetches and extracts content from a web page.
 */
@Configuration
public class CrawlPageMcpTool {

    @Bean
    McpServerFeatures.SyncToolSpecification crawlPageTool(
            CrawlService crawlService,
            ObjectMapper objectMapper) {

        var schema = """
                {
                  "type": "object",
                  "properties": {
                    "url": { "type": "string", "description": "The URL to crawl" },
                    "fetchMode": { "type": "string", "description": "Fetch mode: auto, static, dynamic, api" },
                    "outputFormat": { "type": "string", "description": "Output format: html, markdown, text" },
                    "includeLinks": { "type": "boolean", "description": "Include links found on the page" },
                    "includeImages": { "type": "boolean", "description": "Include image URLs found on the page" },
                    "waitForSelector": { "type": "string", "description": "CSS selector to wait for (dynamic mode)" }
                  },
                  "required": ["url"]
                }
                """;

        return new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("crawl_page")
                        .description("Fetch and extract content from a web page. Returns clean text/markdown "
                                + "content. Supports static HTML fetching and dynamic JavaScript rendering. "
                                + "Use fetchMode 'auto' for automatic detection, 'static' for fast HTML-only, "
                                + "or 'dynamic' for JavaScript-rendered pages.")
                        .inputSchema(McpToolHelper.parseSchema(schema))
                        .build(),
                (exchange, args) -> {
                    var url = (String) args.get("url");
                    var fetchMode = (String) args.get("fetchMode");
                    var outputFormatStr = (String) args.get("outputFormat");
                    var includeLinks = args.get("includeLinks") instanceof Boolean b ? b : false;
                    var includeImages = args.get("includeImages") instanceof Boolean b ? b : false;
                    var waitForSelector = (String) args.get("waitForSelector");

                    OutputFormat format = null;
                    if (outputFormatStr != null) {
                        try {
                            format = OutputFormat.valueOf(outputFormatStr.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            format = OutputFormat.MARKDOWN;
                        }
                    }

                    var result = crawlService.crawl(
                            url,
                            fetchMode != null ? fetchMode : "auto",
                            format,
                            includeLinks,
                            includeImages,
                            waitForSelector);

                    return McpToolHelper.toResult(objectMapper, result);
                }
        );
    }
}
