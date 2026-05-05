package com.wms.po.domain.projection.bi;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BI Projection for PO Detail Reports.
 *
 * Replaces: VW-011 - PO Detail Views (6 views)
 * - V_BI_PO_Detail
 * - V_BI_PO_Summary
 * - V_BI_PO_Status
 * - V_BI_PO_Aging
 * - V_BI_PO_ByVendor
 * - V_BI_PO_Fulfillment
 */
public interface PODetailProjection {

    // ═══════════════════════════════════════════════════════════════════════
    // Core PO Detail View
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_PO_Detail equivalent.
     */
    interface PODetail {
        String getPoKey();
        String getStorerKey();
        String getStorerName();
        String getExternPoKey();
        String getPoType();
        String getStatus();
        String getStatusDescription();
        Integer getPoLineNumber();
        String getSku();
        String getSkuDescription();
        String getPackKey();
        BigDecimal getQtyOrdered();
        BigDecimal getQtyReceived();
        BigDecimal getQtyOpen();
        BigDecimal getQtyInTransit();
        String getUOM();
        BigDecimal getUnitPrice();
        BigDecimal getLineTotal();
        String getCurrency();
        LocalDateTime getExpectedDate();
        LocalDateTime getOrderDate();
        String getVendorKey();
        String getVendorName();
        String getBuyerKey();
        String getLottable01();
        String getLottable02();
        String getNotes();
    }

    /**
     * V_BI_PO_Summary equivalent.
     */
    interface POSummary {
        String getStorerKey();
        String getStorerName();
        Integer getTotalPOs();
        Integer getOpenPOs();
        Integer getClosedPOs();
        Integer getVoidedPOs();
        BigDecimal getTotalQtyOrdered();
        BigDecimal getTotalQtyReceived();
        BigDecimal getTotalQtyOpen();
        BigDecimal getTotalValue();
        BigDecimal getReceivedValue();
        BigDecimal getOpenValue();
        Integer getTotalLines();
        Integer getCompletedLines();
        BigDecimal getFulfillmentRate();
        LocalDateTime getReportDate();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Status & Aging Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_PO_Status equivalent.
     */
    interface POStatus {
        String getPoKey();
        String getStorerKey();
        String getExternPoKey();
        String getStatus();
        String getStatusDescription();
        LocalDateTime getStatusDate();
        Integer getDaysInStatus();
        String getPreviousStatus();
        LocalDateTime getPreviousStatusDate();
        BigDecimal getPercentComplete();
        Integer getLinesComplete();
        Integer getTotalLines();
        LocalDateTime getExpectedDate();
        Integer getDaysUntilExpected();
        Boolean getIsOverdue();
    }

    /**
     * V_BI_PO_Aging equivalent.
     */
    interface POAging {
        String getStorerKey();
        String getStorerName();
        String getAgingBucket(); // 0-7, 8-14, 15-30, 30+
        Integer getPOCount();
        Integer getLineCount();
        BigDecimal getQtyOpen();
        BigDecimal getTotalValue();
        BigDecimal getAvgDaysOpen();
        Integer getOldestDays();
        String getOldestPOKey();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Vendor & Fulfillment Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_PO_ByVendor equivalent.
     */
    interface POByVendor {
        String getVendorKey();
        String getVendorName();
        String getStorerKey();
        String getPeriod();
        Integer getPOCount();
        Integer getLineCount();
        BigDecimal getTotalOrdered();
        BigDecimal getTotalReceived();
        BigDecimal getFillRate();
        BigDecimal getAvgLeadTimeDays();
        Integer getOnTimeCount();
        Integer getLateCount();
        BigDecimal getOnTimePercent();
        BigDecimal getQualityRejectRate();
        BigDecimal getTotalValue();
    }

    /**
     * V_BI_PO_Fulfillment equivalent.
     */
    interface POFulfillment {
        String getPoKey();
        String getStorerKey();
        String getExternPoKey();
        Integer getTotalLines();
        Integer getFullyReceivedLines();
        Integer getPartiallyReceivedLines();
        Integer getNotReceivedLines();
        BigDecimal getTotalQtyOrdered();
        BigDecimal getTotalQtyReceived();
        BigDecimal getFulfillmentPercent();
        Integer getASNCount();
        Integer getReceiptCount();
        LocalDateTime getFirstReceiptDate();
        LocalDateTime getLastReceiptDate();
        Integer getDaysToComplete();
        LocalDateTime getExpectedDate();
        Integer getDaysVariance(); // positive = late, negative = early
        String getFulfillmentStatus(); // COMPLETE, PARTIAL, OPEN
    }
}
