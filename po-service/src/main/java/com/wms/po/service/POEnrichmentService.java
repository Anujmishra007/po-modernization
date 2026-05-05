package com.wms.po.service;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.rules.model.LottableMapping;
import com.wms.po.rules.service.LottableMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * PO enrichment service - adds computed values and defaults.
 *
 * Error codes:
 * - LOT_003 (69402) - Lottable Mapping Failed (mapping retrieval)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class POEnrichmentService {

    private final LottableMappingService lottableMappingService;

    /**
     * Enrich request with computed values and defaults.
     *
     * Error codes:
     * - LOT_003 (69402) - Lottable Mapping Failed (mapping retrieval)
     */
    public PopulateRequest enrichRequest(PopulateRequest request, VariationContext context) {
        log.debug("Enriching request for region: {}, client: {}",
            context.getRegion(), context.getClient());

        try {
            // Get lottable mapping
            LottableMapping mapping = lottableMappingService.getMapping(
                context.getRegion(), context.getClient());

            // Create enriched copy
            PopulateRequest enriched = request.toBuilder()
                .build();

            // Add metadata
            Map<String, Object> metadata = new HashMap<>(
                enriched.getMetadata() != null ? enriched.getMetadata() : new HashMap<>());

            metadata.put("enrichedAt", LocalDateTime.now().toString());
            metadata.put("region", context.getRegion());
            metadata.put("client", context.getClient());
            metadata.put("dbVersion", context.getVersion());
            metadata.put("lottableMapping", mapping);

            // Add region-specific enrichments
            enrichForRegion(metadata, context);

            // Add client-specific enrichments
            enrichForClient(metadata, context);

            log.debug("Enriched request with {} metadata fields", metadata.size());
            return enriched;

        } catch (Exception e) {
            log.error("Failed to enrich request for region {}, client {}: {} (legacy error 69402)",
                context.getRegion(), context.getClient(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.LOTTABLE_MAPPING_FAILED,
                "Failed to retrieve lottable mapping for enrichment: " + e.getMessage(), e)
                .withDetail("region", context.getRegion())
                .withDetail("client", context.getClient());
        }
    }

    private void enrichForRegion(Map<String, Object> metadata, VariationContext context) {
        switch (context.getRegion()) {
            case "ASIA-KR" -> {
                metadata.put("requiresCustomsClearance", true);
                metadata.put("customsDocumentType", "IMPORT_DECLARATION");
            }
            case "ASIA-IN" -> {
                metadata.put("requiresGSTValidation", true);
                metadata.put("gstDocumentType", "E_WAY_BILL");
            }
            case "ASIA-SG" -> {
                metadata.put("requiresTradePermit", true);
            }
            default -> {
                // Standard enrichment
            }
        }
    }

    private void enrichForClient(Map<String, Object> metadata, VariationContext context) {
        switch (context.getClient()) {
            case "NIKE" -> {
                metadata.put("requiresStyleValidation", true);
                metadata.put("barcodeFormat", "NIKE_UPC");
            }
            case "HM" -> {
                metadata.put("requiresArticleValidation", true);
                metadata.put("barcodeFormat", "EAN13");
            }
            case "ZARA" -> {
                metadata.put("requiresFashionCode", true);
                metadata.put("barcodeFormat", "INDITEX");
            }
            default -> {
                metadata.put("barcodeFormat", "STANDARD");
            }
        }
    }
}
