package com.dnamaz.websearch.adapter.cli;

import com.dnamaz.websearch.provider.VectorStore;
import com.dnamaz.websearch.provider.store.LuceneVectorStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.util.Map;

@Component
@Command(name = "cache-promote", description = "Promote agent cache entries to the shared main index")
public class CachePromoteCommand implements Runnable {

    private final VectorStore vectorStore;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public CachePromoteCommand(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run() {
        try {
            if (vectorStore instanceof LuceneVectorStore lucene) {
                int promoted = lucene.promoteToShared();
                System.out.println(mapper.writeValueAsString(
                        Map.of("promoted", promoted, "status", promoted > 0 ? "ok" : "nothing_to_promote")));
            } else {
                System.out.println(mapper.writeValueAsString(
                        Map.of("promoted", 0, "status", "not_supported",
                                "message", "Promote is only supported with the Lucene vector store")));
            }
        } catch (Exception e) {
            System.err.println("Promote failed: " + e.getMessage());
        }
    }
}
