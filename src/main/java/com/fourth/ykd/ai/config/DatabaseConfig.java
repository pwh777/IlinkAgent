package com.fourth.ykd.ai.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {


    @Bean(name = "sqliteDataSource")
    @ConfigurationProperties("spring.datasource")
    public DataSource sqliteDataSource() {
        return DataSourceBuilder.create().build();
    }


    @Bean(name = "sqliteJdbcTemplate")
    public JdbcTemplate sqliteJdbcTemplate(
            @Qualifier("sqliteDataSource") DataSource dataSource) {

        return new JdbcTemplate(dataSource);
    }

}
