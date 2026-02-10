package com.dnamaz.websearch.adapter.mcp;

import com.dnamaz.websearch.model.*;
import com.dnamaz.websearch.service.NamespaceResolver;
import com.dnamaz.websearch.service.WebSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP tool: web_search
 * Searches the internet for a query and returns ranked results.
 */
@Configuration
public class WebSearchMcpTool {

    @Bean
    McpServerFeatures.SyncToolSpecification webSearchTool(
            WebSearchService webSearchService,
            NamespaceResolver namespaceResolver,
            ObjectMapper objectMapper) {

        var schema = """
                {
                  "type": "object",
                  "properties": {
                    "query": { "type": "string", "description": "The search query" },
                    "maxResults": { "type": "integer", "description": "Max results to return (1-100)" },
                    "freshness": { "type": "string", "description": "Recency filter: day, week, month, year" },
                    "language": { "type": "string", "description": "ISO language code (e.g. en, fr)" },
                    "includeDomains": { "type": "array", "items": { "type": "string" }, "description": "Only include results from these domains" },
                    "namespace": { "type": "string", "description": "Project namespace for cache isolation" }
                  },
                  "required": ["query"]
                }
                """;

        return new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("web_search")
                        .description("Search the internet for a query. Returns a ranked list of results with "
                                + "titles, URLs, and snippets. Results are cached -- subsequent similar "
                                + "queries may return cached results. Use 'freshness' to filter by recency. "
                                + "Use 'includeDomains' to restrict to specific sites.")
                        .inputSchema(McpToolHelper.parseSchema(schema))
                        .build(),
                (exchange, args) -> {
                    var query = (String) args.get("query");
                    var maxResults = args.get("maxResults") instanceof Number n ? n.intValue() : null;
                    var freshness = (String) args.get("freshness");
                    var language = (String) args.get("language");
                    @SuppressWarnings("unchecked")
                    var includeDomains = (List<String>) args.get("includeDomains");
                    var namespace = (String) args.get("namespace");

                    String ns = namespaceResolver.resolve(namespace);
                    var result = webSearchService.search(SearchRequest.builder()
                            .query(query)
                            .maxResults(maxResults)
                            .freshness(Freshness.parse(freshness))
                            .language(language)
                            .includeDomains(includeDomains)
                            .build(), ns);

                    return McpToolHelper.toResult(objectMapper, result);
                }
        );
    }
}
