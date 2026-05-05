package com.wms.po.plugin.allocation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Post-Allocation Plugin Dispatcher.
 *
 * Replaces: SP-140 dispatcher logic (ispPOA01-ispPOA26)
 *
 * Orchestrates execution of all applicable post-allocation plugins
 * in priority order with proper error handling and result aggregation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostAllocationDispatcher {

    private final PostAllocationPluginRegistry registry;

    /**
     * Execute all applicable post-allocation plugins.
     *
     * @param context The allocation context
     * @return Aggregated result from all plugins
     */
    public DispatchResult dispatch(PostAllocationContext context) {
        log.info("Dispatching post-allocation plugins for order: {}", context.getOrderKey());

        List<PostAllocationPlugin> plugins = registry.getApplicablePlugins(context);
        log.debug("Found {} applicable plugins", plugins.size());

        DispatchResult dispatchResult = new DispatchResult();
        dispatchResult.setOrderKey(context.getOrderKey());
        dispatchResult.setStartTime(LocalDateTime.now());

        for (PostAllocationPlugin plugin : plugins) {
            try {
                long startTime = System.currentTimeMillis();
                PostAllocationResult result = plugin.execute(context);
                result.setExecutionDurationMs(System.currentTimeMillis() - startTime);

                dispatchResult.addPluginResult(result);

                if (!result.isSuccess() && !plugin.isOptional()) {
                    log.error("Required plugin {} failed for order {}",
                            plugin.getPluginId(), context.getOrderKey());

                    if (!result.isContinueOnFailure()) {
                        dispatchResult.setSuccess(false);
                        dispatchResult.setFailedPlugin(plugin.getPluginId());
                        dispatchResult.setErrorMessage(result.getErrorMessage());
                        break;
                    }
                }

            } catch (Exception e) {
                log.error("Plugin {} threw exception for order {}: {}",
                        plugin.getPluginId(), context.getOrderKey(), e.getMessage());

                PostAllocationResult errorResult = PostAllocationResult.failure(
                        plugin.getPluginId(), "PLUGIN_EXCEPTION", e.getMessage());
                dispatchResult.addPluginResult(errorResult);

                if (!plugin.isOptional()) {
                    dispatchResult.setSuccess(false);
                    dispatchResult.setFailedPlugin(plugin.getPluginId());
                    dispatchResult.setErrorMessage(e.getMessage());
                    break;
                }
            }
        }

        dispatchResult.setEndTime(LocalDateTime.now());

        log.info("Post-allocation dispatch completed for order {}: {} plugins executed, success={}",
                context.getOrderKey(), dispatchResult.getPluginResults().size(),
                dispatchResult.isSuccess());

        return dispatchResult;
    }

    /**
     * Execute a specific plugin by ID.
     */
    public PostAllocationResult executePlugin(String pluginId, PostAllocationContext context) {
        return registry.getPlugin(pluginId)
                .map(plugin -> plugin.execute(context))
                .orElse(PostAllocationResult.failure(pluginId, "PLUGIN_NOT_FOUND",
                        "Plugin not registered: " + pluginId));
    }

    /**
     * Result of dispatching all plugins.
     */
    @lombok.Data
    public static class DispatchResult {
        private String orderKey;
        private boolean success = true;
        private String failedPlugin;
        private String errorMessage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private final List<PostAllocationResult> pluginResults = new ArrayList<>();

        public void addPluginResult(PostAllocationResult result) {
            pluginResults.add(result);
        }

        public int getSuccessCount() {
            return (int) pluginResults.stream().filter(PostAllocationResult::isSuccess).count();
        }

        public int getFailureCount() {
            return (int) pluginResults.stream().filter(r -> !r.isSuccess()).count();
        }

        public int getSkippedCount() {
            return (int) pluginResults.stream().filter(PostAllocationResult::isSkipped).count();
        }

        public long getTotalDurationMs() {
            return pluginResults.stream()
                    .mapToLong(PostAllocationResult::getExecutionDurationMs)
                    .sum();
        }

        public List<String> getWarnings() {
            List<String> warnings = new ArrayList<>();
            pluginResults.forEach(r -> warnings.addAll(r.getWarnings()));
            return warnings;
        }
    }
}
