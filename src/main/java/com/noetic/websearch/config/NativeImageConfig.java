package com.noetic.websearch.config;

import com.noetic.websearch.model.*;
import com.noetic.websearch.service.BatchCrawlService;
import com.noetic.websearch.service.EvictionService;
import com.noetic.websearch.service.SitemapParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * GraalVM native-image configuration.
 *
 * <p>Registers reflection hints for domain records, ONNX Runtime / DJL
 * engine discovery, Lucene codecs, and resource patterns for models and
 * native libraries. Works in conjunction with the static config files
 * in {@code META-INF/native-image/com.noetic/noetic/}.</p>
 */
@Configuration
@ImportRuntimeHints(NativeImageConfig.Hints.class)
public class NativeImageConfig {

    static class Hints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            var reflection = hints.reflection();

            // ---- Domain records (JSON serialization) ----
            registerAllMembers(reflection, FetchRequest.class);
            registerAllMembers(reflection, FetchResult.class);
            registerAllMembers(reflection, FetcherCapabilities.class);
            registerAllMembers(reflection, SearchRequest.class);
            registerAllMembers(reflection, SearchResponse.class);
            registerAllMembers(reflection, SearchResult.class);
            registerAllMembers(reflection, SearchCapabilities.class);
            registerAllMembers(reflection, EmbeddingRequest.class);
            registerAllMembers(reflection, EmbeddingBatchRequest.class);
            registerAllMembers(reflection, EmbeddingResult.class);
            registerAllMembers(reflection, EmbeddingCapabilities.class);
            registerAllMembers(reflection, VectorEntry.class);
            registerAllMembers(reflection, VectorSearchRequest.class);
            registerAllMembers(reflection, VectorMatch.class);
            registerAllMembers(reflection, MetadataFilter.class);
            registerAllMembers(reflection, StoreCapabilities.class);
            registerAllMembers(reflection, ChunkRequest.class);
            registerAllMembers(reflection, ContentChunk.class);
            registerAllMembers(reflection, ProxyConfig.class);
            registerAllMembers(reflection, BatchCrawlService.BatchCrawlResult.class);
            registerAllMembers(reflection, BatchCrawlService.CrawlError.class);
            registerAllMembers(reflection, SitemapParser.SitemapResult.class);
            registerAllMembers(reflection, EvictionService.EvictionResult.class);

            // ---- DJL core + engine discovery ----
            registerByName(reflection, "ai.djl.engine.Engine");
            registerByName(reflection, "ai.djl.engine.EngineProvider");
            registerByName(reflection, "ai.djl.onnxruntime.engine.OrtEngine");
            registerByName(reflection, "ai.djl.onnxruntime.engine.OrtEngineProvider");
            registerByName(reflection, "ai.djl.pytorch.engine.PtEngineProvider");
            registerByName(reflection, "ai.djl.engine.rust.RsEngineProvider");
            registerByName(reflection, "ai.djl.ndarray.NDManager");
            registerByName(reflection, "ai.djl.ndarray.BaseNDManager");
            registerByName(reflection, "ai.djl.repository.zoo.ZooModel");
            registerByName(reflection, "ai.djl.repository.zoo.Criteria");

            // ---- ONNX Runtime ----
            registerByName(reflection, "ai.onnxruntime.OnnxRuntime");
            registerByName(reflection, "ai.onnxruntime.OrtEnvironment");
            registerByName(reflection, "ai.onnxruntime.OrtSession");
            registerByName(reflection, "ai.onnxruntime.OrtSession$SessionOptions");
            registerByName(reflection, "ai.onnxruntime.OnnxTensor");
            registerByName(reflection, "ai.onnxruntime.OnnxValue");
            registerByName(reflection, "ai.onnxruntime.TensorInfo");
            registerByName(reflection, "ai.onnxruntime.OrtProvider");
            registerByName(reflection, "ai.onnxruntime.OrtLoggingLevel");

            // ---- MCP SDK (JSON-RPC serialization) ----
            registerByName(reflection, "io.modelcontextprotocol.spec.McpSchema");
            registerByName(reflection, "io.modelcontextprotocol.spec.McpSchema$Tool");
            registerByName(reflection, "io.modelcontextprotocol.spec.McpSchema$Prompt");
            registerByName(reflection, "io.modelcontextprotocol.spec.McpSchema$PromptArgument");
            registerByName(reflection, "io.modelcontextprotocol.spec.McpSchema$PromptMessage");
            registerByName(reflection, "io.modelcontextprotocol.spec.McpSchema$CallToolResult");
            registerByName(reflection, "io.modelcontextprotocol.spec.McpSchema$TextContent");
            registerByName(reflection, "io.modelcontextprotocol.spec.McpSchema$GetPromptResult");
            registerByName(reflection, "io.modelcontextprotocol.spec.McpSchema$ServerCapabilities");

