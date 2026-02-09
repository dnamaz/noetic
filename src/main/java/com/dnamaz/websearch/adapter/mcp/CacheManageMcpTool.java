package com.dnamaz.websearch.adapter.mcp;

import com.dnamaz.websearch.service.EvictionService;
import com.dnamaz.websearch.service.EvictionService.EvictionResult;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tools: cache_evict, cache_flush
 * Cache maintenance operations for evicting expired entries or flushing the entire cache.
 */
@Component
public class CacheManageMcpTool {

    private final EvictionService evictionService;

    public CacheManageMcpTool(EvictionService evictionService) {
        this.evictionService = evictionService;
    }

    @McpTool(name = "cache_evict", description = "Run TTL-based eviction to remove expired entries "
            + "from the vector cache. Removes search results older than 24h, query cache older than "
            + "6h, and crawl chunks older than 7d. Same as the scheduled hourly eviction.")
    public EvictionResult evict() {
        return evictionService.runEviction();
    }

    @McpTool(name = "cache_flush", description = "Delete ALL entries from the vector cache. "
            + "This is a destructive operation that wipes the entire cache. Use when you want "
            + "a clean slate or to free disk space.")
    public EvictionResult flush() {
        return evictionService.flushAll();
    }
}
