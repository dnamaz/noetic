package com.dnamaz.websearch.adapter.mcp;

import com.dnamaz.websearch.model.JobStatus;
import com.dnamaz.websearch.service.JobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * MCP tools: job_status, job_cancel
 * Check status of an async batch crawl job, or cancel it.
 */
@Configuration
public class JobStatusMcpTool {

    @Bean
    McpServerFeatures.SyncToolSpecification jobStatusTool(
            JobService jobService,
            ObjectMapper objectMapper) {

        var schema = """
                {
                  "type": "object",
                  "properties": {
                    "jobId": { "type": "string", "description": "The job ID returned by batch_crawl" }
                  },
                  "required": ["jobId"]
                }
                """;

        return new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("job_status")
                        .description("Check the status of an async batch crawl job. Returns progress "
                                + "(crawled, failed, chunked counts) and state (PENDING, RUNNING, "
                                + "COMPLETED, CANCELLED, FAILED).")
                        .inputSchema(McpToolHelper.parseSchema(schema))
                        .build(),
                (exchange, args) -> {
                    var jobId = (String) args.get("jobId");
                    JobStatus status = jobService.getStatus(jobId);
                    if (status == null) {
                        return new CallToolResult(
                                List.of(new McpSchema.TextContent("Job not found: " + jobId)), true);
                    }
                    return McpToolHelper.toResult(objectMapper, status);
                }
        );
    }

    @Bean
    McpServerFeatures.SyncToolSpecification jobCancelTool(
            JobService jobService,
            ObjectMapper objectMapper) {

        var schema = """
                {
                  "type": "object",
                  "properties": {
                    "jobId": { "type": "string", "description": "The job ID to cancel" }
                  },
                  "required": ["jobId"]
                }
                """;

        return new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                        .name("job_cancel")
                        .description("Cancel a running batch crawl job.")
                        .inputSchema(McpToolHelper.parseSchema(schema))
                        .build(),
                (exchange, args) -> {
                    var jobId = (String) args.get("jobId");
                    boolean cancelled = jobService.cancel(jobId);
                    return McpToolHelper.toResult(objectMapper,
                            Map.of("jobId", jobId, "cancelled", cancelled));
                }
        );
    }
}
