package com.wms.po.plugin.api;

import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.VariationContext;

import java.util.Map;

/**
 * Plugin interface for post-finalization logic.
 * Implements the ispASNFZ* stored procedure pattern.
 *
 * Post-finalize plugins:
 * - Run after inventory posting and putaway release
 * - Trigger external integrations, notifications
 * - Cannot block finalization (best-effort)
 *
 * Examples:
 * - ispASNFZ01: Batch release for ship-green
 * - ispASNFZ02: CN UCC stamp generation
 * - ispASNFZ09: New Look auto-adjustments
 * - ispASNFZ24: Columbia UCC creation
 */
public interface PostFinalizePlugin {

    /**
     * Unique identifier for this plugin
     */
    String getPluginId();

    /**
     * Client code this plugin applies to (e.g., "NIKE", "HM")
     * Return "STANDARD" for plugins that apply to all clients
     */
    String getClientCode();

    /**
     * Region code this plugin applies to (e.g., "CN", "IN")
     * Return "ALL" for plugins that apply to all regions
     */
    default String getRegionCode() {
        return "ALL";
    }

    /**
     * Execution order (lower = earlier)
     */
    default int getOrder() {
        return 100;
    }

    /**
     * Determine if this plugin should execute for the given finalization
     */
    boolean shouldExecute(String receiptKey, FinalizeRequest request, VariationContext context);

    /**
     * Execute the plugin logic.
     * This is best-effort - exceptions are logged but don't fail finalization.
     *
     * @param receiptKey The finalized receipt
     * @param request Original finalization request
     * @param context Variation context
     * @param finalizationData Data from finalization (inventoryIds, holdIds, putawayTaskIds, etc.)
     */
    void execute(
        String receiptKey,
        FinalizeRequest request,
        VariationContext context,
        Map<String, Object> finalizationData
    );
}
