package com.noetic.websearch.adapter.mcp.prompt;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP Prompt: deep_research
 * Teaches the LLM to search, crawl, cache, and synthesize a comprehensive answer.
 */
@Configuration
public class DeepResearchPrompt {

    @Bean
    McpServerFeatures.SyncPromptSpecification deepResearchPromptSpec() {
        return new McpServerFeatures.SyncPromptSpecification(
                new McpSchema.Prompt("deep_research",
                        "Get instructions for comprehensive research on a topic. Chains "
                                + "web_search, crawl_page, chunk_content, and cache_query to build "
                                + "a thorough, cited synthesis.",
                        List.of(
                                new McpSchema.PromptArgument("topic", "Research topic or question", true),
                                new McpSchema.PromptArgument("numSources", "Number of sources to analyze (default 5)", false)
                        )),
                (exchange, request) -> {
                    String topic = (String) request.arguments().get("topic");
                    String numSourcesStr = (String) request.arguments().get("numSources");
                    int n = numSourcesStr != null ? Integer.parseInt(numSourcesStr) : 5;

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

                    return new GetPromptResult(
                            "Deep research workflow for: " + topic,
                            List.of(new PromptMessage(Role.ASSISTANT, new TextContent(instructions)))
                    );
                }
        );
    }
}
