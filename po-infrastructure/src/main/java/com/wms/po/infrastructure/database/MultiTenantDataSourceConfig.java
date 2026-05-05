package com.wms.po.infrastructure.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-tenant data source configuration for V0/V2 database routing.
 * Supports per-country/region database connections as used in FbM WMS.
 */
@Configuration
@Slf4j
public class MultiTenantDataSourceConfig {

    @Value("${wms.datasource.v0.url:}")
    private String v0Url;

    @Value("${wms.datasource.v0.username:}")
    private String v0Username;

    @Value("${wms.datasource.v0.password:}")
    private String v0Password;

    @Value("${wms.datasource.v2.url:}")
    private String v2Url;

    @Value("${wms.datasource.v2.username:}")
    private String v2Username;

    @Value("${wms.datasource.v2.password:}")
    private String v2Password;

    // Connection pool settings
    @Value("${wms.datasource.pool.max-size:20}")
    private int maxPoolSize;

    @Value("${wms.datasource.pool.min-idle:5}")
    private int minIdle;

    @Value("${wms.datasource.pool.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${wms.datasource.pool.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${wms.datasource.pool.max-lifetime:1800000}")
    private long maxLifetime;

    // Cache of data sources per tenant
    private final Map<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();

    @Bean
    @Primary
    public DataSource multiTenantDataSource() {
        MultiTenantRoutingDataSource routingDataSource = new MultiTenantRoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();

        // Add V0 and V2 default data sources
        if (!v0Url.isEmpty()) {
            targetDataSources.put("V0", createDataSource("V0", v0Url, v0Username, v0Password));
            log.info("Configured V0 data source");
        }

        if (!v2Url.isEmpty()) {
            targetDataSources.put("V2", createDataSource("V2", v2Url, v2Username, v2Password));
            log.info("Configured V2 data source");
        }

        routingDataSource.setTargetDataSources(targetDataSources);

        // Default to V2
        if (!v2Url.isEmpty()) {
            routingDataSource.setDefaultTargetDataSource(targetDataSources.get("V2"));
        } else if (!v0Url.isEmpty()) {
            routingDataSource.setDefaultTargetDataSource(targetDataSources.get("V0"));
        }

        return routingDataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Get or create data source for a specific tenant/country
     */
    public DataSource getDataSourceForTenant(String tenantId, String dbVersion) {
        String key = tenantId + "_" + dbVersion;
        return tenantDataSources.computeIfAbsent(key, k -> {
            // In production, load tenant-specific config from configuration service
            String url = dbVersion.equals("V0") ? v0Url : v2Url;
            String username = dbVersion.equals("V0") ? v0Username : v2Username;
            String password = dbVersion.equals("V0") ? v0Password : v2Password;
            return createDataSource(k, url, username, password);
        });
    }

    private DataSource createDataSource(String name, String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("HikariPool-" + name);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

        // Pool settings
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);

        // SQL Server specific settings
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        // Health check
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);

        log.info("Creating data source: {} -> {}", name, url);
        return new HikariDataSource(config);
    }

    /**
     * Routing data source that switches based on current tenant context
     */
    public static class MultiTenantRoutingDataSource extends AbstractRoutingDataSource {

        @Override
        protected Object determineCurrentLookupKey() {
            return TenantContext.getCurrentTenant();
        }
    }
}
