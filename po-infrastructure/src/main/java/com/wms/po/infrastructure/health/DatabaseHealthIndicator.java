package com.wms.po.infrastructure.health;

import com.wms.po.infrastructure.database.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Health indicator for V0/V2 database connections.
 */
@Component("v0v2DatabaseHealth")
@RequiredArgsConstructor
@Slf4j
public class DatabaseHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        boolean v0Healthy = true;
        boolean v2Healthy = true;

        // Check V2 database
        try {
            TenantContext.runWithTenant("V2", () -> {
                jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            });
            details.put("v2Database", "UP");
        } catch (Exception e) {
            log.warn("V2 database health check failed: {}", e.getMessage());
            details.put("v2Database", "DOWN: " + e.getMessage());
            v2Healthy = false;
        }

        // Check V0 database
        try {
            TenantContext.runWithTenant("V0", () -> {
                jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            });
            details.put("v0Database", "UP");
        } catch (Exception e) {
            log.warn("V0 database health check failed: {}", e.getMessage());
            details.put("v0Database", "DOWN: " + e.getMessage());
            v0Healthy = false;
        }

        // Overall health - at least one database must be up
        if (v2Healthy || v0Healthy) {
            details.put("primaryDatabase", v2Healthy ? "V2" : "V0");
            return Health.up().withDetails(details).build();
        } else {
            return Health.down().withDetails(details).build();
        }
    }
}
