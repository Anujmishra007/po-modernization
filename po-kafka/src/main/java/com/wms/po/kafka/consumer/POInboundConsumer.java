package com.wms.po.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.po.kafka.message.POInboundMessage;
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

/**
 * PO Inbound Kafka Consumer.
 *
 * Replaces: JOB-011 GenericInbound_PO
 *
 * Consumes Purchase Order messages from Kafka topic and creates
 * PO records in the WMS database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class POInboundConsumer {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${wms.kafka.po.enabled:true}")
    private boolean enabled;

    @Value("${wms.kafka.po.auto-approve:false}")
    private boolean autoApprove;

    private static final String STATUS_NEW = "0";
    private static final String STATUS_APPROVED = "1";

    /**
     * Consume PO messages from Kafka topic.
     */
    @KafkaListener(
        topics = "${wms.kafka.topics.po-inbound:wms.po.inbound}",
        groupId = "${wms.kafka.consumer-group:wms-po-consumer}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumePOMessage(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            Acknowledgment ack) {

        if (!enabled) {
            log.debug("PO consumer disabled, skipping message");
            ack.acknowledge();
            return;
        }

        log.info("Received PO message: topic={}, partition={}, offset={}, key={}",
            topic, partition, offset, key);

        try {
            // Parse message
            POInboundMessage message = objectMapper.readValue(payload, POInboundMessage.class);

            // Validate message
            if (!message.isValid()) {
                log.error("Invalid PO message: missing required fields");
                handleInvalidMessage(payload, "Missing required fields");
                ack.acknowledge();
                return;
            }

            // Check for duplicate
            if (isDuplicatePO(message)) {
                log.warn("Duplicate PO detected: {}", message.getExternalPOKey());
                ack.acknowledge();
                return;
            }

            // Process PO
            String poKey = processPOMessage(message);

            log.info("Successfully created PO: externalKey={}, poKey={}",
                message.getExternalPOKey(), poKey);

            // Acknowledge message
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process PO message: {}", e.getMessage(), e);
            handleProcessingError(payload, e);
            // Still acknowledge to avoid infinite retry - error is logged for manual review
            ack.acknowledge();
        }
    }

    /**
     * Process PO message and create database records.
     */
    private String processPOMessage(POInboundMessage message) {
        // Generate PO key
        String poKey = generatePOKey();
        String status = autoApprove ? STATUS_APPROVED : STATUS_NEW;

        // Insert PO header
        jdbcTemplate.update(
            "INSERT INTO dbo.PO (pokey, storerkey, externpokey, potype, status, " +
            "suppliercode, suppliername, buyercode, orderdate, expectedreceiptdate, " +
            "carriercode, shipvia, susr1, susr2, susr3, susr4, susr5, notes, " +
            "adddate, addwho, editdate, editwho) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), ?, GETDATE(), ?)",
            poKey,
            message.getStorerKey(),
            message.getExternalPOKey(),
            message.getPoType() != null ? message.getPoType() : "STANDARD",
            status,
            message.getSupplierCode(),
            message.getSupplierName(),
            message.getBuyerCode(),
            message.getOrderDate(),
            message.getExpectedDeliveryDate(),
            message.getCarrierCode(),
            message.getShipVia(),
            message.getSusr1(),
            message.getSusr2(),
            message.getSusr3(),
            message.getSusr4(),
            message.getSusr5(),
            message.getNotes(),
            "KAFKA",
            "KAFKA"
        );

        // Insert PO details
        for (POInboundMessage.POLineMessage line : message.getLines()) {
            insertPODetail(poKey, message.getStorerKey(), line);
        }

        // Log inbound message
        logInboundMessage(message.getMessageId(), "PO", poKey, message.getExternalPOKey(), "SUCCESS", null);

        return poKey;
    }

    /**
     * Insert PO detail line.
     */
    private void insertPODetail(String poKey, String storerKey, POInboundMessage.POLineMessage line) {
        // Validate SKU exists
        if (!skuExists(storerKey, line.getSku())) {
            log.warn("SKU not found: {} for storer {}", line.getSku(), storerKey);
            // Could auto-create SKU or throw error based on config
        }

        jdbcTemplate.update(
            "INSERT INTO dbo.PODETAIL (pokey, polinenumber, storerkey, sku, " +
            "qtyordered, qtyreceived, openqty, packkey, uom, unitprice, status, " +
            "lottable01, lottable02, lottable03, lottable04, lottable05, " +
            "susr1, susr2, susr3, adddate, addwho) " +
            "VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, '0', ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), ?)",
            poKey,
            line.getLineNumber(),
            storerKey,
            line.getSku(),
            line.getQtyOrdered(),
            line.getQtyOrdered(), // openqty = ordered initially
            line.getPackKey(),
            line.getUom() != null ? line.getUom() : "EA",
            line.getUnitPrice(),
            line.getLottable01(),
            line.getLottable02(),
            line.getLottable03(),
            line.getLottable04(),
            line.getLottable05(),
            line.getSusr1(),
            line.getSusr2(),
            line.getSusr3(),
            "KAFKA"
        );
    }

    /**
     * Check if PO already exists.
     */
    private boolean isDuplicatePO(POInboundMessage message) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.PO WHERE storerkey = ? AND externpokey = ?",
                Integer.class,
                message.getStorerKey(), message.getExternalPOKey()
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if SKU exists.
     */
    private boolean skuExists(String storerKey, String sku) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                Integer.class,
                storerKey, sku
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generate new PO key.
     */
    private String generatePOKey() {
        try {
            return jdbcTemplate.queryForObject(
                "EXEC dbo.nspg_GetKey @tablename = 'PO'",
                String.class
            );
        } catch (Exception e) {
            return "PO" + System.currentTimeMillis();
        }
    }

    /**
     * Log inbound message for audit.
     */
    private void logInboundMessage(String messageId, String messageType, String internalKey,
                                    String externalKey, String status, String errorMessage) {
        try {
            jdbcTemplate.update(
                "INSERT INTO dbo.INBOUNDLOG (messageid, messagetype, internalkey, " +
                "externalkey, status, errormessage, processeddate) " +
                "VALUES (?, ?, ?, ?, ?, ?, GETDATE())",
                messageId, messageType, internalKey, externalKey, status, errorMessage
            );
        } catch (Exception e) {
            log.debug("Inbound log insert failed: {}", e.getMessage());
        }
    }

    /**
     * Handle invalid message.
     */
    private void handleInvalidMessage(String payload, String reason) {
        logInboundMessage(null, "PO", null, null, "INVALID", reason);
        // Could also send to dead-letter queue
    }

    /**
     * Handle processing error.
     */
    private void handleProcessingError(String payload, Exception e) {
        logInboundMessage(null, "PO", null, null, "ERROR", e.getMessage());
        // Could also send to dead-letter queue
    }
}
