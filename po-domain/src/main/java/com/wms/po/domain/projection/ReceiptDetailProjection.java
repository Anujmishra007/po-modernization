package com.wms.po.domain.projection;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Receipt Detail (Line) Projection DTO.
 *
 * Replaces: V_RECEIPTDETAIL view
 * Source tables: RECEIPTDETAIL
 *
 * Provides receipt line data with lottables and calculated fields.
 */
@Data
@Builder
public class ReceiptDetailProjection {

    // Primary Keys
    private String receiptKey;
    private Integer receiptLineNumber;
    private String receiptDetailKey;

    // Storer
    private String storerKey;

    // SKU Information
    private String sku;
    private String skuDescription;
    private String altSku;
    private String upc;

    // PO Reference
    private String poKey;
    private Integer poLineNumber;
    private String poDetailKey;

    // Quantities
    private BigDecimal qtyExpected;
    private BigDecimal qtyReceived;
    private BigDecimal qtyDamaged;
    private BigDecimal qtyRejected;
    private BigDecimal qtyVariance;

    // UOM/Pack
    private String packKey;
    private String uom;
    private BigDecimal uomQty;

    // Location/ID
    private String toLoc;
    private String toId;
    private String caseid;

    // Status
    private String status;
    private String statusDescription;

    // Condition
    private String conditionCode;
    private String holdCode;
    private String reasonCode;

    // Lottables (1-10)
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

    // Dates
    private LocalDate dateReceived;
    private LocalDate lottable04Date; // Often used for manufacturing date
    private LocalDate lottable05Date; // Often used for expiration date

    // Weights/Dimensions
    private BigDecimal grossWeight;
    private BigDecimal netWeight;
    private BigDecimal cube;
    private BigDecimal unitPrice;
    private BigDecimal extendedPrice;

    // User Fields
    private String susr1;
    private String susr2;
    private String susr3;
    private String susr4;
    private String susr5;
    private String notes;

    // Audit
    private LocalDateTime addDate;
    private LocalDateTime editDate;
    private String addWho;
    private String editWho;

    // Flags
    private boolean finalized;
    private boolean skipped;
    private boolean modified;

    // Inventory Reference (after finalization)
    private String lotxlocxidKey;
    private String lot;

    /**
     * Calculate variance.
     */
    public BigDecimal getCalculatedVariance() {
        BigDecimal expected = qtyExpected != null ? qtyExpected : BigDecimal.ZERO;
        BigDecimal received = qtyReceived != null ? qtyReceived : BigDecimal.ZERO;
        return received.subtract(expected);
    }

    /**
     * Calculate variance percentage.
     */
    public BigDecimal getVariancePercent() {
        if (qtyExpected == null || qtyExpected.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getCalculatedVariance()
            .divide(qtyExpected, 4, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Check if line is complete (fully received).
     */
    public boolean isComplete() {
        if (qtyExpected == null || qtyReceived == null) {
            return false;
        }
        return qtyReceived.compareTo(qtyExpected) >= 0;
    }

    /**
     * Check if line has over-receipt.
     */
    public boolean hasOverReceipt() {
        if (qtyExpected == null || qtyReceived == null) {
            return false;
        }
        return qtyReceived.compareTo(qtyExpected) > 0;
    }

    /**
     * Check if line has damages.
     */
    public boolean hasDamages() {
        return qtyDamaged != null && qtyDamaged.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if line is on hold.
     */
    public boolean isOnHold() {
        return holdCode != null && !holdCode.isEmpty();
    }

    /**
     * Get total received (including damaged).
     */
    public BigDecimal getTotalReceived() {
        BigDecimal received = qtyReceived != null ? qtyReceived : BigDecimal.ZERO;
        BigDecimal damaged = qtyDamaged != null ? qtyDamaged : BigDecimal.ZERO;
        return received.add(damaged);
    }

    /**
     * Get days to expiration (if lottable05 is expiry date).
     */
    public Long getDaysToExpiry() {
        if (lottable05Date == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), lottable05Date);
    }

    /**
     * Check if expired (based on lottable05 as expiry date).
     */
    public boolean isExpired() {
        Long days = getDaysToExpiry();
        return days != null && days < 0;
    }

    /**
     * Get style-color (from lottable01, common for apparel).
     */
    public String getStyleColor() {
        return lottable01;
    }

    /**
     * Get lot number (from lottable02, common for batch tracking).
     */
    public String getBatchLot() {
        return lottable02;
    }

    /**
     * Get season code (from lottable03, common for apparel).
     */
    public String getSeasonCode() {
        return lottable03;
    }
}
