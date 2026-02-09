package com.dnamaz.websearch.adapter.mcp.prompt;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP Prompt: extract_structured_data
 * Teaches the LLM to crawl a page and extract structured JSON per a user-defined schema.
 */
@Component
public class ExtractStructuredDataPrompt {

    @McpTool(name = "prompt_extract_structured_data",
            description = "Get instructions for extracting structured data from a web page "
                    + "into a JSON schema you define. Returns a workflow that chains crawl_page "
                    + "with your extraction schema.")
    public Map<String, String> extractStructuredData(
            @McpToolParam(description = "URL to extract from") String url,
            @McpToolParam(description = "JSON schema describing desired output structure") String schema
    ) {
        String instructions = """
                You are a structured data extraction assistant. Follow these steps exactly:

                1. Call `crawl_page` with url="%s" and fetchMode="auto" to get the page content.
                2. Analyze the returned content carefully.
                3. Extract data matching this JSON schema:
                %s
                4. Return ONLY valid JSON conforming to the schema.
                5. For fields you cannot find in the content, use null.
                6. Optionally call `chunk_content` to cache the extracted data for future retrieval.
                """.formatted(url, schema);

        return Map.of(
                "role", "system",
                "instructions", instructions,
                "url", url,
                "schema", schema
        );
    }
}
