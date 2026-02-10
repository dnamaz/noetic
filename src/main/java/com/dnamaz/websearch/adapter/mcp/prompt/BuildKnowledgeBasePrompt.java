package com.dnamaz.websearch.adapter.mcp.prompt;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP Prompt: build_knowledge_base
 * Teaches the LLM to exhaustively search, crawl, and chunk a topic into the vector cache.
 */
@Configuration
public class BuildKnowledgeBasePrompt {

    @Bean
    McpServerFeatures.SyncPromptSpecification buildKnowledgeBasePromptSpec() {
        return new McpServerFeatures.SyncPromptSpecification(
                new McpSchema.Prompt("build_knowledge_base",
                        "Get instructions for building a comprehensive knowledge base on a "
                                + "topic by searching, crawling, and caching multiple sources.",
                        List.of(
                                new McpSchema.PromptArgument("topic", "Topic to build knowledge base for", true),
                                new McpSchema.PromptArgument("numSources", "Number of sources (default 10)", false)
                        )),
                (exchange, request) -> {
                    String topic = (String) request.arguments().get("topic");
                    String numSourcesStr = (String) request.arguments().get("numSources");
                    int n = numSourcesStr != null ? Integer.parseInt(numSourcesStr) : 10;

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

                    return new GetPromptResult(
                            "Build knowledge base for: " + topic,
                            List.of(new PromptMessage(Role.ASSISTANT, new TextContent(instructions)))
                    );
                }
        );
    }
}
