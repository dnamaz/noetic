package com.noetic.websearch.adapter.mcp;

import com.noetic.websearch.service.BatchCrawlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP tool: discover_sitemap
 * Finds crawlable URLs from a domain by parsing robots.txt and sitemap XML.
 */
@Configuration
public class DiscoverSitemapMcpTool {

    @Bean
    McpServerFeatures.SyncToolSpecification discoverSitemapTool(
            BatchCrawlService batchCrawlService,
            ObjectMapper objectMapper) {

        var schema = """
                {
                  "type": "object",
                  "properties": {
                    "domain": { "type": "string", "description": "Domain to discover (e.g. example.com)" },
                    "maxUrls": { "type": "integer", "description": "Max URLs to return" },
                    "pathFilter": { "type": "string", "description": "Regex to filter URLs by path (e.g. /blog/.*)" }
                  },
                  "required": ["domain"]
                }
                """;

        return new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("discover_sitemap")
                        .description("Find crawlable URLs from a domain by parsing robots.txt and sitemap XML. "
                                + "Returns a list of discovered URLs. Use pathFilter to restrict to "
                                + "specific sections (e.g. '/blog/.*' for blog posts only).")
                        .inputSchema(McpToolHelper.parseSchema(schema))
                        .build(),
                (exchange, args) -> {
                    var domain = (String) args.get("domain");
                    var maxUrls = args.get("maxUrls") instanceof Number n ? n.intValue() : null;
                    var pathFilter = (String) args.get("pathFilter");

                    var result = batchCrawlService.discoverSitemap(domain, maxUrls, pathFilter);

                    return McpToolHelper.toResult(objectMapper, result);
                }
        );
    }
}
