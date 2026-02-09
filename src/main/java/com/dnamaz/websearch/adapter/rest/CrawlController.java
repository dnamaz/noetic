package com.dnamaz.websearch.adapter.rest;

import com.dnamaz.websearch.model.FetchResult;
import com.dnamaz.websearch.model.JobStatus;
import com.dnamaz.websearch.model.OutputFormat;
import com.dnamaz.websearch.service.BatchCrawlService;
import com.dnamaz.websearch.service.CrawlService;
import com.dnamaz.websearch.service.JobService;
import com.dnamaz.websearch.service.SitemapParser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST adapter for crawl, sitemap discovery, batch crawl, and job management.
 */
@RestController
@RequestMapping("/api/v1")
public class CrawlController {

    private final CrawlService crawlService;
    private final BatchCrawlService batchCrawlService;
    private final JobService jobService;

    public CrawlController(CrawlService crawlService, BatchCrawlService batchCrawlService,
                            JobService jobService) {
        this.crawlService = crawlService;
        this.batchCrawlService = batchCrawlService;
        this.jobService = jobService;
    }

    @PostMapping("/crawl")
    public FetchResult crawl(@RequestBody Map<String, Object> body) {
        String url = (String) body.get("url");
        String fetchMode = (String) body.getOrDefault("fetchMode", "auto");
        String format = (String) body.get("outputFormat");
        boolean includeLinks = Boolean.TRUE.equals(body.get("includeLinks"));
        boolean includeImages = Boolean.TRUE.equals(body.get("includeImages"));
        String waitForSelector = (String) body.get("waitForSelector");

        OutputFormat outputFormat = null;
        if (format != null) {
            try { outputFormat = OutputFormat.valueOf(format.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        return crawlService.crawl(url, fetchMode, outputFormat, includeLinks,
                includeImages, waitForSelector);
    }

    @PostMapping("/sitemap")
    public SitemapParser.SitemapResult discoverSitemap(@RequestBody Map<String, Object> body) {
        String domain = (String) body.get("domain");
        Integer maxUrls = (Integer) body.get("maxUrls");
        String pathFilter = (String) body.get("pathFilter");
        return batchCrawlService.discoverSitemap(domain, maxUrls, pathFilter);
    }

    @PostMapping("/batch-crawl")
    @SuppressWarnings("unchecked")
    public BatchCrawlService.BatchCrawlResult batchCrawl(@RequestBody Map<String, Object> body) {
        List<String> urls = (List<String>) body.get("urls");
        String domain = (String) body.get("domain");
        String fetchMode = (String) body.get("fetchMode");
        String chunkStrategy = (String) body.get("chunkStrategy");
        Integer maxConcurrency = (Integer) body.get("maxConcurrency");
        Long rateLimitMs = body.get("rateLimitMs") != null
                ? ((Number) body.get("rateLimitMs")).longValue() : null;
        String pathFilter = (String) body.get("pathFilter");
        Integer maxUrls = (Integer) body.get("maxUrls");

        return batchCrawlService.batchCrawl(urls, domain, fetchMode, chunkStrategy,
                maxConcurrency, rateLimitMs, pathFilter, maxUrls);
    }

    // -- Async Job Management --

    @PostMapping("/jobs")
    @SuppressWarnings("unchecked")
    public Map<String, String> submitJob(@RequestBody Map<String, Object> body) {
        List<String> urls = (List<String>) body.get("urls");
        String domain = (String) body.get("domain");
        String fetchMode = (String) body.get("fetchMode");
        String chunkStrategy = (String) body.get("chunkStrategy");
        Integer maxConcurrency = (Integer) body.get("maxConcurrency");
        Long rateLimitMs = body.get("rateLimitMs") != null
                ? ((Number) body.get("rateLimitMs")).longValue() : null;
        String pathFilter = (String) body.get("pathFilter");
        Integer maxUrls = (Integer) body.get("maxUrls");

        String jobId = jobService.submit(urls, domain, fetchMode, chunkStrategy,
                maxConcurrency, rateLimitMs, pathFilter, maxUrls);
        return Map.of("jobId", jobId);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobStatus> getJobStatus(@PathVariable String jobId) {
        JobStatus status = jobService.getStatus(jobId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    @DeleteMapping("/jobs/{jobId}")
    public Map<String, Object> cancelJob(@PathVariable String jobId) {
        boolean cancelled = jobService.cancel(jobId);
        return Map.of("jobId", jobId, "cancelled", cancelled);
    }

    @GetMapping("/jobs")
    public List<String> listJobs() {
        return jobService.listJobs();
    }
}
