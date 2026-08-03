package com.fourth.ykd.ai.service.rag;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一的 RAG 配置属性，绑定在 {@code rag.*} 前缀下。
 *
 * <p>
 * 整合了之前分散的所有属性：
 * <ul>
 *   <li>{@code rag.retrieval.*} — top-k / 相似度阈值</li>
 *   <li>{@code rag.ingestion.*}  — 分块大小 / 重叠</li>
 *   <li>{@code rag.embedding.*}  — 嵌入模型名称</li>
 *   <li>{@code rag.rewrite.*}    — 查询重写器模型</li>
 * </ul>
 * </p>
 */
@Getter
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private final Retrieval retrieval = new Retrieval();
    private final Ingestion ingestion = new Ingestion();
    private final Embedding embedding = new Embedding();
    private final Rewrite rewrite = new Rewrite();

    // ─── 嵌套配置组 ──────────────────────────────

    //检索配置
    @Setter
    @Getter
    public static class Retrieval {
        //最多返回个数
        private int topK = 3;
        //相似度阈值（最低）
        private double similarityThreshold = 0.7;

    }

    //文本切割配置
    @Setter
    @Getter
    public static class Ingestion {
        //文本块字数
        private int chunkSize = 800;
        //防止一句话被切断设置重叠部分
        private int chunkOverlap = 100;

    }

    @Setter
    @Getter
    public static class Embedding {
        //文本变成向量的模型
        private String modelName = "text-embedding-v4";

    }

    @Setter
    @Getter
    public static class Rewrite {
        //用于查询改写
        private String model = "deepseek-chat";

    }
}