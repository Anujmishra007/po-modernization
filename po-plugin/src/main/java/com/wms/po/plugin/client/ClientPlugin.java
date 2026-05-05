package com.wms.po.plugin.client;

import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;

/**
 * Base interface for client-specific plugins
 */
public interface ClientPlugin {

    /**
     * Get the client code this plugin applies to
     */
    String getClientCode();

    /**
     * Get execution order (lower = earlier)
     */
    default int getOrder() {
        return 100;
    }

    /**
     * Check if this plugin applies to the given client
     */
    default boolean appliesTo(String client) {
        return getClientCode().equalsIgnoreCase(client) || "ALL".equalsIgnoreCase(getClientCode());
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
