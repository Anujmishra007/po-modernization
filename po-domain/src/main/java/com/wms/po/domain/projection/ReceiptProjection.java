package com.wms.po.domain.projection;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Receipt Header Projection DTO.
 *
 * Replaces: V_RECEIPT view
 * Source tables: RECEIPT
 *
 * Provides receipt header data with calculated fields
 * for display and processing.
 */
@Data
@Builder
public class ReceiptProjection {

    // Primary Keys
    private String receiptKey;
    private String externalReceiptKey;
    private String externalReceiptKey2;

    // Storer/Facility
    private String storerKey;
    private String storerName;
    private String facility;

    // Receipt Type & Status
    private String receiptType;
    private String receiptTypeDescription;
    private String status;
    private String statusDescription;

    // PO Reference
    private String poKey;
    private String externalPOKey;
    private String poType;

    // Dates
    private LocalDateTime expectedReceiptDate;
    private LocalDateTime receiptDate;
    private LocalDateTime closeDate;
    private LocalDateTime addDate;
    private LocalDateTime editDate;

    // Carrier/Supplier
    private String carrierKey;
    private String carrierName;
    private String carrierReference;
    private String supplierCode;
    private String supplierName;

    // Container/Transport
    private String containerKey;
    private String trailerKey;
    private String sealNumber;
    private String billOfLading;
    private String proNumber;

    // Location
    private String door;
    private String receivingArea;

    // Quantities (Header Aggregates)
    private Integer lineCount;
    private BigDecimal totalExpectedQty;
    private BigDecimal totalReceivedQty;
    private BigDecimal totalVarianceQty;
    private Integer totalCartons;

    // Weights/Cubes (Header Aggregates)
    private BigDecimal totalGrossWeight;
    private BigDecimal totalNetWeight;
    private BigDecimal totalCube;

    // User Fields
    private String susr1;
    private String susr2;
    private String susr3;
    private String susr4;
    private String susr5;
    private String notes;

    // Audit
    private String addWho;
    private String editWho;

    // Flags
    private boolean finalized;
    private boolean closed;
    private boolean cancelled;
    private boolean hold;
    private String holdReason;

    // Related Lines (optional - populated on demand)
    @Builder.Default
    private List<ReceiptDetailProjection> lines = List.of();

    /**
     * Calculate completion percentage.
     */
    public BigDecimal getCompletionPercent() {
        if (totalExpectedQty == null || totalExpectedQty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal received = totalReceivedQty != null ? totalReceivedQty : BigDecimal.ZERO;
        return received.divide(totalExpectedQty, 4, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Get variance as percentage of expected.
     */
    public BigDecimal getVariancePercent() {
        if (totalExpectedQty == null || totalExpectedQty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal variance = totalVarianceQty != null ? totalVarianceQty : BigDecimal.ZERO;
        return variance.divide(totalExpectedQty, 4, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Check if receipt can be finalized.
     */
    public boolean canFinalize() {
        return !finalized && !closed && !cancelled && !hold &&
               "3".equals(status) || "4".equals(status); // Received or Verified
    }

    /**
     * Check if receipt can be edited.
     */
    public boolean canEdit() {
        return !finalized && !closed && !cancelled;
    }

    /**
     * Check if all lines are received.
     */
    public boolean isFullyReceived() {
        if (totalExpectedQty == null || totalReceivedQty == null) {
            return false;
        }
        return totalReceivedQty.compareTo(totalExpectedQty) >= 0;
    }

    /**
     * Check if receipt has variance.
     */
    public boolean hasVariance() {
        return totalVarianceQty != null && totalVarianceQty.compareTo(BigDecimal.ZERO) != 0;
    }

    /**
     * Get status for display.
     */
    public String getDisplayStatus() {
        if (cancelled) return "Cancelled";
        if (closed) return "Closed";
        if (finalized) return "Finalized";
        if (hold) return "On Hold";
        return statusDescription != null ? statusDescription : "Status " + status;
    }
}
