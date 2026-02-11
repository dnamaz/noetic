package com.noetic.websearch.adapter.cli;

import com.noetic.websearch.model.FetchResult;
import com.noetic.websearch.service.CrawlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Component
@Command(name = "crawl", description = "Fetch and extract content from a web page")
public class CrawlCommand implements Runnable {

    private final CrawlService crawlService;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Parameters(index = "0", description = "URL to crawl")
    private String url;

    @Option(names = "--fetch-mode", defaultValue = "auto")
    private String fetchMode;

    @Option(names = "--output-format", defaultValue = "markdown")
    private String outputFormat;

    @Option(names = "--include-links")
    private boolean includeLinks;

    @Option(names = "--include-images")
    private boolean includeImages;

    public CrawlCommand(CrawlService crawlService) {
        this.crawlService = crawlService;
    }

    @Override
    public void run() {
        try {
            FetchResult result = crawlService.crawl(url, fetchMode, null,
                    includeLinks, includeImages, null);
            System.out.println(mapper.writeValueAsString(result));
        } catch (Exception e) {
            System.err.println("Crawl failed: " + e.getMessage());
        }
    }
}
