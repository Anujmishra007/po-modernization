package com.wms.po.variation.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration loaded from YAML for variation handling
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariationConfig {

    // Validation rules
    private ValidationRules rules;

    // Field mapping configuration
    private FieldMappings fieldMappings;

    // Plugin configuration
    private PluginConfig plugins;

    // Feature flags
    private Map<String, Boolean> flags;

    // Allowed facilities
    private Set<String> allowedFacilities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationRules {
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
    public static class FieldMappings {
        private Map<String, String> headerMappings;
        private Map<String, String> detailMappings;
        private Map<String, String> lottableMappings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PluginConfig {
        private List<String> enabledPlugins;
        private Map<String, Map<String, Object>> pluginSettings;
    }

    public boolean isShelfLifeValidationEnabled() {
        return rules != null && rules.isShelfLifeValidationEnabled();
    }

    public boolean isRequireLottable03() {
        return rules != null && rules.isRequireLottable03();
    }

    public boolean isFeatureEnabled(String feature) {
        return flags != null && Boolean.TRUE.equals(flags.get(feature));
    }
}
