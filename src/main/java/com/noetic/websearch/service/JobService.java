package com.noetic.websearch.service;

import com.noetic.websearch.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages async batch crawl jobs with status tracking and cancellation.
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final BatchCrawlService batchCrawlService;
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public JobService(BatchCrawlService batchCrawlService) {
        this.batchCrawlService = batchCrawlService;
    }

    /**
     * Submit a batch crawl as an async job.
     */
    public String submit(List<String> urls, String domain, String fetchMode,
                          String chunkStrategy, Integer maxConcurrency, Long rateLimitMs,
                          String pathFilter, Integer maxUrls) {

        String jobId = UUID.randomUUID().toString();
        JobState state = new JobState(jobId, Instant.now());
        jobs.put(jobId, state);

        executor.submit(() -> {
            state.state = JobStatus.State.RUNNING;
            try {
                BatchCrawlService.BatchCrawlResult result = batchCrawlService.batchCrawl(
                        urls, domain, fetchMode, chunkStrategy,
                        maxConcurrency, rateLimitMs, pathFilter, maxUrls);

                state.totalUrls = result.totalUrls();
                state.crawled.set(result.crawled());
                state.failed.set(result.failed());
                state.chunked.set(result.chunked());
                result.errors().forEach(e -> state.errors.add(e.url() + ": " + e.reason()));
                state.state = JobStatus.State.COMPLETED;

                log.info("Job {} completed: {} crawled, {} failed", jobId,
                        result.crawled(), result.failed());
            } catch (Exception e) {
                state.state = JobStatus.State.FAILED;
                state.errors.add("Job failed: " + e.getMessage());
                log.error("Job {} failed: {}", jobId, e.getMessage());
            }
        });

        log.info("Submitted job {}", jobId);
        return jobId;
    }

    /**
     * Get the status of a job.
     */
    public JobStatus getStatus(String jobId) {
        JobState state = jobs.get(jobId);
        if (state == null) {
            return null;
        }
        return new JobStatus(
                state.jobId, state.state, state.totalUrls,
                state.crawled.get(), state.failed.get(), state.chunked.get(),
                List.copyOf(state.errors), state.startedAt,
                Duration.between(state.startedAt, Instant.now())
        );
    }

    /**
     * Cancel a running job (best-effort).
     */
    public boolean cancel(String jobId) {
        JobState state = jobs.get(jobId);
        if (state == null) return false;
        state.state = JobStatus.State.CANCELLED;
        log.info("Job {} cancelled", jobId);
        return true;
    }

    /**
     * List all job IDs.
     */
    public List<String> listJobs() {
        return List.copyOf(jobs.keySet());
    }

    private static class JobState {
        final String jobId;
        final Instant startedAt;
        volatile JobStatus.State state = JobStatus.State.PENDING;
        volatile int totalUrls;
        final AtomicInteger crawled = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final AtomicInteger chunked = new AtomicInteger();
        final List<String> errors = new CopyOnWriteArrayList<>();

        JobState(String jobId, Instant startedAt) {
            this.jobId = jobId;
            this.startedAt = startedAt;
        }
    }
}
