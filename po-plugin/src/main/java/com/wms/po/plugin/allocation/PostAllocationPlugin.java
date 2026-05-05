package com.wms.po.plugin.allocation;

import java.util.Map;

/**
 * Post-Allocation Plugin Interface.
 *
 * Replaces: SP-140 - ispPOA01-ispPOA26 (26 SPs)
 *
 * Post-allocation plugins execute after order allocation completes
 * to perform additional processing, validation, or integration tasks.
 */
public interface PostAllocationPlugin {

    /**
     * Get the plugin identifier.
     */
    String getPluginId();

    /**
     * Get plugin description.
     */
    String getDescription();

    /**
     * Check if plugin applies to this allocation context.
     */
    boolean appliesTo(PostAllocationContext context);

    /**
     * Get execution priority (lower = earlier).
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Execute post-allocation processing.
     *
     * @param context The allocation context
     * @return Result of plugin execution
     */
    PostAllocationResult execute(PostAllocationContext context);

    /**
     * Check if plugin can be skipped on error.
     */
    default boolean isOptional() {
        return false;
    }

    /**
     * Get plugin configuration.
     */
    default Map<String, Object> getConfiguration() {
        return Map.of();
    }
}
