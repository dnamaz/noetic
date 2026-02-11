package com.noetic.websearch.service;

import com.noetic.websearch.model.FetchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Orchestrates multi-page crawling with sitemap discovery, concurrency
 * control, rate limiting, and automatic chunking/caching.
 */
@Service
public class BatchCrawlService {

    private static final Logger log = LoggerFactory.getLogger(BatchCrawlService.class);

    private final CrawlService crawlService;
    private final ChunkService chunkService;
    private final SitemapParser sitemapParser;
    private final int defaultMaxConcurrency;
    private final long defaultRateLimitMs;
    private final int defaultMaxUrls;
    private final boolean autoChunk;
    private final String chunkStrategy;

    public BatchCrawlService(
            CrawlService crawlService,
            ChunkService chunkService,
            SitemapParser sitemapParser,
            @Value("${websearch.batch-crawl.max-concurrency:3}") int defaultMaxConcurrency,
            @Value("${websearch.batch-crawl.rate-limit-ms:1000}") long defaultRateLimitMs,
            @Value("${websearch.batch-crawl.max-urls:100}") int defaultMaxUrls,
            @Value("${websearch.batch-crawl.auto-chunk:true}") boolean autoChunk,
            @Value("${websearch.batch-crawl.chunk-strategy:sentence}") String chunkStrategy
    ) {
        this.crawlService = crawlService;
        this.chunkService = chunkService;
        this.sitemapParser = sitemapParser;
        this.defaultMaxConcurrency = defaultMaxConcurrency;
        this.defaultRateLimitMs = defaultRateLimitMs;
        this.defaultMaxUrls = defaultMaxUrls;
        this.autoChunk = autoChunk;
        this.chunkStrategy = chunkStrategy;
    }

    public SitemapParser.SitemapResult discoverSitemap(String domain, Integer maxUrls,
                                                        String pathFilter) {
        int max = maxUrls != null ? maxUrls : defaultMaxUrls;
        return sitemapParser.discover(domain, max, pathFilter);
    }

    public BatchCrawlResult batchCrawl(List<String> urls, String domain,
                                        String fetchMode, String chunkStrat,
                                        Integer maxConcurrency, Long rateLimitMs,
                                        String pathFilter, Integer maxUrls) {

        Instant start = Instant.now();

        // If domain is provided, discover URLs first
        List<String> targetUrls = urls;
        if (domain != null && !domain.isBlank()) {
            int max = maxUrls != null ? maxUrls : defaultMaxUrls;
            SitemapParser.SitemapResult sitemap = sitemapParser.discover(domain, max, pathFilter);
            targetUrls = sitemap.discoveredUrls();
        }

        if (targetUrls == null || targetUrls.isEmpty()) {
            return new BatchCrawlResult(0, 0, 0, 0, List.of(),
                    Duration.between(start, Instant.now()));
        }

        int concurrency = maxConcurrency != null ? maxConcurrency : defaultMaxConcurrency;
        long rateLimit = rateLimitMs != null ? rateLimitMs : defaultRateLimitMs;
        String strategy = chunkStrat != null ? chunkStrat : chunkStrategy;

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        Semaphore rateLimiter = new Semaphore(1);

        List<CrawlError> errors = new CopyOnWriteArrayList<>();
        var crawledCount = new java.util.concurrent.atomic.AtomicInteger(0);
        var chunkedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        var failedCount = new java.util.concurrent.atomic.AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (String url : targetUrls) {
            futures.add(executor.submit(() -> {
                try {
                    // Rate limiting
                    rateLimiter.acquire();
                    try {
                        FetchResult result = crawlService.crawl(url, fetchMode, null,
                                false, false, null);
                        crawledCount.incrementAndGet();

                        // Auto-chunk and cache
                        if (autoChunk && result.content() != null && !result.content().isBlank()) {
                            chunkService.chunk(result.content(), strategy, null, null, url, "default");
                            chunkedCount.incrementAndGet();
                        }
                    } finally {
                        // Delay before releasing for next request
                        Thread.sleep(rateLimit);
                        rateLimiter.release();
                    }
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                    errors.add(new CrawlError(url, e.getMessage()));
                    log.warn("Batch crawl failed for {}: {}", url, e.getMessage());
                }
            }));
        }

        // Wait for all to complete
        for (Future<?> future : futures) {
            try {
                future.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Batch crawl task timed out or failed: {}", e.getMessage());
            }
        }

        executor.shutdown();

        Duration elapsed = Duration.between(start, Instant.now());
        log.info("Batch crawl complete: {} total, {} crawled, {} chunked, {} failed in {}s",
                targetUrls.size(), crawledCount.get(), chunkedCount.get(),
                failedCount.get(), elapsed.toSeconds());

        return new BatchCrawlResult(
                targetUrls.size(),
                crawledCount.get(),
                failedCount.get(),
                chunkedCount.get(),
                errors,
                elapsed
        );
    }

    public record BatchCrawlResult(
            int totalUrls,
            int crawled,
            int failed,
            int chunked,
            List<CrawlError> errors,
            Duration elapsedTime
    ) {}

    public record CrawlError(String url, String reason) {}
}
