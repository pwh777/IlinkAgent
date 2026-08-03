package com.fourth.ykd.ai.service.rag;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 负责从向量存储中检索知识的服务。
 * 不决定是否搜索 — 它总是尝试检索，当没有匹配时返回空结果。
 */
public interface RetrievalService {

    /**
     * 对向量存储执行相似度搜索。
     *
     * @param question 查询字符串
     * @return 按相似度降序排列的匹配文档
     */
    List<Document> search(String question);

    /**
     * 从检索到的文档构建知识上下文字符串。
     * 如果没有文档达到相似度阈值，则返回空字符串。
     *
     * @param question 查询字符串
     * @return 格式化后的知识提示片段，或空字符串
     */
    String getKnowledge(String question);
}