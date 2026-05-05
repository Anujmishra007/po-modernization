package com.wms.po.plugin.loader;

import com.wms.po.domain.model.VariationContext;
import com.wms.po.plugin.api.POPlugin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plugin loader - finds and sorts applicable plugins for a context
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PluginLoader {

    private final List<POPlugin> allPlugins;

    /**
     * Get all plugins applicable to the given context, sorted by order
     */
    public List<POPlugin> getApplicablePlugins(VariationContext context) {
        List<POPlugin> applicable = allPlugins.stream()
            .filter(plugin -> plugin.appliesTo(context))
            .sorted(Comparator.comparingInt(POPlugin::getOrder))
            .collect(Collectors.toList());

        log.debug("Found {} applicable plugins for region={}, client={}",
            applicable.size(), context.getRegion(), context.getClient());

        applicable.forEach(plugin ->
            log.debug("  - {} (order={})", plugin.getPluginId(), plugin.getOrder()));

        return applicable;
    }

    /**
     * Get all registered plugins
     */
    public List<POPlugin> getAllPlugins() {
        return List.copyOf(allPlugins);
    }

    /**
     * Get plugin by ID
     */
    public POPlugin getPlugin(String pluginId) {
        return allPlugins.stream()
            .filter(plugin -> plugin.getPluginId().equals(pluginId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Check if a specific plugin applies
     */
    public boolean isPluginApplicable(String pluginId, VariationContext context) {
        POPlugin plugin = getPlugin(pluginId);
        return plugin != null && plugin.appliesTo(context);
    }
}
