package com.wms.po.rules.service;

import com.wms.po.rules.model.LottableMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing lottable field mappings by region/client
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
     * Get lottable mapping for region and client
     */
    public LottableMapping getMapping(String region, String client) {
        // Try client-specific first
        String clientKey = "CLIENT-" + client;
        if (mappings.containsKey(clientKey)) {
            return mappings.get(clientKey);
        }

        // Try region-specific
        if (mappings.containsKey(region)) {
            return mappings.get(region);
        }

        // Return default
        return mappings.get("DEFAULT");
    }

    /**
     * Register custom mapping
     */
    public void registerMapping(String key, LottableMapping mapping) {
        log.info("Registering lottable mapping: {}", key);
        mappings.put(key, mapping);
    }

    /**
     * Get all mappings
     */
    public Map<String, LottableMapping> getAllMappings() {
        return Map.copyOf(mappings);
    }
}
