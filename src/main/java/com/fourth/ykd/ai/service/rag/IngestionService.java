package com.fourth.ykd.ai.service.rag;

import java.util.Map;

/**
 * 知识文档写入/摄取服务，负责将知识文档分块、去重并以事务方式写入向量存储。
 */
public interface IngestionService {

    /**
     * 摄取文档：对文档进行分块，将块写入向量存储，并记录去重条目。
     * 当相同的 sourceId + content hash 已存在时跳过写入。
     *
     * @param sourceId 源文档的唯一标识
     * @param title    可读的标题
     * @param content  待分块存储的完整文本
     * @param metadata 附加到每个块的键值对元数据
     */
    //把知识放进RAG数据库
    void ingestDocument(String sourceId, String title, String content, Map<String, Object> metadata);

    /**
     * 从向量存储和去重表中移除属于指定源的所有块。
     *
     * @param sourceId 要清除的源
     */
    void deleteBySourceId(String sourceId);

    /**
     * 原子更新：先删除指定 sourceId 的现有块，然后用新内容重新摄取。
     *
     * @param sourceId 源文档的唯一标识
     * @param title    人类可读的标题
     * @param content  待分块存储的完整文本
     * @param metadata 附加到每个块的键值对元数据
     */
    void updateDocument(String sourceId, String title, String content, Map<String, Object> metadata);

    /**
     * 为向后兼容而保留的便捷方法。
     * 委托给 {@link #ingestDocument}，使用固定的 sourceId。
     *
     * @param text 原始知识文本
     */
    void addKnowledge(String text);
}