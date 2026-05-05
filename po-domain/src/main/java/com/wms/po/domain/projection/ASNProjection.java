package com.wms.po.domain.projection;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ASN (Advanced Shipping Notice) Projection DTO.
 *
 * Replaces: V_ASN view
 * Source tables: RECEIPT, LOC, PACK
 *
 * Provides a flattened view of ASN/Receipt data with
 * location and pack information for UI display.
 */
@Data
@Builder
public class ASNProjection {

    // Receipt Header
    private String receiptKey;
    private String externalReceiptKey;
    private String storerKey;
    private String facility;
    private String receiptType;
    private String status;
    private String statusDescription;

    // Receipt Dates
    private LocalDateTime expectedReceiptDate;
    private LocalDateTime actualReceiptDate;
    private LocalDateTime closeDate;
    private LocalDateTime addDate;
    private LocalDateTime editDate;

    // PO Reference
    private String poKey;
    private String externalPOKey;

    // Carrier/Supplier
    private String carrierKey;
    private String carrierName;
    private String supplierCode;
    private String supplierName;

    // Container/Vehicle
    private String containerKey;
    private String trailerKey;
    private String sealNumber;

    // Quantities (Aggregated)
    private Integer totalLines;
    private BigDecimal totalExpectedQty;
    private BigDecimal totalReceivedQty;
    private BigDecimal totalOpenQty;

    // Location Info (from LOC)
    private String receivingDock;
    private String dockDescription;
    private String locationStatus;

    // Pack Info (from PACK - default pack)
    private String packKey;
    private String packDescription;
    private BigDecimal casesPerPallet;
    private BigDecimal unitsPerCase;

    // User Fields
    private String susr1;
    private String susr2;
    private String susr3;
    private String susr4;
    private String susr5;

    // Audit
    private String addWho;
    private String editWho;

    /**
     * Get variance percentage.
     */
    public BigDecimal getVariancePercent() {
        if (totalExpectedQty == null || totalExpectedQty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal received = totalReceivedQty != null ? totalReceivedQty : BigDecimal.ZERO;
        return received.subtract(totalExpectedQty)
            .divide(totalExpectedQty, 4, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Check if receipt is complete (all quantity received).
     */
    public boolean isComplete() {
        if (totalExpectedQty == null || totalReceivedQty == null) {
            return false;
        }
        return totalReceivedQty.compareTo(totalExpectedQty) >= 0;
    }

    /**
     * Check if receipt has over-receipt.
     */
    public boolean hasOverReceipt() {
        if (totalExpectedQty == null || totalReceivedQty == null) {
            return false;
        }
        return totalReceivedQty.compareTo(totalExpectedQty) > 0;
    }
}
