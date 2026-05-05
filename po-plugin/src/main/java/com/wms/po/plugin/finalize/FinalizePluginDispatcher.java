package com.wms.po.plugin.finalize;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatcher for executing finalization plugins.
 *
 * Handles plugin resolution, execution ordering, error handling,
 * and result aggregation.
 *
 * Replaces the dynamic SP execution pattern from:
 * - WM.lsp_FinalizeReceipt_Wrapper (pre/post finalize SP lookup)
 * - isp_PrePopulatePO_Wrapper (plugin dispatch pattern)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinalizePluginDispatcher {

    private final FinalizePluginRegistry pluginRegistry;

    /**
     * Execute all pre-finalize plugins for a receipt.
     *
     * @param context The finalization context
     * @return Aggregated result from all plugins
     */
    public DispatchResult executePreFinalize(FinalizeContext context) {
        log.info("Executing pre-finalize plugins for receipt: {}", context.getReceiptKey());

        List<FinalizePlugin> plugins = pluginRegistry.getPreFinalizePlugins(context.getStorerKey());

        return executePlugins(plugins, context, "PRE_FINALIZE");
    }

    /**
     * Execute all post-finalize plugins for a receipt.
     *
     * @param context The finalization context
     * @return Aggregated result from all plugins
     */
    public DispatchResult executePostFinalize(FinalizeContext context) {
        log.info("Executing post-finalize plugins for receipt: {}", context.getReceiptKey());

        List<FinalizePlugin> plugins = pluginRegistry.getPostFinalizePlugins(context.getStorerKey());

        return executePlugins(plugins, context, "POST_FINALIZE");
    }

    /**
     * Execute a specific plugin by ID.
     *
     * @param pluginId The plugin ID
     * @param context The finalization context
     * @return Plugin result
     */
    public FinalizePluginResult executePlugin(String pluginId, FinalizeContext context) {
        FinalizePlugin plugin = pluginRegistry.getPlugin(pluginId);

        if (plugin == null) {
            log.warn("Plugin not found: {}", pluginId);
            return FinalizePluginResult.failure("PLUGIN_NOT_FOUND", "Plugin not found: " + pluginId);
        }

        return executeWithTracking(plugin, context);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal Execution
    // ═══════════════════════════════════════════════════════════════════════

    private DispatchResult executePlugins(List<FinalizePlugin> plugins,
                                           FinalizeContext context,
                                           String phase) {
        DispatchResult result = new DispatchResult();
        result.setPhase(phase);
        result.setReceiptKey(context.getReceiptKey());

        if (plugins.isEmpty()) {
            log.debug("No {} plugins to execute for storer: {}", phase, context.getStorerKey());
            result.setSuccess(true);
            return result;
        }

        log.debug("Executing {} {} plugins", plugins.size(), phase);

        for (FinalizePlugin plugin : plugins) {
            // Check if we should skip remaining plugins
            if (context.isSkipRemainingPlugins()) {
                log.info("Skipping remaining plugins as requested");
                break;
            }

            // Check if plugin should execute
            if (!plugin.shouldExecute(context)) {
                log.debug("Plugin {} skipped (shouldExecute=false)", plugin.getPluginId());
                continue;
            }

            // Execute plugin
            FinalizePluginResult pluginResult = executeWithTracking(plugin, context);
            result.addPluginResult(plugin.getPluginId(), pluginResult);

            // Handle failure
            if (!pluginResult.isSuccess()) {
                log.error("Plugin {} failed: {}", plugin.getPluginId(), pluginResult.getErrorMessage());

                if (!pluginResult.isContinueProcessing()) {
                    result.setSuccess(false);
                    result.setFailedPlugin(plugin.getPluginId());
                    result.setErrorMessage(pluginResult.getErrorMessage());
                    return result;
                }
            }

            // Handle skip remaining
            if (pluginResult.isSkipRemainingPlugins()) {
                context.setSkipRemainingPlugins(true);
            }

            // Accumulate warnings
            result.getWarnings().addAll(pluginResult.getWarnings());
        }

        result.setSuccess(true);
        result.setPluginsExecuted(result.getPluginResults().size());

        log.info("{} complete: {} plugins executed, {} warnings",
            phase, result.getPluginsExecuted(), result.getWarnings().size());

        return result;
    }

    private FinalizePluginResult executeWithTracking(FinalizePlugin plugin, FinalizeContext context) {
        String pluginId = plugin.getPluginId();
        log.debug("Executing plugin: {}", pluginId);

        long startTime = System.currentTimeMillis();

        try {
            FinalizePluginResult result = plugin.execute(context);
            result.setExecutionTimeMs(System.currentTimeMillis() - startTime);

            log.debug("Plugin {} completed in {}ms, success={}",
                pluginId, result.getExecutionTimeMs(), result.isSuccess());

            return result;

        } catch (Exception e) {
            log.error("Plugin {} threw exception: {}", pluginId, e.getMessage(), e);

            return FinalizePluginResult.builder()
                .success(false)
                .errorCode("PLUGIN_EXCEPTION")
                .errorMessage("Plugin " + pluginId + " failed: " + e.getMessage())
                .continueProcessing(false)
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Dispatch Result
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    public static class DispatchResult {
        private boolean success;
        private String phase;
        private String receiptKey;
        private int pluginsExecuted;
        private String failedPlugin;
        private String errorMessage;
        private List<String> warnings = new ArrayList<>();
        private List<PluginExecutionRecord> pluginResults = new ArrayList<>();

        public void addPluginResult(String pluginId, FinalizePluginResult result) {
            pluginResults.add(new PluginExecutionRecord(pluginId, result));
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public boolean hasFailed() {
            return failedPlugin != null;
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class PluginExecutionRecord {
        private String pluginId;
        private FinalizePluginResult result;
    }
}
