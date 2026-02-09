package com.dnamaz.websearch.provider.store;

import com.dnamaz.websearch.model.*;
import com.dnamaz.websearch.provider.VectorStore;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Local vector store backed by Apache Lucene with HNSW indexing.
 *
 * <p>Stores VectorEntry as Lucene documents with stored fields (id, content,
 * metadata) plus a KnnFloatVectorField for the embedding vector.</p>
 */
@Component
public class LuceneVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(LuceneVectorStore.class);
    private static final String FIELD_ID = "id";
    private static final String FIELD_VECTOR = "vector";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_ENTRY_TYPE = "entryType";
    private static final String FIELD_NAMESPACE = "namespace";
    private static final String FIELD_CREATED_AT = "createdAt";

    @Value("${websearch.store.lucene.index-path:${user.home}/.websearch/index}")
    private String indexPath;

    private FSDirectory directory;
    private IndexWriter writer;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public String type() {
        return "lucene";
    }

    @Override
    public StoreCapabilities capabilities() {
        return new StoreCapabilities(
                true,       // supportsNamespaces
                true,       // supportsMetadataFiltering
                true,       // supportsBatchOperations
                true,       // supportsGet
                false,      // supportsNativeTtl
                false,      // requiresExplicitDimensions
                1000        // maxBatchSize
        );
    }

    @Override
    @PostConstruct
    public void initialize() {
        try {
            Path path = Path.of(indexPath);
            Files.createDirectories(path);
            directory = FSDirectory.open(path);

            IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            writer = new IndexWriter(directory, config);
            writer.commit();

            log.info("LuceneVectorStore initialized at: {}", indexPath);
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
            if (directory != null) {
                directory.close();
            }
            log.info("LuceneVectorStore closed");
        } catch (IOException e) {
            log.error("Error closing LuceneVectorStore: {}", e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void upsert(VectorEntry entry) {
        lock.writeLock().lock();
        try {
            // Delete existing document with same ID
            writer.deleteDocuments(new Term(FIELD_ID, entry.id()));

            Document doc = createDocument(entry);
            writer.addDocument(doc);
            writer.commit();
        } catch (IOException e) {
            throw new RuntimeException("Upsert failed: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<VectorEntry> get(String id) {
        lock.readLock().lock();
        try {
            DirectoryReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);

            Query query = new TermQuery(new Term(FIELD_ID, id));
            TopDocs topDocs = searcher.search(query, 1);

            if (topDocs.totalHits.value() == 0) {
                reader.close();
                return Optional.empty();
            }

            Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
            VectorEntry entry = documentToEntry(doc);
            reader.close();
            return Optional.of(entry);
        } catch (IOException e) {
            throw new RuntimeException("Get failed: " + e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
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
    public List<VectorMatch> search(VectorSearchRequest request) {
        lock.readLock().lock();
        try {
            DirectoryReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);

            // Build KNN query with optional namespace filter
            Query knnQuery;
            if (request.namespace() != null && !request.namespace().isBlank()) {
                // Filter KNN results to the specified namespace.
                // Also match legacy entries that have no namespace field (OR with "default").
                BooleanQuery.Builder nsFilter = new BooleanQuery.Builder();
                nsFilter.add(new TermQuery(new Term(FIELD_NAMESPACE, request.namespace())),
                        BooleanClause.Occur.SHOULD);
                if ("default".equals(request.namespace())) {
                    // Legacy entries without namespace field should match "default"
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
                metadata.put(FIELD_ENTRY_TYPE, doc.get(FIELD_ENTRY_TYPE));
                metadata.put(FIELD_CREATED_AT, doc.get(FIELD_CREATED_AT));

                // Collect all metadata fields
                for (IndexableField field : doc.getFields()) {
                    String name = field.name();
                    if (!name.equals(FIELD_ID) && !name.equals(FIELD_VECTOR)
                            && !name.equals(FIELD_CONTENT)) {
                        metadata.put(name, field.stringValue());
                    }
                }

                matches.add(new VectorMatch(id, scoreDoc.score, content, metadata));
            }

            reader.close();
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
        try {
            DirectoryReader reader = DirectoryReader.open(directory);
            int count = reader.numDocs();
            reader.close();
            return count;
        } catch (IOException e) {
            throw new RuntimeException("Count failed: " + e.getMessage(), e);
        } finally {
            lock.readLock().unlock();
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

    private Document createDocument(VectorEntry entry) {
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
                new float[0], // vectors are not stored for retrieval in this implementation
                doc.get(FIELD_CONTENT),
                doc.get(FIELD_ENTRY_TYPE),
                namespace != null ? namespace : "default",
                Instant.parse(doc.get(FIELD_CREATED_AT)),
                metadata
        );
    }
}
