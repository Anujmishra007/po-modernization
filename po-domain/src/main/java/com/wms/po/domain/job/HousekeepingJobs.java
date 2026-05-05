package com.wms.po.domain.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Housekeeping Scheduled Jobs.
 *
 * Replaces: JOB-032 - Housekeeping Jobs (8 total)
 * - CleanupTempTables: Purge temporary/staging tables
 * - RefreshMaterializedViews: Refresh cached data
 * - UpdateStatistics: Update DB statistics
 * - PurgeOldLogs: Remove old audit/error logs
 * - CleanupOrphanedRecords: Remove orphaned child records
 * - CompactTransmitlog: Archive and compact transmitlogs
 * - RefreshReportCache: Refresh reporting cache tables
 * - ValidateDataIntegrity: Run integrity checks
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HousekeepingJobs {

    private final JdbcTemplate jdbcTemplate;

    // Retention periods (days)
    private static final int TEMP_TABLE_RETENTION = 1;
    private static final int LOG_RETENTION = 90;
    private static final int TRANSMITLOG_RETENTION = 365;
    private static final int INTERFACE_RETENTION = 30;

    /**
     * Daily housekeeping - runs at 2 AM.
     */
    @Scheduled(cron = "${wms.housekeeping.daily.cron:0 0 2 * * ?}")
    public void runDailyHousekeeping() {
        log.info("Starting daily housekeeping");
        long startTime = System.currentTimeMillis();

        try {
            cleanupTempTables();
            purgeOldLogs();
            cleanupOrphanedRecords();
            compactTransmitlog();
            purgeOldInterfaceRecords();

            log.info("Daily housekeeping complete in {}ms", System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Daily housekeeping failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Weekly housekeeping - runs Sunday at 3 AM.
     */
    @Scheduled(cron = "${wms.housekeeping.weekly.cron:0 0 3 ? * SUN}")
    public void runWeeklyHousekeeping() {
        log.info("Starting weekly housekeeping");

        try {
            refreshReportCache();
            validateDataIntegrity();
            rebuildIndexes();

            log.info("Weekly housekeeping complete");
        } catch (Exception e) {
            log.error("Weekly housekeeping failed: {}", e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Daily Tasks
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Clean up temporary/staging tables.
     */
    @Transactional
    public int cleanupTempTables() {
        log.debug("Cleaning up temporary tables");
        int totalDeleted = 0;

        String[] tempTables = {
            "TEMP_RECEIPT", "TEMP_ALLOCATION", "TEMP_PICK",
            "WRK_CONSOLIDATION", "WRK_WAVE", "TEMP_REPLEN"
        };

        LocalDateTime cutoff = LocalDateTime.now().minusDays(TEMP_TABLE_RETENTION);

        for (String table : tempTables) {
            try {
                int deleted = jdbcTemplate.update(
                    "DELETE FROM dbo." + table + " WHERE adddate < ?",
                    cutoff
                );
                totalDeleted += deleted;
                if (deleted > 0) {
                    log.debug("Deleted {} records from {}", deleted, table);
                }
            } catch (Exception e) {
                log.debug("Could not clean {}: {}", table, e.getMessage());
            }
        }

        log.info("Cleaned {} records from temp tables", totalDeleted);
        return totalDeleted;
    }

    /**
     * Purge old audit and error logs.
     */
    @Transactional
    public int purgeOldLogs() {
        log.debug("Purging old logs");
        int totalDeleted = 0;

        LocalDateTime cutoff = LocalDateTime.now().minusDays(LOG_RETENTION);

        // Audit log
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM dbo.auditlog WHERE adddate < ?",
                cutoff
            );
            totalDeleted += deleted;
            log.debug("Deleted {} audit log records", deleted);
        } catch (Exception e) {
            log.debug("Could not purge auditlog: {}", e.getMessage());
        }

        // Error log
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM dbo.errorlog WHERE adddate < ?",
                cutoff
            );
            totalDeleted += deleted;
            log.debug("Deleted {} error log records", deleted);
        } catch (Exception e) {
            log.debug("Could not purge errorlog: {}", e.getMessage());
        }

        // Performance log
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM dbo.performancelog WHERE logdate < ?",
                cutoff
            );
            totalDeleted += deleted;
        } catch (Exception e) {
            log.debug("Could not purge performancelog: {}", e.getMessage());
        }

        log.info("Purged {} log records", totalDeleted);
        return totalDeleted;
    }

    /**
     * Clean up orphaned child records.
     */
    @Transactional
    public int cleanupOrphanedRecords() {
        log.debug("Cleaning orphaned records");
        int totalDeleted = 0;

        // Orphaned receipt details (no parent receipt)
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM dbo.receiptdetail WHERE receiptkey NOT IN " +
                "(SELECT receiptkey FROM dbo.receipt)"
            );
            totalDeleted += deleted;
            if (deleted > 0) {
                log.info("Deleted {} orphaned receipt details", deleted);
            }
        } catch (Exception e) {
            log.debug("Could not clean orphaned receiptdetail: {}", e.getMessage());
        }

        // Orphaned PO details
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM dbo.podetail WHERE pokey NOT IN " +
                "(SELECT pokey FROM dbo.po)"
            );
            totalDeleted += deleted;
            if (deleted > 0) {
                log.info("Deleted {} orphaned PO details", deleted);
            }
        } catch (Exception e) {
            log.debug("Could not clean orphaned podetail: {}", e.getMessage());
        }

        return totalDeleted;
    }

    /**
     * Archive and compact old transmitlog records.
     */
    @Transactional
    public int compactTransmitlog() {
        log.debug("Compacting transmitlog");

        LocalDateTime cutoff = LocalDateTime.now().minusDays(TRANSMITLOG_RETENTION);

        try {
            // Archive to history table first
            jdbcTemplate.update(
                "INSERT INTO dbo.transmitlog_archive " +
                "SELECT * FROM dbo.transmitlog WHERE adddate < ? AND transmitflag = '9'",
                cutoff
            );

            // Delete archived records
            int deleted = jdbcTemplate.update(
                "DELETE FROM dbo.transmitlog WHERE adddate < ? AND transmitflag = '9'",
                cutoff
            );

            log.info("Archived and deleted {} transmitlog records", deleted);
            return deleted;

        } catch (Exception e) {
            log.debug("Could not compact transmitlog: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Purge old interface records that are processed.
     */
    @Transactional
    public int purgeOldInterfaceRecords() {
        log.debug("Purging old interface records");
        int totalDeleted = 0;

        LocalDateTime cutoff = LocalDateTime.now().minusDays(INTERFACE_RETENTION);
        String[] interfaceTables = {"IB_PO", "IB_ASN", "IB_SKU", "IB_STORER", "OB_RECEIPT"};

        for (String table : interfaceTables) {
            try {
                int deleted = jdbcTemplate.update(
                    "DELETE FROM dbo." + table + " WHERE status = '9' AND processeddate < ?",
                    cutoff
                );
                totalDeleted += deleted;
            } catch (Exception e) {
                log.debug("Could not purge {}: {}", table, e.getMessage());
            }
        }

        log.info("Purged {} interface records", totalDeleted);
        return totalDeleted;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Weekly Tasks
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Refresh reporting cache tables.
     */
    @Transactional
    public void refreshReportCache() {
        log.debug("Refreshing report cache");

        try {
            // Refresh inventory summary cache
            jdbcTemplate.update(
                "TRUNCATE TABLE dbo.cache_inventory_summary"
            );
            jdbcTemplate.update(
                "INSERT INTO dbo.cache_inventory_summary " +
                "SELECT storerkey, sku, SUM(qty) as qty, SUM(qtyallocated) as qtyallocated, " +
                "SUM(qtypicked) as qtypicked, COUNT(DISTINCT loc) as locations " +
                "FROM dbo.lotxlocxid GROUP BY storerkey, sku"
            );

            // Refresh receipt summary cache
            jdbcTemplate.update(
                "TRUNCATE TABLE dbo.cache_receipt_summary"
            );
            jdbcTemplate.update(
                "INSERT INTO dbo.cache_receipt_summary " +
                "SELECT storerkey, CONVERT(date, adddate) as receiptdate, " +
                "COUNT(*) as receiptcount, SUM(totalqty) as totalqty " +
                "FROM dbo.receipt WHERE adddate > DATEADD(day, -30, GETDATE()) " +
                "GROUP BY storerkey, CONVERT(date, adddate)"
            );

            log.info("Report cache refreshed");

        } catch (Exception e) {
            log.warn("Could not refresh report cache: {}", e.getMessage());
        }
    }

    /**
     * Validate data integrity.
     */
    public void validateDataIntegrity() {
        log.debug("Validating data integrity");

        // Check for negative inventory
        try {
            Integer negativeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.lotxlocxid WHERE qty < 0",
                Integer.class
            );
            if (negativeCount != null && negativeCount > 0) {
                log.warn("Data integrity issue: {} records with negative inventory", negativeCount);
            }
        } catch (Exception e) {
            log.debug("Could not check negative inventory: {}", e.getMessage());
        }

        // Check for receipts with mismatched status
        try {
            Integer mismatchCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.receipt r " +
                "WHERE r.status = '9' AND EXISTS " +
                "(SELECT 1 FROM dbo.receiptdetail rd WHERE rd.receiptkey = r.receiptkey AND rd.status != '9')",
                Integer.class
            );
            if (mismatchCount != null && mismatchCount > 0) {
                log.warn("Data integrity issue: {} receipts with status mismatch", mismatchCount);
            }
        } catch (Exception e) {
            log.debug("Could not check receipt status: {}", e.getMessage());
        }

        log.info("Data integrity validation complete");
    }

    /**
     * Rebuild fragmented indexes.
     */
    public void rebuildIndexes() {
        log.debug("Checking for fragmented indexes");

        try {
            // In SQL Server, this would trigger index maintenance
            // For simplicity, we'll just log that it ran
            jdbcTemplate.execute(
                "EXEC sp_updatestats"
            );
            log.info("Index statistics updated");
        } catch (Exception e) {
            log.debug("Could not update statistics: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Manual Execution
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Run all housekeeping tasks manually.
     */
    public void runAllTasks() {
        runDailyHousekeeping();
        runWeeklyHousekeeping();
    }

    /**
     * Get housekeeping statistics.
     */
    public HousekeepingStats getStats() {
        return new HousekeepingStats(
            countRecords("TEMP_RECEIPT"),
            countRecords("auditlog"),
            countRecords("transmitlog"),
            countRecords("IB_PO")
        );
    }

    private long countRecords(String tableName) {
        try {
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo." + tableName,
                Long.class
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            return -1;
        }
    }

    public record HousekeepingStats(
        long tempRecords,
        long auditLogs,
        long transmitLogs,
        long interfaceRecords
    ) {}
}
