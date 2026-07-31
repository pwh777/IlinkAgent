package com.fourth.ykd.ai.config;

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

    /**
     * Configurable token-based splitter for the ingestion pipeline.
     * Parameters are sourced from {@link RagProperties.Ingestion}.
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter(RagProperties ragProperties) {
        return new TokenTextSplitter(
                ragProperties.getIngestion().getChunkSize(),
                350,   // minChunkSizeChars
                5,     // minChunkLengthToEmbed
                10000, // maxNumChunks
                true   // keepSeparator
        );
    }
}