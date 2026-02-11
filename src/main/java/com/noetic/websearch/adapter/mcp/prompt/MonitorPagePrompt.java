package com.noetic.websearch.adapter.mcp.prompt;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP Prompt: monitor_page
 * Teaches the LLM to detect changes on a web page by comparing cached vs live content.
 */
@Configuration
public class MonitorPagePrompt {

    @Bean
    McpServerFeatures.SyncPromptSpecification monitorPagePromptSpec() {
        return new McpServerFeatures.SyncPromptSpecification(
                new McpSchema.Prompt("monitor_page",
                        "Get instructions for monitoring a web page for changes. "
                                + "Compares cached content with a fresh crawl to detect updates.",
                        List.of(
                                new McpSchema.PromptArgument("url", "URL to monitor", true)
                        )),
                (exchange, request) -> {
                    String url = (String) request.arguments().get("url");

                    String instructions = """
                            You are a page monitoring assistant. Follow these steps:

                            1. Call `cache_query` with the URL "%s" to find any previously cached
                               content from this page.
                            2. Call `crawl_page` with url="%s" and fetchMode="auto" to get the current content.
                            3. Compare the cached content (if any) with the freshly crawled content.
                            4. Report what has changed:
                               - New sections or content added
                               - Content that was removed
                               - Content that was modified
                            5. Call `chunk_content` with the new content to update the cache.
                            6. If no previous cache exists, report this is the first crawl and cache the content.
                            """.formatted(url, url);

                    return new GetPromptResult(
                            "Monitor page: " + url,
                            List.of(new PromptMessage(Role.ASSISTANT, new TextContent(instructions)))
                    );
                }
        );
    }
}
