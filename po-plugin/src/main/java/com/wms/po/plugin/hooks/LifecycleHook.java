package com.wms.po.plugin.hooks;

import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;

/**
 * Lifecycle hook interface for custom extension points
 */
public interface LifecycleHook {

    /**
     * Get execution order
     */
    default int getOrder() {
        return 200; // Hooks run after plugins
    }

    /**
     * Check if this hook applies to the given context
     */
    default boolean appliesTo(VariationContext context) {
        return true; // Default: applies to all
    }

    /**
     * Called before population starts
     */
    default PluginResult onPrePopulate(PopulateRequest request, VariationContext context) {
        return PluginResult.success();
    }

    /**
     * Called after successful population
     */
    default void onPostPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        // Default no-op
    }

    /**
     * Called on error
     */
    default void onError(String error, PopulateRequest request, VariationContext context) {
        // Default no-op
    }

    /**
     * Called when workflow is cancelled
     */
    default void onCancelled(PopulateRequest request, VariationContext context) {
        // Default no-op
    }
}
