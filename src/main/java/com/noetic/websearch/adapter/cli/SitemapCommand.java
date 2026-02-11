package com.noetic.websearch.adapter.cli;

import com.noetic.websearch.service.BatchCrawlService;
import com.noetic.websearch.service.SitemapParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Component
@Command(name = "sitemap", description = "Discover crawlable URLs from a domain")
public class SitemapCommand implements Runnable {

    private final BatchCrawlService batchCrawlService;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Parameters(index = "0", description = "Domain to discover")
    private String domain;

    @Option(names = "--max-urls", defaultValue = "100")
    private int maxUrls;

    @Option(names = "--path-filter")
    private String pathFilter;

    public SitemapCommand(BatchCrawlService batchCrawlService) {
        this.batchCrawlService = batchCrawlService;
    }

    @Override
    public void run() {
        try {
            SitemapParser.SitemapResult result = batchCrawlService.discoverSitemap(
                    domain, maxUrls, pathFilter);
            System.out.println(mapper.writeValueAsString(result));
        } catch (Exception e) {
            System.err.println("Sitemap discovery failed: " + e.getMessage());
        }
    }
}
