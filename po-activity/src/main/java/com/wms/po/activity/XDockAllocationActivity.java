package com.wms.po.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Activity interface for Cross-Dock (XDock) Allocation operations.
 *
 * Replaces:
 * - SP-007: WM.lsp_FlowThruAllocate_Wrapper
 * - SP-008: WM.lsp_XDockAllocation_Wrapper
 *
 * XDock allocation bypasses traditional putaway by directly allocating
 * received inventory to outbound orders.
 */
@ActivityInterface
public interface XDockAllocationActivity {

    /**
     * Execute flow-through (XDock) allocation for a receipt.
     * Finds matching outbound orders and creates allocations.
     *
     * @param receiptKey Receipt to process
     * @param storerKey Storer/owner key
     * @return Allocation result with created allocations
     */
    @ActivityMethod
    XDockAllocationResult allocateFlowThrough(String receiptKey, String storerKey);

    /**
     * Allocate specific receipt line to orders.
     *
     * @param receiptKey Receipt key
     * @param lineNumber Line number to allocate
     * @param orderKeys Target order keys (null = auto-match)
     * @return Allocation result
     */
    @ActivityMethod
    XDockAllocationResult allocateLine(String receiptKey, int lineNumber, List<String> orderKeys);

    /**
     * Release XDock allocation for picking.
     *
     * @param allocationId Allocation to release
     */
    @ActivityMethod
    void releaseAllocation(String allocationId);

    /**
     * Cancel XDock allocation (compensation).
     *
     * @param allocationId Allocation to cancel
     */
    @ActivityMethod
    void cancelAllocation(String allocationId);

    /**
     * Get eligible orders for XDock allocation.
     *
     * @param sku SKU to find orders for
     * @param storerKey Storer key
     * @param qty Available quantity
     * @return List of eligible orders with demand quantities
     */
    @ActivityMethod
    List<EligibleOrder> findEligibleOrders(String sku, String storerKey, BigDecimal qty);

    /**
     * Result of XDock allocation operation.
     */
    record XDockAllocationResult(
        boolean success,
        int allocationsCreated,
        BigDecimal totalQtyAllocated,
        List<AllocationDetail> allocations,
        List<String> errors,
        List<String> warnings
    ) {
        public static XDockAllocationResult success(List<AllocationDetail> allocations, BigDecimal totalQty) {
            return new XDockAllocationResult(true, allocations.size(), totalQty, allocations, List.of(), List.of());
        }

        public static XDockAllocationResult failure(List<String> errors) {
            return new XDockAllocationResult(false, 0, BigDecimal.ZERO, List.of(), errors, List.of());
        }

        public static XDockAllocationResult noMatch() {
            return new XDockAllocationResult(true, 0, BigDecimal.ZERO, List.of(), List.of(),
                List.of("No eligible orders found for XDock allocation"));
        }
    }

    /**
     * Individual allocation detail.
     */
    record AllocationDetail(
        String allocationId,
        String receiptKey,
        int receiptLineNumber,
        String orderKey,
        int orderLineNumber,
        String sku,
        BigDecimal qtyAllocated,
        String lot,
        String loc,
        String id
    ) {}

    /**
     * Order eligible for XDock allocation.
     */
    record EligibleOrder(
        String orderKey,
        int lineNumber,
        String sku,
        BigDecimal openQty,
        String priority,
        String shipDate,
        Map<String, String> lottableRequirements
    ) {}
}
