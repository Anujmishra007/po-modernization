package com.wms.po.kafka.message;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ASN (Advanced Shipping Notice) Inbound Message DTO.
 *
 * Represents an ASN/Receipt message received via Kafka
 * for processing by the GenericInbound_ASN consumer.
 */
@Data
@Builder
public class ASNInboundMessage {

    // Message Metadata
    private String messageId;
    private String messageType;
    private LocalDateTime messageTimestamp;
    private String sourceSystem;
    private String correlationId;

    // ASN Header
    private String externalASNKey;
    private String storerKey;
    private String facility;
    private String asnType;  // ASN, RECEIPT, TRANSFER
    private String externalPOKey;  // Reference to PO

    // Supplier/Carrier
    private String supplierCode;
    private String supplierName;
    private String carrierCode;
    private String carrierName;
    private String carrierReference;

    // Container/Transport
    private String containerNumber;
    private String trailerNumber;
    private String sealNumber;
    private String billOfLading;
    private String proNumber;

    // Dates
    private LocalDate shipDate;
    private LocalDate expectedArrivalDate;
    private LocalDateTime actualArrivalDateTime;

    // Dock Assignment
    private String door;
    private String receivingArea;

    // Totals
    private Integer totalCartons;
    private Integer totalPallets;
    private BigDecimal totalWeight;
    private BigDecimal totalCube;

    // User Fields
    private String susr1;
    private String susr2;
    private String susr3;
    private String susr4;
    private String susr5;
    private String notes;

    // Lines
    private List<ASNLineMessage> lines;

    // Cartons (optional - for carton-level ASN)
    private List<ASNCartonMessage> cartons;

    @Data
    @Builder
    public static class ASNLineMessage {
        private Integer lineNumber;
        private String sku;
        private String skuDescription;
        private BigDecimal qtyExpected;
        private BigDecimal qtyReceived;
        private String uom;
        private String packKey;

        // PO Reference
        private String poKey;
        private Integer poLineNumber;

        // Location
        private String toLoc;
        private String toId;

        // Lottables
        private String lottable01;
        private String lottable02;
        private String lottable03;
        private String lottable04;
        private String lottable05;
        private String lottable06;
        private String lottable07;
        private String lottable08;
        private String lottable09;
        private String lottable10;

        // Condition
        private String conditionCode;
        private String holdCode;

        // User Fields
        private String susr1;
        private String susr2;
        private String susr3;
    }

    @Data
    @Builder
    public static class ASNCartonMessage {
        private String cartonId;
        private String sscc;
        private String cartonType;
        private BigDecimal weight;
        private BigDecimal cube;
        private List<Integer> lineNumbers;  // Lines in this carton
    }

    /**
     * Validate message has required fields.
     */
    public boolean isValid() {
        return externalASNKey != null && !externalASNKey.isEmpty() &&
               storerKey != null && !storerKey.isEmpty() &&
               lines != null && !lines.isEmpty();
    }

    /**
     * Check if this is a carton-level ASN.
     */
    public boolean hasCartonDetail() {
        return cartons != null && !cartons.isEmpty();
    }
}
