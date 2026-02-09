package com.dnamaz.websearch.adapter.cli;

import com.dnamaz.websearch.service.BatchCrawlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

@Component
@Command(name = "batch-crawl", description = "Crawl multiple URLs or an entire domain")
public class BatchCrawlCommand implements Runnable {

    private final BatchCrawlService batchCrawlService;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Option(names = "--urls", split = ",", description = "Comma-separated URLs")
    private List<String> urls;

    @Option(names = "--domain", description = "Domain for sitemap discovery")
    private String domain;

    @Option(names = "--fetch-mode", defaultValue = "auto")
    private String fetchMode;

    @Option(names = "--chunk-strategy", defaultValue = "sentence")
    private String chunkStrategy;

    @Option(names = "--max-concurrency", defaultValue = "3")
    private int maxConcurrency;

    @Option(names = "--rate-limit", defaultValue = "1000")
    private long rateLimitMs;

    @Option(names = "--path-filter")
    private String pathFilter;

    @Option(names = "--max-urls", defaultValue = "100")
    private int maxUrls;

    public BatchCrawlCommand(BatchCrawlService batchCrawlService) {
        this.batchCrawlService = batchCrawlService;
    }

    @Override
    public void run() {
        try {
            BatchCrawlService.BatchCrawlResult result = batchCrawlService.batchCrawl(
                    urls, domain, fetchMode, chunkStrategy,
                    maxConcurrency, rateLimitMs, pathFilter, maxUrls);
            System.out.println(mapper.writeValueAsString(result));
        } catch (Exception e) {
            System.err.println("Batch crawl failed: " + e.getMessage());
        }
    }
}
