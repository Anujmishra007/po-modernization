package com.wms.po.plugin.populate;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Context object passed to populate plugins.
 *
 * Contains client, region, and shared data for plugin execution.
 */
@Data
@Builder
public class PopulateContext {

    /**
     * Client/storer identifier.
     */
    private String client;

    /**
     * Region code (e.g., "ASIA-KR", "ASIA-IN", "ASIA-SG").
     */
    private String region;

    /**
     * Facility code.
     */
    private String facility;

    /**
     * Country code (e.g., "KR", "IN", "SG").
     */
    private String countryCode;

    /**
     * User ID performing the operation.
     */
    private String userId;

    /**
     * Shared data between plugins.
     */
    @Builder.Default
    private Map<String, Object> sharedData = new HashMap<>();

    /**
     * Configuration parameters.
     */
    @Builder.Default
    private Map<String, String> config = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════════
    // Region Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    public boolean isKorea() {
        return "ASIA-KR".equals(region) || "KR".equals(countryCode);
    }

    public boolean isIndia() {
        return "ASIA-IN".equals(region) || "IN".equals(countryCode);
    }

    public boolean isSingapore() {
        return "ASIA-SG".equals(region) || "SG".equals(countryCode);
    }

    public boolean isChina() {
        return "ASIA-CN".equals(region) || "CN".equals(countryCode);
    }

    public boolean isAsia() {
        return region != null && region.startsWith("ASIA");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Shared Data Helpers
    // ═══════════════════════════════════════════════════════════════════════

    public void setSharedData(String key, Object value) {
        sharedData.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getSharedData(String key) {
        return (T) sharedData.get(key);
    }

    public <T> T getSharedData(String key, T defaultValue) {
        Object value = sharedData.get(key);
        if (value == null) {
            return defaultValue;
        }
        return (T) value;
    }

    public String getConfig(String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue);
    }
}
