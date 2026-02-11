package com.noetic.websearch.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Resolves the active namespace for vector store operations using a priority chain:
 *
 * <ol>
 *   <li><b>Explicit parameter</b> -- {@code namespace} field in the request body</li>
 *   <li><b>Auto-detected context:</b>
 *     <ul>
 *       <li>MCP: workspace root set at connection time via {@link #setMcpRoot}</li>
 *       <li>REST: {@code X-Noetic-Project} HTTP header</li>
 *       <li>Skills/CLI: baked into generated instruction files at install time</li>
 *     </ul>
 *   </li>
 *   <li><b>Config default</b> -- {@code websearch.store.namespace} (default: "default")</li>
 * </ol>
 *
 * <p>Long project paths are hashed to short deterministic IDs (e.g. {@code proj-a1b2c3d4})
 * for storage efficiency.</p>
 */
@Component
public class NamespaceResolver {

    private static final Logger log = LoggerFactory.getLogger(NamespaceResolver.class);
    private static final String HEADER_NAME = "X-Noetic-Project";
    private static final String HASH_PREFIX = "proj-";

    private final String defaultNamespace;

    /** MCP workspace root, set once at connection init. */
    private volatile String mcpRoot;

    public NamespaceResolver(
            @Value("${websearch.store.namespace:default}") String defaultNamespace
    ) {
        this.defaultNamespace = defaultNamespace;
    }

    /**
     * Resolve namespace for a REST request.
     *
     * @param explicit explicit namespace from request body (may be null)
     * @param request  the HTTP request (for header extraction)
     * @return resolved namespace, never null
     */
    public String resolve(String explicit, HttpServletRequest request) {
        // 1. Explicit parameter wins
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }

        // 2. HTTP header
        if (request != null) {
            String header = request.getHeader(HEADER_NAME);
            if (header != null && !header.isBlank()) {
                return normalizeNamespace(header);
            }
        }

        // 3. Config default
        return defaultNamespace;
    }

    /**
     * Resolve namespace for an MCP request.
     *
     * @param explicit explicit namespace from tool parameter (may be null)
     * @return resolved namespace, never null
     */
    public String resolve(String explicit) {
        // 1. Explicit parameter wins
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }

        // 2. MCP workspace root
        if (mcpRoot != null && !mcpRoot.isBlank()) {
            return normalizeNamespace(mcpRoot);
        }

        // 3. Config default
        return defaultNamespace;
    }

    /**
     * Set the MCP workspace root, called once at connection initialization.
     */
    public void setMcpRoot(String root) {
        this.mcpRoot = root;
        if (root != null) {
            log.info("MCP workspace root set: {} -> namespace '{}'", root, normalizeNamespace(root));
        }
    }

    /** Returns the current MCP root (for logging). */
    public String getMcpRoot() {
        return mcpRoot;
    }

    /** Returns the config default namespace. */
    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    /**
     * Normalize a namespace value. If it looks like a file path (contains '/'),
     * hash it to a short ID. Otherwise use as-is.
     */
    public String normalizeNamespace(String value) {
        if (value == null || value.isBlank()) {
            return defaultNamespace;
        }
        // Short names used as-is (e.g. "my-project", "default")
        if (!value.contains("/") && value.length() <= 64) {
            return value;
        }
        // Long paths hashed to deterministic short ID
        return hashProjectPath(value);
    }

    /**
     * Hash a project path to a short deterministic namespace ID.
     * Uses first 8 hex chars of SHA-256.
     *
     * @param path the project directory path
     * @return e.g. "proj-a1b2c3d4"
     */
    public String hashProjectPath(String path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(path.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash, 0, 4); // 8 hex chars
            return HASH_PREFIX + hex;
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available
            throw new RuntimeException(e);
        }
    }
}
