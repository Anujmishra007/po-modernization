package com.wms.po.plugin.populate;

import com.wms.po.plugin.api.PluginResult;

/**
 * Plugin interface for pre-populate hooks.
 *
 * Pre-populate plugins execute BEFORE receipt creation and can:
 * - Validate PO data
 * - Transform/modify request data
 * - Apply client-specific business rules
 * - Block receipt creation if validation fails
 *
 * Replaces isp_PrePopulatePO* stored procedure series.
 */
public interface PrePopulatePlugin extends PopulatePlugin {

    /**
     * Execute pre-populate logic.
     *
     * @param request The populate request
     * @param context The variation context
     * @return Result indicating success/failure and whether to continue
     */
    PluginResult prePopulate(PopulateRequest request, PopulateContext context);
}
