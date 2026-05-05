package com.wms.po.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Inbound Master Service.
 *
 * Replaces: SQL Job - InboundMaster (JOB-010)
 * Schedule: Every 1 minute
 *
 * Processes incoming interface records from staging tables:
 * - IB_PO: Purchase Order imports
 * - IB_ASN: ASN/Receipt imports
 * - IB_SKU: SKU master imports
 * - IB_STORER: Storer/Client imports
 *
 * Processing flow:
 * 1. Fetch pending interface records (status = '0')
 * 2. Validate record structure
 * 3. Process/transform data
 * 4. Create WMS entities
 * 5. Update interface status ('9' = processed, 'E' = error)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundMasterService {

    private final JdbcTemplate jdbcTemplate;

    private static final String STATUS_PENDING = "0";
    private static final String STATUS_PROCESSING = "1";
    private static final String STATUS_PROCESSED = "9";
    private static final String STATUS_ERROR = "E";

    private static final int BATCH_SIZE = 100;

    /**
     * Main inbound processing job - runs every minute.
     */
    @Scheduled(fixedDelayString = "${wms.inbound.interval:60000}")
    public void processInboundInterfaces() {
        log.debug("Starting inbound interface processing");

        long startTime = System.currentTimeMillis();
        int totalProcessed = 0;

        try {
            // Process each interface type
            totalProcessed += processPOInterface();
            totalProcessed += processASNInterface();
            totalProcessed += processSKUInterface();
            totalProcessed += processStorerInterface();

            if (totalProcessed > 0) {
                log.info("Inbound processing complete: {} records in {}ms",
                    totalProcessed, System.currentTimeMillis() - startTime);
            }

        } catch (Exception e) {
            log.error("Inbound processing failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Process PO interface records.
     */
    @Transactional
    public int processPOInterface() {
        List<Map<String, Object>> records = fetchPendingRecords("IB_PO");
        int processed = 0;

        for (Map<String, Object> record : records) {
            String interfaceKey = (String) record.get("interfacekey");
            try {
                markProcessing("IB_PO", interfaceKey);

                // Validate and transform
                validatePORecord(record);

                // Create PO header
                String poKey = createPOFromInterface(record);

                // Mark success
                markProcessed("IB_PO", interfaceKey, poKey);
                processed++;

            } catch (Exception e) {
                log.error("Failed to process IB_PO {}: {}", interfaceKey, e.getMessage());
                markError("IB_PO", interfaceKey, e.getMessage());
            }
        }

        return processed;
    }

    /**
     * Process ASN interface records.
     */
    @Transactional
    public int processASNInterface() {
        List<Map<String, Object>> records = fetchPendingRecords("IB_ASN");
        int processed = 0;

        for (Map<String, Object> record : records) {
            String interfaceKey = (String) record.get("interfacekey");
            try {
                markProcessing("IB_ASN", interfaceKey);

                // Validate and transform
                validateASNRecord(record);

                // Create receipt
                String receiptKey = createReceiptFromInterface(record);

                // Mark success
                markProcessed("IB_ASN", interfaceKey, receiptKey);
                processed++;

            } catch (Exception e) {
                log.error("Failed to process IB_ASN {}: {}", interfaceKey, e.getMessage());
                markError("IB_ASN", interfaceKey, e.getMessage());
            }
        }

        return processed;
    }

    /**
     * Process SKU interface records.
     */
    @Transactional
    public int processSKUInterface() {
        List<Map<String, Object>> records = fetchPendingRecords("IB_SKU");
        int processed = 0;

        for (Map<String, Object> record : records) {
            String interfaceKey = (String) record.get("interfacekey");
            try {
                markProcessing("IB_SKU", interfaceKey);

                // Upsert SKU
                upsertSKU(record);

                markProcessed("IB_SKU", interfaceKey, null);
                processed++;

            } catch (Exception e) {
                log.error("Failed to process IB_SKU {}: {}", interfaceKey, e.getMessage());
                markError("IB_SKU", interfaceKey, e.getMessage());
            }
        }

        return processed;
    }

    /**
     * Process Storer interface records.
     */
    @Transactional
    public int processStorerInterface() {
        List<Map<String, Object>> records = fetchPendingRecords("IB_STORER");
        int processed = 0;

        for (Map<String, Object> record : records) {
            String interfaceKey = (String) record.get("interfacekey");
            try {
                markProcessing("IB_STORER", interfaceKey);

                // Upsert Storer
                upsertStorer(record);

                markProcessed("IB_STORER", interfaceKey, null);
                processed++;

            } catch (Exception e) {
                log.error("Failed to process IB_STORER {}: {}", interfaceKey, e.getMessage());
                markError("IB_STORER", interfaceKey, e.getMessage());
            }
        }

        return processed;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private List<Map<String, Object>> fetchPendingRecords(String tableName) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT TOP " + BATCH_SIZE + " * FROM dbo." + tableName +
                " WHERE status = ? ORDER BY adddate",
                STATUS_PENDING
            );
        } catch (Exception e) {
            log.debug("Could not fetch from {}: {}", tableName, e.getMessage());
            return List.of();
        }
    }

    private void markProcessing(String tableName, String interfaceKey) {
        jdbcTemplate.update(
            "UPDATE dbo." + tableName + " SET status = ?, editdate = GETDATE() " +
            "WHERE interfacekey = ?",
            STATUS_PROCESSING, interfaceKey
        );
    }

    private void markProcessed(String tableName, String interfaceKey, String createdKey) {
        jdbcTemplate.update(
            "UPDATE dbo." + tableName + " SET status = ?, processedkey = ?, " +
            "processeddate = GETDATE(), editdate = GETDATE() WHERE interfacekey = ?",
            STATUS_PROCESSED, createdKey, interfaceKey
        );
    }

    private void markError(String tableName, String interfaceKey, String errorMsg) {
        String truncatedMsg = errorMsg != null && errorMsg.length() > 500
            ? errorMsg.substring(0, 500) : errorMsg;
        jdbcTemplate.update(
            "UPDATE dbo." + tableName + " SET status = ?, errormessage = ?, " +
            "editdate = GETDATE() WHERE interfacekey = ?",
            STATUS_ERROR, truncatedMsg, interfaceKey
        );
    }

    private void validatePORecord(Map<String, Object> record) {
        String storerKey = (String) record.get("storerkey");
        String externalKey = (String) record.get("externpokey");

        if (storerKey == null || storerKey.isEmpty()) {
            throw new IllegalArgumentException("StorerKey is required");
        }
        if (externalKey == null || externalKey.isEmpty()) {
            throw new IllegalArgumentException("ExternalPOKey is required");
        }
    }

    private void validateASNRecord(Map<String, Object> record) {
        String storerKey = (String) record.get("storerkey");
        String externalKey = (String) record.get("externreceiptkey");

        if (storerKey == null || storerKey.isEmpty()) {
            throw new IllegalArgumentException("StorerKey is required");
        }
        if (externalKey == null || externalKey.isEmpty()) {
            throw new IllegalArgumentException("ExternalReceiptKey is required");
        }
    }

    private String createPOFromInterface(Map<String, Object> record) {
        String poKey = generateKey("PO");

        jdbcTemplate.update(
            "INSERT INTO dbo.po (pokey, storerkey, externpokey, podate, " +
            "expectedreceiptdate, status, adddate, addwho) " +
            "VALUES (?, ?, ?, GETDATE(), ?, '0', GETDATE(), 'INTERFACE')",
            poKey,
            record.get("storerkey"),
            record.get("externpokey"),
            record.get("expectedreceiptdate")
        );

        return poKey;
    }

    private String createReceiptFromInterface(Map<String, Object> record) {
        String receiptKey = generateKey("RECEIPT");

        jdbcTemplate.update(
            "INSERT INTO dbo.receipt (receiptkey, storerkey, externreceiptkey, " +
            "type, status, adddate, addwho) " +
            "VALUES (?, ?, ?, 'ASN', '0', GETDATE(), 'INTERFACE')",
            receiptKey,
            record.get("storerkey"),
            record.get("externreceiptkey")
        );

        return receiptKey;
    }

    private void upsertSKU(Map<String, Object> record) {
        String storerKey = (String) record.get("storerkey");
        String sku = (String) record.get("sku");

        // Check if exists
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dbo.sku WHERE storerkey = ? AND sku = ?",
            Integer.class,
            storerKey, sku
        );

        if (count != null && count > 0) {
            // Update
            jdbcTemplate.update(
                "UPDATE dbo.sku SET descr = ?, stdcube = ?, stdgrosswgt = ?, " +
                "editdate = GETDATE(), editwho = 'INTERFACE' " +
                "WHERE storerkey = ? AND sku = ?",
                record.get("descr"),
                record.get("stdcube"),
                record.get("stdgrosswgt"),
                storerKey, sku
            );
        } else {
            // Insert
            jdbcTemplate.update(
                "INSERT INTO dbo.sku (storerkey, sku, descr, stdcube, stdgrosswgt, " +
                "adddate, addwho) VALUES (?, ?, ?, ?, ?, GETDATE(), 'INTERFACE')",
                storerKey, sku,
                record.get("descr"),
                record.get("stdcube"),
                record.get("stdgrosswgt")
            );
        }
    }

    private void upsertStorer(Map<String, Object> record) {
        String storerKey = (String) record.get("storerkey");

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dbo.storer WHERE storerkey = ?",
            Integer.class,
            storerKey
        );

        if (count != null && count > 0) {
            jdbcTemplate.update(
                "UPDATE dbo.storer SET company = ?, address1 = ?, city = ?, " +
                "editdate = GETDATE(), editwho = 'INTERFACE' WHERE storerkey = ?",
                record.get("company"),
                record.get("address1"),
                record.get("city"),
                storerKey
            );
        } else {
            jdbcTemplate.update(
                "INSERT INTO dbo.storer (storerkey, type, company, address1, city, " +
                "adddate, addwho) VALUES (?, '1', ?, ?, ?, GETDATE(), 'INTERFACE')",
                storerKey,
                record.get("company"),
                record.get("address1"),
                record.get("city")
            );
        }
    }

    private String generateKey(String tableName) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT dbo.nspg_GetKey(?, 1)",
                String.class,
                tableName
            );
        } catch (Exception e) {
            return tableName + "_" + System.currentTimeMillis();
        }
    }

    /**
     * Reprocess failed interface records.
     */
    @Transactional
    public int reprocessFailedRecords(String tableName, int maxRecords) {
        jdbcTemplate.update(
            "UPDATE TOP (" + maxRecords + ") dbo." + tableName +
            " SET status = ?, errormessage = NULL, editdate = GETDATE() " +
            "WHERE status = ?",
            STATUS_PENDING, STATUS_ERROR
        );

        return maxRecords;
    }

    /**
     * Get interface statistics.
     */
    public Map<String, InterfaceStats> getInterfaceStats() {
        return Map.of(
            "IB_PO", getStatsForTable("IB_PO"),
            "IB_ASN", getStatsForTable("IB_ASN"),
            "IB_SKU", getStatsForTable("IB_SKU"),
            "IB_STORER", getStatsForTable("IB_STORER")
        );
    }

    private InterfaceStats getStatsForTable(String tableName) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT " +
                "SUM(CASE WHEN status = '0' THEN 1 ELSE 0 END) as pending, " +
                "SUM(CASE WHEN status = '1' THEN 1 ELSE 0 END) as processing, " +
                "SUM(CASE WHEN status = '9' THEN 1 ELSE 0 END) as processed, " +
                "SUM(CASE WHEN status = 'E' THEN 1 ELSE 0 END) as error " +
                "FROM dbo." + tableName,
                (rs, rowNum) -> new InterfaceStats(
                    rs.getInt("pending"),
                    rs.getInt("processing"),
                    rs.getInt("processed"),
                    rs.getInt("error")
                )
            );
        } catch (Exception e) {
            return new InterfaceStats(0, 0, 0, 0);
        }
    }

    public record InterfaceStats(int pending, int processing, int processed, int error) {
        public int total() {
            return pending + processing + processed + error;
        }
    }
}
