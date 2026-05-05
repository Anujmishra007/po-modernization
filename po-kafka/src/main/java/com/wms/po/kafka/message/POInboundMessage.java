package com.wms.po.kafka.message;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PO Inbound Message DTO.
 *
 * Represents a Purchase Order message received via Kafka
 * for processing by the GenericInbound_PO consumer.
 */
@Data
@Builder
public class POInboundMessage {

    // Message Metadata
    private String messageId;
    private String messageType;
    private LocalDateTime messageTimestamp;
    private String sourceSystem;
    private String correlationId;

    // PO Header
    private String externalPOKey;
    private String storerKey;
    private String facility;
    private String poType;
    private String supplierCode;
    private String supplierName;
    private String buyerCode;

    // Dates
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private LocalDate cancelDate;

    // Carrier/Shipping
    private String carrierCode;
    private String shipVia;
    private String incoterms;

    // References
    private String contractNumber;
    private String requisitionNumber;
    private String projectCode;

    // User Fields
    private String susr1;
    private String susr2;
    private String susr3;
    private String susr4;
    private String susr5;
    private String notes;

    // Lines
    private List<POLineMessage> lines;

    @Data
    @Builder
    public static class POLineMessage {
        private Integer lineNumber;
        private String sku;
        private String skuDescription;
        private BigDecimal qtyOrdered;
        private String uom;
        private String packKey;
        private BigDecimal unitPrice;
        private String currency;

        // Lottables
        private String lottable01;
        private String lottable02;
        private String lottable03;
        private String lottable04;
        private String lottable05;

        // User Fields
        private String susr1;
        private String susr2;
        private String susr3;
    }

    /**
     * Validate message has required fields.
     */
    public boolean isValid() {
        return externalPOKey != null && !externalPOKey.isEmpty() &&
               storerKey != null && !storerKey.isEmpty() &&
               lines != null && !lines.isEmpty();
    }
}
