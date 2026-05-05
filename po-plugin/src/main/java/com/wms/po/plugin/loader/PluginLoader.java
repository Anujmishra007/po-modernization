package com.wms.po.plugin.loader;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.plugin.api.POPlugin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plugin loader - finds and sorts applicable plugins for a context.
 *
 * Error codes:
 * - PLG_001 (69500) - Plugin Not Found
 * - PLG_002 (69501) - Plugin Execution Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PluginLoader {

    private final List<POPlugin> allPlugins;

    /**
     * Get all plugins applicable to the given context, sorted by order.
     *
     * @param context Variation context
     * @return List of applicable plugins sorted by order
     * @throws BusinessException if context is null
     */
    public List<POPlugin> getApplicablePlugins(VariationContext context) {
        if (context == null) {
            log.error("Variation context is null for plugin loading (legacy error 69501)");
            throw new BusinessException(ErrorCode.PLUGIN_EXECUTION_FAILED,
                "Variation context is required for plugin loading")
                .withDetail("context", "null");
        }

        try {
            List<POPlugin> applicable = allPlugins.stream()
                .filter(plugin -> plugin.appliesTo(context))
                .sorted(Comparator.comparingInt(POPlugin::getOrder))
                .collect(Collectors.toList());

            log.debug("Found {} applicable plugins for region={}, client={}",
                applicable.size(), context.getRegion(), context.getClient());

            applicable.forEach(plugin ->
                log.debug("  - {} (order={})", plugin.getPluginId(), plugin.getOrder()));

            return applicable;

        } catch (Exception e) {
            log.error("Failed to load applicable plugins for region={}, client={}: {} (legacy error 69501)",
                context.getRegion(), context.getClient(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.PLUGIN_EXECUTION_FAILED,
                "Failed to load applicable plugins: " + e.getMessage(), e)
                .withDetail("region", context.getRegion())
                .withDetail("client", context.getClient());
        }
    }

    /**
     * Get all registered plugins.
     *
     * @return Immutable list of all plugins
     */
    public List<POPlugin> getAllPlugins() {
        return List.copyOf(allPlugins);
    }

    /**
     * Get plugin by ID.
     *
     * Error codes:
     * - PLG_001 (69500) - Plugin Not Found
     *
     * @param pluginId Plugin ID to find
     * @return Plugin or null if not found
     */
    public POPlugin getPlugin(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            log.debug("Plugin ID is null or blank");
            return null;
        }

        return allPlugins.stream()
            .filter(plugin -> plugin.getPluginId().equals(pluginId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get plugin by ID, throwing exception if not found.
     *
     * Error codes:
     * - PLG_001 (69500) - Plugin Not Found
     *
     * @param pluginId Plugin ID to find
     * @return Plugin
     * @throws BusinessException if plugin not found
     */
    public POPlugin getPluginOrThrow(String pluginId) {
        POPlugin plugin = getPlugin(pluginId);
        if (plugin == null) {
            log.error("Plugin not found: {} (legacy error 69500)", pluginId);
            throw new BusinessException(ErrorCode.PLUGIN_NOT_FOUND,
                "Plugin not found: " + pluginId)
                .withDetail("pluginId", pluginId);
        }
        return plugin;
    }

    /**
     * Check if a specific plugin applies.
     *
     * @param pluginId Plugin ID to check
     * @param context Variation context
     * @return true if plugin exists and applies to context
     */
    public boolean isPluginApplicable(String pluginId, VariationContext context) {
        if (context == null) {
            log.debug("Context is null, plugin {} not applicable", pluginId);
            return false;
        }
        POPlugin plugin = getPlugin(pluginId);
        return plugin != null && plugin.appliesTo(context);
    }
}
