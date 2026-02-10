package com.dnamaz.websearch.adapter.mcp.prompt;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP Prompt: extract_structured_data
 * Teaches the LLM to crawl a page and extract structured JSON per a user-defined schema.
 */
@Configuration
public class ExtractStructuredDataPrompt {

    @Bean
    McpServerFeatures.SyncPromptSpecification extractStructuredDataPromptSpec() {
        return new McpServerFeatures.SyncPromptSpecification(
                new McpSchema.Prompt("extract_structured_data",
                        "Get instructions for extracting structured data from a web page "
                                + "into a JSON schema you define. Returns a workflow that chains "
                                + "crawl_page with your extraction schema.",
                        List.of(
                                new McpSchema.PromptArgument("url", "URL to extract from", true),
                                new McpSchema.PromptArgument("schema", "JSON schema describing desired output structure", true)
                        )),
                (exchange, request) -> {
                    String url = (String) request.arguments().get("url");
                    String schema = (String) request.arguments().get("schema");

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

                    return new GetPromptResult(
                            "Extract structured data from: " + url,
                            List.of(new PromptMessage(Role.ASSISTANT, new TextContent(instructions)))
                    );
                }
        );
    }
}
