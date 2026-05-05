package com.wms.po.variation.plugin;

import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;

/**
 * Plugin interface for post-populate hooks in the variation framework.
 *
 * Post-populate plugins execute AFTER receipt creation and can:
 * - Perform additional processing on the created receipt
 * - Trigger external integrations
 * - Update related records
 * - Send notifications
 */
public interface PostPopulatePlugin {

    /**
     * Execute post-populate logic.
     *
     * @param receiptKey The created receipt key
     * @param request The original populate request
     * @param context The variation context
     * @return Result indicating success/failure
     */
    PluginResult postPopulate(String receiptKey, PopulateRequest request, VariationContext context);
}
