package com.dnamaz.websearch.adapter.rest;

import com.dnamaz.websearch.model.VectorMatch;
import com.dnamaz.websearch.service.CacheService;
import com.dnamaz.websearch.service.EvictionService;
import com.dnamaz.websearch.service.EvictionService.EvictionResult;
import com.dnamaz.websearch.service.NamespaceResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST adapter for vector cache query and maintenance operations.
 */
@RestController
@RequestMapping("/api/v1")
public class CacheController {

    private final CacheService cacheService;
    private final EvictionService evictionService;
    private final NamespaceResolver namespaceResolver;

    public CacheController(CacheService cacheService, EvictionService evictionService,
                            NamespaceResolver namespaceResolver) {
        this.cacheService = cacheService;
        this.evictionService = evictionService;
        this.namespaceResolver = namespaceResolver;
    }

    @PostMapping("/cache")
    public List<VectorMatch> cacheQuery(@RequestBody Map<String, Object> body,
                                         HttpServletRequest httpRequest) {
        String query = (String) body.get("query");
        Integer topK = (Integer) body.get("topK");
        Float similarityThreshold = body.get("similarityThreshold") != null
                ? ((Number) body.get("similarityThreshold")).floatValue() : null;
        String namespace = (String) body.get("namespace");

        String ns = namespaceResolver.resolve(namespace, httpRequest);
        return cacheService.query(query, topK, similarityThreshold, ns);
    }

    /** Trigger TTL-based eviction (same as the scheduled job). */
    @PostMapping("/cache/evict")
    public EvictionResult evict() {
        return evictionService.runEviction();
    }

    /** Delete ALL entries from the cache. */
    @DeleteMapping("/cache")
    public EvictionResult flush() {
        return evictionService.flushAll();
    }
}
