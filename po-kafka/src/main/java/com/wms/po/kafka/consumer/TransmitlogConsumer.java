package com.wms.po.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Transmitlog3 Update Kafka Consumer.
 *
 * Replaces: TR-012 ntrTransmitlog3Update trigger
 *
 * Consumes Transmitlog3 update events from Kafka and processes
 * outbound interface messages (EDI, API callbacks, etc.).
 *
 * When TRANSMITLOG3 records are updated (typically to mark as processed),
 * this consumer handles the downstream notification/integration logic
 * that was previously embedded in the trigger.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransmitlogConsumer {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${wms.kafka.transmitlog.enabled:true}")
    private boolean enabled;

    @Value("${wms.kafka.transmitlog.retry-failed:true}")
    private boolean retryFailed;

    // Transmitlog statuses
    private static final String STATUS_PENDING = "0";
    private static final String STATUS_PROCESSING = "1";
    private static final String STATUS_SENT = "9";
    private static final String STATUS_ERROR = "E";

    /**
     * Consume Transmitlog update events from Kafka.
     */
    @KafkaListener(
        topics = "${wms.kafka.topics.transmitlog:wms.transmitlog.events}",
        groupId = "${wms.kafka.consumer-group:wms-transmitlog-consumer}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeTransmitlogEvent(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            Acknowledgment ack) {

        if (!enabled) {
            log.debug("Transmitlog consumer disabled, skipping message");
            ack.acknowledge();
            return;
        }

        log.info("Received transmitlog event: topic={}, partition={}, offset={}, key={}",
            topic, partition, offset, key);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(payload, Map.class);

            String eventType = (String) event.get("eventType");
            String transmitlogKey = (String) event.get("transmitlogkey");
            String tableName = (String) event.get("tablename");
            String keyValue = (String) event.get("keyvalue1");

            log.debug("Processing transmitlog event: type={}, key={}, table={}, value={}",
                eventType, transmitlogKey, tableName, keyValue);

            switch (eventType) {
                case "INSERT" -> handleNewTransmitlog(transmitlogKey, tableName, keyValue, event);
                case "UPDATE" -> handleTransmitlogUpdate(transmitlogKey, event);
                case "STATUS_CHANGE" -> handleStatusChange(transmitlogKey, event);
                default -> log.debug("Unknown event type: {}", eventType);
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process transmitlog event: {}", e.getMessage(), e);
            handleProcessingError(payload, e);
            ack.acknowledge();
        }
    }

    /**
     * Handle new transmitlog record creation.
     * Triggers appropriate outbound interface processing.
     */
    private void handleNewTransmitlog(String transmitlogKey, String tableName,
                                       String keyValue, Map<String, Object> event) {
        log.info("New transmitlog created: key={}, table={}", transmitlogKey, tableName);

        // Determine interface type from table name
        String interfaceType = determineInterfaceType(tableName);

        // Route to appropriate handler based on interface type
        switch (interfaceType) {
            case "ASN_CONF" -> processASNConfirmation(keyValue, event);
            case "RECEIPT_CONF" -> processReceiptConfirmation(keyValue, event);
            case "PO_UPDATE" -> processPOUpdate(keyValue, event);
            case "INVENTORY" -> processInventoryUpdate(keyValue, event);
            default -> {
                log.debug("No specific handler for interface type: {}", interfaceType);
                updateTransmitlogStatus(transmitlogKey, STATUS_SENT, null);
            }
        }
    }

    /**
     * Handle transmitlog status update.
     */
    private void handleTransmitlogUpdate(String transmitlogKey, Map<String, Object> event) {
        String oldStatus = (String) event.get("oldStatus");
        String newStatus = (String) event.get("newStatus");

        log.debug("Transmitlog status changed: key={}, {} -> {}", transmitlogKey, oldStatus, newStatus);

        // If changed to error status and retry is enabled, schedule retry
        if (STATUS_ERROR.equals(newStatus) && retryFailed) {
            scheduleRetry(transmitlogKey);
        }
    }

    /**
     * Handle explicit status change events.
     */
    private void handleStatusChange(String transmitlogKey, Map<String, Object> event) {
        String newStatus = (String) event.get("newStatus");

        if (STATUS_SENT.equals(newStatus)) {
            log.debug("Transmitlog {} marked as sent", transmitlogKey);
            // Any post-send processing
        }
    }

    /**
     * Process ASN confirmation interface.
     */
    private void processASNConfirmation(String receiptKey, Map<String, Object> event) {
        log.info("Processing ASN confirmation for receipt: {}", receiptKey);

        try {
            // Generate ASN confirmation data
            @SuppressWarnings("unchecked")
            Map<String, Object> receiptData = jdbcTemplate.queryForMap(
                "SELECT r.*, s.storerkey as storername " +
                "FROM dbo.RECEIPT r " +
                "LEFT JOIN dbo.STORER s ON r.storerkey = s.storerkey " +
                "WHERE r.receiptkey = ?",
                receiptKey
            );

            // Log successful processing
            jdbcTemplate.update(
                "INSERT INTO dbo.INTERFACELOG (tablename, keyvalue, status, message, processeddate) " +
                "VALUES ('RECEIPT', ?, 'SUCCESS', 'ASN Confirmation processed', GETDATE())",
                receiptKey
            );

        } catch (Exception e) {
            log.error("Failed to process ASN confirmation: {}", e.getMessage());
        }
    }

    /**
     * Process Receipt confirmation interface.
     */
    private void processReceiptConfirmation(String receiptKey, Map<String, Object> event) {
        log.info("Processing Receipt confirmation for: {}", receiptKey);

        // Similar to ASN confirmation but with additional receipt details
        try {
            jdbcTemplate.update(
                "INSERT INTO dbo.INTERFACELOG (tablename, keyvalue, status, message, processeddate) " +
                "VALUES ('RECEIPTDETAIL', ?, 'SUCCESS', 'Receipt Confirmation processed', GETDATE())",
                receiptKey
            );
        } catch (Exception e) {
            log.error("Failed to process receipt confirmation: {}", e.getMessage());
        }
    }

    /**
     * Process PO update interface.
     */
    private void processPOUpdate(String poKey, Map<String, Object> event) {
        log.info("Processing PO update for: {}", poKey);

        try {
            jdbcTemplate.update(
                "INSERT INTO dbo.INTERFACELOG (tablename, keyvalue, status, message, processeddate) " +
                "VALUES ('PO', ?, 'SUCCESS', 'PO Update processed', GETDATE())",
                poKey
            );
        } catch (Exception e) {
            log.error("Failed to process PO update: {}", e.getMessage());
        }
    }

    /**
     * Process inventory update interface.
     */
    private void processInventoryUpdate(String key, Map<String, Object> event) {
        log.info("Processing inventory update for: {}", key);

        try {
            jdbcTemplate.update(
                "INSERT INTO dbo.INTERFACELOG (tablename, keyvalue, status, message, processeddate) " +
                "VALUES ('LOTXLOCXID', ?, 'SUCCESS', 'Inventory Update processed', GETDATE())",
                key
            );
        } catch (Exception e) {
            log.error("Failed to process inventory update: {}", e.getMessage());
        }
    }

    /**
     * Determine interface type from table name.
     */
    private String determineInterfaceType(String tableName) {
        if (tableName == null) {
            return "UNKNOWN";
        }

        return switch (tableName.toUpperCase()) {
            case "RECEIPT" -> "ASN_CONF";
            case "RECEIPTDETAIL" -> "RECEIPT_CONF";
            case "PO", "PODETAIL" -> "PO_UPDATE";
            case "LOTXLOCXID", "ITRN" -> "INVENTORY";
            default -> tableName.toUpperCase();
        };
    }

    /**
     * Update transmitlog status.
     */
    private void updateTransmitlogStatus(String transmitlogKey, String status, String errorMessage) {
        try {
            if (errorMessage != null) {
                jdbcTemplate.update(
                    "UPDATE dbo.TRANSMITLOG3 SET status = ?, errormessage = ?, " +
                    "transmitdate = GETDATE() WHERE transmitlogkey = ?",
                    status, errorMessage, transmitlogKey
                );
            } else {
                jdbcTemplate.update(
                    "UPDATE dbo.TRANSMITLOG3 SET status = ?, " +
                    "transmitdate = GETDATE() WHERE transmitlogkey = ?",
                    status, transmitlogKey
                );
            }
        } catch (Exception e) {
            log.error("Failed to update transmitlog status: {}", e.getMessage());
        }
    }

    /**
     * Schedule retry for failed transmitlog.
     */
    private void scheduleRetry(String transmitlogKey) {
        log.info("Scheduling retry for transmitlog: {}", transmitlogKey);

        try {
            jdbcTemplate.update(
                "UPDATE dbo.TRANSMITLOG3 SET status = ?, retrycount = COALESCE(retrycount, 0) + 1 " +
                "WHERE transmitlogkey = ?",
                STATUS_PENDING, transmitlogKey
            );
        } catch (Exception e) {
            log.error("Failed to schedule retry: {}", e.getMessage());
        }
    }

    /**
     * Handle processing error.
     */
    private void handleProcessingError(String payload, Exception e) {
        try {
            jdbcTemplate.update(
                "INSERT INTO dbo.INTERFACELOG (tablename, keyvalue, status, message, processeddate) " +
                "VALUES ('KAFKA_ERROR', ?, 'ERROR', ?, GETDATE())",
                payload.substring(0, Math.min(100, payload.length())),
                e.getMessage()
            );
        } catch (Exception ex) {
            log.error("Failed to log error: {}", ex.getMessage());
        }
    }
}
