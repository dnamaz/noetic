package com.dnamaz.websearch.adapter.mcp.prompt;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP Prompt: monitor_page
 * Teaches the LLM to detect changes on a web page by comparing cached vs live content.
 */
@Component
public class MonitorPagePrompt {

    @McpTool(name = "prompt_monitor_page",
            description = "Get instructions for monitoring a web page for changes. "
                    + "Compares cached content with a fresh crawl to detect updates.")
    public Map<String, String> monitorPage(
            @McpToolParam(description = "URL to monitor") String url
    ) {
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

        return Map.of(
                "role", "system",
                "instructions", instructions,
                "url", url
        );
    }
}
