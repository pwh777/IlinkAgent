package com.fourth.ykd.ai.config.ragpro;

import com.fourth.ykd.ai.service.rag.RagProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    @Bean
    public VectorStore vectorStore(
            @Qualifier("ragJdbcTemplate") JdbcTemplate ragJdbcTemplate,
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(ragJdbcTemplate, embeddingModel)
                .initializeSchema(true)
                .build();
    }

    //适用于摄入流水线的可配置基于 Token 的切分器。
    //参数源自 {@link RagProperties.Ingestion}。

    //作用是把长文拆成多个较小的文本块，方便生成向量从而进行精确检索
    @Bean
    public TokenTextSplitter tokenTextSplitter(RagProperties ragProperties) {
        return new TokenTextSplitter(
                ragProperties.getIngestion().getChunkSize(),
                350,   // 最小分块字符数
                5,     // 用于嵌入的最小分块长度
                10000, // 最大分块数量
                true   // 保留分隔符
        );
    }
}