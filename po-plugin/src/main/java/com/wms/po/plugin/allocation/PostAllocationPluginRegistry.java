package com.wms.po.plugin.allocation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for Post-Allocation Plugins.
 *
 * Manages plugin registration, discovery, and execution ordering.
 */
@Component
@Slf4j
public class PostAllocationPluginRegistry {

    private final Map<String, PostAllocationPlugin> plugins = new ConcurrentHashMap<>();
    private final Map<String, List<String>> storerPluginMapping = new ConcurrentHashMap<>();
    private final Map<String, List<String>> countryPluginMapping = new ConcurrentHashMap<>();

    /**
     * Register a plugin.
     */
    public void register(PostAllocationPlugin plugin) {
        log.info("Registering post-allocation plugin: {}", plugin.getPluginId());
        plugins.put(plugin.getPluginId(), plugin);
    }

    /**
     * Unregister a plugin.
     */
    public void unregister(String pluginId) {
        log.info("Unregistering post-allocation plugin: {}", pluginId);
        plugins.remove(pluginId);
    }

    /**
     * Get plugin by ID.
     */
    public Optional<PostAllocationPlugin> getPlugin(String pluginId) {
        return Optional.ofNullable(plugins.get(pluginId));
    }

    /**
     * Get all registered plugins.
     */
    public Collection<PostAllocationPlugin> getAllPlugins() {
        return Collections.unmodifiableCollection(plugins.values());
    }

    /**
     * Get applicable plugins for context, sorted by priority.
     */
    public List<PostAllocationPlugin> getApplicablePlugins(PostAllocationContext context) {
        return plugins.values().stream()
                .filter(plugin -> plugin.appliesTo(context))
                .sorted(Comparator.comparingInt(PostAllocationPlugin::getPriority))
                .toList();
    }

    /**
     * Map a plugin to specific storers.
     */
    public void mapToStorer(String pluginId, String... storerKeys) {
        for (String storerKey : storerKeys) {
            storerPluginMapping.computeIfAbsent(storerKey, k -> new ArrayList<>()).add(pluginId);
        }
    }

    /**
     * Map a plugin to specific countries.
     */
    public void mapToCountry(String pluginId, String... countryCodes) {
        for (String countryCode : countryCodes) {
            countryPluginMapping.computeIfAbsent(countryCode, k -> new ArrayList<>()).add(pluginId);
        }
    }

    /**
     * Get plugins mapped to a storer.
     */
    public List<PostAllocationPlugin> getPluginsForStorer(String storerKey) {
        List<String> pluginIds = storerPluginMapping.getOrDefault(storerKey, Collections.emptyList());
        return pluginIds.stream()
                .map(plugins::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Get plugins mapped to a country.
     */
    public List<PostAllocationPlugin> getPluginsForCountry(String countryCode) {
        List<String> pluginIds = countryPluginMapping.getOrDefault(countryCode, Collections.emptyList());
        return pluginIds.stream()
                .map(plugins::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Check if a plugin is registered.
     */
    public boolean isRegistered(String pluginId) {
        return plugins.containsKey(pluginId);
    }

    /**
     * Get count of registered plugins.
     */
    public int getPluginCount() {
        return plugins.size();
    }

    /**
     * Clear all registrations.
     */
    public void clear() {
        log.warn("Clearing all post-allocation plugin registrations");
        plugins.clear();
        storerPluginMapping.clear();
        countryPluginMapping.clear();
    }
}
