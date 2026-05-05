package com.wms.po.plugin.populate;

/**
 * Base interface for populate plugins (Pre/Post populate).
 *
 * Replaces client-specific stored procedures:
 * - Pre-populate: isp_PrePopulatePO* series (40+ variants)
 * - Post-populate: isp_PostPopulatePO* series (25+ variants)
 *
 * Populate plugins execute during the receipt creation phase,
 * before finalization.
 */
public interface PopulatePlugin {

    /**
     * Get the client code this plugin applies to.
     * Return "STANDARD" for plugins that apply to all clients.
     *
     * @return Client code (e.g., "NIKE", "HM", "ADIDAS")
     */
    String getClientCode();

    /**
     * Get the execution order (lower = earlier).
     * Default is 100.
     *
     * @return Order (1-999)
     */
    default int getOrder() {
        return 100;
    }

    /**
     * Check if this plugin applies to the given region.
     *
     * @param region Region code (e.g., "ASIA-KR", "ASIA-IN")
     * @return true if plugin applies to this region
     */
    default boolean appliesTo(String region) {
        return true; // Default: all regions
    }

    /**
     * Plugin phase enumeration.
     */
    enum Phase {
        PRE_POPULATE,   // Before receipt creation
        POST_POPULATE   // After receipt creation
    }
}
