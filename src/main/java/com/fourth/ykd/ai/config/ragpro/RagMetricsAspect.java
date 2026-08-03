package com.fourth.ykd.ai.config.ragpro;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RagMetricsAspect 就是给RAG加了一套“监控摄像头”，它不改变搜索逻辑，只负责记录每次RAG检索和入库的运行状态，让你知道系统运行得怎么样。
 * RAG 操作的可观测性切面。
 * <p>
 *拦截 {@code RetrievalService.search()} 和 {@code IngestionService.ingestDocument()}，
 * 输出包含方法名、耗时、关键参数、结果数量以及异常类型（若失败）的结构化 JSON 日志行。
 * </p>
 */

//就是一个实现了监控逻辑的监测类
@Slf4j
@Aspect
@Component
@Order(3)
public class RagMetricsAspect {
    //around环绕通知通过切点表达式来指定监控方法
    @Around("execution(* com.fourth.ykd.ai.service.rag.RetrievalService.search(..))")
    public Object aroundSearch(ProceedingJoinPoint pjp) throws Throwable {
        //记录开始时间
        long start = System.currentTimeMillis();
        //获取用户查询
        String query = pjp.getArgs().length > 0 ? String.valueOf(pjp.getArgs()[0]) : "?";
        try {
            //然后再放行之前的search用法
            @SuppressWarnings("unchecked")
            List<Document> result = (List<Document>) pjp.proceed();
            //执行成功计算耗时
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

    //还监控知识入库，逻辑同上
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