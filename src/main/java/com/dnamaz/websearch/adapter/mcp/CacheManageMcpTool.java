package com.dnamaz.websearch.adapter.mcp;

import com.dnamaz.websearch.service.EvictionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP tools: cache_evict, cache_flush
 * Cache maintenance operations for evicting expired entries or flushing the entire cache.
 */
@Configuration
public class CacheManageMcpTool {

    @Bean
    McpServerFeatures.SyncToolSpecification cacheEvictTool(
            EvictionService evictionService,
            ObjectMapper objectMapper) {

        var schema = """
                { "type": "object", "properties": {} }
                """;

        return new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("cache_evict")
                        .description("Run TTL-based eviction to remove expired entries from the vector cache. "
                                + "Removes search results older than 24h, query cache older than 6h, "
                                + "and crawl chunks older than 7d. Same as the scheduled hourly eviction.")
                        .inputSchema(McpToolHelper.parseSchema(schema))
                        .build(),
                (exchange, args) -> {
                    var result = evictionService.runEviction();
                    return McpToolHelper.toResult(objectMapper, result);
                }
        );
    }

    @Bean
    McpServerFeatures.SyncToolSpecification cacheFlushTool(
            EvictionService evictionService,
            ObjectMapper objectMapper) {

        var schema = """
                { "type": "object", "properties": {} }
                """;

        return new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("cache_flush")
                        .description("Delete ALL entries from the vector cache. This is a destructive operation "
                                + "that wipes the entire cache. Use when you want a clean slate or to "
                                + "free disk space.")
                        .inputSchema(McpToolHelper.parseSchema(schema))
                        .build(),
                (exchange, args) -> {
                    var result = evictionService.flushAll();
                    return McpToolHelper.toResult(objectMapper, result);
                }
        );
    }
}
