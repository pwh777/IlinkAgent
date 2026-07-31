package com.fourth.ykd.ai.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class RagDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.rag")
    public DataSource ragDataSource() {
        return DataSourceBuilder
                .create()
                .build();
    }

    @Bean
    public JdbcTemplate ragJdbcTemplate(
            @Qualifier("ragDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Transaction manager for the RAG PostgreSQL datasource.
     * Bind @Transactional(transactionManager = "ragTransactionManager")
     * on ingestion methods so that dedup-table operations and
     * PgVectorStore writes are atomic.
     */
    @Bean
    public PlatformTransactionManager ragTransactionManager(
            @Qualifier("ragDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}