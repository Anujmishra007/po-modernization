package com.wms.po.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Generic Outbound Kafka Producer.
 *
 * Replaces: JOB-013 GenericOutbound
 *
 * Polls TRANSMITLOG3 for pending outbound messages and publishes
 * them to appropriate Kafka topics for external system consumption.
 *
 * This replaces the polling-based SQL Job that checked for pending
 * interface records and sent them to external systems.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenericOutboundProducer {

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${wms.kafka.outbound.enabled:true}")
    private boolean enabled;

    @Value("${wms.kafka.outbound.batch-size:100}")
    private int batchSize;

    @Value("${wms.kafka.outbound.max-retries:3}")
    private int maxRetries;

    @Value("${wms.kafka.topics.outbound.asn:wms.outbound.asn}")
    private String asnOutboundTopic;

    @Value("${wms.kafka.topics.outbound.receipt:wms.outbound.receipt}")
    private String receiptOutboundTopic;

    @Value("${wms.kafka.topics.outbound.inventory:wms.outbound.inventory}")
    private String inventoryOutboundTopic;

    @Value("${wms.kafka.topics.outbound.po:wms.outbound.po}")
    private String poOutboundTopic;

    // Status constants
    private static final String STATUS_PENDING = "0";
    private static final String STATUS_PROCESSING = "1";
    private static final String STATUS_SENT = "9";
    private static final String STATUS_ERROR = "E";

    /**
     * Scheduled job to process pending outbound messages.
     * Runs every minute by default.
     */
    @Scheduled(fixedDelayString = "${wms.kafka.outbound.poll-interval:60000}")
    @Transactional
    public void processOutboundMessages() {
        if (!enabled) {
            return;
        }

        log.debug("Starting outbound message processing");

        try {
            // Find pending transmitlog records
            List<Map<String, Object>> pendingMessages = getPendingMessages();

            if (pendingMessages.isEmpty()) {
                log.debug("No pending outbound messages");
                return;
            }

            log.info("Processing {} pending outbound messages", pendingMessages.size());

            int successCount = 0;
            int errorCount = 0;

            for (Map<String, Object> message : pendingMessages) {
                String transmitlogKey = (String) message.get("transmitlogkey");
                String tableName = (String) message.get("tablename");

                try {
                    // Mark as processing
                    updateStatus(transmitlogKey, STATUS_PROCESSING);

                    // Process and send
                    boolean sent = processAndSend(message);

                    if (sent) {
                        updateStatus(transmitlogKey, STATUS_SENT);
                        successCount++;
                    } else {
                        handleError(transmitlogKey, "Failed to send message");
                        errorCount++;
                    }

                } catch (Exception e) {
                    log.error("Error processing transmitlog {}: {}", transmitlogKey, e.getMessage());
                    handleError(transmitlogKey, e.getMessage());
                    errorCount++;
                }
            }

            log.info("Outbound processing complete: {} sent, {} errors", successCount, errorCount);

        } catch (Exception e) {
            log.error("Outbound processing failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Get pending transmitlog records for processing.
     */
    private List<Map<String, Object>> getPendingMessages() {
        String sql = """
            SELECT TOP (?) t.transmitlogkey, t.tablename, t.keyvalue1, t.keyvalue2,
                   t.storerkey, t.facility, t.messagetype, t.adddate,
                   t.retrycount
            FROM dbo.TRANSMITLOG3 t
            WHERE t.status = ?
            AND (t.retrycount IS NULL OR t.retrycount < ?)
            ORDER BY t.adddate ASC
            """;

        return jdbcTemplate.queryForList(sql, batchSize, STATUS_PENDING, maxRetries);
    }

    /**
     * Process message and send to appropriate Kafka topic.
     */
    private boolean processAndSend(Map<String, Object> transmitlogRecord) throws Exception {
        String tableName = (String) transmitlogRecord.get("tablename");
        String keyValue = (String) transmitlogRecord.get("keyvalue1");
        String storerKey = (String) transmitlogRecord.get("storerkey");

        // Determine topic and build message based on table
        String topic = determineTopic(tableName);
        Map<String, Object> messagePayload = buildMessagePayload(tableName, keyValue, storerKey);

        if (messagePayload == null || messagePayload.isEmpty()) {
            log.warn("Empty payload for table={}, key={}", tableName, keyValue);
            return false;
        }

        // Add metadata
        messagePayload.put("transmitlogkey", transmitlogRecord.get("transmitlogkey"));
        messagePayload.put("timestamp", LocalDateTime.now().toString());
        messagePayload.put("source", "WMS");

        // Serialize and send
        String payload = objectMapper.writeValueAsString(messagePayload);
        kafkaTemplate.send(topic, keyValue, payload).get(); // Synchronous send

        log.debug("Sent message to topic={}, key={}", topic, keyValue);
        return true;
    }

    /**
     * Determine Kafka topic based on table name.
     */
    private String determineTopic(String tableName) {
        if (tableName == null) {
            return asnOutboundTopic; // Default
        }

        return switch (tableName.toUpperCase()) {
            case "RECEIPT", "RECEIPTDETAIL" -> asnOutboundTopic;
            case "PO", "PODETAIL" -> poOutboundTopic;
            case "LOTXLOCXID", "ITRN" -> inventoryOutboundTopic;
            default -> receiptOutboundTopic;
        };
    }

    /**
     * Build message payload based on table type.
     */
    private Map<String, Object> buildMessagePayload(String tableName, String keyValue, String storerKey) {
        Map<String, Object> payload = new HashMap<>();

        try {
            switch (tableName.toUpperCase()) {
                case "RECEIPT" -> payload = buildReceiptPayload(keyValue);
                case "RECEIPTDETAIL" -> payload = buildReceiptDetailPayload(keyValue);
                case "PO" -> payload = buildPOPayload(keyValue);
                case "LOTXLOCXID" -> payload = buildInventoryPayload(keyValue);
                default -> {
                    // Generic payload
                    payload.put("tableName", tableName);
                    payload.put("keyValue", keyValue);
                    payload.put("storerKey", storerKey);
                }
            }
        } catch (Exception e) {
            log.error("Error building payload for table={}, key={}: {}",
                tableName, keyValue, e.getMessage());
        }

        return payload;
    }

    /**
     * Build receipt (ASN) confirmation payload.
     */
    private Map<String, Object> buildReceiptPayload(String receiptKey) {
        Map<String, Object> receipt = jdbcTemplate.queryForMap(
            "SELECT receiptkey, externreceiptkey, storerkey, status, type, " +
            "adddate, editdate FROM dbo.RECEIPT WHERE receiptkey = ?",
            receiptKey
        );

        List<Map<String, Object>> details = jdbcTemplate.queryForList(
            "SELECT receiptlinenumber, sku, qtyexpected, qtyreceived, status " +
            "FROM dbo.RECEIPTDETAIL WHERE receiptkey = ?",
            receiptKey
        );

        Map<String, Object> payload = new HashMap<>(receipt);
        payload.put("messageType", "ASN_CONFIRMATION");
        payload.put("details", details);

        return payload;
    }

    /**
     * Build receipt detail payload.
     */
    private Map<String, Object> buildReceiptDetailPayload(String detailKey) {
        // Parse composite key if needed
        String[] parts = detailKey.split("-");
        String receiptKey = parts[0];
        int lineNum = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

        Map<String, Object> detail = jdbcTemplate.queryForMap(
            "SELECT rd.*, r.externreceiptkey " +
            "FROM dbo.RECEIPTDETAIL rd " +
            "JOIN dbo.RECEIPT r ON rd.receiptkey = r.receiptkey " +
            "WHERE rd.receiptkey = ? AND rd.receiptlinenumber = ?",
            receiptKey, lineNum
        );

        detail.put("messageType", "RECEIPT_DETAIL_CONFIRMATION");
        return detail;
    }

    /**
     * Build PO update payload.
     */
    private Map<String, Object> buildPOPayload(String poKey) {
        Map<String, Object> po = jdbcTemplate.queryForMap(
            "SELECT pokey, externpokey, storerkey, status, potype, " +
            "adddate, editdate FROM dbo.PO WHERE pokey = ?",
            poKey
        );

        List<Map<String, Object>> details = jdbcTemplate.queryForList(
            "SELECT polinenumber, sku, qtyordered, qtyreceived, openqty, status " +
            "FROM dbo.PODETAIL WHERE pokey = ?",
            poKey
        );

        Map<String, Object> payload = new HashMap<>(po);
        payload.put("messageType", "PO_UPDATE");
        payload.put("details", details);

        return payload;
    }

    /**
     * Build inventory update payload.
     */
    private Map<String, Object> buildInventoryPayload(String key) {
        // Key format: storerkey|sku|lot|loc|id
        String[] parts = key.split("\\|");

        Map<String, Object> payload = new HashMap<>();
        payload.put("messageType", "INVENTORY_UPDATE");
        payload.put("keyValue", key);

        if (parts.length >= 5) {
            payload.put("storerKey", parts[0]);
            payload.put("sku", parts[1]);
            payload.put("lot", parts[2]);
            payload.put("loc", parts[3]);
            payload.put("id", parts[4]);

            try {
                Map<String, Object> inv = jdbcTemplate.queryForMap(
                    "SELECT qty, qtyallocated, qtypicked, status " +
                    "FROM dbo.LOTXLOCXID " +
                    "WHERE storerkey = ? AND sku = ? AND lot = ? AND loc = ? AND id = ?",
                    parts[0], parts[1], parts[2], parts[3], parts[4]
                );
                payload.putAll(inv);
            } catch (Exception e) {
                log.debug("Inventory record not found: {}", key);
            }
        }

        return payload;
    }

    /**
     * Update transmitlog status.
     */
    private void updateStatus(String transmitlogKey, String status) {
        jdbcTemplate.update(
            "UPDATE dbo.TRANSMITLOG3 SET status = ?, transmitdate = GETDATE() " +
            "WHERE transmitlogkey = ?",
            status, transmitlogKey
        );
    }

    /**
     * Handle error and update transmitlog.
     */
    private void handleError(String transmitlogKey, String errorMessage) {
        jdbcTemplate.update(
            "UPDATE dbo.TRANSMITLOG3 SET status = ?, errormessage = ?, " +
            "retrycount = COALESCE(retrycount, 0) + 1, transmitdate = GETDATE() " +
            "WHERE transmitlogkey = ?",
            STATUS_ERROR, errorMessage, transmitlogKey
        );
    }

    /**
     * Manually trigger outbound processing (for testing/admin).
     */
    public int processNow() {
        if (!enabled) {
            return 0;
        }

        List<Map<String, Object>> pending = getPendingMessages();
        int processed = 0;

        for (Map<String, Object> message : pending) {
            String key = (String) message.get("transmitlogkey");
            try {
                updateStatus(key, STATUS_PROCESSING);
                if (processAndSend(message)) {
                    updateStatus(key, STATUS_SENT);
                    processed++;
                } else {
                    handleError(key, "Send failed");
                }
            } catch (Exception e) {
                handleError(key, e.getMessage());
            }
        }

        return processed;
    }
}
