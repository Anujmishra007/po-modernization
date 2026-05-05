package com.wms.po.plugin.registry;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.plugin.client.ClientPlugin;
import com.wms.po.plugin.hooks.LifecycleHook;
import com.wms.po.plugin.region.RegionPlugin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central plugin manager for all plugin types.
 *
 * Error codes:
 * - PLG_002 (69501) - Plugin Execution Failed
 * - PLG_040 (69540) - Pre-Populate Hook Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PluginManager {

    private final List<ClientPlugin> clientPlugins;
    private final List<RegionPlugin> regionPlugins;
    private final List<LifecycleHook> lifecycleHooks;

    /**
     * Run all applicable pre-populate plugins.
     *
     * Error codes:
     * - PLG_040 (69540) - Pre-Populate Hook Failed
     * - PLG_002 (69501) - Plugin Execution Failed
     *
     * @param request Populate request
     * @param context Variation context
     * @return Plugin execution result
     * @throws BusinessException if a critical plugin failure occurs
     */
    public PluginResult runPrePopulate(PopulateRequest request, VariationContext context) {
        log.info("Running pre-populate plugins for context: client={}, region={}",
            context.getClient(), context.getRegion());

        if (request == null) {
            log.error("Populate request is null for pre-populate plugins (legacy error 69540)");
            throw new BusinessException(ErrorCode.PRE_POPULATE_HOOK_FAILED,
                "Populate request is required for pre-populate plugins")
                .withDetail("request", "null");
        }

        if (context == null) {
            log.error("Variation context is null for pre-populate plugins (legacy error 69540)");
            throw new BusinessException(ErrorCode.PRE_POPULATE_HOOK_FAILED,
                "Variation context is required for pre-populate plugins")
                .withDetail("context", "null");
        }

        try {
            // Run region plugins first
            for (RegionPlugin plugin : getApplicableRegionPlugins(context)) {
                log.debug("Running region plugin: {}", plugin.getClass().getSimpleName());
                try {
                    PluginResult result = plugin.prePopulate(request, context);
                    if (!result.isShouldContinue()) {
                        log.warn("Region plugin {} stopped workflow: {}",
                            plugin.getClass().getSimpleName(), result.getReason());
                        return result;
                    }
                } catch (Exception e) {
                    log.error("Region plugin {} failed: {} (legacy error 69501)",
                        plugin.getClass().getSimpleName(), e.getMessage(), e);
                    throw new BusinessException(ErrorCode.PLUGIN_EXECUTION_FAILED,
                        "Region plugin failed: " + e.getMessage(), e)
                        .withDetail("plugin", plugin.getClass().getSimpleName())
                        .withDetail("region", context.getRegion());
                }
            }

            // Run client plugins
            for (ClientPlugin plugin : getApplicableClientPlugins(context)) {
                log.debug("Running client plugin: {}", plugin.getClass().getSimpleName());
                try {
                    PluginResult result = plugin.prePopulate(request, context);
                    if (!result.isShouldContinue()) {
                        log.warn("Client plugin {} stopped workflow: {}",
                            plugin.getClass().getSimpleName(), result.getReason());
                        return result;
                    }
                } catch (Exception e) {
                    log.error("Client plugin {} failed: {} (legacy error 69501)",
                        plugin.getClass().getSimpleName(), e.getMessage(), e);
                    throw new BusinessException(ErrorCode.PLUGIN_EXECUTION_FAILED,
                        "Client plugin failed: " + e.getMessage(), e)
                        .withDetail("plugin", plugin.getClass().getSimpleName())
                        .withDetail("client", context.getClient());
                }
            }

            // Run lifecycle hooks
            for (LifecycleHook hook : getOrderedHooks()) {
                if (hook.appliesTo(context)) {
                    log.debug("Running lifecycle hook: {}", hook.getClass().getSimpleName());
                    try {
                        PluginResult result = hook.onPrePopulate(request, context);
                        if (!result.isShouldContinue()) {
                            return result;
                        }
                    } catch (Exception e) {
                        log.error("Lifecycle hook {} failed: {} (legacy error 69540)",
                            hook.getClass().getSimpleName(), e.getMessage(), e);
                        throw new BusinessException(ErrorCode.PRE_POPULATE_HOOK_FAILED,
                            "Lifecycle hook failed: " + e.getMessage(), e)
                            .withDetail("hook", hook.getClass().getSimpleName());
                    }
                }
            }

            return PluginResult.success();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Pre-populate plugin execution failed: {} (legacy error 69540)",
                e.getMessage(), e);
            throw new BusinessException(ErrorCode.PRE_POPULATE_HOOK_FAILED,
                "Pre-populate plugin execution failed: " + e.getMessage(), e)
                .withDetail("client", context.getClient())
                .withDetail("region", context.getRegion());
        }
    }

    /**
     * Run all applicable post-populate plugins.
     *
     * Error codes:
     * - PLG_002 (69501) - Plugin Execution Failed (logged, continues execution)
     *
     * @param receiptKey Receipt key
     * @param request Populate request
     * @param context Variation context
     * @return Plugin execution result
     */
    public PluginResult runPostPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        log.info("Running post-populate plugins for receiptKey={}", receiptKey);

        if (receiptKey == null || receiptKey.isBlank()) {
            log.warn("Receipt key is null/blank for post-populate plugins, skipping plugins");
            return PluginResult.success();
        }

        List<String> failedPlugins = new ArrayList<>();

        // Run region plugins
        for (RegionPlugin plugin : getApplicableRegionPlugins(context)) {
            try {
                plugin.postPopulate(receiptKey, request, context);
                log.debug("Region plugin {} completed for receipt {}",
                    plugin.getClass().getSimpleName(), receiptKey);
            } catch (Exception e) {
                log.error("Region plugin {} failed in post-populate for receipt {}: {} (legacy error 69501)",
                    plugin.getClass().getSimpleName(), receiptKey, e.getMessage(), e);
                failedPlugins.add(plugin.getClass().getSimpleName());
            }
        }

        // Run client plugins
        for (ClientPlugin plugin : getApplicableClientPlugins(context)) {
            try {
                plugin.postPopulate(receiptKey, request, context);
                log.debug("Client plugin {} completed for receipt {}",
                    plugin.getClass().getSimpleName(), receiptKey);
            } catch (Exception e) {
                log.error("Client plugin {} failed in post-populate for receipt {}: {} (legacy error 69501)",
                    plugin.getClass().getSimpleName(), receiptKey, e.getMessage(), e);
                failedPlugins.add(plugin.getClass().getSimpleName());
            }
        }

        // Run lifecycle hooks
        for (LifecycleHook hook : getOrderedHooks()) {
            if (hook.appliesTo(context)) {
                try {
                    hook.onPostPopulate(receiptKey, request, context);
                    log.debug("Lifecycle hook {} completed for receipt {}",
                        hook.getClass().getSimpleName(), receiptKey);
                } catch (Exception e) {
                    log.error("Lifecycle hook {} failed for receipt {}: {} (legacy error 69501)",
                        hook.getClass().getSimpleName(), receiptKey, e.getMessage(), e);
                    failedPlugins.add(hook.getClass().getSimpleName());
                }
            }
        }

        if (!failedPlugins.isEmpty()) {
            log.warn("Post-populate completed with {} plugin failures: {}",
                failedPlugins.size(), failedPlugins);
        }

        return PluginResult.success();
    }

    /**
     * Run on-error hooks.
     *
     * Error codes:
     * - PLG_002 (69501) - Plugin Execution Failed (logged only, never throws)
     *
     * @param error Error message that triggered the hook
     * @param request Original populate request
     * @param context Variation context
     */
    public void runOnError(String error, PopulateRequest request, VariationContext context) {
        log.info("Running on-error hooks for error: {}", error);

        if (context == null) {
            log.warn("Context is null for on-error hooks, skipping hook execution");
            return;
        }

        int hooksExecuted = 0;
        int hooksFailed = 0;

        for (LifecycleHook hook : getOrderedHooks()) {
            if (hook.appliesTo(context)) {
                try {
                    hook.onError(error, request, context);
                    hooksExecuted++;
                    log.debug("On-error hook {} completed", hook.getClass().getSimpleName());
                } catch (Exception e) {
                    hooksFailed++;
                    log.error("Lifecycle hook {} failed on error handling: {} (legacy error 69501)",
                        hook.getClass().getSimpleName(), e.getMessage(), e);
                    // Never throw from error handlers to avoid cascading failures
                }
            }
        }

        log.info("On-error hooks completed: {} executed, {} failed", hooksExecuted, hooksFailed);
    }

    private List<RegionPlugin> getApplicableRegionPlugins(VariationContext context) {
        return regionPlugins.stream()
            .filter(p -> p.appliesTo(context.getRegion()))
            .sorted(Comparator.comparingInt(RegionPlugin::getOrder))
            .collect(Collectors.toList());
    }

    private List<ClientPlugin> getApplicableClientPlugins(VariationContext context) {
        return clientPlugins.stream()
            .filter(p -> p.appliesTo(context.getClient()))
            .sorted(Comparator.comparingInt(ClientPlugin::getOrder))
            .collect(Collectors.toList());
    }

    private List<LifecycleHook> getOrderedHooks() {
        return lifecycleHooks.stream()
            .sorted(Comparator.comparingInt(LifecycleHook::getOrder))
            .collect(Collectors.toList());
    }

    /**
     * Get all registered plugins info
     */
    public Map<String, List<String>> getRegisteredPlugins() {
        Map<String, List<String>> info = new HashMap<>();

        info.put("clientPlugins", clientPlugins.stream()
            .map(p -> p.getClass().getSimpleName() + " [" + p.getClientCode() + "]")
            .collect(Collectors.toList()));

        info.put("regionPlugins", regionPlugins.stream()
            .map(p -> p.getClass().getSimpleName() + " [" + p.getRegionCode() + "]")
            .collect(Collectors.toList()));

        info.put("lifecycleHooks", lifecycleHooks.stream()
            .map(h -> h.getClass().getSimpleName())
            .collect(Collectors.toList()));

        return info;
    }
}
