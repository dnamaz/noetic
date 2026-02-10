package com.dnamaz.websearch.adapter.mcp;

import com.dnamaz.websearch.service.ChunkService;
import com.dnamaz.websearch.service.NamespaceResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP tool: chunk_content
 * Splits content into chunks, embeds them, and stores in the vector cache.
 */
@Configuration
public class ChunkContentMcpTool {

    @Bean
    McpServerFeatures.SyncToolSpecification chunkContentTool(
            ChunkService chunkService,
            NamespaceResolver namespaceResolver,
            ObjectMapper objectMapper) {

        var schema = """
                {
                  "type": "object",
                  "properties": {
                    "content": { "type": "string", "description": "The text content to chunk and cache" },
                    "strategy": { "type": "string", "description": "Chunking strategy: sentence, token, semantic" },
                    "maxChunkSize": { "type": "integer", "description": "Maximum chunk size in characters" },
                    "overlap": { "type": "integer", "description": "Overlap between chunks in characters" },
                    "sourceUrl": { "type": "string", "description": "Source URL for metadata tracking" },
                    "namespace": { "type": "string", "description": "Project namespace for cache isolation" }
                  },
                  "required": ["content"]
                }
                """;

        return new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("chunk_content")
                        .description("Split content into chunks, generate embeddings, and store in the vector "
                                + "cache for future semantic retrieval. Choose a strategy: 'sentence' preserves "
                                + "sentence boundaries, 'token' splits by word count, 'semantic' splits at "
                                + "paragraph/section boundaries.")
                        .inputSchema(McpToolHelper.parseSchema(schema))
                        .build(),
                (exchange, args) -> {
                    var content = (String) args.get("content");
                    var strategy = (String) args.get("strategy");
                    var maxChunkSize = args.get("maxChunkSize") instanceof Number n ? n.intValue() : null;
                    var overlap = args.get("overlap") instanceof Number n ? n.intValue() : null;
                    var sourceUrl = (String) args.get("sourceUrl");
                    var namespace = (String) args.get("namespace");

                    String ns = namespaceResolver.resolve(namespace);
                    var result = chunkService.chunk(content, strategy, maxChunkSize, overlap, sourceUrl, ns);

                    return McpToolHelper.toResult(objectMapper, result);
                }
        );
    }
}
