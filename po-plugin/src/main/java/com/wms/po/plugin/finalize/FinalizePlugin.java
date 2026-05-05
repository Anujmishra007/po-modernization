package com.wms.po.plugin.finalize;

/**
 * Base interface for receipt finalization plugins.
 *
 * Plugins replace client-specific stored procedures:
 * - Pre-finalize: ispPRREC* series (37+ variants)
 * - Post-finalize: ispASNFZ* series (30+ variants)
 *
 * Plugin lifecycle:
 * 1. Plugin registered with FinalizePluginRegistry
 * 2. FinalizePluginDispatcher resolves plugin by storer/client
 * 3. Plugin executed with FinalizeContext
 * 4. Plugin returns FinalizePluginResult
 */
public interface FinalizePlugin {

    /**
     * Get the unique plugin identifier.
     * Should match the stored procedure name it replaces (e.g., "ispPRREC01").
     *
     * @return Plugin ID
     */
    String getPluginId();

    /**
     * Get the client/storer this plugin applies to.
     * Return "*" for default/all clients.
     *
     * @return Client key or "*"
     */
    String getClientKey();

    /**
     * Get the plugin type (PRE or POST finalize).
     *
     * @return Plugin type
     */
    PluginType getType();

    /**
     * Get the execution priority (lower = earlier).
     * Default is 100.
     *
     * @return Priority (1-999)
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Check if this plugin should execute for the given context.
     *
     * @param context The finalization context
     * @return true if plugin should execute
     */
    default boolean shouldExecute(FinalizeContext context) {
        return true;
    }

    /**
     * Execute the plugin logic.
     *
     * @param context The finalization context
     * @return Plugin result
     */
    FinalizePluginResult execute(FinalizeContext context);

    /**
     * Plugin type enumeration.
     */
    enum PluginType {
        PRE_FINALIZE,   // Before receipt finalization (ispPRREC*)
        POST_FINALIZE   // After receipt finalization (ispASNFZ*)
    }
}
