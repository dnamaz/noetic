package com.dnamaz.websearch.provider;

import com.dnamaz.websearch.model.ChunkRequest;
import com.dnamaz.websearch.model.ContentChunk;

import java.util.List;

/**
 * Provider interface for splitting content into chunks.
 */
public interface ChunkingStrategy {

    /** Strategy type identifier (e.g. "sentence", "token", "semantic"). */
    String type();

    /** Split content into chunks according to the strategy. */
    List<ContentChunk> chunk(ChunkRequest request);
}
