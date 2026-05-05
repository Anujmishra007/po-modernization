package com.wms.po.plugin.populate;

/**
 * Plugin interface for post-populate hooks.
 *
 * Post-populate plugins execute AFTER receipt creation and can:
 * - Create related records
 * - Send notifications
 * - Update external systems
 * - Trigger downstream processes
 *
 * Replaces isp_PostPopulatePO* stored procedure series.
 */
public interface PostPopulatePlugin extends PopulatePlugin {

    /**
     * Execute post-populate logic.
     *
     * @param receiptKey The created receipt key
     * @param request The original populate request
     * @param context The variation context
     */
    void postPopulate(String receiptKey, PopulateRequest request, PopulateContext context);
}
