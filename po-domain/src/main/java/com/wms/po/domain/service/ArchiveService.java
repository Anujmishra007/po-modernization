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
 * Archive Service for WMS data lifecycle management.
 *
 * Replaces: JOB-020 Archive_WMS
 *
 * Handles archiving of historical data from main tables to archive tables.
 * This is essential for maintaining database performance while preserving
 * historical data for audit and reporting purposes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArchiveService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${wms.archive.enabled:true}")
    private boolean enabled;

    @Value("${wms.archive.receipt-retention-days:90}")
    private int receiptRetentionDays;

    @Value("${wms.archive.po-retention-days:365}")
    private int poRetentionDays;

    @Value("${wms.archive.batch-size:1000}")
    private int batchSize;

    @Value("${wms.archive.dry-run:false}")
    private boolean dryRun;

    /**
     * Scheduled daily archive job.
     * Runs at 2 AM by default.
     */
    @Scheduled(cron = "${wms.archive.cron:0 0 2 * * ?}")
    public void runScheduledArchive() {
        if (!enabled) {
            log.info("Archive service is disabled");
            return;
        }

        log.info("Starting scheduled archive job");
        ArchiveResult result = archiveAll();
        log.info("Archive job completed: {}", result);
    }

    /**
     * Archive all eligible data.
     */
    public ArchiveResult archiveAll() {
        ArchiveResult result = new ArchiveResult();
        result.startTime = LocalDateTime.now();

        try {
            // Archive closed receipts
            int receiptsArchived = archiveReceipts();
            result.receiptsArchived = receiptsArchived;

            // Archive closed POs
            int posArchived = archivePOs();
            result.posArchived = posArchived;

            // Archive transmitlog
            int transmitlogsArchived = archiveTransmitlogs();
            result.transmitlogsArchived = transmitlogsArchived;

            result.success = true;

        } catch (Exception e) {
            log.error("Archive failed: {}", e.getMessage(), e);
            result.errors.add(e.getMessage());
            result.success = false;
        }

        result.endTime = LocalDateTime.now();
        return result;
    }

    /**
     * Archive closed receipts older than retention period.
     */
    @Transactional
    public int archiveReceipts() {
        LocalDate cutoffDate = LocalDate.now().minusDays(receiptRetentionDays);
        log.info("Archiving receipts closed before: {}", cutoffDate);

        if (dryRun) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.RECEIPT WHERE status = '9' AND closedate < ?",
                Integer.class, cutoffDate
            );
            log.info("DRY RUN: Would archive {} receipts", count);
            return count != null ? count : 0;
        }

        int totalArchived = 0;
        boolean hasMore = true;

        while (hasMore) {
            // Get batch of receipt keys to archive
            List<String> receiptKeys = jdbcTemplate.queryForList(
                "SELECT TOP (?) receiptkey FROM dbo.RECEIPT " +
                "WHERE status = '9' AND closedate < ? " +
                "ORDER BY closedate",
                String.class, batchSize, cutoffDate
            );

            if (receiptKeys.isEmpty()) {
                hasMore = false;
                continue;
            }

            for (String receiptKey : receiptKeys) {
                try {
                    archiveReceipt(receiptKey);
                    totalArchived++;
                } catch (Exception e) {
                    log.error("Failed to archive receipt {}: {}", receiptKey, e.getMessage());
                }
            }

            log.debug("Archived {} receipts in this batch", receiptKeys.size());
        }

        log.info("Total receipts archived: {}", totalArchived);
        return totalArchived;
    }

    /**
     * Archive a single receipt with its details.
     */
    @Transactional
    public void archiveReceipt(String receiptKey) {
        // Copy to archive table
        jdbcTemplate.update(
            "INSERT INTO dbo.RECEIPT_ARCHIVE SELECT *, GETDATE() as archivedate FROM dbo.RECEIPT WHERE receiptkey = ?",
            receiptKey
        );

        jdbcTemplate.update(
            "INSERT INTO dbo.RECEIPTDETAIL_ARCHIVE SELECT *, GETDATE() as archivedate FROM dbo.RECEIPTDETAIL WHERE receiptkey = ?",
            receiptKey
        );

        // Delete from main tables
        jdbcTemplate.update("DELETE FROM dbo.RECEIPTDETAIL WHERE receiptkey = ?", receiptKey);
        jdbcTemplate.update("DELETE FROM dbo.RECEIPT WHERE receiptkey = ?", receiptKey);
    }

    /**
     * Archive closed POs older than retention period.
     */
    @Transactional
    public int archivePOs() {
        LocalDate cutoffDate = LocalDate.now().minusDays(poRetentionDays);
        log.info("Archiving POs closed before: {}", cutoffDate);

        if (dryRun) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.PO WHERE status = '9' AND closedate < ?",
                Integer.class, cutoffDate
            );
            log.info("DRY RUN: Would archive {} POs", count);
            return count != null ? count : 0;
        }

        int totalArchived = 0;
        boolean hasMore = true;

        while (hasMore) {
            List<String> poKeys = jdbcTemplate.queryForList(
                "SELECT TOP (?) pokey FROM dbo.PO " +
                "WHERE status = '9' AND closedate < ? " +
                "ORDER BY closedate",
                String.class, batchSize, cutoffDate
            );

            if (poKeys.isEmpty()) {
                hasMore = false;
                continue;
            }

            for (String poKey : poKeys) {
                try {
                    archivePO(poKey);
                    totalArchived++;
                } catch (Exception e) {
                    log.error("Failed to archive PO {}: {}", poKey, e.getMessage());
                }
            }
        }

        log.info("Total POs archived: {}", totalArchived);
        return totalArchived;
    }

    /**
     * Archive a single PO with its details.
     */
    @Transactional
    public void archivePO(String poKey) {
        // Copy to archive table
        jdbcTemplate.update(
            "INSERT INTO dbo.PO_ARCHIVE SELECT *, GETDATE() as archivedate FROM dbo.PO WHERE pokey = ?",
            poKey
        );

        jdbcTemplate.update(
            "INSERT INTO dbo.PODETAIL_ARCHIVE SELECT *, GETDATE() as archivedate FROM dbo.PODETAIL WHERE pokey = ?",
            poKey
        );

        // Delete from main tables
        jdbcTemplate.update("DELETE FROM dbo.PODETAIL WHERE pokey = ?", poKey);
        jdbcTemplate.update("DELETE FROM dbo.PO WHERE pokey = ?", poKey);
    }

    /**
     * Archive old transmitlog records.
     */
    @Transactional
    public int archiveTransmitlogs() {
        LocalDate cutoffDate = LocalDate.now().minusDays(30); // 30 days for transmitlog
        log.info("Archiving transmitlogs before: {}", cutoffDate);

        if (dryRun) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.TRANSMITLOG3 WHERE status = '9' AND transmitdate < ?",
                Integer.class, cutoffDate
            );
            log.info("DRY RUN: Would archive {} transmitlogs", count);
            return count != null ? count : 0;
        }

        // Archive in batches
        int archived = jdbcTemplate.update(
            "INSERT INTO dbo.TRANSMITLOG3_ARCHIVE " +
            "SELECT TOP (?) *, GETDATE() as archivedate FROM dbo.TRANSMITLOG3 " +
            "WHERE status = '9' AND transmitdate < ?",
            batchSize * 10, cutoffDate
        );

        // Delete archived records
        jdbcTemplate.update(
            "DELETE FROM dbo.TRANSMITLOG3 " +
            "WHERE transmitlogkey IN (SELECT transmitlogkey FROM dbo.TRANSMITLOG3_ARCHIVE " +
            "WHERE archivedate >= DATEADD(minute, -5, GETDATE()))"
        );

        log.info("Transmitlogs archived: {}", archived);
        return archived;
    }

    /**
     * Archive result summary.
     */
    public static class ArchiveResult {
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public boolean success;
        public int receiptsArchived;
        public int posArchived;
        public int transmitlogsArchived;
        public List<String> errors = new ArrayList<>();

        @Override
        public String toString() {
            return String.format("ArchiveResult{success=%s, receipts=%d, pos=%d, transmitlogs=%d, errors=%d}",
                success, receiptsArchived, posArchived, transmitlogsArchived, errors.size());
        }
    }
}
