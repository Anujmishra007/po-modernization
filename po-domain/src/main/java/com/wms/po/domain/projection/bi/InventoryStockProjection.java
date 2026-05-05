package com.wms.po.domain.projection.bi;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BI Projection for Inventory Stock Reports.
 *
 * Replaces: VW-013 - Inventory Stock Views (12 views)
 * - V_BI_Inventory_Stock
 * - V_BI_Inventory_StockByLocation
 * - V_BI_Inventory_StockByLot
 * - V_BI_Inventory_StockAging
 * - V_BI_Inventory_StockMovement
 * - V_BI_Inventory_StockSnapshot
 * - V_BI_Inventory_OnHand
 * - V_BI_Inventory_Available
 * - V_BI_Inventory_Allocated
 * - V_BI_Inventory_InTransit
 * - V_BI_Inventory_OnHold
 * - V_BI_Inventory_Turnover
 */
public interface InventoryStockProjection {

    // ═══════════════════════════════════════════════════════════════════════
    // Core Inventory Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_Inventory_Stock equivalent.
     */
    interface InventoryStock {
        String getStorerKey();
        String getStorerName();
        String getSku();
        String getSkuDescription();
        String getPackKey();
        BigDecimal getQtyOnHand();
        BigDecimal getQtyAllocated();
        BigDecimal getQtyAvailable();
        BigDecimal getQtyInTransit();
        BigDecimal getQtyOnHold();
        BigDecimal getQtyPicked();
        BigDecimal getQtyPacked();
        String getUOM();
        BigDecimal getCubeOnHand();
        BigDecimal getWeightOnHand();
        BigDecimal getUnitCost();
        BigDecimal getTotalValue();
        Integer getLocationCount();
        Integer getLotCount();
        Integer getIdCount();
        LocalDateTime getLastReceiptDate();
        LocalDateTime getLastShipDate();
        LocalDateTime getOldestLotDate();
    }

    /**
     * V_BI_Inventory_StockByLocation equivalent.
     */
    interface InventoryByLocation {
        String getLocationKey();
        String getZone();
        String getAisle();
        String getBay();
        String getLevel();
        String getPosition();
        String getLocationType();
        String getStorerKey();
        String getSku();
        String getSkuDescription();
        String getLot();
        String getId();
        BigDecimal getQty();
        BigDecimal getQtyAllocated();
        BigDecimal getQtyAvailable();
        BigDecimal getCube();
        BigDecimal getWeight();
        BigDecimal getCapacityCube();
        BigDecimal getUtilizationPercent();
        String getLottable01();
        String getLottable02();
        LocalDateTime getLottable04();
        LocalDateTime getLottable05();
        LocalDateTime getLastActivityDate();
    }

    /**
     * V_BI_Inventory_StockByLot equivalent.
     */
    interface InventoryByLot {
        String getStorerKey();
        String getSku();
        String getSkuDescription();
        String getLot();
        String getLottable01();
        String getLottable02();
        String getLottable03();
        LocalDateTime getLottable04();
        LocalDateTime getLottable05();
        BigDecimal getQtyOnHand();
        BigDecimal getQtyAllocated();
        BigDecimal getQtyAvailable();
        BigDecimal getQtyOnHold();
        Integer getLocationCount();
        Integer getIdCount();
        LocalDateTime getReceiptDate();
        Integer getDaysInStock();
        LocalDateTime getExpiryDate();
        Integer getDaysToExpiry();
        String getHoldStatus();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Aging & Movement Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_Inventory_StockAging equivalent.
     */
    interface InventoryAging {
        String getStorerKey();
        String getStorerName();
        String getSku();
        String getSkuDescription();
        String getAgingBucket(); // 0-30, 31-60, 61-90, 91-180, 180+
        BigDecimal getQty();
        BigDecimal getValue();
        BigDecimal getPercentOfTotal();
        Integer getLotCount();
        LocalDateTime getOldestDate();
        Integer getOldestDays();
        LocalDateTime getNewestDate();
        Integer getNewestDays();
        BigDecimal getAvgDaysInStock();
    }

