package com.wms.po.infrastructure.health;

import com.wms.po.infrastructure.cache.DistributedLockService;
import com.wms.po.infrastructure.database.TenantContext;
import com.wms.po.infrastructure.transaction.SagaTransactionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Health indicator for PO service infrastructure.
 * Checks database connections, Redis, and saga state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class POHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SagaTransactionManager sagaManager;

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        boolean healthy = true;

        // Check database
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            details.put("database", "UP");
        } catch (Exception e) {
            log.error("Database health check failed", e);
            details.put("database", "DOWN: " + e.getMessage());
            healthy = false;
        }

        // Check Redis
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection().ping();
            details.put("redis", "UP");
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            details.put("redis", "DOWN: " + e.getMessage());
            healthy = false;
        }

        // Check active sagas
        Map<String, SagaTransactionManager.SagaState> activeSagas = sagaManager.getActiveSagas();
        details.put("activeSagas", activeSagas.size());

        // Check for stuck sagas (running > 30 minutes)
        long stuckThreshold = System.currentTimeMillis() - (30 * 60 * 1000);
        long stuckCount = activeSagas.values().stream()
                .filter(s -> s.getStartTime() < stuckThreshold)
                .count();
        if (stuckCount > 0) {
            details.put("stuckSagas", stuckCount);
            log.warn("Found {} potentially stuck sagas", stuckCount);
        }

        // Current tenant context (for debugging)
        String currentTenant = TenantContext.getCurrentTenant();
        if (currentTenant != null) {
            details.put("currentTenant", currentTenant);
        }

        if (healthy) {
            return Health.up().withDetails(details).build();
        } else {
            return Health.down().withDetails(details).build();
        }
    }
}
