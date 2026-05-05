package com.wms.po.plugin.finalize.impl;

import com.wms.po.plugin.finalize.AbstractPostFinalizePlugin;
import com.wms.po.plugin.finalize.FinalizeContext;
import com.wms.po.plugin.finalize.FinalizePluginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Batch Release Post-Finalize Plugin.
 *
 * Replaces: ispASNFZ01 (250 LOC)
 * Purpose: ShipGreen batch release
 *
 * Triggers batch release for finalized receipts:
 * - Releases batches for immediate processing
 * - Updates batch status
 * - Notifies downstream systems
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchReleasePlugin extends AbstractPostFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispASNFZ01";
    private static final String CLIENT_KEY = "*"; // All clients

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
        return 100; // Run after other plugins
    }

    @Override
    public boolean shouldExecute(FinalizeContext context) {
        // Execute for receipts configured for batch release
        String batchRelease = context.getParameter("enableBatchRelease", "N");
        return "Y".equalsIgnoreCase(batchRelease) || "1".equals(batchRelease);
    }

    @Override
    protected FinalizePluginResult doExecute(FinalizeContext context) {
        log.info("Executing Batch Release for receipt: {}", context.getReceiptKey());

        FinalizePluginResult result = FinalizePluginResult.success();

        try {
            // 1. Find batches associated with receipt
            int batchCount = findAndReleaseBatches(context);

            // 2. Update batch tracking
            if (batchCount > 0) {
                updateBatchTracking(context, batchCount);
                result.addMessage("Released " + batchCount + " batches for processing");
            } else {
                result.addMessage("No batches found for release");
            }

            // 3. Notify downstream systems
            notifyDownstream(context, batchCount);

        } catch (Exception e) {
            log.error("Error in batch release for receipt {}: {}", context.getReceiptKey(), e.getMessage());
            result.addWarning("Batch release error: " + e.getMessage());
        }

        return result;
    }

    private int findAndReleaseBatches(FinalizeContext context) {
        try {
            // Find batches linked to this receipt
            return jdbcTemplate.update(
                "UPDATE dbo.batchheader SET status = '2' " +
                "WHERE receiptkey = ? AND status = '1'",
                context.getReceiptKey()
            );
        } catch (Exception e) {
            log.debug("Could not release batches: {}", e.getMessage());
            return 0;
        }
    }

    private void updateBatchTracking(FinalizeContext context, int batchCount) {
        try {
            jdbcTemplate.update(
                "INSERT INTO dbo.batchreleasehistory (receiptkey, batchcount, releasetime, releaseby) " +
                "VALUES (?, ?, GETDATE(), ?)",
                context.getReceiptKey(), batchCount, context.getUserId()
            );
        } catch (Exception e) {
            log.debug("Could not update batch tracking: {}", e.getMessage());
        }
    }

    private void notifyDownstream(FinalizeContext context, int batchCount) {
        // Store notification data in context for later processing
        context.setSharedData("batchReleaseCount", batchCount);
        context.setSharedData("batchReleaseTime", java.time.LocalDateTime.now());
    }
}
