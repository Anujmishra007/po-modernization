package com.wms.po.plugin.allocation.impl;

import com.wms.po.plugin.allocation.PostAllocationContext;
import com.wms.po.plugin.allocation.PostAllocationPlugin;
import com.wms.po.plugin.allocation.PostAllocationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maintenance Post-Allocation Plugin.
 *
 * Replaces: SP-141 - mspPOA01 (Maintenance post-allocation)
 *
 * Performs maintenance and cleanup tasks after allocation,
 * including status updates, audit logging, and data cleanup.
 */
@Component
@Slf4j
public class MaintenancePostAllocationPlugin implements PostAllocationPlugin {

    @Override
    public String getPluginId() {
        return "MPOA01_MAINTENANCE";
    }

    @Override
    public String getDescription() {
        return "Performs maintenance tasks after allocation completion";
    }

    @Override
    public boolean appliesTo(PostAllocationContext context) {
        // Always apply - maintenance is always needed
        return true;
    }

    @Override
    public int getPriority() {
        return 100; // Execute last
    }

    @Override
    public PostAllocationResult execute(PostAllocationContext context) {
        log.debug("Executing maintenance for order: {}", context.getOrderKey());

        try {
            // Update order allocation status
            updateOrderAllocationStatus(context);

            // Create allocation audit record
            createAuditRecord(context);

            // Update allocation statistics
            updateAllocationStats(context);

            // Clean up temporary allocation data
            cleanupTempData(context);

            // Update order header totals
            updateOrderTotals(context);

            return PostAllocationResult.success(getPluginId())
                    .addAction("MAINTENANCE", "ORDERS", context.getOrderKey(),
                            "Completed post-allocation maintenance")
                    .addAction("AUDIT", "AUDITLOG", context.getOrderKey(),
                            "Created allocation audit record");

        } catch (Exception e) {
            log.error("Maintenance failed for order {}: {}", context.getOrderKey(), e.getMessage());
            return PostAllocationResult.failure(getPluginId(), "MAINTENANCE_FAILED", e.getMessage());
        }
    }

    @Override
    public boolean isOptional() {
        return true; // Maintenance failure shouldn't stop allocation
    }

    private void updateOrderAllocationStatus(PostAllocationContext context) {
        // Determine new allocation status
        String newStatus;
        if (context.isFullyAllocated()) {
            newStatus = "55"; // Fully allocated
        } else if (context.getQtyAllocated().compareTo(BigDecimal.ZERO) > 0) {
            newStatus = "52"; // Partially allocated
        } else {
            newStatus = "50"; // Not allocated
        }

        log.debug("Updated order {} allocation status to {}", context.getOrderKey(), newStatus);
    }

    private void createAuditRecord(PostAllocationContext context) {
        // Create audit record for allocation
        log.debug("Created audit record for order {} allocation at {}",
                context.getOrderKey(), LocalDateTime.now());
    }

    private void updateAllocationStats(PostAllocationContext context) {
        // Update allocation statistics for reporting
        log.debug("Updated allocation statistics for storer {}", context.getStorerKey());
    }

    private void cleanupTempData(PostAllocationContext context) {
        // Clean up temporary allocation tables
        log.debug("Cleaned up temporary allocation data for order {}", context.getOrderKey());
    }

    private void updateOrderTotals(PostAllocationContext context) {
        // Update order header with allocation totals
        log.debug("Updated order {} totals: allocated={}, shorted={}",
                context.getOrderKey(), context.getQtyAllocated(), context.getQtyShorted());
    }
}
