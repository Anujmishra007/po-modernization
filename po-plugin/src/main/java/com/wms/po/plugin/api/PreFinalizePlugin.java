package com.wms.po.plugin.api;

import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.VariationContext;

/**
 * Plugin interface for pre-finalization logic.
 * Implements the ispPRREC* stored procedure pattern.
 *
 * Pre-finalize plugins:
 * - Run before status update and inventory posting
 * - Can modify receipt data, apply transformations
 * - Can block finalization if conditions not met
 *
 * Examples:
 * - ispPRREC01: H&M China pre-finalize validation
 * - ispPRREC02: Nike pre-finalize quality checks
 * - ispPRREC03: Adidas customs validation
 */
public interface PreFinalizePlugin {

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
     * Determine if this plugin should execute for the given request
     */
    boolean shouldExecute(FinalizeRequest request, VariationContext context);

    /**
     * Execute the plugin logic
     *
     * @param request Finalization request
     * @param context Variation context
     * @return Result indicating success/failure and whether to continue
     */
    PluginResult execute(FinalizeRequest request, VariationContext context);
}
