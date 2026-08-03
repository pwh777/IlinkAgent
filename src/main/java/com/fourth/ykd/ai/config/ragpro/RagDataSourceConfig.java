package com.fourth.ykd.ai.config.ragpro;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

//RAG数据源配置
@Configuration
public class RagDataSourceConfig {

    //rag用于连接数据库
    @Bean
    @ConfigurationProperties("spring.datasource.rag")
    public DataSource ragDataSource() {
        return DataSourceBuilder
                .create()
                .build();
    }
    //给向量库操作提供jdbc支持
    @Bean
    public JdbcTemplate ragJdbcTemplate(
            @Qualifier("ragDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * RAG PostgreSQL 数据源的事务管理器。事务是数据库保证一组操作要么全部成功要么全部失败的机制
     * 在数据摄入方法上绑定 @Transactional(transactionManager = "ragTransactionManager")，
     * 以确保去重表操作与 PgVectorStore 写入操作具备原子性。
     */
    @Bean
    public PlatformTransactionManager ragTransactionManager(
            @Qualifier("ragDataSource") DataSource dataSource) {
        //绑定rag的datasource，使得公用同一个数据库事务上下文，要么全部成功要么全部失败回滚，保证了rag支持库状态一致
        return new DataSourceTransactionManager(dataSource);
    }
}