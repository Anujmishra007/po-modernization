package com.wms.po.domain.projection.bi;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BI Projection for Receipt Confirmation Reports.
 *
 * Replaces: VW-012 - Receipt Confirmation Views (10 views)
 * - V_BI_Receipt_Confirmation
 * - V_BI_Receipt_ConfirmationDetail
 * - V_BI_Receipt_ConfirmationByUser
 * - V_BI_Receipt_ConfirmationByLocation
 * - V_BI_Receipt_ConfirmationByShift
 * - V_BI_Receipt_Productivity
 * - V_BI_Receipt_Quality
 * - V_BI_Receipt_Exception
 * - V_BI_Receipt_Timeline
 * - V_BI_Receipt_Throughput
 */
public interface ReceiptConfirmationProjection {

    // ═══════════════════════════════════════════════════════════════════════
    // Core Receipt Confirmation Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_Receipt_Confirmation equivalent.
     */
    interface ReceiptConfirmation {
        String getReceiptKey();
        String getReceiptDetailKey();
        String getStorerKey();
        String getStorerName();
        String getSku();
        String getSkuDescription();
        BigDecimal getQtyReceived();
        String getUOM();
        String getToLocation();
        String getToId();
        String getCaseId();
        String getLottable01();
        String getLottable02();
        String getLottable03();
        LocalDateTime getLottable04();
        LocalDateTime getLottable05();
        String getConditionCode();
        String getConditionDescription();
        String getPackKey();
        BigDecimal getCube();
        BigDecimal getGrossWeight();
        BigDecimal getNetWeight();
        String getReceivedBy();
        LocalDateTime getReceivedDate();
        String getFinalizedBy();
        LocalDateTime getFinalizedDate();
        String getStatus();
    }

    /**
     * V_BI_Receipt_ConfirmationDetail equivalent.
     */
    interface ReceiptConfirmationDetail {
        String getReceiptKey();
        String getReceiptDetailKey();
        String getITrnKey();
        String getStorerKey();
        String getSku();
        BigDecimal getQty();
        String getFromLocation();
        String getFromId();
        String getToLocation();
        String getToId();
        String getTranType();
        String getTranTypeDescription();
        String getSourceType();
        String getSourceKey();
        LocalDateTime getAddDate();
        String getAddWho();
        String getLot();
        String getId();
        BigDecimal getCube();
        BigDecimal getWeight();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // User & Location Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_Receipt_ConfirmationByUser equivalent.
     */
    interface ReceiptByUser {
        String getUserKey();
        String getUserName();
        String getStorerKey();
        LocalDateTime getWorkDate();
        String getShift();
        Integer getReceiptsProcessed();
        Integer getLinesProcessed();
        BigDecimal getTotalQty();
        BigDecimal getTotalCube();
        BigDecimal getTotalWeight();
        BigDecimal getAvgQtyPerLine();
        BigDecimal getLinesPerHour();
        BigDecimal getUnitsPerHour();
        Integer getExceptionCount();
        BigDecimal getAccuracyPercent();
        BigDecimal getHoursWorked();
    }

    /**
     * V_BI_Receipt_ConfirmationByLocation equivalent.
     */
    interface ReceiptByLocation {
        String getLocationKey();
        String getZone();
        String getAisle();
        String getStorerKey();
        LocalDateTime getWorkDate();
        Integer getReceiptCount();
        BigDecimal getTotalQty();
        BigDecimal getTotalCube();
        BigDecimal getCapacityUsed();
        BigDecimal getCapacityPercent();
        Integer getUniqueSKUs();
        BigDecimal getAvgQtyPerReceipt();
        LocalDateTime getLastActivityDate();
    }

    /**
     * V_BI_Receipt_ConfirmationByShift equivalent.
     */
    interface ReceiptByShift {
        String getShift();
        String getShiftDescription();
        LocalDateTime getWorkDate();
        String getStorerKey();
        Integer getUserCount();
        Integer getReceiptCount();
        Integer getLineCount();
        BigDecimal getTotalQty();
        BigDecimal getTotalCube();
        BigDecimal getAvgQtyPerUser();
        BigDecimal getAvgLinesPerUser();
        BigDecimal getUnitsPerHour();
        BigDecimal getLinesPerHour();
        Integer getExceptionCount();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Productivity & Quality Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_Receipt_Productivity equivalent.
     */
    interface ReceiptProductivity {
        String getStorerKey();
        String getStorerName();
        String getPeriod();
        LocalDateTime getPeriodStart();
        LocalDateTime getPeriodEnd();
        Integer getTotalReceipts();
        Integer getTotalLines();
        BigDecimal getTotalQty();
        BigDecimal getTotalCube();
        BigDecimal getAvgReceiptsPerDay();
        BigDecimal getAvgLinesPerDay();
        BigDecimal getAvgQtyPerDay();
        BigDecimal getPeakUnitsPerHour();
        BigDecimal getAvgUnitsPerHour();
        Integer getPeakUserCount();
        BigDecimal getAvgProcessingMinutes();
    }

    /**
     * V_BI_Receipt_Quality equivalent.
     */
    interface ReceiptQuality {
        String getStorerKey();
        String getStorerName();
        String getPeriod();
        Integer getTotalReceipts();
        Integer getCleanReceipts();
        Integer getExceptionReceipts();
        BigDecimal getCleanPercent();
        Integer getVarianceCount();
        BigDecimal getTotalVarianceQty();
        Integer getDamageCount();
        BigDecimal getDamageQty();
        Integer getShortCount();
        Integer getOverCount();
        Integer getWrongItemCount();
        BigDecimal getFirstPassYield();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Exception & Timeline Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_Receipt_Exception equivalent.
     */
    interface ReceiptException {
        String getExceptionKey();
        String getReceiptKey();
        String getReceiptDetailKey();
        String getStorerKey();
        String getExceptionType();
        String getExceptionDescription();
        String getSku();
        BigDecimal getExpectedQty();
        BigDecimal getActualQty();
        BigDecimal getVarianceQty();
        String getStatus();
        LocalDateTime getCreatedDate();
        String getCreatedBy();
        LocalDateTime getResolvedDate();
        String getResolvedBy();
        String getResolutionNotes();
        Integer getMinutesToResolve();
    }

    /**
     * V_BI_Receipt_Timeline equivalent.
     */
    interface ReceiptTimeline {
        String getReceiptKey();
        String getStorerKey();
        LocalDateTime getCreatedDate();
        LocalDateTime getArrivalDate();
        LocalDateTime getUnloadStartDate();
        LocalDateTime getUnloadEndDate();
        LocalDateTime getReceiveStartDate();
        LocalDateTime getReceiveEndDate();
        LocalDateTime getPutawayStartDate();
        LocalDateTime getPutawayEndDate();
        LocalDateTime getFinalizedDate();
        Integer getMinutesToUnload();
        Integer getMinutesToReceive();
        Integer getMinutesToPutaway();
        Integer getTotalMinutes();
        String getStatus();
    }

    /**
     * V_BI_Receipt_Throughput equivalent.
     */
    interface ReceiptThroughput {
        String getStorerKey();
        LocalDateTime getHour();
        Integer getReceiptCount();
        Integer getLineCount();
        BigDecimal getTotalQty();
        BigDecimal getTotalCube();
        Integer getUserCount();
        BigDecimal getUnitsPerHour();
        BigDecimal getLinesPerHour();
        BigDecimal getCubePerHour();
        BigDecimal getUtilizationPercent();
    }
}
