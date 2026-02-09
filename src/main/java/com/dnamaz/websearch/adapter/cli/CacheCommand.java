package com.dnamaz.websearch.adapter.cli;

import com.dnamaz.websearch.model.VectorMatch;
import com.dnamaz.websearch.service.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Component
@Command(name = "cache", description = "Search the local vector cache")
public class CacheCommand implements Runnable {

    private final CacheService cacheService;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Parameters(index = "0", description = "Search query")
    private String query;

    @Option(names = "--top-k", defaultValue = "5")
    private int topK;

    @Option(names = "--threshold", defaultValue = "0.0")
    private float similarityThreshold;

    public CacheCommand(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public void run() {
        try {
            List<VectorMatch> matches = cacheService.query(query, topK, similarityThreshold, "default");
            System.out.println(mapper.writeValueAsString(matches));
        } catch (Exception e) {
            System.err.println("Cache query failed: " + e.getMessage());
        }
    }
}
