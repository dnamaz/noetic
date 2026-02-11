package com.noetic.websearch.provider;

import com.noetic.websearch.model.*;

import java.util.List;
import java.util.Optional;

/**
 * Provider interface for storing and searching vector embeddings.
 *
 * <p>Implementations include local Lucene HNSW and remote cloud
 * vector databases (Pinecone, Qdrant, Weaviate, Milvus).</p>
 */
public interface VectorStore extends AutoCloseable {

    /** Provider type identifier (e.g. "lucene", "pinecone", "qdrant"). */
    String type();

    /** Declares what this store supports. */
    StoreCapabilities capabilities();

    // -- Lifecycle --

    /** Initialize the store (create index/collection if needed). */
    void initialize();

    /** Flush and release connections. */
    @Override
    void close();

    // -- Single operations --

    /** Insert or overwrite an entry by ID. */
    void upsert(VectorEntry entry);

    /** Retrieve an entry by ID. */
    Optional<VectorEntry> get(String id);

    /** Delete an entry by ID. */
    void delete(String id);

    // -- Batch operations --

    /** Insert or overwrite multiple entries. */
    void upsertBatch(List<VectorEntry> entries);

    /** Delete multiple entries by ID. */
    void deleteBatch(List<String> ids);

    // -- Search --

    /** Search for similar vectors. */
    List<VectorMatch> search(VectorSearchRequest request);

    // -- Maintenance --

    /** Total number of entries in the store. */
    long count();

    /** Delete all entries matching the metadata filter. */
    void deleteByMetadata(MetadataFilter filter);
}
