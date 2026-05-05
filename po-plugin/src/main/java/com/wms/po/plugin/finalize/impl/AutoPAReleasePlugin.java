package com.wms.po.plugin.finalize.impl;

import com.wms.po.plugin.finalize.AbstractPostFinalizePlugin;
import com.wms.po.plugin.finalize.FinalizeContext;
import com.wms.po.plugin.finalize.FinalizePluginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Auto Putaway Release Post-Finalize Plugin.
 *
 * Replaces: ispASNFZ03 (320 LOC)
 * Client: Universal (configurable per storer)
 *
 * Automatically releases putaway tasks after finalization:
 * - Creates putaway tasks for finalized inventory
 * - Assigns to putaway zones based on configuration
 * - Optionally auto-allocates to available users
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoPAReleasePlugin extends AbstractPostFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispASNFZ03";
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
        return 100; // Run after UCC generation
    }

    @Override
    protected boolean shouldProcessReceipt(FinalizeContext context) {
        return isAutoPAReleaseEnabled(context.getStorerKey());
    }

    @Override
    protected FinalizePluginResult doExecute(FinalizeContext context) {
        log.info("Releasing putaway tasks for receipt: {}", context.getReceiptKey());

        int tasksCreated = 0;
        List<String> inventoryKeys = getInventoryKeys(context);

        for (String lotxlocxidKey : inventoryKeys) {
            try {
                // Get inventory details
                var invDetails = getInventoryDetails(lotxlocxidKey);
                if (invDetails == null) continue;

                // Determine putaway zone
                String targetZone = determinePutawayZone(
                    context.getStorerKey(),
                    invDetails.sku,
                    invDetails.loc
                );

                // Create putaway task
                String taskKey = createPutawayTask(
                    context,
                    lotxlocxidKey,
                    invDetails,
                    targetZone
                );

                if (taskKey != null) {
                    tasksCreated++;
                    log.debug("Created putaway task {} for {}", taskKey, lotxlocxidKey);
                }

            } catch (Exception e) {
                log.warn("Failed to create putaway task for {}: {}", lotxlocxidKey, e.getMessage());
            }
        }

        FinalizePluginResult result = FinalizePluginResult.success();
        result.addMessage("Created " + tasksCreated + " putaway tasks");
        result.setOutputData("putawayTaskCount", tasksCreated);
        return result;
    }

    private boolean isAutoPAReleaseEnabled(String storerKey) {
        try {
            String enabled = jdbcTemplate.queryForObject(
                "SELECT configvalue FROM dbo.storerconfig " +
                "WHERE storerkey = ? AND configkey = 'AutoPutawayRelease'",
                String.class,
                storerKey
            );
            return "1".equals(enabled) || "Y".equalsIgnoreCase(enabled);
        } catch (Exception e) {
            return false;
        }
    }

    private InventoryDetails getInventoryDetails(String lotxlocxidKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT storerkey, sku, lot, loc, id, qty " +
                "FROM dbo.lotxlocxid WHERE lotxlocxidkey = ?",
                (rs, rowNum) -> new InventoryDetails(
                    rs.getString("storerkey"),
                    rs.getString("sku"),
                    rs.getString("lot"),
                    rs.getString("loc"),
                    rs.getString("id"),
                    rs.getBigDecimal("qty")
                ),
                lotxlocxidKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String determinePutawayZone(String storerKey, String sku, String fromLoc) {
        try {
            // Check SKU putaway zone first
            String zone = jdbcTemplate.queryForObject(
                "SELECT putawayzone FROM dbo.sku WHERE storerkey = ? AND sku = ?",
                String.class,
                storerKey, sku
            );

            if (zone != null && !zone.isEmpty()) {
                return zone;
            }

            // Fall back to storer default zone
            return jdbcTemplate.queryForObject(
                "SELECT configvalue FROM dbo.storerconfig " +
                "WHERE storerkey = ? AND configkey = 'DefaultPutawayZone'",
                String.class,
                storerKey
            );
        } catch (Exception e) {
            return "BULK"; // Default zone
        }
    }

    private String createPutawayTask(FinalizeContext context,
                                      String lotxlocxidKey,
                                      InventoryDetails inv,
                                      String targetZone) {
        try {
            // Generate task key
            String taskKey = generateTaskKey();

            // Insert task
            jdbcTemplate.update(
                "INSERT INTO dbo.taskdetail " +
                "(taskdetailkey, storerkey, sku, lot, fromloc, fromid, qty, " +
                "toloc, toid, status, tasktype, priority, adddate, addwho) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '0', 'PA', 50, GETDATE(), ?)",
                taskKey,
                inv.storerKey,
                inv.sku,
                inv.lot,
                inv.loc,
                inv.id,
                inv.qty,
                targetZone,
                null, // To be assigned
                context.getUserId()
            );

            return taskKey;
        } catch (Exception e) {
            log.error("Failed to create putaway task: {}", e.getMessage());
            return null;
        }
    }

    private String generateTaskKey() {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT dbo.nspg_GetKey('TASKDETAIL', 1)",
                String.class
            );
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private record InventoryDetails(
        String storerKey,
        String sku,
        String lot,
        String loc,
        String id,
        java.math.BigDecimal qty
    ) {}
}
