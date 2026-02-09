package com.dnamaz.websearch.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Status of an async batch crawl job.
 */
public record JobStatus(
        String jobId,
        State state,
        int totalUrls,
        int crawled,
        int failed,
        int chunked,
        List<String> errors,
        Instant startedAt,
        Duration elapsed
) {
    public enum State {
        PENDING,
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED
    }
}
