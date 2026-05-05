package com.wms.po.plugin.finalize;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for finalization plugins.
 *
 * Manages plugin registration, lookup, and resolution by client/storer.
 * Replaces the dynamic SP lookup from StorerConfig tables.
 *
 * Error codes:
 * - PLG_001 (69500) - Plugin Not Found
 * - PLG_004 (69503) - Plugin Registration Failed
 */
@Component
@Slf4j
public class FinalizePluginRegistry {

    // Plugin storage by type
    private final Map<FinalizePlugin.PluginType, List<FinalizePlugin>> pluginsByType =
        new ConcurrentHashMap<>();

    // Plugin lookup by ID
    private final Map<String, FinalizePlugin> pluginsById = new ConcurrentHashMap<>();

    // Client-specific plugin mapping
    private final Map<String, Map<FinalizePlugin.PluginType, List<String>>> clientPluginMap =
        new ConcurrentHashMap<>();

    private final List<FinalizePlugin> plugins;

    public FinalizePluginRegistry(List<FinalizePlugin> plugins) {
        this.plugins = plugins != null ? plugins : Collections.emptyList();
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing finalize plugin registry with {} plugins", plugins.size());

        for (FinalizePlugin plugin : plugins) {
            registerPlugin(plugin);
        }

        log.info("Finalize plugin registry initialized: {} pre-finalize, {} post-finalize",
            getPluginCount(FinalizePlugin.PluginType.PRE_FINALIZE),
            getPluginCount(FinalizePlugin.PluginType.POST_FINALIZE));
    }

    /**
     * Register a plugin.
     *
     * Error codes:
     * - PLG_004 (69503) - Plugin Registration Failed
     *
     * @param plugin The plugin to register
     * @throws BusinessException if plugin is invalid or registration fails
     */
    public void registerPlugin(FinalizePlugin plugin) {
        if (plugin == null) {
            log.error("Cannot register null plugin (legacy error 69503)");
            throw new BusinessException(ErrorCode.PLUGIN_REGISTRATION_FAILED,
                "Cannot register null plugin")
                .withDetail("plugin", "null");
        }

        String pluginId = plugin.getPluginId();
        if (pluginId == null || pluginId.isBlank()) {
            log.error("Cannot register plugin with null/blank ID (legacy error 69503)");
            throw new BusinessException(ErrorCode.PLUGIN_REGISTRATION_FAILED,
                "Plugin must have a valid ID")
                .withDetail("pluginId", "null or blank")
                .withDetail("pluginClass", plugin.getClass().getSimpleName());
        }

        try {
            // Register by ID
            pluginsById.put(pluginId, plugin);

            // Register by type
            pluginsByType
                .computeIfAbsent(plugin.getType(), k -> new ArrayList<>())
                .add(plugin);

            // Register client mapping
            String clientKey = plugin.getClientKey();
            if (clientKey != null && !clientKey.equals("*")) {
                clientPluginMap
                    .computeIfAbsent(clientKey, k -> new HashMap<>())
                    .computeIfAbsent(plugin.getType(), k -> new ArrayList<>())
                    .add(pluginId);
            }

            log.debug("Registered finalize plugin: id={}, type={}, client={}",
                pluginId, plugin.getType(), clientKey);

        } catch (Exception e) {
            log.error("Failed to register plugin {}: {} (legacy error 69503)",
                pluginId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.PLUGIN_REGISTRATION_FAILED,
                "Failed to register plugin: " + e.getMessage(), e)
                .withDetail("pluginId", pluginId)
                .withDetail("pluginClass", plugin.getClass().getSimpleName());
        }
    }

    /**
     * Get a plugin by ID.
     *
     * @param pluginId Plugin ID
     * @return Plugin or null
     */
    public FinalizePlugin getPlugin(String pluginId) {
        return pluginsById.get(pluginId);
    }

    /**
     * Get all plugins of a type.
     *
     * @param type Plugin type
     * @return List of plugins sorted by priority
     */
    public List<FinalizePlugin> getPlugins(FinalizePlugin.PluginType type) {
        List<FinalizePlugin> list = pluginsByType.getOrDefault(type, Collections.emptyList());
        return list.stream()
            .sorted(Comparator.comparingInt(FinalizePlugin::getPriority))
            .collect(Collectors.toList());
    }

    /**
     * Get plugins for a specific client and type.
     *
     * @param clientKey Client/storer key
     * @param type Plugin type
     * @return List of plugins for this client
     */
    public List<FinalizePlugin> getPluginsForClient(String clientKey, FinalizePlugin.PluginType type) {
        List<FinalizePlugin> result = new ArrayList<>();

        // Get client-specific plugins
        Map<FinalizePlugin.PluginType, List<String>> clientPlugins = clientPluginMap.get(clientKey);
        if (clientPlugins != null) {
            List<String> pluginIds = clientPlugins.get(type);
            if (pluginIds != null) {
                for (String id : pluginIds) {
                    FinalizePlugin plugin = pluginsById.get(id);
                    if (plugin != null) {
                        result.add(plugin);
                    }
                }
            }
        }

        // Add default plugins (client = "*")
        List<FinalizePlugin> allPlugins = pluginsByType.getOrDefault(type, Collections.emptyList());
        for (FinalizePlugin plugin : allPlugins) {
            if ("*".equals(plugin.getClientKey()) && !result.contains(plugin)) {
                result.add(plugin);
            }
        }

        // Sort by priority
        result.sort(Comparator.comparingInt(FinalizePlugin::getPriority));

        return result;
    }

    /**
     * Get pre-finalize plugins for a client.
     */
    public List<FinalizePlugin> getPreFinalizePlugins(String clientKey) {
        return getPluginsForClient(clientKey, FinalizePlugin.PluginType.PRE_FINALIZE);
    }

    /**
     * Get post-finalize plugins for a client.
     */
    public List<FinalizePlugin> getPostFinalizePlugins(String clientKey) {
        return getPluginsForClient(clientKey, FinalizePlugin.PluginType.POST_FINALIZE);
    }

    /**
     * Check if a plugin exists.
     */
    public boolean hasPlugin(String pluginId) {
        return pluginsById.containsKey(pluginId);
    }

    /**
     * Get count of plugins by type.
     */
    public int getPluginCount(FinalizePlugin.PluginType type) {
        return pluginsByType.getOrDefault(type, Collections.emptyList()).size();
    }

    /**
     * Get all registered plugin IDs.
     */
    public Set<String> getPluginIds() {
        return Collections.unmodifiableSet(pluginsById.keySet());
    }

    /**
     * Get clients with registered plugins.
     */
    public Set<String> getRegisteredClients() {
        return Collections.unmodifiableSet(clientPluginMap.keySet());
    }

    /**
     * Unregister a plugin.
     */
    public void unregisterPlugin(String pluginId) {
        FinalizePlugin plugin = pluginsById.remove(pluginId);
        if (plugin != null) {
            List<FinalizePlugin> typeList = pluginsByType.get(plugin.getType());
            if (typeList != null) {
                typeList.remove(plugin);
            }
            log.debug("Unregistered finalize plugin: {}", pluginId);
        }
    }
}
