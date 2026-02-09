package com.dnamaz.websearch.adapter.mcp.prompt;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP Prompt: build_knowledge_base
 * Teaches the LLM to exhaustively search, crawl, and chunk a topic into the vector cache.
 */
@Component
public class BuildKnowledgeBasePrompt {

    @McpTool(name = "prompt_build_knowledge_base",
            description = "Get instructions for building a comprehensive knowledge base on a "
                    + "topic by searching, crawling, and caching multiple sources.")
    public Map<String, String> buildKnowledgeBase(
            @McpToolParam(description = "Topic to build knowledge base for") String topic,
            @McpToolParam(description = "Number of sources (default 10)", required = false) Integer numSources
    ) {
        int n = numSources != null ? numSources : 10;
        String instructions = """
                You are a knowledge base builder. Follow these steps:

                1. Call `web_search` with query="%s" and maxResults=%d.
                2. For EVERY search result URL, call `crawl_page` to get full content.
                3. For each crawled page, call `chunk_content` with strategy="semantic"
                   and sourceUrl set to the page URL.
                4. After all pages are processed, call `cache_query` with "%s"
                   to verify the knowledge base has been populated.
                5. Report a summary: number of pages crawled, chunks stored,
                   and sample topics covered.
                6. The knowledge base is now ready for future `cache_query` calls
                   on this topic.
                """.formatted(topic, n, topic);

        return Map.of(
                "role", "system",
                "instructions", instructions,
                "topic", topic,
                "numSources", String.valueOf(n)
        );
    }
}
