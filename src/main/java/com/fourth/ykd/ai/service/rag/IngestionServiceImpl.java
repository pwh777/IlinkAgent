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
 * 摄取管道实现。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>计算完整文档内容的 SHA-256 哈希值。</li>
 *   <li>通过 {@code sourceId + hash} 检查去重表（{@code rag_ingestion_dedup}）。</li>
 *   <li>如果重复，则静默跳过。</li>
 *   <li>否则通过 {@link TokenTextSplitter} 分块，分配每块的元数据
 *       （{@code sourceId, title, chunkIndex, totalChunks, ingestedAt}），
 *       存储到 pgvector，并记录哈希值。</li>
 * </ol>
 *
 * <h3>事务范围</h3>
 * 所有写入方法都绑定到 {@code ragTransactionManager}，
 * 以确保向量存储写入和去重表变更是原子性的。
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

    // ─── 公共 API ────────────────────────────────────────────

    @Override
    @Transactional(transactionManager = "ragTransactionManager")
    public void ingestDocument(String sourceId, String title, String content,
                               Map<String, Object> metadata) {
        //计算HASH判断是不是同一份文件
        String contentHash = sha256(content);
        //查表查重内容
        if (isDuplicate(sourceId, contentHash)) {
            log.debug("[RAG][INGEST] duplicate skipped: sourceId={}, hash={}", sourceId, contentHash);
            return;
        }

        Map<String, Object> baseMeta = new HashMap<>(
                metadata != null ? metadata : Map.of());
        Document raw = new Document(content, baseMeta);
        //切块
        List<Document> chunks = splitter.split(List.of(raw));

        String ingestedAt = Instant.now().toString();
        int totalChunks = chunks.size();
        //知道知识的来源
        for (int i = 0; i < totalChunks; i++) {
            Document chunk = chunks.get(i);
            chunk.getMetadata().put("sourceId", sourceId);
            chunk.getMetadata().put("title", title);
            chunk.getMetadata().put("chunkIndex", i);
            chunk.getMetadata().put("totalChunks", totalChunks);
            chunk.getMetadata().put("ingestedAt", ingestedAt);
        }
        //写入向量库
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
        // 1. 从向量存储中移除块
        List<String> ids = ragJdbcTemplate.queryForList(
                "SELECT id FROM vector_store WHERE metadata ->> 'sourceId' = ?",
                String.class, sourceId);
        if (!ids.isEmpty()) {
            store.delete(ids);
            log.debug("[RAG][INGEST] removed {} chunks from vector store for sourceId={}",
                    ids.size(), sourceId);
        }

        // 2. 清除去重记录
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

    // ─── 私有辅助方法 ───────────────────────────────────────

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
            // SHA-256 在每个 JVM 中都是必需的 — 不应该执行到这里
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}