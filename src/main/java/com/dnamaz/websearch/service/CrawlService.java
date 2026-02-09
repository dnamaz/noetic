package com.dnamaz.websearch.service;

import com.dnamaz.websearch.model.*;
import com.dnamaz.websearch.provider.FetcherResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates single-page crawling.
 * Delegates to FetcherResolver for provider selection.
 */
@Service
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);

    private final FetcherResolver fetcherResolver;

    public CrawlService(FetcherResolver fetcherResolver) {
        this.fetcherResolver = fetcherResolver;
    }

    public FetchResult crawl(String url, String fetchMode, OutputFormat outputFormat,
                              boolean includeLinks, boolean includeImages,
                              String waitForSelector) {

        FetchRequest request = FetchRequest.builder()
                .url(url)
                .renderJavaScript(!"static".equalsIgnoreCase(fetchMode))
                .waitForNetworkIdle(true)
                .waitForSelector(waitForSelector)
                .includeLinks(includeLinks)
                .includeImages(includeImages)
                .outputFormat(outputFormat != null ? outputFormat : OutputFormat.MARKDOWN)
                .build();

        log.info("Crawling {} with fetchMode={}", url, fetchMode);
        return fetcherResolver.resolve(request, fetchMode);
    }

    public FetchResult crawl(String url) {
        return crawl(url, "auto", OutputFormat.MARKDOWN, false, false, null);
    }
}
