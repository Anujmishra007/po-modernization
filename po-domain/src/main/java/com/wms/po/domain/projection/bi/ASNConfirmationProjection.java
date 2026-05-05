package com.wms.po.domain.projection.bi;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BI Projection for ASN Confirmation Reports.
 *
 * Replaces: VW-010 - ASN Confirmation Views (8 views)
 * - V_BI_ASN_Confirmation
 * - V_BI_ASN_ConfirmationDetail
 * - V_BI_ASN_ConfirmationSummary
 * - V_BI_ASN_ConfirmationByStorer
 * - V_BI_ASN_ConfirmationByDate
 * - V_BI_ASN_ConfirmationByCarrier
 * - V_BI_ASN_ConfirmationVariance
 * - V_BI_ASN_ConfirmationTrend
 */
public interface ASNConfirmationProjection {

    // ═══════════════════════════════════════════════════════════════════════
    // Core ASN Confirmation View
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_ASN_Confirmation equivalent.
     */
    interface ASNConfirmation {
        String getReceiptKey();
        String getStorerKey();
        String getStorerName();
        String getExternReceiptKey();
        String getExternASNKey();
        String getPOKey();
        String getExternPOKey();
        String getStatus();
        String getStatusDescription();
        String getReceiptType();
        LocalDateTime getExpectedReceiptDate();
        LocalDateTime getActualReceiptDate();
        LocalDateTime getFinalizedDate();
        String getCarrierId();
        String getCarrierName();
        String getTrailerNumber();
        String getSealNumber();
        BigDecimal getTotalExpectedQty();
        BigDecimal getTotalReceivedQty();
        BigDecimal getTotalVarianceQty();
        BigDecimal getVariancePercent();
        Integer getTotalLines();
        Integer getCompletedLines();
        Integer getShortLines();
        Integer getOverLines();
        String getFinalizedBy();
        String getWarehouse();
        String getDoor();
    }

    /**
     * V_BI_ASN_ConfirmationDetail equivalent.
     */
    interface ASNConfirmationDetail {
        String getReceiptKey();
        String getReceiptDetailKey();
        String getStorerKey();
        String getSku();
        String getSkuDescription();
        String getPackKey();
        Integer getPoLineNumber();
        BigDecimal getQtyExpected();
        BigDecimal getQtyReceived();
        BigDecimal getVarianceQty();
        BigDecimal getVariancePercent();
        String getUOM();
        String getLottable01();
        String getLottable02();
        String getLottable03();
        LocalDateTime getLottable04();
        LocalDateTime getLottable05();
        String getExternLineNo();
        String getConditionCode();
        String getToLocation();
        String getToId();
        String getCaseId();
        String getStatus();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Summary Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_ASN_ConfirmationSummary equivalent.
     */
    interface ASNConfirmationSummary {
        String getStorerKey();
        String getStorerName();
        Integer getTotalASNs();
        Integer getFinalizedASNs();
        Integer getPendingASNs();
        BigDecimal getTotalQtyExpected();
        BigDecimal getTotalQtyReceived();
        BigDecimal getTotalVariance();
        BigDecimal getAvgVariancePercent();
        Integer getTotalShortages();
        Integer getTotalOverages();
        BigDecimal getOnTimePercent();
        LocalDateTime getReportDate();
    }

    /**
     * V_BI_ASN_ConfirmationByStorer equivalent.
     */
    interface ASNConfirmationByStorer {
        String getStorerKey();
        String getStorerName();
        String getPeriod();
        Integer getASNCount();
        BigDecimal getQtyReceived();
        BigDecimal getAvgProcessingTime();
        BigDecimal getOnTimeDeliveryPercent();
        BigDecimal getAccuracyPercent();
        Integer getExceptionCount();
    }

    /**
     * V_BI_ASN_ConfirmationByDate equivalent.
     */
    interface ASNConfirmationByDate {
        LocalDateTime getReceiptDate();
        Integer getASNCount();
        BigDecimal getTotalQty();
        BigDecimal getAvgQtyPerASN();
        Integer getOnTimeCount();
        Integer getLateCount();
        BigDecimal getOnTimePercent();
        Integer getExceptionCount();
    }

    /**
     * V_BI_ASN_ConfirmationByCarrier equivalent.
     */
    interface ASNConfirmationByCarrier {
        String getCarrierId();
        String getCarrierName();
        Integer getDeliveryCount();
        BigDecimal getTotalQty();
        BigDecimal getAvgDeliveryTime();
        Integer getOnTimeCount();
        Integer getLateCount();
        BigDecimal getOnTimePercent();
        Integer getDamageCount();
        BigDecimal getDamageRate();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Variance & Trend Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_ASN_ConfirmationVariance equivalent.
     */
    interface ASNConfirmationVariance {
        String getReceiptKey();
        String getStorerKey();
        String getSku();
        String getSkuDescription();
        BigDecimal getExpectedQty();
        BigDecimal getReceivedQty();
        BigDecimal getVarianceQty();
        BigDecimal getVariancePercent();
        String getVarianceType(); // SHORT, OVER, MATCH
        String getVarianceReason();
        LocalDateTime getReceiptDate();
        String getAdjustedBy();
        LocalDateTime getAdjustedDate();
    }

    /**
     * V_BI_ASN_ConfirmationTrend equivalent.
     */
    interface ASNConfirmationTrend {
        String getPeriod(); // DAILY, WEEKLY, MONTHLY
        LocalDateTime getPeriodStart();
        LocalDateTime getPeriodEnd();
        Integer getASNCount();
        BigDecimal getTotalQty();
        BigDecimal getAvgProcessingHours();
        BigDecimal getAccuracyPercent();
        BigDecimal getOnTimePercent();
        BigDecimal getShortagePercent();
        BigDecimal getOveragePercent();
        BigDecimal getGrowthPercent(); // vs previous period
    }
}
