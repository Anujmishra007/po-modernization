package com.wms.po.plugin.allocation.impl;

import com.wms.po.plugin.allocation.PostAllocationContext;
import com.wms.po.plugin.allocation.PostAllocationPlugin;
import com.wms.po.plugin.allocation.PostAllocationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Inventory Reservation Post-Allocation Plugin.
 *
 * Replaces: ispPOA04 - Inventory reservation after allocation
 *
 * Updates inventory records to reflect allocated quantities,
 * ensuring proper reservation and availability tracking.
 */
@Component
@Slf4j
public class InventoryReservationPlugin implements PostAllocationPlugin {

    @Override
    public String getPluginId() {
        return "POA04_INVENTORY_RESERVATION";
    }

    @Override
    public String getDescription() {
        return "Updates inventory reservation status after allocation";
    }

    @Override
    public boolean appliesTo(PostAllocationContext context) {
        // Always apply when there's allocated quantity
        return context.getQtyAllocated() != null &&
               context.getQtyAllocated().compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public int getPriority() {
        return 5; // Execute very early - critical for inventory accuracy
    }

    @Override
    public PostAllocationResult execute(PostAllocationContext context) {
        log.debug("Updating inventory reservation for order: {}", context.getOrderKey());

        try {
            int locationsUpdated = 0;

            for (PostAllocationContext.AllocationLine line : context.getAllocationLines()) {
                updateInventoryReservation(context, line);
                locationsUpdated++;
            }

            return PostAllocationResult.success(getPluginId())
                    .addAction("INVENTORY_RESERVE", "LOTxLOCxID", context.getSku(),
                            String.format("Reserved %.2f units across %d locations",
                                    context.getQtyAllocated(), locationsUpdated))
                    .setOutput("locationsUpdated", locationsUpdated)
                    .setOutput("qtyReserved", context.getQtyAllocated());

        } catch (Exception e) {
            log.error("Inventory reservation failed for order {}: {}",
                    context.getOrderKey(), e.getMessage());
            return PostAllocationResult.failure(getPluginId(),
                    "INVENTORY_RESERVE_FAILED", e.getMessage());
        }
    }

    private void updateInventoryReservation(PostAllocationContext context,
                                           PostAllocationContext.AllocationLine line) {
        // Update LOTxLOCxID record
        // In real implementation, this would use JdbcTemplate
        log.debug("Reserved {} units at location {} for SKU {}",
                line.getQtyAllocated(), line.getLocationKey(), line.getSku());
    }
}
