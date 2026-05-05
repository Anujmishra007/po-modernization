package com.wms.po.api.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Data source configuration
 */
@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url:jdbc:h2:mem:testdb}")
    private String jdbcUrl;

    @Value("${spring.datasource.username:sa}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Value("${spring.datasource.driver-class-name:org.h2.Driver}")
    private String driverClassName;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);
        return dataSource;
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    // Legacy V0 data source (if separate database)
    @Bean("v0DataSource")
    @ConditionalOnProperty(name = "legacy.v0.enabled", havingValue = "true")
    public DataSource v0DataSource(
            @Value("${legacy.v0.url}") String url,
            @Value("${legacy.v0.username}") String user,
            @Value("${legacy.v0.password}") String pass) {

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(user);
        dataSource.setPassword(pass);
        dataSource.setMaximumPoolSize(5);
        return dataSource;
    }

    // Legacy V2 data source (if separate database)
    @Bean("v2DataSource")
    @ConditionalOnProperty(name = "legacy.v2.enabled", havingValue = "true")
    public DataSource v2DataSource(
            @Value("${legacy.v2.url}") String url,
            @Value("${legacy.v2.username}") String user,
            @Value("${legacy.v2.password}") String pass) {

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(user);
        dataSource.setPassword(pass);
        dataSource.setMaximumPoolSize(5);
        return dataSource;
    }
}
