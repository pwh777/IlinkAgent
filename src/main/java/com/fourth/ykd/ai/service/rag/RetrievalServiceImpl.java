package com.fourth.ykd.ai.service.rag;

import com.fourth.ykd.ai.config.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Retrieval service implementation using pgvector.
 *
 * <ul>
 *   <li>Configuration comes from {@link RagProperties.Retrieval}</li>
 *   <li>search() is protected by {@code @Retryable} and {@code @CircuitBreaker}</li>
 *   <li>Results are always sorted by similarity descending</li>
 *   <li>getKnowledge() uses a self-proxy call so that retry / circuit-breaker
 *       apply to the internal search invocation</li>
 *   <li>Prompt fragments include metadata source and title (when present)</li>
 * </ul>
 */
@Slf4j
@Service
public class RetrievalServiceImpl implements RetrievalService {

    private final VectorStore store;
    private final RagProperties ragProperties;
    private RetrievalService self;

    public RetrievalServiceImpl(VectorStore store, RagProperties ragProperties) {
        this.store = store;
        this.ragProperties = ragProperties;
    }

    /** Self-proxy injection — allows {@code getKnowledge()} to benefit from AOP. */
    @Autowired
    @Lazy
    public void setSelf(RetrievalService self) {
        this.self = self;
    }

    // ─── public API ──────────────────────────────────────────

    @Override
    @CircuitBreaker(name = "rag-search", failureThreshold = 5, openTimeoutMs = 30000)
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000))
    public List<Document> search(String question) {
        RagProperties.Retrieval r = ragProperties.getRetrieval();
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(r.getTopK())
                .similarityThreshold(r.getSimilarityThreshold())
                .build();

        List<Document> results = new java.util.ArrayList<>(store.similaritySearch(request));

        results.sort(Comparator.comparingDouble(
                (Document d) -> d.getScore() != null ? d.getScore() : 0.0
        ).reversed());

        log.debug("[RAG][SEARCH] query=\"{}\", topK={}, threshold={}, results={}",
                question, r.getTopK(), r.getSimilarityThreshold(), results.size());

        return results;
    }

    /**
     * {@code @Recover} fallback — invoked when all {@code @Retryable} attempts
     * are exhausted. Returns an empty list so the chat pipeline does not receive
     * a 500 error.
     */
    @Recover
    public List<Document> recoverSearch(Exception e, String question) {
        log.warn("[RAG][FALLBACK] all retries exhausted for query=\"{}\", error={}",
                question, e.getMessage());
        return Collections.emptyList();
    }

    @Override
    public String getKnowledge(String question) {
        // Use self-proxy so that @Retryable/@CircuitBreaker apply to the call.
        List<Document> docs = self.search(question);

        if (docs.isEmpty()) {
            return "";
        }

        double threshold = ragProperties.getRetrieval().getSimilarityThreshold();
        List<Document> validDocs = docs.stream()
                .filter(d -> d.getScore() != null && d.getScore() >= threshold)
                .collect(Collectors.toList());

        if (validDocs.isEmpty()) {
            return "";
        }

        return validDocs.stream()
                .map(this::formatDocument)
                .collect(Collectors.joining("\n"));
    }

    // ─── private helpers ─────────────────────────────────────

    private String formatDocument(Document document) {
        StringBuilder meta = new StringBuilder();
        if (document.getMetadata() != null) {
            String source = (String) document.getMetadata().get("source");
            String title  = (String) document.getMetadata().get("title");
            if (source != null) meta.append("source: ").append(source);
            if (title != null) {
                if (meta.length() > 0) meta.append(", ");
                meta.append("title: ").append(title);
            }
        }
        String metaStr = meta.length() > 0 ? meta.toString() : "unknown";
        return """
                     【知识来源】
                     %s
                    【内容】
                     %s
                """.formatted(metaStr, document.getText());
    }
}