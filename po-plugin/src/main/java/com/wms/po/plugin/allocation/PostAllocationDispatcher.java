package com.wms.po.plugin.allocation;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
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
 *
 * Error codes:
 * - PLG_060 (69560) - Post-Allocation Hook Failed
 * - PLG_001 (69500) - Plugin Not Found
 * - PLG_002 (69501) - Plugin Execution Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostAllocationDispatcher {

    private final PostAllocationPluginRegistry registry;

    /**
     * Execute all applicable post-allocation plugins.
     *
     * Error codes:
     * - PLG_060 (69560) - Post-Allocation Hook Failed
     * - PLG_002 (69501) - Plugin Execution Failed
     *
     * @param context The allocation context
     * @return Aggregated result from all plugins
     * @throws BusinessException if context is invalid
     */
    public DispatchResult dispatch(PostAllocationContext context) {
        if (context == null) {
            log.error("Post-allocation context is null (legacy error 69560)");
            throw new BusinessException(ErrorCode.POST_ALLOCATION_HOOK_FAILED,
                "Post-allocation context is required")
                .withDetail("context", "null");
        }

        if (context.getOrderKey() == null || context.getOrderKey().isBlank()) {
            log.error("Order key is null/blank for post-allocation (legacy error 69560)");
            throw new BusinessException(ErrorCode.POST_ALLOCATION_HOOK_FAILED,
                "Order key is required for post-allocation")
                .withDetail("orderKey", "null or blank");
        }

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
                    log.error("Required plugin {} failed for order {}: {} (legacy error 69501)",
                            plugin.getPluginId(), context.getOrderKey(), result.getErrorMessage());

                    if (!result.isContinueOnFailure()) {
                        dispatchResult.setSuccess(false);
                        dispatchResult.setFailedPlugin(plugin.getPluginId());
                        dispatchResult.setErrorMessage(result.getErrorMessage());
                        break;
                    }
                }

            } catch (Exception e) {
                log.error("Plugin {} threw exception for order {}: {} (legacy error 69501)",
                        plugin.getPluginId(), context.getOrderKey(), e.getMessage(), e);

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
     *
     * Error codes:
     * - PLG_001 (69500) - Plugin Not Found
     * - PLG_002 (69501) - Plugin Execution Failed
     *
     * @param pluginId Plugin ID to execute
     * @param context Post-allocation context
     * @return Plugin execution result
     * @throws BusinessException if plugin not found
     */
    public PostAllocationResult executePlugin(String pluginId, PostAllocationContext context) {
        if (pluginId == null || pluginId.isBlank()) {
            log.error("Plugin ID is null/blank for execution (legacy error 69500)");
            throw new BusinessException(ErrorCode.PLUGIN_NOT_FOUND,
                "Plugin ID is required for execution")
                .withDetail("pluginId", "null or blank");
        }

        return registry.getPlugin(pluginId)
                .map(plugin -> {
                    try {
                        return plugin.execute(context);
                    } catch (Exception e) {
                        log.error("Plugin {} execution failed: {} (legacy error 69501)",
                            pluginId, e.getMessage(), e);
                        return PostAllocationResult.failure(pluginId, "PLUGIN_EXCEPTION", e.getMessage());
                    }
                })
                .orElseThrow(() -> {
                    log.error("Plugin not found: {} (legacy error 69500)", pluginId);
                    return new BusinessException(ErrorCode.PLUGIN_NOT_FOUND,
                        "Plugin not registered: " + pluginId)
                        .withDetail("pluginId", pluginId);
                });
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
