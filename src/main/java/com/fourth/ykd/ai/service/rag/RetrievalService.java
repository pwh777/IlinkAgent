package com.fourth.ykd.ai.service.rag;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Service responsible for retrieving knowledge from the vector store.
 * Does NOT decide whether to search — it always attempts retrieval
 * and returns an empty result when nothing matches.
 */
public interface RetrievalService {

    /**
     * Execute a similarity search against the vector store.
     *
     * @param question the query string
     * @return matching documents sorted by similarity descending
     */
    List<Document> search(String question);

    /**
     * Build a knowledge-context string from retrieved documents.
     * If no document passes the similarity threshold, returns an empty string.
     *
     * @param question the query string
     * @return formatted knowledge prompt fragment, or empty string
     */
    String getKnowledge(String question);
}