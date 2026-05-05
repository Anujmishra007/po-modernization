package com.wms.po.plugin.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Client-specific configuration loaded from YAML
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientConfig {

    private String clientCode;
    private String clientName;
    private boolean enabled;

    // Rules configuration (from YAML)
    private RulesConfig rules;

    // Field mappings (from YAML)
    private FieldMappingsConfig fieldMappings;

    // Plugin configuration (from YAML)
    private PluginsConfig plugins;

    // Feature flags (from YAML)
    private Map<String, Boolean> flags;

    // Legacy fields (kept for backwards compatibility)
    private ValidationConfig validation;
    private Map<String, String> lottableFields;
    private BarcodeConfig barcode;
    private IntegrationConfig integration;
    private Map<String, Boolean> features;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RulesConfig {
        private boolean shelfLifeValidationEnabled;
        private int minShelfLifeDays;
        private boolean requireLottable03;
        private boolean requireCustomsCode;
        private boolean allowPartialShipment;
        private List<String> requiredFields;
        private List<String> blockedStatuses;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldMappingsConfig {
        private Map<String, String> headerMappings;
        private Map<String, String> detailMappings;
        private Map<String, String> lottableMappings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PluginsConfig {
        private List<String> enabledPlugins;
        private Map<String, Map<String, Object>> pluginSettings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationConfig {
        private boolean requireStyleCode;
        private boolean requireSizeCode;
        private boolean requireSeasonCode;
        private String styleCodePattern;
        private String sizeCodePattern;
        private List<String> validSeasons;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BarcodeConfig {
        private String format;  // UPC, EAN13, CODE128, etc.
        private String prefix;
        private int length;
        private boolean validateCheckDigit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntegrationConfig {
        private String apiEndpoint;
        private String apiKey;
        private boolean sendNotifications;
        private List<String> notificationEvents;
    }
}
