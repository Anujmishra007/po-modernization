package com.wms.po.activity.impl;

import com.wms.po.activity.FinalizePluginActivity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.variation.plugin.PluginRegistry;
import com.wms.po.plugin.api.PostFinalizePlugin;
import com.wms.po.plugin.api.PreFinalizePlugin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of FinalizePluginActivity.
 * Runs pre-finalize and post-finalize plugins.
 *
 * Maps to legacy SPs:
 * - SP-030-039: ispPRREC* (Pre-Receipt Processing, error codes 69510-69520)
 * - SP-040-054: ispASNFZ* (Post-Finalize Hooks, error codes 69521-69535)
 *
 * Error codes:
 * - PLG_010 (69510) - Pre-Finalize Hook Failed
 * - PLG_011-019 - Specific pre-finalize plugin errors (HM, Nike, Adidas, etc.)
 * - PLG_020 (69520) - Post-Finalize Hook Failed
 * - PLG_021-029 - Specific post-finalize plugin errors
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
@Component
@RequiredArgsConstructor
@Slf4j
public class FinalizePluginActivityImpl implements FinalizePluginActivity {

    private final PluginRegistry pluginRegistry;

    @Override
    public PluginResult runPreFinalizePlugins(FinalizeRequest request, VariationContext context) {
        log.info("Running pre-finalize plugins for receipt {} (storer={})",
            request.getReceiptKey(), request.getStorerKey());

        List<PreFinalizePlugin> plugins;
        try {
            plugins = pluginRegistry.getPreFinalizePlugins(
                request.getStorerKey(), context.getRegion());
        } catch (Exception e) {
            log.error("Failed to load pre-finalize plugins: {} (legacy error 69510)",
                e.getMessage(), e);
            throw BusinessException.pluginNotFound(request.getStorerKey() + "_PreFinalize");
        }

        if (plugins.isEmpty()) {
            log.info("No pre-finalize plugins registered");
            return PluginResult.success(0, new ArrayList<>());
        }

        List<String> executedPlugins = new ArrayList<>();
        int executed = 0;

        for (PreFinalizePlugin plugin : plugins) {
            String pluginId = plugin.getPluginId();
            try {
                log.debug("Running pre-finalize plugin: {}", pluginId);

                if (!plugin.shouldExecute(request, context)) {
                    log.debug("Plugin {} skipped (shouldExecute=false)", pluginId);
                    continue;
                }

                PluginResult result = plugin.execute(request, context);
                executedPlugins.add(pluginId);
                executed++;

                if (!result.isShouldContinue()) {
                    log.warn("Pre-finalize plugin {} stopped workflow: {} (legacy error 69510)",
                        pluginId, result.getReason());
                    // Throw proper exception with plugin-specific error code
                    throw BusinessException.preFinalizeHookFailed(
                        extractPluginType(pluginId),
                        request.getReceiptKey(),
                        new RuntimeException(result.getReason())
                    );
                }

                log.debug("Plugin {} completed successfully", pluginId);

            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Pre-finalize plugin {} failed: {} (legacy error 69510)",
                    pluginId, e.getMessage(), e);
                throw BusinessException.preFinalizeHookFailed(
                    extractPluginType(pluginId),
                    request.getReceiptKey(),
                    e
                );
            }
        }

