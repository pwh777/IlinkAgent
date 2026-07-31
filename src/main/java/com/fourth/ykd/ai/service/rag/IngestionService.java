package com.fourth.ykd.ai.service.rag;

import java.util.Map;

/**
 * Service responsible for writing / ingesting knowledge
 * documents into the vector store with chunking, deduplication,
 * and transactional guarantees.
 */
public interface IngestionService {

    /**
     * Ingest a document: chunk it, write chunks to the vector store,
     * and record a deduplication entry. Skips write when the same
     * sourceId + content hash already exists.
     *
     * @param sourceId unique identifier of the source document
     * @param title    human-readable title
     * @param content  full text to be chunked and stored
     * @param metadata additional key-value pairs attached to every chunk
     */
    void ingestDocument(String sourceId, String title, String content, Map<String, Object> metadata);

    /**
     * Remove all chunks belonging to the given source from both the
     * vector store and the deduplication table.
     *
     * @param sourceId the source to purge
     */
    void deleteBySourceId(String sourceId);

    /**
     * Atomic update: deletes existing chunks for the sourceId, then
     * re-ingests with the new content.
     *
     * @param sourceId unique identifier of the source document
     * @param title    human-readable title
     * @param content  full text to be chunked and stored
     * @param metadata additional key-value pairs attached to every chunk
     */
    void updateDocument(String sourceId, String title, String content, Map<String, Object> metadata);

    /**
     * Convenience method kept for backward compatibility.
     * Delegates to {@link #ingestDocument} with a fixed sourceId.
     *
     * @param text the raw knowledge text
     */
    void addKnowledge(String text);
}