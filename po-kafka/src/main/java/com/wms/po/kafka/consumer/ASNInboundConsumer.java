package com.wms.po.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.po.kafka.message.ASNInboundMessage;
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

import java.math.BigDecimal;

/**
 * ASN Inbound Kafka Consumer.
 *
 * Replaces: JOB-012 GenericInbound_ASN
 *
 * Consumes ASN (Advanced Shipping Notice) messages from Kafka topic
 * and creates Receipt records in the WMS database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ASNInboundConsumer {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${wms.kafka.asn.enabled:true}")
    private boolean enabled;

    @Value("${wms.kafka.asn.auto-receive:false}")
    private boolean autoReceive;

    @Value("${wms.kafka.asn.create-ucc:true}")
    private boolean createUCC;

    private static final String STATUS_NEW = "0";
    private static final String STATUS_INPROGRESS = "1";

    /**
     * Consume ASN messages from Kafka topic.
     */
    @KafkaListener(
        topics = "${wms.kafka.topics.asn-inbound:wms.asn.inbound}",
        groupId = "${wms.kafka.consumer-group:wms-asn-consumer}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeASNMessage(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            Acknowledgment ack) {

        if (!enabled) {
            log.debug("ASN consumer disabled, skipping message");
            ack.acknowledge();
            return;
        }

        log.info("Received ASN message: topic={}, partition={}, offset={}, key={}",
            topic, partition, offset, key);

        try {
            // Parse message
            ASNInboundMessage message = objectMapper.readValue(payload, ASNInboundMessage.class);

            // Validate message
            if (!message.isValid()) {
                log.error("Invalid ASN message: missing required fields");
                handleInvalidMessage(payload, "Missing required fields");
                ack.acknowledge();
                return;
            }

            // Check for duplicate
            if (isDuplicateASN(message)) {
                log.warn("Duplicate ASN detected: {}", message.getExternalASNKey());
                ack.acknowledge();
                return;
            }

            // Process ASN
            String receiptKey = processASNMessage(message);

            log.info("Successfully created ASN/Receipt: externalKey={}, receiptKey={}",
                message.getExternalASNKey(), receiptKey);

            // Acknowledge message
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process ASN message: {}", e.getMessage(), e);
            handleProcessingError(payload, e);
            ack.acknowledge();
        }
    }

    /**
     * Process ASN message and create database records.
     */
    private String processASNMessage(ASNInboundMessage message) {
        // Generate receipt key
        String receiptKey = generateReceiptKey();
        String status = autoReceive ? STATUS_INPROGRESS : STATUS_NEW;

        // Resolve PO key if external PO reference provided
        String poKey = resolvePOKey(message.getStorerKey(), message.getExternalPOKey());

        // Insert receipt header
        jdbcTemplate.update(
            "INSERT INTO dbo.RECEIPT (receiptkey, storerkey, externreceiptkey, " +
            "type, status, pokey, suppliercode, suppliername, " +
            "carrierkey, carriername, carrierreference, " +
            "containerkey, trailerkey, sealnumber, billofladingnumber, pronumber, " +
            "expectedreceiptdate, door, susr1, susr2, susr3, susr4, susr5, notes, " +
            "adddate, addwho, editdate, editwho) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), ?, GETDATE(), ?)",
            receiptKey,
            message.getStorerKey(),
            message.getExternalASNKey(),
            message.getAsnType() != null ? message.getAsnType() : "ASN",
            status,
            poKey,
            message.getSupplierCode(),
            message.getSupplierName(),
            message.getCarrierCode(),
            message.getCarrierName(),
            message.getCarrierReference(),
            message.getContainerNumber(),
            message.getTrailerNumber(),
            message.getSealNumber(),
            message.getBillOfLading(),
            message.getProNumber(),
            message.getExpectedArrivalDate(),
            message.getDoor(),
            message.getSusr1(),
            message.getSusr2(),
            message.getSusr3(),
            message.getSusr4(),
            message.getSusr5(),
            message.getNotes(),
            "KAFKA",
            "KAFKA"
        );

        // Insert receipt details
        for (ASNInboundMessage.ASNLineMessage line : message.getLines()) {
            insertReceiptDetail(receiptKey, message.getStorerKey(), poKey, line);
        }

        // Create UCC records if carton detail provided
        if (createUCC && message.hasCartonDetail()) {
            for (ASNInboundMessage.ASNCartonMessage carton : message.getCartons()) {
                createUCCRecord(receiptKey, message.getStorerKey(), carton);
            }
        }

        // Log inbound message
        logInboundMessage(message.getMessageId(), "ASN", receiptKey, message.getExternalASNKey(), "SUCCESS", null);

        return receiptKey;
    }

    /**
     * Insert receipt detail line.
     */
    private void insertReceiptDetail(String receiptKey, String storerKey, String poKey,
                                      ASNInboundMessage.ASNLineMessage line) {
        // Resolve PO line number if not provided
        Integer poLineNumber = line.getPoLineNumber();
        if (poLineNumber == null && poKey != null && line.getSku() != null) {
            poLineNumber = resolvePOLineNumber(poKey, line.getSku());
        }

        BigDecimal qtyReceived = autoReceive && line.getQtyReceived() != null ?
            line.getQtyReceived() : BigDecimal.ZERO;

        jdbcTemplate.update(
            "INSERT INTO dbo.RECEIPTDETAIL (receiptkey, receiptlinenumber, storerkey, sku, " +
            "qtyexpected, qtyreceived, packkey, uom, status, " +
            "pokey, polinenumber, toloc, toid, " +
            "lottable01, lottable02, lottable03, lottable04, lottable05, " +
            "lottable06, lottable07, lottable08, lottable09, lottable10, " +
            "conditioncode, holdcode, susr1, susr2, susr3, adddate, addwho) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, '0', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), ?)",
            receiptKey,
            line.getLineNumber(),
            storerKey,
            line.getSku(),
            line.getQtyExpected(),
            qtyReceived,
            line.getPackKey(),
            line.getUom() != null ? line.getUom() : "EA",
            poKey,
            poLineNumber,
            line.getToLoc(),
            line.getToId(),
            line.getLottable01(),
            line.getLottable02(),
            line.getLottable03(),
            line.getLottable04(),
            line.getLottable05(),
            line.getLottable06(),
            line.getLottable07(),
            line.getLottable08(),
            line.getLottable09(),
            line.getLottable10(),
            line.getConditionCode(),
            line.getHoldCode(),
            line.getSusr1(),
            line.getSusr2(),
            line.getSusr3(),
            "KAFKA"
        );
    }

    /**
     * Create UCC record for carton.
     */
    private void createUCCRecord(String receiptKey, String storerKey,
                                  ASNInboundMessage.ASNCartonMessage carton) {
        String uccKey = generateUCCKey();

        jdbcTemplate.update(
            "INSERT INTO dbo.UCC (ucckey, storerkey, lpn, sscc, receiptkey, " +
            "cartontype, grosswgt, cube, status, adddate, addwho) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, '0', GETDATE(), ?)",
            uccKey,
            storerKey,
            carton.getCartonId(),
            carton.getSscc(),
            receiptKey,
            carton.getCartonType(),
            carton.getWeight(),
            carton.getCube(),
            "KAFKA"
        );

        // Link UCC to receipt lines
        if (carton.getLineNumbers() != null) {
            for (Integer lineNum : carton.getLineNumbers()) {
                jdbcTemplate.update(
                    "UPDATE dbo.RECEIPTDETAIL SET caseid = ? " +
                    "WHERE receiptkey = ? AND receiptlinenumber = ?",
                    carton.getCartonId(), receiptKey, lineNum
                );
            }
        }
    }

    /**
     * Check if ASN already exists.
     */
    private boolean isDuplicateASN(ASNInboundMessage message) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.RECEIPT WHERE storerkey = ? AND externreceiptkey = ?",
                Integer.class,
                message.getStorerKey(), message.getExternalASNKey()
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolve internal PO key from external key.
     */
    private String resolvePOKey(String storerKey, String externalPOKey) {
        if (externalPOKey == null || externalPOKey.isEmpty()) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                "SELECT pokey FROM dbo.PO WHERE storerkey = ? AND externpokey = ?",
                String.class,
                storerKey, externalPOKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolve PO line number for SKU.
     */
    private Integer resolvePOLineNumber(String poKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT TOP 1 polinenumber FROM dbo.PODETAIL " +
                "WHERE pokey = ? AND sku = ? AND openqty > 0 " +
                "ORDER BY polinenumber",
                Integer.class,
                poKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Generate new receipt key.
     */
    private String generateReceiptKey() {
        try {
            return jdbcTemplate.queryForObject(
                "EXEC dbo.nspg_GetKey @tablename = 'RECEIPT'",
                String.class
            );
        } catch (Exception e) {
            return "RCP" + System.currentTimeMillis();
        }
    }

    /**
     * Generate new UCC key.
     */
    private String generateUCCKey() {
        try {
            return jdbcTemplate.queryForObject(
                "EXEC dbo.nspg_GetKey @tablename = 'UCC'",
                String.class
            );
        } catch (Exception e) {
            return "UCC" + System.currentTimeMillis();
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
        logInboundMessage(null, "ASN", null, null, "INVALID", reason);
    }

    /**
     * Handle processing error.
     */
    private void handleProcessingError(String payload, Exception e) {
        logInboundMessage(null, "ASN", null, null, "ERROR", e.getMessage());
    }
}
