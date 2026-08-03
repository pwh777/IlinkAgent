package com.fourth.ykd.ai.service.rag;

import com.fourth.ykd.ai.config.ragpro.CircuitBreaker;
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
import com.fourth.ykd.ai.service.rag.RagProperties.Retrieval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 pgvector 的检索服务实现。
 *
 * <ul>
 *   <li>配置来自 {@link RagProperties.Retrieval}</li>
 *   <li>search() 受 {@code @Retryable} 和 {@code @CircuitBreaker} 保护</li>
 *   <li>结果始终按相似度降序排列</li>
 *   <li>getKnowledge() 使用自代理调用，以便重试/断路器策略应用于内部搜索调用</li>
 *   <li>提示片段包含元数据来源和标题（如存在）</li>
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

    /** 自代理注入 — 允许 {@code getKnowledge()} 受益于 AOP。 */
    @Autowired
    @Lazy
    public void setSelf(RetrievalService self) {
        this.self = self;
    }

    // ─── 公共 API ──────────────────────────────────────────

    @Override
    //熔断器
    @CircuitBreaker(name = "rag-search", failureThreshold = 5, openTimeoutMs = 30000)
    //可重新尝试
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000))
    public List<Document> search(String question) {
        Retrieval r = ragProperties.getRetrieval();
        //创建搜索请求告诉向量数据库要查询对应的向量
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(r.getTopK())
                .similarityThreshold(r.getSimilarityThreshold())
                .build();

        List<Document> results = new ArrayList<>(store.similaritySearch(request));

        results.sort(Comparator.comparingDouble(
                (Document d) -> d.getScore() != null ? d.getScore() : 0.0
        ).reversed());

        log.debug("[RAG][SEARCH] query=\"{}\", topK={}, threshold={}, results={}",
                question, r.getTopK(), r.getSimilarityThreshold(), results.size());

        return results;
    }

    /**
     * {@code @Recover} 降级方法 — 当所有 {@code @Retryable} 尝试都耗尽时调用。
     * 返回空列表，使聊天管道不会收到 500 错误。
     */
    @Recover
    public List<Document> recoverSearch(Exception e, String question) {
        log.warn("[RAG][FALLBACK] all retries exhausted for query=\"{}\", error={}",
                question, e.getMessage());
        return Collections.emptyList();
    }

    @Override
    public String getKnowledge(String question) {
        // 使用自代理，使 @Retryable/@CircuitBreaker 应用于该调用。
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

    // ─── 私有辅助方法 ─────────────────────────────────────

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