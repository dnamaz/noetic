package com.noetic.websearch.adapter.mcp;

import com.noetic.websearch.service.MapService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP tool: map_site
 * Discover all reachable URLs from a starting URL via BFS link crawling.
 */
@Configuration
public class MapSiteMcpTool {

    @Bean
    McpServerFeatures.SyncToolSpecification mapSiteTool(
            MapService mapService,
            ObjectMapper objectMapper) {

        var schema = """
                {
                  "type": "object",
                  "properties": {
                    "startUrl": { "type": "string", "description": "Starting URL or domain (e.g. https://example.com)" },
                    "maxDepth": { "type": "integer", "description": "Maximum link depth to crawl (default 3)" },
                    "maxUrls": { "type": "integer", "description": "Maximum URLs to discover (default 100)" },
                    "pathFilter": { "type": "string", "description": "Regex to filter URLs by path (e.g. /docs/.*)" }
                  },
                  "required": ["startUrl"]
                }
                """;

        return new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("map_site")
                        .description("Discover all reachable URLs from a starting URL by following links. "
                                + "Uses BFS crawling with same-domain filtering. Faster than "
                                + "discover_sitemap for sites without sitemaps. Returns a flat list "
                                + "of discovered URLs.")
                        .inputSchema(McpToolHelper.parseSchema(schema))
                        .build(),
                (exchange, args) -> {
                    var startUrl = (String) args.get("startUrl");
                    var maxDepth = args.get("maxDepth") instanceof Number n ? n.intValue() : null;
                    var maxUrls = args.get("maxUrls") instanceof Number n ? n.intValue() : null;
                    var pathFilter = (String) args.get("pathFilter");

                    var result = mapService.map(startUrl, maxDepth, maxUrls, pathFilter);

                    return McpToolHelper.toResult(objectMapper, result);
                }
        );
    }
}
