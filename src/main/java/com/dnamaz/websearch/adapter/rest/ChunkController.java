package com.dnamaz.websearch.adapter.rest;

import com.dnamaz.websearch.model.ContentChunk;
import com.dnamaz.websearch.service.ChunkService;
import com.dnamaz.websearch.service.NamespaceResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST adapter for content chunking operations.
 */
@RestController
@RequestMapping("/api/v1")
public class ChunkController {

    private final ChunkService chunkService;
    private final NamespaceResolver namespaceResolver;

    public ChunkController(ChunkService chunkService, NamespaceResolver namespaceResolver) {
        this.chunkService = chunkService;
        this.namespaceResolver = namespaceResolver;
    }

    @PostMapping("/chunk")
    public List<ContentChunk> chunk(@RequestBody Map<String, Object> body,
                                     HttpServletRequest httpRequest) {
        String content = (String) body.get("content");
        String strategy = (String) body.get("strategy");
        Integer maxChunkSize = (Integer) body.get("maxChunkSize");
        Integer overlap = (Integer) body.get("overlap");
        String sourceUrl = (String) body.get("sourceUrl");
        String namespace = (String) body.get("namespace");

        String ns = namespaceResolver.resolve(namespace, httpRequest);
        return chunkService.chunk(content, strategy, maxChunkSize, overlap, sourceUrl, ns);
    }
}