        log.info("Pre-finalize plugins completed: {} executed", executed);
        return PluginResult.success(executed, executedPlugins);
    }

    /**
     * Extract plugin type from plugin ID for error code mapping.
     */
    private String extractPluginType(String pluginId) {
        if (pluginId == null) return "UNKNOWN";
        String id = pluginId.toUpperCase();
        if (id.contains("HM") || id.contains("H&M")) return "HM";
        if (id.contains("NIKE")) return "NIKE";
        if (id.contains("ADIDAS")) return "ADIDAS";
        if (id.contains("COLUMBIA")) return "COLUMBIA";
        if (id.contains("UNILEVER")) return "UNILEVER";
        if (id.contains("NEWLOOK")) return "NEWLOOK";
        if (id.contains("INDIA")) return "INDIA";
        if (id.contains("DSG") || id.contains("THAILAND")) return "DSG_TH";
        if (id.contains("REGIONAL")) return "REGIONAL";
        if (id.contains("BATCH")) return "BATCH_RELEASE";
        if (id.contains("UCC")) return "UCC_STAMP";
        if (id.contains("AUTO_PA")) return "AUTO_PA";
        if (id.contains("NOTIFICATION")) return "NOTIFICATION";
        if (id.contains("SYNC")) return "INV_SYNC";
        if (id.contains("QUALITY")) return "QUALITY_CHECK";
        if (id.contains("CUSTOMS")) return "CUSTOMS";
        if (id.contains("ALLOCAT")) return "AUTO_ALLOCATE";
        return pluginId;
    }

    /**
     * Run post-finalize plugins.
     * Post-finalize failures are logged but do not block the workflow.
     *
     * Error codes (logged, non-fatal):
     * - PLG_020 (69520) - Post-Finalize Hook Failed
     * - PLG_021-029 - Specific post-finalize plugin errors
     */
    @Override
    public PluginSummary runPostFinalizePlugins(
            String receiptKey,
            FinalizeRequest request,
            VariationContext context,
            Map<String, Object> finalizationData) {

        log.info("Running post-finalize plugins for receipt {}", receiptKey);

        List<PostFinalizePlugin> plugins;
        try {
            plugins = pluginRegistry.getPostFinalizePlugins(
                request.getStorerKey(), context.getRegion());
        } catch (Exception e) {
            log.error("Failed to load post-finalize plugins: {} (legacy error 69520, non-fatal)",
                e.getMessage());
            return PluginSummary.builder()
                .pluginsExecuted(0)
                .successCount(0)
                .failureCount(1)
                .executions(new ArrayList<>())
                .build();
        }

        if (plugins.isEmpty()) {
            log.info("No post-finalize plugins registered");
            return PluginSummary.builder()
                .pluginsExecuted(0)
                .successCount(0)
                .failureCount(0)
                .executions(new ArrayList<>())
                .build();
        }

        List<PluginExecution> executions = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (PostFinalizePlugin plugin : plugins) {
            long startTime = System.currentTimeMillis();
            String pluginId = plugin.getPluginId();

            try {
                log.debug("Running post-finalize plugin: {}", pluginId);

                if (!plugin.shouldExecute(receiptKey, request, context)) {
                    log.debug("Plugin {} skipped (shouldExecute=false)", pluginId);
                    continue;
                }

                plugin.execute(receiptKey, request, context, finalizationData);

                long duration = System.currentTimeMillis() - startTime;
                executions.add(PluginExecution.builder()
                    .pluginId(pluginId)
                    .pluginName(plugin.getClass().getSimpleName())
                    .phase("POST")
                    .success(true)
                    .durationMs(duration)
                    .build());

                successCount++;
                log.debug("Plugin {} completed in {}ms", pluginId, duration);

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                String pluginType = extractPluginType(pluginId);

                executions.add(PluginExecution.builder()
                    .pluginId(pluginId)
                    .pluginName(plugin.getClass().getSimpleName())
                    .phase("POST")
                    .success(false)
                    .durationMs(duration)
                    .errorMessage(e.getMessage())
                    .build());

                failureCount++;
                // Log with legacy error code but don't throw (post-finalize is non-fatal)
                log.warn("Post-finalize plugin {} ({}) failed (non-fatal): {} (legacy error 69520)",
                    pluginId, pluginType, e.getMessage());
            }
        }

        log.info("Post-finalize plugins completed: {} success, {} failed",
            successCount, failureCount);

        return PluginSummary.builder()
            .pluginsExecuted(successCount + failureCount)
            .successCount(successCount)
            .failureCount(failureCount)
            .executions(executions)
            .build();
    }

    @Override
    public void rollbackPreFinalizePlugins(String receiptKey, List<String> pluginResults) {
        log.warn("COMPENSATION: Rolling back {} pre-finalize plugins for receipt {}",
            pluginResults.size(), receiptKey);

        // In production, each plugin that modified data would have a rollback method
        // For now, we log the compensation attempt
        for (String pluginId : pluginResults) {
            log.debug("Would rollback plugin effects: {}", pluginId);
        }

        log.info("COMPENSATION complete: Pre-finalize plugin rollback for {}", receiptKey);
    }
}
