package com.dnamaz.websearch.adapter.cli;

import com.dnamaz.websearch.model.ContentChunk;
import com.dnamaz.websearch.service.ChunkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Command(name = "chunk", description = "Split content into chunks and cache")
public class ChunkCommand implements Runnable {

    private final ChunkService chunkService;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Option(names = "--strategy", defaultValue = "sentence")
    private String strategy;

    @Option(names = "--max-chunk-size", defaultValue = "512")
    private int maxChunkSize;

    @Option(names = "--overlap", defaultValue = "50")
    private int overlap;

    @Option(names = "--source-url")
    private String sourceUrl;

    @Option(names = "--content", description = "Content to chunk (or pipe via stdin)")
    private String content;

    public ChunkCommand(ChunkService chunkService) {
        this.chunkService = chunkService;
    }

    @Override
    public void run() {
        try {
            String text = content;
            if (text == null || text.isBlank()) {
                // Read from stdin
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                text = reader.lines().collect(Collectors.joining("\n"));
            }

            List<ContentChunk> chunks = chunkService.chunk(text, strategy,
                    maxChunkSize, overlap, sourceUrl, "default");
            System.out.println(mapper.writeValueAsString(chunks));
        } catch (Exception e) {
            System.err.println("Chunk failed: " + e.getMessage());
        }
    }
}
