package com.wms.po.rules.service;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.rules.model.LottableMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing lottable field mappings by region/client.
 *
 * Error codes:
 * - LOT_003 (69402) - Lottable Mapping Failed
 * - LOT_001 (69400) - Lottable Rule Not Found
 */
@Service
@Slf4j
public class LottableMappingService {

    private final Map<String, LottableMapping> mappings = new HashMap<>();

    public LottableMappingService() {
        initializeDefaultMappings();
    }

    private void initializeDefaultMappings() {
        // Default mapping
        mappings.put("DEFAULT", LottableMapping.builder()
            .storerKey("*")
            .region("*")
            .client("*")
            .lottable01Field("LOT_NUMBER")
            .lottable02Field("BATCH_NUMBER")
            .lottable03Field("SERIAL_NUMBER")
            .lottable04Field("EXPIRY_DATE")
            .lottable05Field("MFG_DATE")
            .lottable04Required(false)
            .lottable04Pattern("yyyy-MM-dd")
            .build());

        // Korea region mapping
        mappings.put("ASIA-KR", LottableMapping.builder()
            .storerKey("*")
            .region("ASIA-KR")
            .client("*")
            .lottable01Field("LOT_NUMBER")
            .lottable02Field("KC_MARK")
            .lottable03Field("CUSTOMS_REF")
            .lottable04Field("EXPIRY_DATE")
            .lottable05Field("IMPORT_DATE")
            .lottable06Field("COUNTRY_OF_ORIGIN")
            .lottable01Required(true)
            .lottable03Required(true)
            .lottable04Required(true)
            .lottable04Pattern("yyyy-MM-dd")
            .build());

        // India region mapping
        mappings.put("ASIA-IN", LottableMapping.builder()
            .storerKey("*")
            .region("ASIA-IN")
            .client("*")
            .lottable01Field("LOT_NUMBER")
            .lottable02Field("GST_INVOICE")
            .lottable03Field("HSN_CODE")
            .lottable04Field("EXPIRY_DATE")
            .lottable05Field("MFG_DATE")
            .lottable06Field("MRP")
            .lottable01Required(true)
            .lottable02Required(true)
            .lottable04Pattern("yyyy-MM-dd")
            .build());

        // Nike client mapping
        mappings.put("CLIENT-NIKE", LottableMapping.builder()
            .storerKey("NIKE")
            .region("*")
            .client("NIKE")
            .lottable01Field("STYLE_COLOR")
            .lottable02Field("SIZE_CODE")
            .lottable03Field("SEASON")
            .lottable04Field("SHIP_DATE")
            .lottable05Field("FACTORY_CODE")
            .lottable01Required(true)
            .lottable02Required(true)
            .lottable01Pattern("[A-Z]{2}[0-9]{6}-[0-9]{3}")
            .build());

        // H&M client mapping
        mappings.put("CLIENT-HM", LottableMapping.builder()
            .storerKey("HM")
            .region("*")
            .client("HM")
            .lottable01Field("ARTICLE_NUMBER")
            .lottable02Field("COLOR_CODE")
            .lottable03Field("SIZE_CODE")
            .lottable04Field("QUALITY_CODE")
            .lottable01Required(true)
            .lottable01Pattern("[0-9]{7}")
            .build());
    }

    /**
     * Get lottable mapping for region and client.
     *
     * Error codes:
     * - LOT_001 (69400) - Lottable Rule Not Found (when no default exists)
     *
     * @param region Region code
     * @param client Client code
     * @return Lottable mapping for the region/client combination
     * @throws BusinessException if no mapping can be found
     */
    public LottableMapping getMapping(String region, String client) {
        log.debug("Getting lottable mapping for region={}, client={}", region, client);

        try {
            // Try client-specific first
            if (client != null && !client.isBlank()) {
                String clientKey = "CLIENT-" + client;
                if (mappings.containsKey(clientKey)) {
                    log.debug("Using client-specific mapping: {}", clientKey);
                    return mappings.get(clientKey);
                }
            }

            // Try region-specific
            if (region != null && !region.isBlank() && mappings.containsKey(region)) {
                log.debug("Using region-specific mapping: {}", region);
                return mappings.get(region);
            }

            // Return default
            LottableMapping defaultMapping = mappings.get("DEFAULT");
            if (defaultMapping == null) {
                log.error("No default lottable mapping configured (legacy error 69400)");
                throw new BusinessException(ErrorCode.LOTTABLE_RULE_NOT_FOUND,
                    "No lottable mapping found and no default configured")
                    .withDetail("region", region)
                    .withDetail("client", client);
            }

            log.debug("Using default mapping for region={}, client={}", region, client);
            return defaultMapping;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get lottable mapping for region={}, client={}: {} (legacy error 69402)",
                region, client, e.getMessage(), e);
            throw new BusinessException(ErrorCode.LOTTABLE_MAPPING_FAILED,
                "Failed to retrieve lottable mapping: " + e.getMessage(), e)
                .withDetail("region", region)
                .withDetail("client", client);
        }
    }

    /**
     * Register custom mapping.
     *
     * Error codes:
     * - LOT_003 (69402) - Lottable Mapping Failed
     *
     * @param key Mapping key
     * @param mapping Lottable mapping to register
     * @throws BusinessException if registration fails
     */
    public void registerMapping(String key, LottableMapping mapping) {
        if (key == null || key.isBlank()) {
            log.error("Cannot register lottable mapping with null/blank key (legacy error 69402)");
            throw new BusinessException(ErrorCode.LOTTABLE_MAPPING_FAILED,
                "Mapping key is required")
                .withDetail("key", "null or blank");
        }

        if (mapping == null) {
            log.error("Cannot register null lottable mapping (legacy error 69402)");
            throw new BusinessException(ErrorCode.LOTTABLE_MAPPING_FAILED,
                "Lottable mapping is required")
                .withDetail("key", key)
                .withDetail("mapping", "null");
        }

        log.info("Registering lottable mapping: {}", key);
        mappings.put(key, mapping);
    }

    /**
     * Get all mappings.
     *
     * @return Immutable copy of all registered mappings
     */
    public Map<String, LottableMapping> getAllMappings() {
        return Map.copyOf(mappings);
    }
}
