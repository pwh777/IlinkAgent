package com.fourth.ykd.ai.service.rag;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ingestion pipeline implementation.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Compute SHA-256 hash of the full document content.</li>
 *   <li>Check dedup table ({@code rag_ingestion_dedup}) by {@code sourceId + hash}.</li>
 *   <li>If duplicate, skip silently.</li>
 *   <li>Otherwise chunk via {@link TokenTextSplitter}, assign per-chunk metadata
 *       ({@code sourceId, title, chunkIndex, totalChunks, ingestedAt}), store in pgvector,
 *       and record the hash.</li>
 * </ol>
 *
 * <h3>Transaction scope</h3>
 * All write methods are bound to {@code ragTransactionManager} so that
 * vector-store writes and dedup-table mutations are atomic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionServiceImpl implements IngestionService {

    private static final String TABLE_DEDUP = "rag_ingestion_dedup";

    private final VectorStore store;
    private final JdbcTemplate ragJdbcTemplate;
    private final TokenTextSplitter splitter;

    @PostConstruct
    public void initDedupTable() {
        ragJdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rag_ingestion_dedup (
                    source_id    VARCHAR(512) NOT NULL,
                    content_hash VARCHAR(64)  NOT NULL,
                    ingested_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (source_id, content_hash)
                )
                """);
        log.info("[RAG][INGEST] dedup table ready");
    }

    // ─── public API ────────────────────────────────────────────

    @Override
    @Transactional(transactionManager = "ragTransactionManager")
    public void ingestDocument(String sourceId, String title, String content,
                               Map<String, Object> metadata) {
        String contentHash = sha256(content);

        if (isDuplicate(sourceId, contentHash)) {
            log.debug("[RAG][INGEST] duplicate skipped: sourceId={}, hash={}", sourceId, contentHash);
            return;
        }

        Map<String, Object> baseMeta = new HashMap<>(
                metadata != null ? metadata : Map.of());
        Document raw = new Document(content, baseMeta);
        List<Document> chunks = splitter.split(List.of(raw));

        String ingestedAt = Instant.now().toString();
        int totalChunks = chunks.size();

        for (int i = 0; i < totalChunks; i++) {
            Document chunk = chunks.get(i);
            chunk.getMetadata().put("sourceId", sourceId);
            chunk.getMetadata().put("title", title);
            chunk.getMetadata().put("chunkIndex", i);
            chunk.getMetadata().put("totalChunks", totalChunks);
            chunk.getMetadata().put("ingestedAt", ingestedAt);
        }

        store.add(chunks);

        ragJdbcTemplate.update(
                "INSERT INTO " + TABLE_DEDUP
                + " (source_id, content_hash, ingested_at) VALUES (?, ?, ?)",
                sourceId, contentHash, Timestamp.from(Instant.now()));

        log.info("[RAG][INGEST] stored sourceId={}, title={}, chunks={}, hash={}",
                sourceId, title, totalChunks, contentHash);
    }

    @Override
    @Transactional(transactionManager = "ragTransactionManager")
    public void deleteBySourceId(String sourceId) {
        // 1. Remove chunks from vector store
        List<String> ids = ragJdbcTemplate.queryForList(
                "SELECT id FROM vector_store WHERE metadata ->> 'sourceId' = ?",
                String.class, sourceId);
        if (!ids.isEmpty()) {
            store.delete(ids);
            log.debug("[RAG][INGEST] removed {} chunks from vector store for sourceId={}",
                    ids.size(), sourceId);
        }

        // 2. Clear dedup record
        int deleted = ragJdbcTemplate.update(
                "DELETE FROM " + TABLE_DEDUP + " WHERE source_id = ?", sourceId);
        log.info("[RAG][INGEST] deleted sourceId={}, dedupRows={}", sourceId, deleted);
    }

    @Override
    @Transactional(transactionManager = "ragTransactionManager")
    public void updateDocument(String sourceId, String title, String content,
                               Map<String, Object> metadata) {
        deleteBySourceId(sourceId);
        ingestDocument(sourceId, title, content, metadata);
        log.info("[RAG][INGEST] updated sourceId={}", sourceId);
    }

    @Override
    @Transactional(transactionManager = "ragTransactionManager")
    public void addKnowledge(String text) {
        ingestDocument("knowledge-adhoc", "User Knowledge", text,
                Map.of("source", "knowledge"));
    }

    // ─── private helpers ───────────────────────────────────────

    private boolean isDuplicate(String sourceId, String contentHash) {
        Integer count = ragJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE_DEDUP
                + " WHERE source_id = ? AND content_hash = ?",
                Integer.class, sourceId, contentHash);
        return count != null && count > 0;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JVM — should never reach here
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}