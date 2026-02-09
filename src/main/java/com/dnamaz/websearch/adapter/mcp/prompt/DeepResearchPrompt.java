package com.dnamaz.websearch.adapter.mcp.prompt;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP Prompt: deep_research
 * Teaches the LLM to search, crawl, cache, and synthesize a comprehensive answer.
 */
@Component
public class DeepResearchPrompt {

    @McpTool(name = "prompt_deep_research",
            description = "Get instructions for comprehensive research on a topic. "
                    + "Chains web_search, crawl_page, chunk_content, and cache_query "
                    + "to build a thorough, cited synthesis.")
    public Map<String, String> deepResearch(
            @McpToolParam(description = "Research topic or question") String topic,
            @McpToolParam(description = "Number of sources to analyze (default 5)", required = false) Integer numSources
    ) {
        int n = numSources != null ? numSources : 5;
        String instructions = """
                You are a research assistant. Follow these steps:

                1. Call `web_search` with query="%s" and maxResults=%d.
                2. For each search result URL, call `crawl_page` to get the full content.
                3. For each crawled page, call `chunk_content` with strategy="sentence"
                   to cache it in the vector store.
                4. After processing all sources, call `cache_query` with the original topic
                   to retrieve the most relevant cached chunks.
                5. Synthesize a comprehensive answer citing specific sources by URL.
                6. List all sources at the end with their titles and URLs.
                """.formatted(topic, n);

        return Map.of(
                "role", "system",
                "instructions", instructions,
                "topic", topic,
                "numSources", String.valueOf(n)
        );
    }
}
