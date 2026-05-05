package com.wms.po.rules.service;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
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
 * High-level rules engine service.
 *
 * Error codes:
 * - VAL_020 (69120) - Extended Validation Failed
 * - VAL_012 (69112) - SKU Configuration Invalid
 * - LOT_003 (69402) - Lottable Mapping Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RulesEngineService {

    private final RulesExecutor rulesExecutor;
    private final LottableMappingService lottableMappingService;

    /**
     * Validate a populate request.
     *
     * Error codes:
     * - VAL_020 (69120) - Extended Validation Failed
     *
     * @param request Populate request to validate
     * @param context Variation context
     * @return Validation result
     * @throws BusinessException if validation execution fails
     */
    public ValidationResult validatePopulateRequest(PopulateRequest request, VariationContext context) {
        if (request == null) {
            log.error("Populate request is null for validation (legacy error 69120)");
            throw new BusinessException(ErrorCode.VALIDATION_EXTENDED_FAILED,
                "Populate request is required for validation")
                .withDetail("request", "null");
        }

        if (context == null) {
            log.error("Variation context is null for validation (legacy error 69120)");
            throw new BusinessException(ErrorCode.VALIDATION_EXTENDED_FAILED,
                "Variation context is required for validation")
                .withDetail("context", "null");
        }

        log.info("Validating populate request for {} POs",
            request.getPoKeys() != null ? request.getPoKeys().size() : 0);

        try {
            POValidationFact fact = buildFact(request, context);
            POValidationFact result = rulesExecutor.validatePO(fact);

            return ValidationResult.builder()
                .valid(result.isValid())
                .errors(result.getErrors())
                .warnings(result.getWarnings())
                .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Populate request validation failed: {} (legacy error 69120)", e.getMessage(), e);
            throw new BusinessException(ErrorCode.VALIDATION_EXTENDED_FAILED,
                "Populate request validation failed: " + e.getMessage(), e)
                .withDetail("storerKey", request.getStorerKey())
                .withDetail("facility", request.getFacility());
        }
    }

    /**
     * Validate SKUs.
     *
     * Error codes:
     * - VAL_012 (69112) - SKU Configuration Invalid
     *
     * @param skuCodes List of SKU codes to validate
     * @param context Variation context
     * @return List of validation results
     * @throws BusinessException if validation execution fails
     */
    public List<SKUValidationResult> validateSKUs(List<String> skuCodes, VariationContext context) {
        if (skuCodes == null || skuCodes.isEmpty()) {
            log.debug("No SKUs to validate");
            return new ArrayList<>();
        }

        if (context == null) {
            log.error("Variation context is null for SKU validation (legacy error 69112)");
            throw new BusinessException(ErrorCode.VALIDATION_SKU_CONFIG,
                "Variation context is required for SKU validation")
                .withDetail("context", "null");
        }

        log.info("Validating {} SKUs", skuCodes.size());

        try {
            List<SKUValidationResult> results = new ArrayList<>();

            for (String skuCode : skuCodes) {
                try {
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

                } catch (Exception e) {
                    log.warn("SKU {} validation failed: {} (legacy warning 69112)", skuCode, e.getMessage());
                    results.add(SKUValidationResult.builder()
                        .sku(skuCode)
                        .valid(false)
                        .error("Validation failed: " + e.getMessage())
                        .build());
                }
            }

            return results;

        } catch (Exception e) {
            log.error("Batch SKU validation failed: {} (legacy error 69112)", e.getMessage(), e);
            throw new BusinessException(ErrorCode.VALIDATION_SKU_CONFIG,
                "Batch SKU validation failed: " + e.getMessage(), e)
                .withDetail("skuCount", skuCodes.size())
                .withDetail("client", context.getClient());
        }
    }

    /**
     * Get lottable mapping for context.
     *
     * Error codes:
     * - LOT_003 (69402) - Lottable Mapping Failed
     *
     * @param context Variation context
     * @return Lottable mapping result
     * @throws BusinessException if mapping retrieval fails
     */
    public LottableMappingResult getLottableMapping(VariationContext context) {
        if (context == null) {
            log.error("Variation context is null for lottable mapping (legacy error 69402)");
            throw new BusinessException(ErrorCode.LOTTABLE_MAPPING_FAILED,
                "Variation context is required for lottable mapping")
                .withDetail("context", "null");
        }

        try {
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

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lottable mapping retrieval failed for region={}, client={}: {} (legacy error 69402)",
                context.getRegion(), context.getClient(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.LOTTABLE_MAPPING_FAILED,
                "Lottable mapping retrieval failed: " + e.getMessage(), e)
                .withDetail("region", context.getRegion())
                .withDetail("client", context.getClient());
        }
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
