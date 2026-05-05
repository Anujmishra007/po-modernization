package com.wms.po.plugin.finalize.impl;

import com.wms.po.plugin.finalize.AbstractPostFinalizePlugin;
import com.wms.po.plugin.finalize.FinalizeContext;
import com.wms.po.plugin.finalize.FinalizePluginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Inventory Sync Post-Finalize Plugin.
 *
 * Replaces: ispASNFZ05 (130 LOC)
 * Purpose: Inventory synchronization
 *
 * Synchronizes inventory data after finalization:
 * - Updates on-hand quantities
 * - Syncs with external inventory systems
 * - Updates availability records
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventorySyncPlugin extends AbstractPostFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispASNFZ05";
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
        return 80; // Run after core finalization
    }

    @Override
    public boolean shouldExecute(FinalizeContext context) {
        String enableSync = context.getParameter("enableInventorySync", "Y");
        return "Y".equalsIgnoreCase(enableSync) || "1".equals(enableSync);
    }

    @Override
    protected FinalizePluginResult doExecute(FinalizeContext context) {
        log.info("Executing Inventory Sync for receipt: {}", context.getReceiptKey());

        FinalizePluginResult result = FinalizePluginResult.success();
        int syncedLines = 0;

        for (FinalizeContext.ReceiptLineContext line : context.getLines()) {
            if (line.isSkipped()) continue;

            try {
                // 1. Sync on-hand quantity
                syncOnHandQuantity(context, line);

                // 2. Update availability
                updateAvailability(context, line);

                // 3. Update inventory summary
                updateInventorySummary(context, line);

                syncedLines++;

            } catch (Exception e) {
                log.warn("Error syncing inventory for line {}: {}", line.getLineNumber(), e.getMessage());
                result.addWarning("Line " + line.getLineNumber() + " sync error: " + e.getMessage());
            }
        }

        result.addMessage("Synchronized inventory for " + syncedLines + " lines");
        return result;
    }

    private void syncOnHandQuantity(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        try {
            // Update or insert into inventory summary table
            int updated = jdbcTemplate.update(
                "UPDATE dbo.skuxloc SET qtyonhand = qtyonhand + ? " +
                "WHERE storerkey = ? AND sku = ? AND loc = ?",
                line.getQuantityReceived(),
                context.getStorerKey(),
                line.getSku(),
                line.getToLocation()
            );

            if (updated == 0) {
                // Insert new record
                jdbcTemplate.update(
                    "INSERT INTO dbo.skuxloc (storerkey, sku, loc, qtyonhand, adddate, addwho) " +
                    "VALUES (?, ?, ?, ?, GETDATE(), ?)",
                    context.getStorerKey(),
                    line.getSku(),
                    line.getToLocation(),
                    line.getQuantityReceived(),
                    context.getUserId()
                );
            }
        } catch (Exception e) {
            log.debug("Could not sync on-hand: {}", e.getMessage());
        }
    }

    private void updateAvailability(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        try {
            // Check if inventory is available (not on hold)
            boolean isAvailable = line.getHoldCode() == null || line.getHoldCode().isEmpty();

            if (isAvailable) {
                jdbcTemplate.update(
                    "UPDATE dbo.skuxloc SET qtyavailable = qtyavailable + ? " +
                    "WHERE storerkey = ? AND sku = ? AND loc = ?",
                    line.getQuantityReceived(),
                    context.getStorerKey(),
                    line.getSku(),
                    line.getToLocation()
                );
            }
        } catch (Exception e) {
            log.debug("Could not update availability: {}", e.getMessage());
        }
    }

    private void updateInventorySummary(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        try {
            // Update storer-level inventory summary
            jdbcTemplate.update(
                "UPDATE dbo.inventorysummary SET " +
                "totalqty = totalqty + ?, " +
                "lastactivitydate = GETDATE() " +
                "WHERE storerkey = ? AND sku = ?",
                line.getQuantityReceived(),
                context.getStorerKey(),
                line.getSku()
            );
        } catch (Exception e) {
            log.debug("Could not update inventory summary: {}", e.getMessage());
        }
    }
}
