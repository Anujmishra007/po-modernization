package com.wms.po.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PO Projection DTO.
 *
 * Replaces: VW-001 V_PO view
 *
 * Provides a read-optimized view of PO data for queries and reporting.
 * This projection consolidates data from PO and related tables into
 * a single denormalized view.
 */
@Data
@Builder
public class POProjection {

    // Primary identifiers
    private String poKey;
    private String externPOKey;
    private String storerKey;
    private String storerName;
    private String facility;

    // Type and status
    private String poType;
    private String poTypeDescription;
    private String status;
    private String statusDescription;

    // Supplier info
    private String supplierCode;
    private String supplierName;
    private String buyerCode;
    private String buyerName;

    // Dates
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private LocalDate cancelDate;
    private LocalDate closeDate;
    private LocalDateTime addDate;
    private LocalDateTime editDate;

    // Carrier/Shipping
    private String carrierCode;
    private String carrierName;
    private String shipVia;
    private String incoterms;

    // Quantities summary
    private int totalLines;
    private BigDecimal totalQtyOrdered;
    private BigDecimal totalQtyReceived;
    private BigDecimal totalOpenQty;
    private BigDecimal receiptPercentage;

    // Value summary
    private BigDecimal totalValue;
    private String currency;

    // References
    private String contractNumber;
    private String requisitionNumber;
    private String projectCode;

    // User fields
    private String susr1;
    private String susr2;
    private String susr3;
    private String susr4;
    private String susr5;
    private String notes;

    // Audit
    private String addWho;
    private String editWho;

    // Related counts
    private int asnCount;
    private int receiptCount;

    /**
     * Get status description from code.
     */
    public static String getStatusDescription(String status) {
        return switch (status) {
            case "0" -> "New";
            case "1" -> "Approved";
            case "5" -> "In Progress";
            case "8" -> "Cancelled";
            case "9" -> "Closed";
            default -> "Unknown";
        };
    }

    /**
     * Check if PO is fully received.
     */
    public boolean isFullyReceived() {
        return totalOpenQty != null && totalOpenQty.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Check if PO is overdue.
     */
    public boolean isOverdue() {
        return expectedDeliveryDate != null &&
               expectedDeliveryDate.isBefore(LocalDate.now()) &&
               !"9".equals(status);
    }
}
