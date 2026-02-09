package com.dnamaz.websearch.adapter.mcp;

import com.dnamaz.websearch.model.ContentChunk;
import com.dnamaz.websearch.service.ChunkService;
import com.dnamaz.websearch.service.NamespaceResolver;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP tool: chunk_content
 * Splits content into chunks, embeds them, and stores in the vector cache.
 */
@Component
public class ChunkContentMcpTool {

    private final ChunkService chunkService;
    private final NamespaceResolver namespaceResolver;

    public ChunkContentMcpTool(ChunkService chunkService, NamespaceResolver namespaceResolver) {
        this.chunkService = chunkService;
        this.namespaceResolver = namespaceResolver;
    }

    @McpTool(name = "chunk_content", description = "Split content into chunks, generate embeddings, "
            + "and store in the vector cache for future semantic retrieval. Choose a strategy: "
            + "'sentence' preserves sentence boundaries, 'token' splits by word count, "
            + "'semantic' splits at paragraph/section boundaries.")
    public List<ContentChunk> chunkContent(
            @McpToolParam(description = "The text content to chunk and cache") String content,
            @McpToolParam(description = "Chunking strategy: sentence, token, semantic", required = false) String strategy,
            @McpToolParam(description = "Maximum chunk size in characters", required = false) Integer maxChunkSize,
            @McpToolParam(description = "Overlap between chunks in characters", required = false) Integer overlap,
            @McpToolParam(description = "Source URL for metadata tracking", required = false) String sourceUrl,
            @McpToolParam(description = "Project namespace for cache isolation", required = false) String namespace
    ) {
        String ns = namespaceResolver.resolve(namespace);
        return chunkService.chunk(content, strategy, maxChunkSize, overlap, sourceUrl, ns);
    }
}
