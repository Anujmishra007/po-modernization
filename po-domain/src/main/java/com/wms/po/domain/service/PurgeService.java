package com.wms.po.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Purge Service for interface and temporary data cleanup.
 *
 * Replaces:
 * - JOB-021 Purge_Interface
 * - JOB-022 Purge_LOTxLOCxID (inventory cleanup)
 *
 * Handles permanent deletion of old interface data, temporary records,
 * and zero-quantity inventory records that are no longer needed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PurgeService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${wms.purge.enabled:true}")
    private boolean enabled;

    @Value("${wms.purge.interface-retention-days:90}")
    private int interfaceRetentionDays;

    @Value("${wms.purge.log-retention-days:30}")
    private int logRetentionDays;

    @Value("${wms.purge.batch-size:5000}")
    private int batchSize;

    @Value("${wms.purge.dry-run:false}")
    private boolean dryRun;

    /**
     * Scheduled daily purge job.
     * Runs at 3 AM by default.
     */
    @Scheduled(cron = "${wms.purge.cron:0 0 3 * * ?}")
    public void runScheduledPurge() {
        if (!enabled) {
            log.info("Purge service is disabled");
            return;
        }

        log.info("Starting scheduled purge job");
        PurgeResult result = purgeAll();
        log.info("Purge job completed: {}", result);
    }

    /**
     * Purge all eligible data.
     */
    public PurgeResult purgeAll() {
        PurgeResult result = new PurgeResult();
        result.startTime = LocalDateTime.now();

        try {
            // Purge interface staging tables
            int interfacePurged = purgeInterfaceTables();
            result.interfaceRecordsPurged = interfacePurged;

            // Purge zero-quantity inventory
            int inventoryPurged = purgeZeroInventory();
            result.inventoryRecordsPurged = inventoryPurged;

            // Purge old logs
            int logsPurged = purgeLogs();
            result.logRecordsPurged = logsPurged;

            // Purge temporary tables
            int tempPurged = purgeTempTables();
            result.tempRecordsPurged = tempPurged;

            result.success = true;

        } catch (Exception e) {
            log.error("Purge failed: {}", e.getMessage(), e);
            result.errors.add(e.getMessage());
            result.success = false;
        }

        result.endTime = LocalDateTime.now();
        return result;
    }

    /**
     * Purge old interface staging records.
     */
    @Transactional
    public int purgeInterfaceTables() {
        LocalDate cutoffDate = LocalDate.now().minusDays(interfaceRetentionDays);
        log.info("Purging interface tables older than: {}", cutoffDate);

        int totalPurged = 0;

        // List of interface staging tables to purge
        String[] interfaceTables = {
            "INBOUNDSTAGING_PO",
            "INBOUNDSTAGING_ASN",
            "INBOUNDSTAGING_ITEM",
            "OUTBOUNDSTAGING",
            "INTERFACELOG"
        };

        for (String table : interfaceTables) {
            try {
                int purged = purgeTable(table, "processeddate", cutoffDate);
                totalPurged += purged;
                log.debug("Purged {} records from {}", purged, table);
            } catch (Exception e) {
                log.warn("Failed to purge table {}: {}", table, e.getMessage());
            }
        }

        log.info("Total interface records purged: {}", totalPurged);
        return totalPurged;
    }

    /**
     * Purge zero-quantity inventory records.
     *
     * LOTxLOCxID records with qty=0 and no allocations can be safely removed
     * to improve query performance.
     */
    @Transactional
    public int purgeZeroInventory() {
        log.info("Purging zero-quantity inventory records");

        if (dryRun) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.LOTXLOCXID " +
                "WHERE qty = 0 AND COALESCE(qtyallocated, 0) = 0 " +
                "AND COALESCE(qtypicked, 0) = 0 " +
                "AND editdate < DATEADD(day, -7, GETDATE())",
                Integer.class
            );
            log.info("DRY RUN: Would purge {} zero inventory records", count);
            return count != null ? count : 0;
        }

        int totalPurged = 0;
        boolean hasMore = true;

        while (hasMore) {
            int purged = jdbcTemplate.update(
                "DELETE TOP (?) FROM dbo.LOTXLOCXID " +
                "WHERE qty = 0 AND COALESCE(qtyallocated, 0) = 0 " +
                "AND COALESCE(qtypicked, 0) = 0 " +
                "AND editdate < DATEADD(day, -7, GETDATE())",
                batchSize
            );

            totalPurged += purged;
            hasMore = (purged == batchSize);

            if (purged > 0) {
                log.debug("Purged {} zero inventory records in this batch", purged);
            }
        }

        log.info("Total zero inventory records purged: {}", totalPurged);
        return totalPurged;
    }

    /**
     * Purge old log records.
     */
    @Transactional
    public int purgeLogs() {
        LocalDate cutoffDate = LocalDate.now().minusDays(logRetentionDays);
        log.info("Purging logs older than: {}", cutoffDate);

        int totalPurged = 0;

        // Log tables to purge
        String[] logTables = {
            "INBOUNDLOG",
            "AUDITLOG",
            "ERRORLOG",
            "TASKLOG"
        };

        for (String table : logTables) {
            try {
                int purged = purgeTable(table, "adddate", cutoffDate);
                totalPurged += purged;
                log.debug("Purged {} records from {}", purged, table);
            } catch (Exception e) {
                log.warn("Failed to purge log table {}: {}", table, e.getMessage());
            }
        }

        log.info("Total log records purged: {}", totalPurged);
        return totalPurged;
    }

    /**
     * Purge temporary/work tables.
     */
    @Transactional
    public int purgeTempTables() {
        log.info("Purging temporary tables");

        int totalPurged = 0;

        // Temporary tables to purge (older than 1 day)
        String[] tempTables = {
            "TEMPRECEIPT",
            "TEMPRECEIPTDETAIL",
            "WORKRECEIPT",
            "WORKRECEIPTDETAIL"
        };

        for (String table : tempTables) {
            try {
                if (dryRun) {
                    Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM dbo." + table + " WHERE adddate < DATEADD(day, -1, GETDATE())",
                        Integer.class
                    );
                    log.info("DRY RUN: Would purge {} records from {}", count, table);
                    totalPurged += (count != null ? count : 0);
                } else {
                    int purged = jdbcTemplate.update(
                        "DELETE FROM dbo." + table + " WHERE adddate < DATEADD(day, -1, GETDATE())"
                    );
                    totalPurged += purged;
                    log.debug("Purged {} records from {}", purged, table);
                }
            } catch (Exception e) {
                log.debug("Temp table {} not found or error: {}", table, e.getMessage());
            }
        }

        log.info("Total temp records purged: {}", totalPurged);
        return totalPurged;
    }

    /**
     * Helper method to purge a table by date column.
     */
    private int purgeTable(String tableName, String dateColumn, LocalDate cutoffDate) {
        if (dryRun) {
            Integer count = jdbcTemplate.queryForObject(
                String.format("SELECT COUNT(*) FROM dbo.%s WHERE %s < ?", tableName, dateColumn),
                Integer.class, cutoffDate
            );
            return count != null ? count : 0;
        }

        int totalPurged = 0;
        boolean hasMore = true;

        while (hasMore) {
            int purged = jdbcTemplate.update(
                String.format("DELETE TOP (?) FROM dbo.%s WHERE %s < ?", tableName, dateColumn),
                batchSize, cutoffDate
            );

            totalPurged += purged;
            hasMore = (purged == batchSize);
        }

        return totalPurged;
    }

    /**
     * Purge result summary.
     */
    public static class PurgeResult {
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public boolean success;
        public int interfaceRecordsPurged;
        public int inventoryRecordsPurged;
        public int logRecordsPurged;
        public int tempRecordsPurged;
        public List<String> errors = new ArrayList<>();

        @Override
        public String toString() {
            return String.format(
                "PurgeResult{success=%s, interface=%d, inventory=%d, logs=%d, temp=%d, errors=%d}",
                success, interfaceRecordsPurged, inventoryRecordsPurged,
                logRecordsPurged, tempRecordsPurged, errors.size());
        }

        public int getTotalPurged() {
            return interfaceRecordsPurged + inventoryRecordsPurged +
                   logRecordsPurged + tempRecordsPurged;
        }
    }
}
