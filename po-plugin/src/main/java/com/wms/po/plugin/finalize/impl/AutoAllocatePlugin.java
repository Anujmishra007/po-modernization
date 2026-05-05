package com.wms.po.plugin.finalize.impl;

import com.wms.po.plugin.finalize.AbstractPostFinalizePlugin;
import com.wms.po.plugin.finalize.FinalizeContext;
import com.wms.po.plugin.finalize.FinalizePluginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Auto-Allocate Post-Finalize Plugin.
 *
 * Replaces: ispASNFZ08 (100 LOC)
 * Purpose: Automatic allocation trigger
 *
 * Triggers automatic allocation for received inventory:
 * - Checks for pending orders waiting for stock
 * - Initiates allocation process
 * - Updates order availability status
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoAllocatePlugin extends AbstractPostFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispASNFZ08";
    private static final String CLIENT_KEY = "*";

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public String getClientKey() {
        return CLIENT_KEY;
    }

    @Override
    public int getPriority() {
        return 90; // Run late in the process
    }

    @Override
    public boolean shouldExecute(FinalizeContext context) {
        String enableAutoAlloc = context.getParameter("enableAutoAllocation", "N");
        return "Y".equalsIgnoreCase(enableAutoAlloc) || "1".equals(enableAutoAlloc);
    }

    @Override
    protected FinalizePluginResult doExecute(FinalizeContext context) {
        log.info("Executing Auto-Allocate for receipt: {}", context.getReceiptKey());

        FinalizePluginResult result = FinalizePluginResult.success();
        int allocatedOrders = 0;

        for (FinalizeContext.ReceiptLineContext line : context.getLines()) {
            if (line.isSkipped()) continue;

            try {
                // 1. Find pending orders for this SKU
                List<Map<String, Object>> pendingOrders = findPendingOrders(context, line);

                // 2. Queue orders for allocation
                for (Map<String, Object> order : pendingOrders) {
                    queueForAllocation(order);
                    allocatedOrders++;
                }

            } catch (Exception e) {
                log.warn("Error in auto-allocate for line {}: {}", line.getLineNumber(), e.getMessage());
                result.addWarning("Line " + line.getLineNumber() + " allocation error: " + e.getMessage());
            }
        }

        if (allocatedOrders > 0) {
            result.addMessage("Queued " + allocatedOrders + " orders for allocation");

            // Mark receipt for allocation processing
            markForAllocation(context);
        }

        return result;
    }

    private List<Map<String, Object>> findPendingOrders(FinalizeContext context,
                                                        FinalizeContext.ReceiptLineContext line) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT DISTINCT od.orderkey, od.orderlinenumber " +
                "FROM dbo.orderdetail od " +
                "JOIN dbo.orders o ON od.orderkey = o.orderkey " +
                "WHERE od.storerkey = ? AND od.sku = ? " +
                "AND o.status IN ('0', '1') " + // Open/Released orders
                "AND od.openqty > 0 " +
                "ORDER BY o.orderdate",
                context.getStorerKey(),
                line.getSku()
            );
        } catch (Exception e) {
            log.debug("Could not find pending orders: {}", e.getMessage());
            return List.of();
        }
    }

    private void queueForAllocation(Map<String, Object> order) {
        try {
            String orderKey = (String) order.get("orderkey");
            jdbcTemplate.update(
                "INSERT INTO dbo.allocationqueue (orderkey, status, queuedate, priority) " +
                "VALUES (?, 'PENDING', GETDATE(), 5) " +
                "ON CONFLICT (orderkey) DO NOTHING",
                orderKey
            );
        } catch (Exception e) {
            log.debug("Could not queue order for allocation: {}", e.getMessage());
        }
    }

    private void markForAllocation(FinalizeContext context) {
        try {
            jdbcTemplate.update(
                "UPDATE dbo.receipt SET allocateflag = 'Y' WHERE receiptkey = ?",
                context.getReceiptKey()
            );
        } catch (Exception e) {
            log.debug("Could not mark receipt for allocation: {}", e.getMessage());
        }
    }
}
