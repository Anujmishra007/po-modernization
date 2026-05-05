package com.wms.po.plugin.registry;

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
 * Central plugin manager for all plugin types
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PluginManager {

    private final List<ClientPlugin> clientPlugins;
    private final List<RegionPlugin> regionPlugins;
    private final List<LifecycleHook> lifecycleHooks;

    /**
     * Run all applicable pre-populate plugins
     */
    public PluginResult runPrePopulate(PopulateRequest request, VariationContext context) {
        log.info("Running pre-populate plugins for context: client={}, region={}",
            context.getClient(), context.getRegion());

        // Run region plugins first
        for (RegionPlugin plugin : getApplicableRegionPlugins(context)) {
            log.debug("Running region plugin: {}", plugin.getClass().getSimpleName());
            PluginResult result = plugin.prePopulate(request, context);
            if (!result.isShouldContinue()) {
                log.warn("Region plugin {} stopped workflow: {}",
                    plugin.getClass().getSimpleName(), result.getReason());
                return result;
            }
        }

        // Run client plugins
        for (ClientPlugin plugin : getApplicableClientPlugins(context)) {
            log.debug("Running client plugin: {}", plugin.getClass().getSimpleName());
            PluginResult result = plugin.prePopulate(request, context);
            if (!result.isShouldContinue()) {
                log.warn("Client plugin {} stopped workflow: {}",
                    plugin.getClass().getSimpleName(), result.getReason());
                return result;
            }
        }

        // Run lifecycle hooks
        for (LifecycleHook hook : getOrderedHooks()) {
            if (hook.appliesTo(context)) {
                log.debug("Running lifecycle hook: {}", hook.getClass().getSimpleName());
                PluginResult result = hook.onPrePopulate(request, context);
                if (!result.isShouldContinue()) {
                    return result;
                }
            }
        }

        return PluginResult.success();
    }

    /**
     * Run all applicable post-populate plugins
     */
    public PluginResult runPostPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        log.info("Running post-populate plugins for receiptKey={}", receiptKey);

        // Run region plugins
        for (RegionPlugin plugin : getApplicableRegionPlugins(context)) {
            try {
                plugin.postPopulate(receiptKey, request, context);
            } catch (Exception e) {
                log.warn("Region plugin {} failed in post-populate: {}",
                    plugin.getClass().getSimpleName(), e.getMessage());
            }
        }

        // Run client plugins
        for (ClientPlugin plugin : getApplicableClientPlugins(context)) {
            try {
                plugin.postPopulate(receiptKey, request, context);
            } catch (Exception e) {
                log.warn("Client plugin {} failed in post-populate: {}",
                    plugin.getClass().getSimpleName(), e.getMessage());
            }
        }

        // Run lifecycle hooks
        for (LifecycleHook hook : getOrderedHooks()) {
            if (hook.appliesTo(context)) {
                try {
                    hook.onPostPopulate(receiptKey, request, context);
                } catch (Exception e) {
                    log.warn("Lifecycle hook {} failed: {}", hook.getClass().getSimpleName(), e.getMessage());
                }
            }
        }

        return PluginResult.success();
    }

    /**
     * Run on-error hooks
     */
    public void runOnError(String error, PopulateRequest request, VariationContext context) {
        log.info("Running on-error hooks");

        for (LifecycleHook hook : getOrderedHooks()) {
            if (hook.appliesTo(context)) {
                try {
                    hook.onError(error, request, context);
                } catch (Exception e) {
                    log.warn("Lifecycle hook {} failed on error: {}", hook.getClass().getSimpleName(), e.getMessage());
                }
            }
        }
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
