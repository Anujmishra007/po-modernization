package com.wms.po.activity;

import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.VariationContext;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Activity for running finalization plugins.
 *
 * Pre-finalize plugins (ispPRREC* series):
 * - Run before status update and inventory posting
 * - Can modify receipt data, apply transformations
 * - Can block finalization if conditions not met
 *
 * Post-finalize plugins (ispASNFZ* series):
 * - Run after inventory posting and putaway release
 * - Trigger external integrations, notifications
 * - Cannot block finalization (best-effort)
 */
@ActivityInterface
public interface FinalizePluginActivity {

    /**
     * Run all applicable pre-finalize plugins.
     * Plugin selection based on client/region from context.
     *
     * @param request The finalize request
     * @param context Variation context with client/region info
     * @return Plugin result indicating whether to continue
     */
    @ActivityMethod
    PluginResult runPreFinalizePlugins(FinalizeRequest request, VariationContext context);

    /**
     * Run all applicable post-finalize plugins.
     * These run after successful finalization.
     *
     * @param receiptKey The finalized receipt
     * @param request Original finalize request
     * @param context Variation context
     * @param finalizationData Data from finalization (inventory IDs, quantities, etc.)
     * @return Summary of plugin executions
     */
    @ActivityMethod
    PluginSummary runPostFinalizePlugins(
        String receiptKey,
        FinalizeRequest request,
        VariationContext context,
        Map<String, Object> finalizationData
    );

    /**
     * Rollback pre-finalize plugin effects.
     * Called when finalization fails after pre-plugins ran.
     *
     * @param receiptKey Receipt to rollback
     * @param pluginResults Results from pre-finalize plugins
     */
    @ActivityMethod
    void rollbackPreFinalizePlugins(String receiptKey, List<String> pluginResults);

    /**
     * Summary of plugin executions
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class PluginSummary {
        private int pluginsExecuted;
        private int successCount;
        private int failureCount;
        private List<PluginExecution> executions;
        private List<String> errors;
    }

    /**
     * Single plugin execution record
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class PluginExecution {
        private String pluginId;
        private String pluginName;
        private String phase;    // PRE or POST
        private boolean success;
        private long durationMs;
        private String errorMessage;
    }
}
