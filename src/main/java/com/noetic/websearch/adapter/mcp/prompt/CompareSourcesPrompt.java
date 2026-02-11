package com.noetic.websearch.adapter.mcp.prompt;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP Prompt: compare_sources
 * Teaches the LLM to crawl multiple URLs, extract the same schema, and compare.
 */
@Configuration
public class CompareSourcesPrompt {

    @Bean
    McpServerFeatures.SyncPromptSpecification compareSourcesPromptSpec() {
        return new McpServerFeatures.SyncPromptSpecification(
                new McpSchema.Prompt("compare_sources",
                        "Get instructions for comparing structured data across multiple "
                                + "web pages using a consistent schema.",
                        List.of(
                                new McpSchema.PromptArgument("urls", "Comma-separated URLs to compare", true),
                                new McpSchema.PromptArgument("schema", "JSON schema to extract from each page", true)
                        )),
                (exchange, request) -> {
                    String urlsStr = (String) request.arguments().get("urls");
                    String schema = (String) request.arguments().get("schema");

                    String instructions = """
                            You are a comparative analysis assistant. Follow these steps:

                            1. For each of the following URLs, call `crawl_page` with fetchMode="auto":
                               %s
                            2. From each page's content, extract data matching this JSON schema:
                               %s
                            3. Present the extracted data in a comparison table.
                            4. Highlight key differences and similarities between sources.
                            5. Optionally call `chunk_content` for each extraction to cache the results.
                            """.formatted(urlsStr, schema);

                    return new GetPromptResult(
                            "Compare sources workflow",
                            List.of(new PromptMessage(Role.ASSISTANT, new TextContent(instructions)))
                    );
                }
        );
    }
}
