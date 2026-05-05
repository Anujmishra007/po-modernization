package com.wms.po.plugin.region;

import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;

/**
 * Base interface for region-specific plugins
 */
public interface RegionPlugin {

    /**
     * Get the region code this plugin applies to
     */
    String getRegionCode();

    /**
     * Get execution order (lower = earlier)
     */
    default int getOrder() {
        return 50; // Region plugins run before client plugins
    }

    /**
     * Check if this plugin applies to the given region
     */
    default boolean appliesTo(String region) {
        return getRegionCode().equalsIgnoreCase(region) || "ALL".equalsIgnoreCase(getRegionCode());
    }

    /**
     * Pre-populate hook
     */
    PluginResult prePopulate(PopulateRequest request, VariationContext context);

    /**
     * Post-populate hook
     */
    default void postPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        // Default no-op
    }
}
