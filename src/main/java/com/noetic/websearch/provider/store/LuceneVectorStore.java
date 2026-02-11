package com.noetic.websearch.provider.store;

import com.noetic.websearch.model.*;
import com.noetic.websearch.provider.VectorStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Two-tier Lucene vector store with per-agent isolation and shared main index.
 *
 * <p><b>Tier 1: Agent index</b> -- Each agent/session writes to its own index
 * directory ({@code ~/.websearch/agents/<agent-id>/}). No write lock contention
 * between agents.</p>
 *
 * <p><b>Tier 2: Shared index</b> -- The main index ({@code ~/.websearch/index/})
 * is used by MCP/REST servers and for promoted content. CLI agents can promote
 * their local entries to the shared index.</p>
 *
 * <p>Searches use Lucene {@link MultiReader} to query both the agent's own index
 * and the shared index simultaneously.</p>
 */
@Component
public class LuceneVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(LuceneVectorStore.class);
    static final String FIELD_ID = "id";
    static final String FIELD_VECTOR = "vector";
    static final String FIELD_CONTENT = "content";
    static final String FIELD_ENTRY_TYPE = "entryType";
    static final String FIELD_NAMESPACE = "namespace";
    static final String FIELD_CREATED_AT = "createdAt";

    @Value("${websearch.store.lucene.index-path:${user.home}/.websearch/index}")
    private String sharedIndexPath;

    @Value("${websearch.store.agent-id:#{null}}")
    private String agentId;

    @Value("${websearch.store.lucene.agents-dir:${user.home}/.websearch/agents}")
    private String agentsDir;

    /** The writable index -- either the shared index or an agent-specific index. */
    private FSDirectory writeDirectory;
    private IndexWriter writer;

    /** The shared index opened read-only (null if this IS the shared index). */
    private FSDirectory sharedDirectory;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private boolean isAgentMode;

    @Override
    public String type() {
        return "lucene";
    }

    @Override
    public StoreCapabilities capabilities() {
        return new StoreCapabilities(
                true, true, true, true, false, false, 1000);
    }

    @Override
    @PostConstruct
    public void initialize() {
        try {
            isAgentMode = agentId != null && !agentId.isBlank();

            if (isAgentMode) {
                // Agent mode: write to per-agent index, read from both
                Path agentPath = Path.of(agentsDir, agentId);
                Files.createDirectories(agentPath);
                writeDirectory = FSDirectory.open(agentPath);

                IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                writer = new IndexWriter(writeDirectory, config);
                writer.commit();

                // Open shared index read-only (if it exists)
                Path sharedPath = Path.of(sharedIndexPath);
                if (Files.exists(sharedPath) && Files.exists(sharedPath.resolve("segments_1").resolveSibling("write.lock").getParent())) {
                    try {
                        sharedDirectory = FSDirectory.open(sharedPath);
                        // Verify it's a valid index by trying to open a reader
                        if (DirectoryReader.indexExists(sharedDirectory)) {
                            log.info("Agent '{}' will also search shared index at: {}", agentId, sharedIndexPath);
                        } else {
                            sharedDirectory.close();
                            sharedDirectory = null;
                        }
                    } catch (IOException e) {
                        log.debug("Shared index not available for reading: {}", e.getMessage());
                        sharedDirectory = null;
                    }
                }

                log.info("LuceneVectorStore initialized in agent mode: agent={}, path={}", agentId, agentPath);
            } else {
                // Server mode: write directly to the shared index
                Path path = Path.of(sharedIndexPath);
                Files.createDirectories(path);
                writeDirectory = FSDirectory.open(path);

                IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                writer = new IndexWriter(writeDirectory, config);
                writer.commit();

                log.info("LuceneVectorStore initialized at: {}", sharedIndexPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Lucene index: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            if (writer != null) {
                writer.commit();
                writer.close();
            }
            if (writeDirectory != null) {
                writeDirectory.close();
            }
            if (sharedDirectory != null) {
                sharedDirectory.close();
            }
            log.info("LuceneVectorStore closed");
        } catch (IOException e) {
            log.error("Error closing LuceneVectorStore: {}", e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    // -- Write operations (always go to the writable index) --

    @Override
    public void upsert(VectorEntry entry) {
        lock.writeLock().lock();
        try {
            writer.deleteDocuments(new Term(FIELD_ID, entry.id()));
            writer.addDocument(createDocument(entry));
            writer.commit();
        } catch (IOException e) {
            throw new RuntimeException("Upsert failed: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void upsertBatch(List<VectorEntry> entries) {
        lock.writeLock().lock();
        try {
            for (VectorEntry entry : entries) {
                writer.deleteDocuments(new Term(FIELD_ID, entry.id()));
                writer.addDocument(createDocument(entry));
            }
            writer.commit();
        } catch (IOException e) {
            throw new RuntimeException("Batch upsert failed: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(String id) {
        lock.writeLock().lock();
        try {
            writer.deleteDocuments(new Term(FIELD_ID, id));
            writer.commit();
        } catch (IOException e) {
            throw new RuntimeException("Delete failed: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteBatch(List<String> ids) {
        lock.writeLock().lock();
        try {
            for (String id : ids) {
                writer.deleteDocuments(new Term(FIELD_ID, id));
            }
            writer.commit();
        } catch (IOException e) {
            throw new RuntimeException("Batch delete failed: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteByMetadata(MetadataFilter filter) {
        lock.writeLock().lock();
        try {
            BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
            for (Map.Entry<String, String> eq : filter.equals().entrySet()) {
                queryBuilder.add(new TermQuery(new Term(eq.getKey(), eq.getValue())),
                        BooleanClause.Occur.MUST);
            }
            if (filter.createdBefore() != null) {
                queryBuilder.add(LongPoint.newRangeQuery(FIELD_CREATED_AT + "_epoch",
                        Long.MIN_VALUE, filter.createdBefore().toEpochMilli()),
                        BooleanClause.Occur.MUST);
            }
            writer.deleteDocuments(queryBuilder.build());
            writer.commit();
        } catch (IOException e) {
            throw new RuntimeException("DeleteByMetadata failed: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // -- Read operations (search both agent index + shared index) --

    @Override
    public Optional<VectorEntry> get(String id) {
        lock.readLock().lock();
        try (IndexReader reader = openMultiReader()) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Query query = new TermQuery(new Term(FIELD_ID, id));
            TopDocs topDocs = searcher.search(query, 1);
            if (topDocs.totalHits.value() == 0) {
                return Optional.empty();
            }
            Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
            return Optional.of(documentToEntry(doc));
        } catch (IOException e) {
            throw new RuntimeException("Get failed: " + e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<VectorMatch> search(VectorSearchRequest request) {
        lock.readLock().lock();
        try (IndexReader reader = openMultiReader()) {
            IndexSearcher searcher = new IndexSearcher(reader);

            Query knnQuery;
            if (request.namespace() != null && !request.namespace().isBlank()) {
                BooleanQuery.Builder nsFilter = new BooleanQuery.Builder();
                nsFilter.add(new TermQuery(new Term(FIELD_NAMESPACE, request.namespace())),
                        BooleanClause.Occur.SHOULD);
                if ("default".equals(request.namespace())) {
                    nsFilter.add(new BooleanQuery.Builder()
                            .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
                            .add(new TermQuery(new Term(FIELD_NAMESPACE, request.namespace())),
                                    BooleanClause.Occur.MUST_NOT)
                            .build(), BooleanClause.Occur.SHOULD);
                }
                knnQuery = new KnnFloatVectorQuery(FIELD_VECTOR,
                        request.queryVector(), request.topK(), nsFilter.build());
            } else {
                knnQuery = new KnnFloatVectorQuery(FIELD_VECTOR,
                        request.queryVector(), request.topK());
            }

            TopDocs topDocs = searcher.search(knnQuery, request.topK());

            List<VectorMatch> matches = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                if (scoreDoc.score < request.similarityThreshold()) continue;
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String id = doc.get(FIELD_ID);
                String content = doc.get(FIELD_CONTENT);
                Map<String, String> metadata = new HashMap<>();
                for (IndexableField field : doc.getFields()) {
                    String name = field.name();
                    if (!name.equals(FIELD_ID) && !name.equals(FIELD_VECTOR)
                            && !name.equals(FIELD_CONTENT)) {
                        metadata.put(name, field.stringValue());
                    }
                }
                matches.add(new VectorMatch(id, scoreDoc.score, content, metadata));
            }
            return matches;
        } catch (IOException e) {
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long count() {
        lock.readLock().lock();
        try (IndexReader reader = openMultiReader()) {
            return reader.numDocs();
        } catch (IOException e) {
            throw new RuntimeException("Count failed: " + e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
        }
    }

    // -- Promote: copy entries from agent index to shared index --

    /**
     * Promote all entries from the agent's local index to the shared main index.
     * Only available in agent mode. Acquires the shared index write lock briefly.
     *
     * @return number of entries promoted
     */
    public int promoteToShared() {
        if (!isAgentMode) {
            log.info("Not in agent mode -- nothing to promote");
            return 0;
        }

        lock.readLock().lock();
        try {
            // Read all entries from agent index (stored fields + vectors)
            DirectoryReader agentReader = DirectoryReader.open(writeDirectory);
            StoredFields storedFields = agentReader.storedFields();
            List<Document> docs = new ArrayList<>();
            Bits liveDocs = MultiBits.getLiveDocs(agentReader);

            for (int i = 0; i < agentReader.maxDoc(); i++) {
                if (liveDocs != null && !liveDocs.get(i)) continue; // skip deleted
                Document storedDoc = storedFields.document(i);

                // Rebuild full document with vector (KnnFloatVectorField is not stored)
                // Read the vector from the FloatVectorValues for this leaf
                Document fullDoc = new Document();
                for (IndexableField field : storedDoc.getFields()) {
                    fullDoc.add(field);
                }

                // Extract vector from the index
                float[] vector = readVector(agentReader, i);
                if (vector != null) {
                    fullDoc.add(new KnnFloatVectorField(FIELD_VECTOR, vector));
                }

                docs.add(fullDoc);
            }
            agentReader.close();

            if (docs.isEmpty()) {
                log.info("No entries to promote");
                return 0;
            }

            // Write to shared index
            Path sharedPath = Path.of(sharedIndexPath);
            Files.createDirectories(sharedPath);
            try (FSDirectory sharedDir = FSDirectory.open(sharedPath)) {
                IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                try (IndexWriter sharedWriter = new IndexWriter(sharedDir, config)) {
                    for (Document doc : docs) {
                        String id = doc.get(FIELD_ID);
                        if (id != null) {
                            sharedWriter.deleteDocuments(new Term(FIELD_ID, id));
                        }
                        sharedWriter.addDocument(doc);
                    }
                    sharedWriter.commit();
                }
            }

            log.info("Promoted {} entries from agent '{}' to shared index", docs.size(), agentId);
            return docs.size();

        } catch (IOException e) {
            throw new RuntimeException("Promote failed: " + e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Read the float vector for a document from the index.
     * Navigates through leaf readers to find the correct segment.
     */
    private float[] readVector(DirectoryReader reader, int docId) {
        try {
            for (LeafReaderContext leaf : reader.leaves()) {
                int localDoc = docId - leaf.docBase;
                if (localDoc >= 0 && localDoc < leaf.reader().maxDoc()) {
                    FloatVectorValues vectors = leaf.reader().getFloatVectorValues(FIELD_VECTOR);
                    if (vectors == null) continue;

                    // Iterate through the vector values to find our doc
                    var iter = vectors.iterator();
                    int ord = iter.advance(localDoc);
                    if (ord == localDoc) {
                        return vectors.vectorValue(iter.index()).clone();
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Could not read vector for doc {}: {}", docId, e.getMessage());
        }
        return null;
    }

    // -- Internal helpers --

    /**
     * Opens a reader that searches both the writable index and the shared index
     * (in agent mode). In server mode, just opens the writable (shared) index.
     */
    private IndexReader openMultiReader() throws IOException {
        DirectoryReader writeReader = DirectoryReader.open(writeDirectory);

        if (isAgentMode && sharedDirectory != null) {
            try {
                if (DirectoryReader.indexExists(sharedDirectory)) {
                    DirectoryReader sharedReader = DirectoryReader.open(sharedDirectory);
                    return new MultiReader(writeReader, sharedReader);
                }
            } catch (IOException e) {
                log.debug("Could not open shared index for reading: {}", e.getMessage());
            }
        }

        return writeReader;
    }

    static Document createDocument(VectorEntry entry) {
        Document doc = new Document();
        doc.add(new StringField(FIELD_ID, entry.id(), Field.Store.YES));
        doc.add(new KnnFloatVectorField(FIELD_VECTOR, entry.vector()));
        doc.add(new StoredField(FIELD_CONTENT, entry.content()));
        doc.add(new StringField(FIELD_ENTRY_TYPE, entry.entryType(), Field.Store.YES));
        doc.add(new StringField(FIELD_NAMESPACE, entry.namespace(), Field.Store.YES));
        doc.add(new StringField(FIELD_CREATED_AT,
                entry.createdAt().toString(), Field.Store.YES));
        doc.add(new LongPoint(FIELD_CREATED_AT + "_epoch",
                entry.createdAt().toEpochMilli()));
        for (Map.Entry<String, String> meta : entry.metadata().entrySet()) {
            doc.add(new StringField(meta.getKey(), meta.getValue(), Field.Store.YES));
        }
        return doc;
    }

    private VectorEntry documentToEntry(Document doc) {
        Map<String, String> metadata = new HashMap<>();
        for (IndexableField field : doc.getFields()) {
            String name = field.name();
            if (!name.equals(FIELD_ID) && !name.equals(FIELD_VECTOR)
                    && !name.equals(FIELD_CONTENT) && !name.equals(FIELD_ENTRY_TYPE)
                    && !name.equals(FIELD_NAMESPACE) && !name.equals(FIELD_CREATED_AT)
                    && !name.endsWith("_epoch")) {
                metadata.put(name, field.stringValue());
            }
        }
        String namespace = doc.get(FIELD_NAMESPACE);
        return new VectorEntry(
                doc.get(FIELD_ID),
                new float[0],
                doc.get(FIELD_CONTENT),
                doc.get(FIELD_ENTRY_TYPE),
                namespace != null ? namespace : "default",
                Instant.parse(doc.get(FIELD_CREATED_AT)),
                metadata
        );
    }
}
