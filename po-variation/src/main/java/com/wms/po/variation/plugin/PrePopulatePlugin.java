package com.wms.po.variation.plugin;

import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;

/**
 * Plugin interface for pre-populate hooks in the variation framework.
 *
 * Pre-populate plugins execute BEFORE receipt creation and can:
 * - Validate PO data
 * - Transform/modify request data
 * - Apply client-specific business rules
 * - Block receipt creation if validation fails
 */
public interface PrePopulatePlugin {

    /**
     * Execute pre-populate logic.
     *
     * @param request The populate request
     * @param context The variation context
     * @return Result indicating success/failure and whether to continue
     */
    PluginResult prePopulate(PopulateRequest request, VariationContext context);
}
