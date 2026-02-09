package com.dnamaz.websearch.adapter.mcp.prompt;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP Prompt: compare_sources
 * Teaches the LLM to crawl multiple URLs, extract the same schema, and compare.
 */
@Component
public class CompareSourcesPrompt {

    @McpTool(name = "prompt_compare_sources",
            description = "Get instructions for comparing structured data across multiple "
                    + "web pages using a consistent schema.")
    public Map<String, Object> compareSources(
            @McpToolParam(description = "URLs to compare") List<String> urls,
            @McpToolParam(description = "JSON schema to extract from each page") String schema
    ) {
        String instructions = """
                You are a comparative analysis assistant. Follow these steps:

                1. For each of the following URLs, call `crawl_page` with fetchMode="auto":
                   %s
                2. From each page's content, extract data matching this JSON schema:
                   %s
                3. Present the extracted data in a comparison table.
                4. Highlight key differences and similarities between sources.
                5. Optionally call `chunk_content` for each extraction to cache the results.
                """.formatted(String.join("\n   ", urls), schema);

        return Map.of(
                "role", "system",
                "instructions", instructions,
                "urls", urls,
                "schema", schema
        );
    }
}
