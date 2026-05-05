package com.wms.po.variation.plugin;

import com.wms.po.plugin.api.PostFinalizePlugin;
import com.wms.po.plugin.api.PreFinalizePlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unified Plugin Registry.
 *
 * Provides access to all plugin registries for the variation framework.
 * Acts as a facade over the specific plugin registries.
 *
 * Note: This registry manages PreFinalizePlugin/PostFinalizePlugin from po-plugin/api.
 * For FinalizePlugin (from po-plugin/finalize), use FinalizePluginRegistry directly.
 */
@Component
@Slf4j
public class PluginRegistry {

    private final List<PreFinalizePlugin> prePlugins;
    private final List<PostFinalizePlugin> postPlugins;
    private final List<PrePopulatePlugin> prePopulatePlugins;
    private final List<PostPopulatePlugin> postPopulatePlugins;

    @Autowired
    public PluginRegistry(
            @Autowired(required = false) List<PreFinalizePlugin> prePlugins,
            @Autowired(required = false) List<PostFinalizePlugin> postPlugins,
            @Autowired(required = false) List<PrePopulatePlugin> prePopulatePlugins,
            @Autowired(required = false) List<PostPopulatePlugin> postPopulatePlugins) {
        this.prePlugins = prePlugins != null ? prePlugins : Collections.emptyList();
        this.postPlugins = postPlugins != null ? postPlugins : Collections.emptyList();
        this.prePopulatePlugins = prePopulatePlugins != null ? prePopulatePlugins : Collections.emptyList();
        this.postPopulatePlugins = postPopulatePlugins != null ? postPopulatePlugins : Collections.emptyList();
        log.info("PluginRegistry initialized with {} pre-finalize, {} post-finalize, {} pre-populate, {} post-populate plugins",
            this.prePlugins.size(), this.postPlugins.size(),
            this.prePopulatePlugins.size(), this.postPopulatePlugins.size());
    }

    /**
     * Get pre-finalize plugins for a storer and region.
     */
    public List<PreFinalizePlugin> getPreFinalizePlugins(String storerKey, String region) {
        List<PreFinalizePlugin> result = new ArrayList<>();
        for (PreFinalizePlugin plugin : prePlugins) {
            String clientCode = plugin.getClientCode();
            String regionCode = plugin.getRegionCode();

            // Match if client is STANDARD or matches storerKey
            boolean clientMatch = "STANDARD".equals(clientCode) ||
                (storerKey != null && storerKey.toUpperCase().contains(clientCode));

            // Match if region is ALL or matches
            boolean regionMatch = "ALL".equals(regionCode) ||
                (region != null && region.equalsIgnoreCase(regionCode));

            if (clientMatch && regionMatch) {
                result.add(plugin);
            }
        }
        // Sort by order
        result.sort((a, b) -> Integer.compare(a.getOrder(), b.getOrder()));
        return result;
    }

    /**
     * Get post-finalize plugins for a storer and region.
     */
    public List<PostFinalizePlugin> getPostFinalizePlugins(String storerKey, String region) {
        List<PostFinalizePlugin> result = new ArrayList<>();
        for (PostFinalizePlugin plugin : postPlugins) {
            String clientCode = plugin.getClientCode();
            String regionCode = plugin.getRegionCode();

            // Match if client is STANDARD or matches storerKey
            boolean clientMatch = "STANDARD".equals(clientCode) ||
                (storerKey != null && storerKey.toUpperCase().contains(clientCode));

            // Match if region is ALL or matches
            boolean regionMatch = "ALL".equals(regionCode) ||
                (region != null && region.equalsIgnoreCase(regionCode));

            if (clientMatch && regionMatch) {
                result.add(plugin);
            }
        }
        // Sort by order
        result.sort((a, b) -> Integer.compare(a.getOrder(), b.getOrder()));
        return result;
    }

    /**
     * Get pre-populate plugins for a client.
     */
    public List<PrePopulatePlugin> getPrePopulatePlugins(String clientKey) {
        // Return all pre-populate plugins (client filtering can be added later)
        return new ArrayList<>(prePopulatePlugins);
    }

    /**
     * Get post-populate plugins for a client.
     */
    public List<PostPopulatePlugin> getPostPopulatePlugins(String clientKey) {
        // Return all post-populate plugins (client filtering can be added later)
        return new ArrayList<>(postPopulatePlugins);
    }
}
