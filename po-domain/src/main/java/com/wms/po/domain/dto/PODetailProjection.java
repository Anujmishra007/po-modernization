package com.wms.po.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PODetail Projection DTO.
 *
 * Replaces: VW-002 V_PODetail view
 *
 * Provides a read-optimized view of PO detail/line data for queries
 * and reporting. Includes denormalized SKU and pack information.
 */
@Data
@Builder
public class PODetailProjection {

    // Line identifiers
    private String poKey;
    private String externPOKey;
    private int lineNumber;
    private String storerKey;

    // SKU info (denormalized from SKU table)
    private String sku;
    private String skuDescription;
    private String skuAlternateDescription;
    private String skuCategory;
    private String skuClass;

    // Pack info (denormalized from PACK table)
    private String packKey;
    private String packDescription;
    private BigDecimal packQty;
    private String uom;

    // Quantities
    private BigDecimal qtyOrdered;
    private BigDecimal qtyReceived;
    private BigDecimal openQty;
    private BigDecimal qtyAllocated;
    private BigDecimal receiptPercentage;

    // Pricing
    private BigDecimal unitPrice;
    private BigDecimal lineValue;
    private String currency;

    // Status
    private String status;
    private String statusDescription;

    // Lottables (required lot attributes for receiving)
    private String lottable01;
    private String lottable02;
    private String lottable03;
    private String lottable04;
    private String lottable05;

    // User fields
    private String susr1;
    private String susr2;
    private String susr3;
    private String notes;

    // Audit
    private LocalDateTime addDate;
    private String addWho;
    private LocalDateTime editDate;
    private String editWho;

    // Calculated fields
    private int receiptLineCount;
    private String lastReceiptKey;
    private LocalDateTime lastReceiptDate;

    /**
     * Get status description from code.
     */
    public static String getStatusDescription(String status) {
        return switch (status) {
            case "0" -> "New";
            case "1" -> "Approved";
            case "5" -> "Partially Received";
            case "9" -> "Closed";
            default -> "Unknown";
        };
    }

    /**
     * Check if line is fully received.
     */
    public boolean isFullyReceived() {
        return openQty != null && openQty.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Calculate receipt percentage.
     */
    public BigDecimal calculateReceiptPercentage() {
        if (qtyOrdered == null || qtyOrdered.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (qtyReceived == null) {
            return BigDecimal.ZERO;
        }
        return qtyReceived.multiply(BigDecimal.valueOf(100))
            .divide(qtyOrdered, 2, java.math.RoundingMode.HALF_UP);
    }
}