    /**
     * V_BI_Inventory_StockMovement equivalent.
     */
    interface InventoryMovement {
        String getStorerKey();
        String getSku();
        LocalDateTime getMovementDate();
        BigDecimal getOpeningQty();
        BigDecimal getReceiptQty();
        BigDecimal getAdjustmentQty();
        BigDecimal getShipmentQty();
        BigDecimal getTransferInQty();
        BigDecimal getTransferOutQty();
        BigDecimal getClosingQty();
        BigDecimal getNetChange();
        Integer getTransactionCount();
    }

    /**
     * V_BI_Inventory_StockSnapshot equivalent.
     */
    interface InventorySnapshot {
        String getStorerKey();
        String getStorerName();
        LocalDateTime getSnapshotDate();
        BigDecimal getTotalQtyOnHand();
        BigDecimal getTotalQtyAllocated();
        BigDecimal getTotalQtyAvailable();
        BigDecimal getTotalValue();
        Integer getTotalSKUs();
        Integer getTotalLots();
        Integer getTotalLocations();
        BigDecimal getAvgDaysInStock();
        BigDecimal getTurnoverRate();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Status-Based Views
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_Inventory_OnHand equivalent.
     */
    interface InventoryOnHand {
        String getStorerKey();
        String getSku();
        String getSkuDescription();
        String getPackKey();
        String getUOM();
        BigDecimal getTotalQty();
        BigDecimal getTotalCube();
        BigDecimal getTotalWeight();
        BigDecimal getTotalValue();
        Integer getLocationCount();
        Integer getLotCount();
        String getMostRecentLot();
        LocalDateTime getMostRecentReceiptDate();
        String getMostUsedLocation();
    }

    /**
     * V_BI_Inventory_Available equivalent.
     */
    interface InventoryAvailable {
        String getStorerKey();
        String getSku();
        String getSkuDescription();
        String getLocationKey();
        String getLot();
        String getId();
        BigDecimal getQtyAvailable();
        String getUOM();
        String getLottable01();
        String getLottable02();
        LocalDateTime getLottable04();
        LocalDateTime getLottable05();
        String getHoldStatus();
        Boolean getIsPickable();
        Boolean getIsAllocatable();
        Integer getPriority();
    }

    /**
     * V_BI_Inventory_Allocated equivalent.
     */
    interface InventoryAllocated {
        String getStorerKey();
        String getSku();
        String getLocationKey();
        String getLot();
        String getId();
        BigDecimal getQtyAllocated();
        String getOrderKey();
        String getOrderDetailKey();
        String getPickDetailKey();
        String getAllocationType();
        LocalDateTime getAllocationDate();
        String getAllocatedBy();
        String getStatus();
    }

    /**
     * V_BI_Inventory_InTransit equivalent.
     */
    interface InventoryInTransit {
        String getStorerKey();
        String getSku();
        String getSkuDescription();
        String getFromLocation();
        String getToLocation();
        String getId();
        String getLot();
        BigDecimal getQty();
        String getTaskType();
        String getTaskKey();
        String getAssignedTo();
        LocalDateTime getTaskCreatedDate();
        LocalDateTime getTaskStartDate();
        Integer getMinutesInTransit();
        String getStatus();
    }

    /**
     * V_BI_Inventory_OnHold equivalent.
     */
    interface InventoryOnHold {
        String getStorerKey();
        String getSku();
        String getSkuDescription();
        String getLocationKey();
        String getLot();
        String getId();
        BigDecimal getQtyOnHold();
        String getHoldCode();
        String getHoldDescription();
        String getHoldReason();
        LocalDateTime getHoldDate();
        String getHeldBy();
        Integer getDaysOnHold();
        LocalDateTime getExpectedReleaseDate();
        String getStatus();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Turnover View
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * V_BI_Inventory_Turnover equivalent.
     */
    interface InventoryTurnover {
        String getStorerKey();
        String getStorerName();
        String getSku();
        String getSkuDescription();
        String getPeriod();
        BigDecimal getAvgOnHand();
        BigDecimal getTotalShipped();
        BigDecimal getTurnoverRate();
        Integer getDaysOfSupply();
        BigDecimal getSellThroughRate();
        BigDecimal getWeeksOfCover();
        String getTurnoverCategory(); // FAST, MEDIUM, SLOW, DEAD
        BigDecimal getReorderPoint();
        BigDecimal getSafetyStock();
    }
}
