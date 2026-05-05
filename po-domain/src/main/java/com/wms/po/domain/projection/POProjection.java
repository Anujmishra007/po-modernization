package com.wms.po.domain.projection;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Purchase Order Projection DTO.
 *
 * Replaces: V_PO view
 * Source tables: PO, PODETAIL (aggregated), STORER, SUPPLIER
 *
 * Provides a flattened view of PO data with aggregated
 * line information for UI display and reporting.
 */
@Data
@Builder
public class POProjection {

    // PO Header
    private String poKey;
    private String externalPoKey;
    private String storerKey;
    private String facility;
    private String poType;
    private String status;
    private String statusDescription;

    // PO Dates
    private LocalDateTime orderDate;
    private LocalDateTime expectedDeliveryDate;
    private LocalDateTime actualDeliveryDate;
    private LocalDateTime effectiveDate;
    private LocalDateTime expirationDate;
    private LocalDateTime addDate;
    private LocalDateTime editDate;
    private LocalDateTime closeDate;

    // Supplier Info
    private String supplierCode;
    private String supplierName;
    private String supplierAddress1;
    private String supplierAddress2;
    private String supplierCity;
    private String supplierState;
    private String supplierCountry;
    private String supplierZip;
    private String supplierContact;
    private String supplierPhone;
    private String supplierEmail;

    // Storer Info
    private String storerName;
    private String storerCompany;

    // Buyer Info
    private String buyerCode;
    private String buyerName;
    private String buyerPhone;
    private String buyerEmail;

    // Ship-to Info
    private String shipToCode;
    private String shipToName;
    private String shipToAddress1;
    private String shipToAddress2;
    private String shipToCity;
    private String shipToState;
    private String shipToCountry;
    private String shipToZip;

    // Carrier Info
    private String carrierKey;
    private String carrierName;
    private String carrierMode;
    private String carrierService;

    // Quantities (Aggregated from PODETAIL)
    private Integer totalLines;
    private BigDecimal totalOrderedQty;
    private BigDecimal totalReceivedQty;
    private BigDecimal totalOpenQty;
    private BigDecimal totalCancelledQty;

    // Amounts (Aggregated)
    private BigDecimal totalOrderedAmount;
    private BigDecimal totalReceivedAmount;
    private String currency;

    // Weights/Dimensions (Aggregated)
    private BigDecimal totalGrossWeight;
    private BigDecimal totalNetWeight;
    private BigDecimal totalCube;
    private String weightUOM;
    private String dimensionUOM;

    // ASN Info (linked receipts)
    private Integer totalASNs;
    private Integer openASNs;
    private Integer closedASNs;

    // Priority/Urgency
    private String priority;
    private Integer priorityLevel;
    private boolean rushOrder;
    private boolean criticalOrder;

    // References
    private String externalReference;
    private String internalReference;
    private String contractNumber;
    private String projectCode;
    private String costCenter;

    // User Fields
    private String susr1;
    private String susr2;
    private String susr3;
    private String susr4;
    private String susr5;

    // Notes
    private String notes;
    private String internalNotes;
    private String supplierNotes;

    // Audit
    private String addWho;
    private String editWho;

    // Flags
    private boolean allowOverReceipt;
    private boolean allowEarlyReceipt;
    private boolean requireQualityCheck;
    private boolean requireApproval;
    private boolean approved;
    private String approvedBy;
    private LocalDateTime approvalDate;

    /**
     * Get fulfillment percentage.
     */
    public BigDecimal getFulfillmentPercent() {
        if (totalOrderedQty == null || totalOrderedQty.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal received = totalReceivedQty != null ? totalReceivedQty : BigDecimal.ZERO;
        return received.divide(totalOrderedQty, 4, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Check if PO is fully received.
     */
    public boolean isFullyReceived() {
        if (totalOrderedQty == null || totalReceivedQty == null) {
            return false;
        }
        return totalReceivedQty.compareTo(totalOrderedQty) >= 0;
    }

    /**
     * Check if PO is partially received.
     */
    public boolean isPartiallyReceived() {
        if (totalReceivedQty == null || totalOrderedQty == null) {
            return false;
        }
        return totalReceivedQty.compareTo(BigDecimal.ZERO) > 0
            && totalReceivedQty.compareTo(totalOrderedQty) < 0;
    }

    /**
     * Check if PO has any open quantity.
     */
    public boolean hasOpenQuantity() {
        if (totalOpenQty == null) {
            return false;
        }
        return totalOpenQty.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if PO is overdue.
     */
    public boolean isOverdue() {
        if (expectedDeliveryDate == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expectedDeliveryDate) && hasOpenQuantity();
    }

    /**
     * Get days until expected delivery.
     */
    public Long getDaysUntilExpectedDelivery() {
        if (expectedDeliveryDate == null) {
            return null;
        }
        return java.time.Duration.between(LocalDateTime.now(), expectedDeliveryDate).toDays();
    }

    /**
     * Check if PO is closed.
     */
    public boolean isClosed() {
        return "9".equals(status) || closeDate != null;
    }

    /**
     * Check if PO is cancelled.
     */
    public boolean isCancelled() {
        return "X".equals(status);
    }

    /**
     * Get open amount.
     */
    public BigDecimal getOpenAmount() {
        if (totalOrderedAmount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal receivedAmt = totalReceivedAmount != null ? totalReceivedAmount : BigDecimal.ZERO;
        return totalOrderedAmount.subtract(receivedAmt);
    }
}
