package com.dnamaz.websearch.adapter.cli;

import com.dnamaz.websearch.model.Freshness;
import com.dnamaz.websearch.model.SearchRequest;
import com.dnamaz.websearch.model.SearchResponse;
import com.dnamaz.websearch.service.WebSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Component
@Command(name = "search", description = "Search the internet for a query")
public class SearchCommand implements Runnable {

    private final WebSearchService webSearchService;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Parameters(index = "0", description = "Search query")
    private String query;

    @Option(names = "--max-results", defaultValue = "10")
    private int maxResults;

    @Option(names = "--freshness", description = "day, week, month, year")
    private String freshness;

    @Option(names = "--language")
    private String language;

    @Option(names = "--include-domains", split = ",")
    private List<String> includeDomains;

    public SearchCommand(WebSearchService webSearchService) {
        this.webSearchService = webSearchService;
    }

    @Override
    public void run() {
        try {
            SearchResponse response = webSearchService.search(SearchRequest.builder()
                    .query(query)
                    .maxResults(maxResults)
                    .freshness(Freshness.parse(freshness))
                    .language(language)
                    .includeDomains(includeDomains)
                    .build(), "default");
            System.out.println(mapper.writeValueAsString(response));
        } catch (Exception e) {
            System.err.println("Search failed: " + e.getMessage());
        }
    }
}
