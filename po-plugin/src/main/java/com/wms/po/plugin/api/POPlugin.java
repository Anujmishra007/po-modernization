package com.wms.po.plugin.api;

import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;

/**
 * Base interface for PO plugins
 */
public interface POPlugin {

    /**
     * Get plugin identifier
     */
    String getPluginId();

    /**
     * Check if plugin applies to context
     */
    boolean appliesTo(VariationContext context);

    /**
     * Get execution order (lower = earlier)
     */
    default int getOrder() {
        return 100;
    }

    /**
     * Execute pre-populate logic
     */
    default PluginResult prePopulate(PopulateRequest request, VariationContext context) {
        return PluginResult.success();
    }

    /**
     * Execute post-populate logic
     */
    default void postPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        // Default no-op
    }

    /**
     * Handle error
     */
    default void onError(String error, PopulateRequest request, VariationContext context) {
        // Default no-op
    }
}
