package com.dnamaz.websearch.adapter.mcp;

import com.dnamaz.websearch.model.JobStatus;
import com.dnamaz.websearch.service.JobService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP tool: job_status
 * Check status of an async batch crawl job, or cancel it.
 */
@Component
public class JobStatusMcpTool {

    private final JobService jobService;

    public JobStatusMcpTool(JobService jobService) {
        this.jobService = jobService;
    }

    @McpTool(name = "job_status", description = "Check the status of an async batch crawl job. "
            + "Returns progress (crawled, failed, chunked counts) and state (PENDING, RUNNING, "
            + "COMPLETED, CANCELLED, FAILED).")
    public JobStatus jobStatus(
            @McpToolParam(description = "The job ID returned by batch_crawl") String jobId
    ) {
        JobStatus status = jobService.getStatus(jobId);
        if (status == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        return status;
    }

    @McpTool(name = "job_cancel", description = "Cancel a running batch crawl job.")
    public Map<String, Object> jobCancel(
            @McpToolParam(description = "The job ID to cancel") String jobId
    ) {
        boolean cancelled = jobService.cancel(jobId);
        return Map.of("jobId", jobId, "cancelled", cancelled);
    }
}
