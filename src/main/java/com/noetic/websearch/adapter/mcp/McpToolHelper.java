package com.noetic.websearch.adapter.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.util.List;
import java.util.Map;

/**
 * Utility methods for building MCP tool definitions.
 */
final class McpToolHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpToolHelper() {}

    /**
     * Parse a JSON schema string into a {@link JsonSchema} object suitable for
     * the MCP SDK's {@link McpSchema.Tool} builder.
     */
    @SuppressWarnings("unchecked")
    static JsonSchema parseSchema(String jsonSchema) {
        try {
            var map = MAPPER.readValue(jsonSchema, new TypeReference<Map<String, Object>>() {});
            var type = (String) map.get("type");
            var properties = (Map<String, Object>) map.get("properties");
            var required = (List<String>) map.get("required");
            return new JsonSchema(type, properties, required, null, null, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON schema for MCP tool", e);
        }
    }

    /**
     * Serialize an object to JSON and wrap in a successful {@link CallToolResult}.
     * Handles checked {@code JsonProcessingException} by wrapping as unchecked.
     */
    static CallToolResult toResult(ObjectMapper objectMapper, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return new CallToolResult(List.of(new TextContent(json)), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }
}
