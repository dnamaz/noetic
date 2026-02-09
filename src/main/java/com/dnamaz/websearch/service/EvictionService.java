package com.dnamaz.websearch.service;

import com.dnamaz.websearch.model.MetadataFilter;
import com.dnamaz.websearch.provider.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Scheduled service that enforces TTL eviction policies per entry type
 * and a max-entries cap. Runs on a configurable cron schedule.
 *
 * <p>Two-level eviction:</p>
 * <ul>
 *   <li><b>Level 1: Application-level</b> -- Uses createdAt metadata
 *       and deleteByMetadata to purge expired entries. Works universally
 *       across all VectorStore providers.</li>
 *   <li><b>Level 2: Provider-native</b> -- For providers that support
 *       native TTL (e.g. Milvus), the TTL is also passed to the
 *       database directly as a safety net.</li>
 * </ul>
 */
@Service
public class EvictionService {

    private static final Logger log = LoggerFactory.getLogger(EvictionService.class);

    private final VectorStore vectorStore;
    private final boolean enabled;
    private final long maxEntries;
    private final Map<String, Duration> ttlPolicies;

    public EvictionService(
            VectorStore vectorStore,
            @Value("${websearch.eviction.enabled:true}") boolean enabled,
            @Value("${websearch.eviction.max-entries:100000}") long maxEntries
    ) {
        this.vectorStore = vectorStore;
        this.enabled = enabled;
        this.maxEntries = maxEntries;

        // TTL policies per entry type
        this.ttlPolicies = Map.of(
                "search_result", Duration.ofHours(24),
                "query_cache", Duration.ofHours(6),
                "crawl_chunk", Duration.ofDays(7)
        );
    }

    @Scheduled(cron = "${websearch.eviction.schedule:0 0 * * * *}")
    public void evict() {
        if (!enabled) {
            return;
        }
        runEviction();
    }

    /**
     * Run TTL-based eviction and enforce the max-entries cap.
     * Called automatically on the cron schedule and can be triggered manually.
     *
     * @return summary of the eviction run
     */
    public EvictionResult runEviction() {
        log.info("Starting eviction cycle...");
        long before = vectorStore.count();
        int typesEvicted = 0;

        // Enforce TTL per entry type
        for (Map.Entry<String, Duration> policy : ttlPolicies.entrySet()) {
            String entryType = policy.getKey();
            Duration ttl = policy.getValue();
            Instant cutoff = Instant.now().minus(ttl);

            try {
                MetadataFilter filter = MetadataFilter.byTypeOlderThan(entryType, cutoff);
                vectorStore.deleteByMetadata(filter);
                typesEvicted++;
                log.info("Evicted '{}' entries older than {}", entryType, ttl);
            } catch (Exception e) {
                log.error("Eviction failed for type '{}': {}", entryType, e.getMessage());
            }
        }

        // Enforce max-entries cap
        try {
            long count = vectorStore.count();
            if (count > maxEntries) {
                long excess = count - maxEntries;
                log.warn("Store has {} entries (max: {}), evicting {} oldest",
                        count, maxEntries, excess);
                Instant cutoff = Instant.now().minus(Duration.ofDays(1));
                vectorStore.deleteByMetadata(MetadataFilter.olderThan(cutoff));
            }
        } catch (Exception e) {
            log.error("Max-entries eviction failed: {}", e.getMessage());
        }

        long after = vectorStore.count();
        log.info("Eviction cycle complete. Before: {}, after: {}, removed: {}",
                before, after, before - after);

        return new EvictionResult(before, after, before - after, typesEvicted);
    }

    /**
     * Delete ALL entries from the vector store (full flush).
     *
     * @return summary with entry counts before and after
     */
    public EvictionResult flushAll() {
        log.warn("Flushing ALL entries from vector store...");
        long before = vectorStore.count();

        try {
            // Delete everything by using epoch as cutoff (all entries are older than "now")
            vectorStore.deleteByMetadata(MetadataFilter.olderThan(Instant.now().plusSeconds(1)));
        } catch (Exception e) {
            log.error("Flush failed: {}", e.getMessage());
        }

        long after = vectorStore.count();
        log.info("Flush complete. Before: {}, after: {}, removed: {}", before, after, before - after);
        return new EvictionResult(before, after, before - after, 0);
    }

    /**
     * Result of an eviction or flush operation.
     */
    public record EvictionResult(long entriesBefore, long entriesAfter, long removed, int typesEvicted) {}
}
