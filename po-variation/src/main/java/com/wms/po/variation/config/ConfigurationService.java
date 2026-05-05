package com.wms.po.variation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.wms.po.domain.exception.ConfigurationException;
import com.wms.po.domain.model.VariationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.util.*;

/**
 * Service for loading and caching variation configuration from YAML files.
 * Supports hierarchical configuration with base -> region -> client overrides.
 */
@Service
@Slf4j
public class ConfigurationService {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper yamlMapper;
    private final LoadingCache<ConfigKey, VariationConfig> configCache;

    @Value("${config.base-path:classpath:config/}")
    private String configBasePath;

    public ConfigurationService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.configCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .refreshAfterWrite(Duration.ofMinutes(1))
            .maximumSize(100)
            .build(this::loadConfig);
    }

    /**
     * Get configuration for the given variation context
     */
    public VariationConfig getConfig(VariationContext context) {
        ConfigKey key = new ConfigKey(context.getRegion(), context.getClient());
        return configCache.get(key);
    }

    /**
     * Load configuration with hierarchical merge:
     * base.yaml -> regions/{region}.yaml -> clients/{client}.yaml
     */
    private VariationConfig loadConfig(ConfigKey key) {
        try {
            log.info("Loading configuration for region={}, client={}", key.region(), key.client());

            // 1. Load base config
            VariationConfig base = loadYaml("base.yaml");
            if (base == null) {
                base = createDefaultConfig();
            }

            // 2. Merge region config
            String regionFile = "regions/" + key.region().toLowerCase().replace("-", "_") + ".yaml";
            VariationConfig regionConfig = loadYaml(regionFile);
            if (regionConfig != null) {
                base = merge(base, regionConfig);
                log.debug("Merged region config: {}", regionFile);
            }

            // 3. Merge client config
            String clientFile = "clients/" + key.client().toLowerCase() + ".yaml";
            VariationConfig clientConfig = loadYaml(clientFile);
            if (clientConfig != null) {
                base = merge(base, clientConfig);
                log.debug("Merged client config: {}", clientFile);
            }

            log.info("Configuration loaded for region={}, client={}", key.region(), key.client());
            return base;

        } catch (Exception e) {
            log.error("Failed to load configuration for region={}, client={}: {}",
                key.region(), key.client(), e.getMessage());
            return createDefaultConfig();
        }
    }

    private VariationConfig loadYaml(String filename) {
        try {
            String path = configBasePath + filename;
            Resource resource = resourceLoader.getResource(path);

            if (!resource.exists()) {
                log.debug("Config file not found: {}", path);
                return null;
            }

            try (InputStream is = resource.getInputStream()) {
                return yamlMapper.readValue(is, VariationConfig.class);
            }
        } catch (Exception e) {
            log.warn("Failed to load config file {}: {}", filename, e.getMessage());
            return null;
        }
    }

    private VariationConfig merge(VariationConfig base, VariationConfig overlay) {
        if (overlay == null) return base;
        if (base == null) return overlay;

        return VariationConfig.builder()
            .rules(mergeRules(base.getRules(), overlay.getRules()))
            .fieldMappings(mergeMappings(base.getFieldMappings(), overlay.getFieldMappings()))
            .plugins(mergePlugins(base.getPlugins(), overlay.getPlugins()))
            .flags(mergeFlags(base.getFlags(), overlay.getFlags()))
            .allowedFacilities(mergeSet(base.getAllowedFacilities(), overlay.getAllowedFacilities()))
            .build();
    }

    private VariationConfig.ValidationRules mergeRules(
            VariationConfig.ValidationRules base,
            VariationConfig.ValidationRules overlay) {
        if (overlay == null) return base;
        if (base == null) return overlay;

        return VariationConfig.ValidationRules.builder()
            .shelfLifeValidationEnabled(overlay.isShelfLifeValidationEnabled())
            .minShelfLifeDays(overlay.getMinShelfLifeDays() > 0 ? overlay.getMinShelfLifeDays() : base.getMinShelfLifeDays())
            .requireLottable03(overlay.isRequireLottable03())
            .requireCustomsCode(overlay.isRequireCustomsCode())
            .allowPartialShipment(overlay.isAllowPartialShipment())
            .requiredFields(mergeList(base.getRequiredFields(), overlay.getRequiredFields()))
            .blockedStatuses(mergeList(base.getBlockedStatuses(), overlay.getBlockedStatuses()))
            .build();
    }

    private VariationConfig.FieldMappings mergeMappings(
            VariationConfig.FieldMappings base,
            VariationConfig.FieldMappings overlay) {
        if (overlay == null) return base;
        if (base == null) return overlay;

        return VariationConfig.FieldMappings.builder()
            .headerMappings(mergeMap(base.getHeaderMappings(), overlay.getHeaderMappings()))
            .detailMappings(mergeMap(base.getDetailMappings(), overlay.getDetailMappings()))
            .lottableMappings(mergeMap(base.getLottableMappings(), overlay.getLottableMappings()))
            .build();
    }

    private VariationConfig.PluginConfig mergePlugins(
            VariationConfig.PluginConfig base,
            VariationConfig.PluginConfig overlay) {
        if (overlay == null) return base;
        if (base == null) return overlay;

        return VariationConfig.PluginConfig.builder()
            .enabledPlugins(mergeList(base.getEnabledPlugins(), overlay.getEnabledPlugins()))
            .pluginSettings(mergeMapOfMaps(base.getPluginSettings(), overlay.getPluginSettings()))
            .build();
    }

    private <T> List<T> mergeList(List<T> base, List<T> overlay) {
        if (overlay != null && !overlay.isEmpty()) return overlay;
        return base != null ? base : Collections.emptyList();
    }

    private <T> Set<T> mergeSet(Set<T> base, Set<T> overlay) {
        if (overlay != null && !overlay.isEmpty()) return overlay;
        return base != null ? base : Collections.emptySet();
    }

    private <K, V> Map<K, V> mergeMap(Map<K, V> base, Map<K, V> overlay) {
        Map<K, V> result = new HashMap<>();
        if (base != null) result.putAll(base);
        if (overlay != null) result.putAll(overlay);
        return result;
    }

    private Map<String, Boolean> mergeFlags(Map<String, Boolean> base, Map<String, Boolean> overlay) {
        Map<String, Boolean> result = new HashMap<>();
        if (base != null) result.putAll(base);
        if (overlay != null) result.putAll(overlay);
        return result;
    }

    @SuppressWarnings("unchecked")
    private <K, V> Map<K, Map<K, V>> mergeMapOfMaps(Map<K, Map<K, V>> base, Map<K, Map<K, V>> overlay) {
        Map<K, Map<K, V>> result = new HashMap<>();
        if (base != null) result.putAll(base);
        if (overlay != null) {
            for (Map.Entry<K, Map<K, V>> entry : overlay.entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), (v1, v2) -> {
                    Map<K, V> merged = new HashMap<>(v1);
                    merged.putAll(v2);
                    return merged;
                });
            }
        }
        return result;
    }

    private VariationConfig createDefaultConfig() {
        return VariationConfig.builder()
            .rules(VariationConfig.ValidationRules.builder()
                .shelfLifeValidationEnabled(false)
                .minShelfLifeDays(0)
                .requireLottable03(false)
                .requireCustomsCode(false)
                .allowPartialShipment(true)
                .requiredFields(List.of("storerKey", "facility", "sku"))
                .blockedStatuses(List.of("8", "9"))
                .build())
            .fieldMappings(VariationConfig.FieldMappings.builder()
                .headerMappings(new HashMap<>())
                .detailMappings(new HashMap<>())
                .lottableMappings(new HashMap<>())
                .build())
            .plugins(VariationConfig.PluginConfig.builder()
                .enabledPlugins(List.of())
                .pluginSettings(new HashMap<>())
                .build())
            .flags(new HashMap<>())
            .allowedFacilities(new HashSet<>())
            .build();
    }

    /**
     * Invalidate all cached configurations
     */
    public void invalidateCache() {
        log.info("Invalidating configuration cache");
        configCache.invalidateAll();
    }

    record ConfigKey(String region, String client) {}
}