            // ---- Lucene internal class loading (TestSecrets uses Class.forName at runtime) ----
            registerByName(reflection, "org.apache.lucene.index.ConcurrentMergeScheduler");
            registerByName(reflection, "org.apache.lucene.index.IndexWriter");
            registerByName(reflection, "org.apache.lucene.index.SegmentReader");
            registerByName(reflection, "org.apache.lucene.store.FilterIndexInput");
            registerByName(reflection, "org.apache.lucene.store.MMapDirectory");
            registerByName(reflection, "org.apache.lucene.store.PosixNativeAccess");
            registerByName(reflection, "org.apache.lucene.store.NativeAccess");
            registerByName(reflection, "org.apache.lucene.store.MemorySegmentIndexInputProvider");

            // ---- Jvppeteer (Jackson serialization of CDP protocol messages) ----
            registerPackage(reflection, classLoader, "com.ruiyun.jvppeteer.entities");
            registerPackage(reflection, classLoader, "com.ruiyun.jvppeteer.core");
            registerPackage(reflection, classLoader, "com.ruiyun.jvppeteer.events");
            registerPackage(reflection, classLoader, "com.ruiyun.jvppeteer.transport");
            registerPackage(reflection, classLoader, "com.ruiyun.jvppeteer.common");

            // ---- Resource hints ----
            var resources = hints.resources();
            resources.registerPattern("models/.*");
            resources.registerPattern(".*\\.onnx");
            resources.registerPattern(".*vocab\\.txt");
            resources.registerPattern(".*config\\.json");
            resources.registerPattern("ai/djl/.*");
            resources.registerPattern("ai/onnxruntime/.*");
            resources.registerPattern("native/.*");
            resources.registerPattern("native/lib/.*");
            resources.registerPattern(".*\\.dylib");
            resources.registerPattern(".*\\.so");
            resources.registerPattern("META-INF/services/.*");
            resources.registerPattern("instructions/.*");
            resources.registerPattern("logback.*\\.xml");
            resources.registerPattern("application.*\\.yml");
            resources.registerPattern("application.*\\.properties");
        }

        private void registerAllMembers(ReflectionHints reflection, Class<?> clazz) {
            reflection.registerType(clazz, MemberCategory.values());
        }

        private void registerByName(ReflectionHints reflection, String className) {
            try {
                // Use initialize=false to avoid triggering static initializers
                // during AOT processing (e.g. OnnxRuntime loads native libs in <clinit>)
                Class<?> clazz = Class.forName(className, false,
                        Thread.currentThread().getContextClassLoader());
                reflection.registerType(clazz, MemberCategory.values());
            } catch (ClassNotFoundException | NoClassDefFoundError | UnsatisfiedLinkError e) {
                // Class may not be on classpath (optional provider) or native lib unavailable
            }
        }

        /**
         * Registers all classes in a package (and sub-packages) for reflection.
         * Scans the classpath for .class resources matching the package pattern.
         */
        private void registerPackage(ReflectionHints reflection, ClassLoader classLoader,
                                     String packageName) {
            Logger log = LoggerFactory.getLogger(NativeImageConfig.class);
            try {
                String pattern = "classpath*:" + packageName.replace('.', '/') + "/**/*.class";
                var resolver = new PathMatchingResourcePatternResolver(classLoader);
                Resource[] resources = resolver.getResources(pattern);
                int count = 0;
                for (Resource resource : resources) {
                    String path = resource.getURL().getPath();
                    // Extract class name from path: .../com/ruiyun/jvppeteer/entities/Foo.class
                    int pkgIdx = path.indexOf(packageName.replace('.', '/'));
                    if (pkgIdx >= 0) {
                        String className = path.substring(pkgIdx)
                                .replace('/', '.')
                                .replace(".class", "");
                        try {
                            Class<?> clazz = Class.forName(className);
                            reflection.registerType(clazz, MemberCategory.values());
                            count++;
                        } catch (ClassNotFoundException | NoClassDefFoundError e) {
                            // Skip classes that can't be loaded
                        }
                    }
                }
                log.info("Registered {} classes from package '{}' for native-image reflection",
                        count, packageName);
            } catch (Exception e) {
                log.warn("Failed to scan package '{}' for reflection hints: {}",
                        packageName, e.getMessage());
            }
        }
    }
}
