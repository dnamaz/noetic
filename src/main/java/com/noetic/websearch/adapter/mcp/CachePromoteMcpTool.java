package com.noetic.websearch.adapter.mcp;

import com.noetic.websearch.provider.VectorStore;
import com.noetic.websearch.provider.store.LuceneVectorStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * MCP tool: cache_promote
 * Promotes agent-local cache entries to the shared main index.
 */
@Configuration
public class CachePromoteMcpTool {

    @Bean
    McpServerFeatures.SyncToolSpecification cachePromoteTool(
            VectorStore vectorStore,
            ObjectMapper objectMapper) {

        var schema = """
                { "type": "object", "properties": {} }
                """;

        return new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("cache_promote")
                        .description("Promote all entries from the agent's local cache to the shared "
                                + "main index. Makes cached content available to other agents and sessions. "
                                + "Only applicable when running with an agent-id/session.")
                        .inputSchema(McpToolHelper.parseSchema(schema))
                        .build(),
                (exchange, args) -> {
                    if (vectorStore instanceof LuceneVectorStore lucene) {
                        int promoted = lucene.promoteToShared();
                        return McpToolHelper.toResult(objectMapper,
                                Map.of("promoted", promoted, "status", promoted > 0 ? "ok" : "nothing_to_promote"));
                    }
                    return McpToolHelper.toResult(objectMapper,
                            Map.of("promoted", 0, "status", "not_supported"));
                }
        );
    }
}
