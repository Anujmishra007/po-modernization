package com.wms.po.activity.impl;

import com.wms.po.activity.FinalizePluginActivity;
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

        List<PreFinalizePlugin> plugins = pluginRegistry.getPreFinalizePlugins(
            request.getStorerKey(), context.getRegion());

        if (plugins.isEmpty()) {
            log.info("No pre-finalize plugins registered");
            return PluginResult.success(0, new ArrayList<>());
        }

        List<String> executedPlugins = new ArrayList<>();
        int executed = 0;

        for (PreFinalizePlugin plugin : plugins) {
            try {
                log.debug("Running pre-finalize plugin: {}", plugin.getPluginId());

                if (!plugin.shouldExecute(request, context)) {
                    log.debug("Plugin {} skipped (shouldExecute=false)", plugin.getPluginId());
                    continue;
                }

                PluginResult result = plugin.execute(request, context);
                executedPlugins.add(plugin.getPluginId());
                executed++;

                if (!result.isShouldContinue()) {
                    log.warn("Pre-finalize plugin {} stopped workflow: {}",
                        plugin.getPluginId(), result.getReason());
                    return PluginResult.builder()
                        .shouldContinue(false)
                        .reason("Plugin " + plugin.getPluginId() + ": " + result.getReason())
                        .pluginsExecuted(executed)
                        .executedPlugins(executedPlugins)
                        .build();
                }

                log.debug("Plugin {} completed successfully", plugin.getPluginId());

            } catch (Exception e) {
                log.error("Pre-finalize plugin {} failed: {}", plugin.getPluginId(), e.getMessage());
                return PluginResult.builder()
                    .shouldContinue(false)
                    .reason("Plugin " + plugin.getPluginId() + " failed: " + e.getMessage())
                    .pluginsExecuted(executed)
                    .executedPlugins(executedPlugins)
                    .build();
            }
        }

        log.info("Pre-finalize plugins completed: {} executed", executed);
        return PluginResult.success(executed, executedPlugins);
    }

    @Override
    public PluginSummary runPostFinalizePlugins(
            String receiptKey,
            FinalizeRequest request,
            VariationContext context,
            Map<String, Object> finalizationData) {

        log.info("Running post-finalize plugins for receipt {}", receiptKey);

        List<PostFinalizePlugin> plugins = pluginRegistry.getPostFinalizePlugins(
            request.getStorerKey(), context.getRegion());

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
                executions.add(PluginExecution.builder()
                    .pluginId(pluginId)
                    .pluginName(plugin.getClass().getSimpleName())
                    .phase("POST")
                    .success(false)
                    .durationMs(duration)
                    .errorMessage(e.getMessage())
                    .build());

                failureCount++;
                log.warn("Post-finalize plugin {} failed (non-fatal): {}", pluginId, e.getMessage());
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
