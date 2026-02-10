package com.dnamaz.websearch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;

import java.util.List;

/**
 * Configures the MCP server using the official Model Context Protocol Java SDK.
 *
 * <p>Creates an {@link McpSyncServer} with the appropriate transport (STDIO or SSE)
 * and registers all tool and prompt specifications collected from Spring beans.</p>
 *
 * <p>Only active when the adapter mode is {@code mcp} (the default).</p>
 */
@Lazy(false)
@Configuration
@ConditionalOnProperty(name = "websearch.adapter.default-mode", havingValue = "mcp", matchIfMissing = true)
public class McpServerConfig {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfig.class);

    @Value("${websearch.adapter.mcp.transport:stdio}")
    private String transport;

    private final List<McpServerFeatures.SyncToolSpecification> toolSpecs;
    private final List<McpServerFeatures.SyncPromptSpecification> promptSpecs;
    private final ObjectMapper objectMapper;

    private McpSyncServer mcpServer;

    public McpServerConfig(
            List<McpServerFeatures.SyncToolSpecification> toolSpecs,
            List<McpServerFeatures.SyncPromptSpecification> promptSpecs,
            ObjectMapper objectMapper) {
        this.toolSpecs = toolSpecs;
        this.promptSpecs = promptSpecs;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        log.info("Starting MCP server (transport={}, tools={}, prompts={})",
                transport, toolSpecs.size(), promptSpecs.size());

        var jsonMapper = new JacksonMcpJsonMapper(objectMapper);

        var transportProvider = switch (transport) {
            case "stdio" -> new StdioServerTransportProvider(jsonMapper);
            default -> throw new IllegalArgumentException(
                    "Unsupported MCP transport: " + transport + ". Supported: stdio");
        };

        var capabilities = ServerCapabilities.builder()
                .tools(true)
                .prompts(true)
                .logging()
                .build();

        mcpServer = McpServer.sync(transportProvider)
                .serverInfo("noetic", "0.1.0")
                .capabilities(capabilities)
                .tools(toolSpecs)
                .prompts(promptSpecs)
                .build();

        log.info("MCP server started successfully");
    }

    @PreDestroy
    public void stop() {
        if (mcpServer != null) {
            log.info("Shutting down MCP server");
            mcpServer.close();
        }
    }

    /**
     * Ensure an ObjectMapper is available even under lazy initialization.
     * Defined in a nested static class to avoid circular dependency with
     * tool beans that depend on ObjectMapper.
     */
    @Lazy(false)
    @Configuration
    static class JacksonConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }
}
