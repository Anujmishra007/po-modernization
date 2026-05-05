package com.wms.po.rules.service;

import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.rules.model.POValidationFact;
import com.wms.po.rules.model.SKUEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * High-level rules engine service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RulesEngineService {

    private final RulesExecutor rulesExecutor;
    private final LottableMappingService lottableMappingService;

    /**
     * Validate a populate request
     */
    public ValidationResult validatePopulateRequest(PopulateRequest request, VariationContext context) {
        log.info("Validating populate request for {} POs", request.getPoKeys().size());

        POValidationFact fact = buildFact(request, context);
        POValidationFact result = rulesExecutor.validatePO(fact);

        return ValidationResult.builder()
            .valid(result.isValid())
            .errors(result.getErrors())
            .warnings(result.getWarnings())
            .build();
    }

    /**
     * Validate SKUs
     */
    public List<SKUValidationResult> validateSKUs(List<String> skuCodes, VariationContext context) {
        log.info("Validating {} SKUs", skuCodes.size());

        List<SKUValidationResult> results = new ArrayList<>();

        for (String skuCode : skuCodes) {
            SKUEntity sku = SKUEntity.builder()
                .sku(skuCode)
                .storerKey(context.getClient())
                .build();

            SKUEntity validated = rulesExecutor.validateSKU(sku);

            results.add(SKUValidationResult.builder()
                .sku(skuCode)
                .valid(validated.isValid())
                .error(validated.getValidationError())
                .storageType(validated.getComputedStorageType())
                .build());
        }

        return results;
    }

    /**
     * Get lottable mapping for context
     */
    public LottableMappingResult getLottableMapping(VariationContext context) {
        var mapping = lottableMappingService.getMapping(context.getRegion(), context.getClient());

        return LottableMappingResult.builder()
            .region(context.getRegion())
            .client(context.getClient())
            .lottable01Field(mapping.getLottable01Field())
            .lottable02Field(mapping.getLottable02Field())
            .lottable03Field(mapping.getLottable03Field())
            .lottable04Field(mapping.getLottable04Field())
            .lottable05Field(mapping.getLottable05Field())
            .build();
    }

    private POValidationFact buildFact(PopulateRequest request, VariationContext context) {
        return POValidationFact.builder()
            .poKey(request.getPoKeys().isEmpty() ? null : request.getPoKeys().get(0))
            .storerKey(request.getStorerKey())
            .facility(request.getFacility())
            .region(context.getRegion())
            .client(context.getClient())
            .dbVersion(context.getVersion())
            .userId(request.getUserId())
            .lines(new ArrayList<>())
            .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;
    }

    @lombok.Data
    @lombok.Builder
    public static class SKUValidationResult {
        private String sku;
        private boolean valid;
        private String error;
        private String storageType;
    }

    @lombok.Data
    @lombok.Builder
    public static class LottableMappingResult {
        private String region;
        private String client;
        private String lottable01Field;
        private String lottable02Field;
        private String lottable03Field;
        private String lottable04Field;
        private String lottable05Field;
    }
}
