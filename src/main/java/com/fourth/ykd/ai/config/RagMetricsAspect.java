package com.fourth.ykd.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Observability aspect for RAG operations.
 * <p>
 * Intercepts {@code RetrievalService.search()} and
 * {@code IngestionService.ingestDocument()}, outputting structured JSON
 * log lines with method name, duration, key parameters, result size,
 * and exception type (on failure).
 * </p>
 */
@Slf4j
@Aspect
@Component
@Order(3)
public class RagMetricsAspect {

    @Around("execution(* com.fourth.ykd.ai.service.rag.RetrievalService.search(..))")
    public Object aroundSearch(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String query = pjp.getArgs().length > 0 ? String.valueOf(pjp.getArgs()[0]) : "?";
        try {
            @SuppressWarnings("unchecked")
            List<Document> result = (List<Document>) pjp.proceed();
            long duration = System.currentTimeMillis() - start;
            log.info(metricJson("search", duration,
                    "query", truncate(query, 200),
                    "resultCount", result.size(),
                    "status", "success"));
            return result;
        } catch (Throwable t) {
            long duration = System.currentTimeMillis() - start;
            log.error(metricJson("search", duration,
                    "query", truncate(query, 200),
                    "status", "error",
                    "errorType", t.getClass().getSimpleName()));
            throw t;
        }
    }

    @Around("execution(* com.fourth.ykd.ai.service.rag.IngestionService.ingestDocument(..))")
    public Object aroundIngestDocument(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String sourceId = pjp.getArgs().length > 0 ? String.valueOf(pjp.getArgs()[0]) : "?";
        try {
            pjp.proceed();  // void return
            long duration = System.currentTimeMillis() - start;
            log.info(metricJson("ingestDocument", duration,
                    "sourceId", sourceId,
                    "status", "success"));
            return null;
        } catch (Throwable t) {
            long duration = System.currentTimeMillis() - start;
            log.error(metricJson("ingestDocument", duration,
                    "sourceId", sourceId,
                    "status", "error",
                    "errorType", t.getClass().getSimpleName()));
            throw t;
        }
    }

    // ─── helpers ────────────────────────────────────────────

    private static String metricJson(String method, long durationMs, Object... kvPairs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"event\":\"rag.metrics\",\"method\":\"").append(method).append("\"");
        sb.append(",\"durationMs\":").append(durationMs);
        for (int i = 0; i < kvPairs.length; i += 2) {
            sb.append(",\"").append(kvPairs[i]).append("\":");
            Object v = kvPairs[i + 1];
            if (v instanceof Number) {
                sb.append(v);
            } else {
                sb.append("\"").append(escape(String.valueOf(v))).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}