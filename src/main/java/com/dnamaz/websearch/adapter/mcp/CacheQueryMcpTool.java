package com.dnamaz.websearch.adapter.mcp;

import com.dnamaz.websearch.service.CacheService;
import com.dnamaz.websearch.service.NamespaceResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP tool: cache_query
 * Semantic search over previously cached content.
 */
@Configuration
public class CacheQueryMcpTool {

    @Bean
    McpServerFeatures.SyncToolSpecification cacheQueryTool(
            CacheService cacheService,
            NamespaceResolver namespaceResolver,
            ObjectMapper objectMapper) {

        var schema = """
                {
                  "type": "object",
                  "properties": {
                    "query": { "type": "string", "description": "Search query text" },
                    "topK": { "type": "integer", "description": "Number of results to return (default 5)" },
                    "similarityThreshold": { "type": "number", "description": "Minimum similarity score (0.0-1.0)" },
                    "namespace": { "type": "string", "description": "Project namespace for cache isolation" }
                  },
                  "required": ["query"]
                }
                """;

        return new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("cache_query")
                        .description("Search the local vector cache for content similar to your query. Returns "
                                + "previously crawled and cached content ranked by semantic similarity. "
                                + "Use after crawl_page or chunk_content to retrieve stored information.")
                        .inputSchema(McpToolHelper.parseSchema(schema))
                        .build(),
                (exchange, args) -> {
                    String query = (String) args.get("query");
                    Integer topK = args.get("topK") instanceof Number n ? n.intValue() : null;
                    Float similarityThreshold = args.get("similarityThreshold") instanceof Number n ? n.floatValue() : null;
                    String namespace = (String) args.get("namespace");

                    String ns = namespaceResolver.resolve(namespace);
                    var result = cacheService.query(query, topK, similarityThreshold, ns);

                    return McpToolHelper.toResult(objectMapper, result);
                }
        );
    }
}
