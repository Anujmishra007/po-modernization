package com.wms.po.variation.context;

import com.wms.po.domain.model.VariationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Resolves the variation context (V0/V2, region, client) from request parameters
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VariationResolver {

    @Value("${po.dualwrite.enabled:false}")
    private boolean dualWriteEnabled;

    @Value("${po.default.version:V2}")
    private String defaultVersion;

    /**
     * Resolve variation context from storer key and facility
     */
    public VariationContext resolve(String storerKey, String facility) {
        String version = determineVersion(storerKey);
        String region = determineRegion(facility);
        String client = determineClient(storerKey);
        boolean dualWrite = isDualWriteEnabled(storerKey);

        VariationContext context = VariationContext.builder()
            .version(version)
            .region(region)
            .client(client)
            .facility(facility)
            .storerKey(storerKey)
            .dualWriteEnabled(dualWrite)
            .build();

        log.info("Resolved variation context: version={}, region={}, client={}, dualWrite={}",
            version, region, client, dualWrite);

        return context;
    }

    /**
     * Determine version (V0 or V2) based on storer configuration
     */
    private String determineVersion(String storerKey) {
        // V0 clients have specific patterns (legacy)
        if (storerKey != null) {
            // V0 patterns (legacy unified system)
            if (storerKey.startsWith("V0_") || storerKey.endsWith("_V0")) {
                return "V0";
            }

            // Known V0 storers
            if (isV0Storer(storerKey)) {
                return "V0";
            }
        }

        return defaultVersion; // Default to V2
    }

    private boolean isV0Storer(String storerKey) {
        // In production, this would check a database or configuration
        // For now, return false to default to V2
        return false;
    }

    /**
     * Determine region from facility prefix
     */
    private String determineRegion(String facility) {
        if (facility == null || facility.length() < 2) {
            return "ASIA-DEFAULT";
        }

        String prefix = facility.substring(0, 2).toUpperCase();

        return switch (prefix) {
            case "SG" -> "ASIA-SG";    // Singapore
            case "KR" -> "ASIA-KR";    // Korea
            case "TH" -> "ASIA-TH";    // Thailand
            case "IN" -> "ASIA-IN";    // India
            case "MY" -> "ASIA-MY";    // Malaysia
            case "VN" -> "ASIA-VN";    // Vietnam
            case "PH" -> "ASIA-PH";    // Philippines
            case "ID" -> "ASIA-ID";    // Indonesia
            case "AU" -> "APAC-AU";    // Australia
            case "NZ" -> "APAC-NZ";    // New Zealand
            default -> "ASIA-DEFAULT";
        };
    }

    /**
     * Determine client from storer key pattern
     */
    private String determineClient(String storerKey) {
        if (storerKey == null) {
            return "STANDARD";
        }

        String upper = storerKey.toUpperCase();

        // Major client patterns
        if (upper.contains("NIKE") || upper.startsWith("NK")) {
            return "NIKE";
        }
        if (upper.contains("H&M") || upper.contains("HM") || upper.startsWith("HNM")) {
            return "HM";
        }
        if (upper.contains("ZARA") || upper.contains("INDITEX")) {
            return "ZARA";
        }
        if (upper.contains("UNIQLO") || upper.startsWith("UQ")) {
            return "UNIQLO";
        }
        if (upper.contains("ADIDAS") || upper.startsWith("AD")) {
            return "ADIDAS";
        }
        if (upper.contains("PUMA")) {
            return "PUMA";
        }
        if (upper.contains("DECATHLON") || upper.startsWith("DCT")) {
            return "DECATHLON";
        }
        if (upper.contains("GAP")) {
            return "GAP";
        }

        return "STANDARD";
    }

    /**
     * Check if dual-write is enabled for this storer
     */
    private boolean isDualWriteEnabled(String storerKey) {
        // In production, check feature flags service
        // For now, use global configuration
        return dualWriteEnabled;
    }
}
