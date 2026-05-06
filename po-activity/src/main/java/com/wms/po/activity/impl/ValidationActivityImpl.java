package com.wms.po.activity.impl;

import com.wms.po.activity.ValidationActivity;
import com.wms.po.domain.entity.POEntity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.TradeReturnRequest;
import com.wms.po.domain.model.ValidationResult;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.domain.repository.PORepository;
import com.wms.po.variation.context.VariationResolver;
import com.wms.po.variation.rule.RuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of ValidationActivity.
 *
 * Maps to legacy SPs:
 * - SP-009: WM.lsp_Validate_Receipt_Std (error code 69100+)
 * - SP-010: WM.lsp_Validate_Receiptdetail_Std (error code 69106+)
 * - SP-065: isp_ASN_ExtendedValidation (error code 69120)
 *
 * @see ErrorCode for error code mappings
 */
@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class ValidationActivityImpl implements ValidationActivity {

    private final VariationResolver variationResolver;
    private final RuleEngine ruleEngine;
    private final PORepository poRepository;

    @Override
    public VariationContext resolveContext(PopulateRequest request) {
        log.info("Resolving variation context for storerKey={}, facility={}",
            request.getStorerKey(), request.getFacility());

        try {
            return variationResolver.resolve(
                request.getStorerKey(),
                request.getFacility()
            );
        } catch (Exception e) {
            log.error("Failed to resolve variation context: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.CONFIG_STORER_NOT_FOUND,
                String.format("Failed to resolve context for storer %s: %s",
                    request.getStorerKey(), e.getMessage()), e)
                .withDetail("storerKey", request.getStorerKey())
                .withDetail("facility", request.getFacility());
        }
    }

    @Override
    public ValidationResult validate(PopulateRequest request, VariationContext context) {
        log.info("Validating populate request: poKeys={}, context={}",
            request.getPoKeys(), context);

        ValidationResult result = new ValidationResult();

        // Basic validations - throws BusinessException for missing required fields
        validateBasicRequirements(request, result);

        if (!result.isValid()) {
            return result;
        }

        // PO existence and status validation - throws BusinessException for not found/invalid status
        validatePOs(request, result);

        if (!result.isValid()) {
            return result;
        }

        // Run Drools rules if available
        try {
            ValidationResult ruleResult = ruleEngine.validate(request, context);
            result.getErrors().addAll(ruleResult.getErrors());
            result.getWarnings().addAll(ruleResult.getWarnings());
            if (!ruleResult.isValid()) {
                result.setValid(false);
            }
        } catch (Exception e) {
            log.warn("Rule engine validation failed: {}", e.getMessage());
            throw BusinessException.droolsRuleFailed("Validation", e);
        }

        log.info("Validation result: valid={}, errors={}, warnings={}",
            result.isValid(), result.getErrors().size(), result.getWarnings().size());

        return result;
    }

    /**
     * Validates basic required fields.
     * Error codes: VAL_001 (69100) - Required Field Missing
     */
    private void validateBasicRequirements(PopulateRequest request, ValidationResult result) {
        if (request.getPoKeys() == null || request.getPoKeys().isEmpty()) {
            // Throw immediately for critical validation failures
            throw BusinessException.validationFailed("poKeys", "At least one PO key is required");
        }

        if (request.getStorerKey() == null || request.getStorerKey().isBlank()) {
            throw BusinessException.validationFailed("storerKey", "Storer key is required");
        }

        if (request.getFacility() == null || request.getFacility().isBlank()) {
            throw BusinessException.validationFailed("facility", "Facility is required");
        }
    }

    /**
     * Validates PO existence and status.
     * Error codes:
     * - PO_001 (68800) - PO Not Found
     * - PO_002 (68801) - PO Already Closed
     * - PO_003 (68802) - PO Cancelled
     * - PO_010 (68810) - Storer Key Mismatch
     */
    private void validatePOs(PopulateRequest request, ValidationResult result) {
        List<String> poKeys = request.getPoKeys();
        List<POEntity> foundPOs = poRepository.findByPoKeyIn(poKeys);

        // Check all POs exist - throw PO_NOT_FOUND for first missing
        if (foundPOs.size() != poKeys.size()) {
            List<String> foundKeys = foundPOs.stream()
                .map(POEntity::getPoKey)
                .toList();

            List<String> missingKeys = new ArrayList<>(poKeys);
            missingKeys.removeAll(foundKeys);

            // Throw exception for first missing PO (legacy code 68800)
            if (!missingKeys.isEmpty()) {
                String firstMissing = missingKeys.get(0);
                log.error("PO not found: {} (legacy error 68800)", firstMissing);
                throw BusinessException.poNotFound(firstMissing);
            }
        }

        // Check PO statuses
        for (POEntity po : foundPOs) {
            String status = po.getStatus();
            String poKey = po.getPoKey();

            // Status 9 = Closed, cannot populate (legacy code 68801)
            if ("9".equals(status)) {
                log.error("PO {} is closed (status=9) - legacy error 68801", poKey);
                throw BusinessException.poAlreadyClosed(poKey);
            }

            // Status 8 = Cancelled (legacy code 68802)
            if ("8".equals(status)) {
                log.error("PO {} is cancelled (status=8) - legacy error 68802", poKey);
                throw BusinessException.poCancelled(poKey);
            }

            // Check storer key matches (legacy code 68810)
            if (!request.getStorerKey().equals(po.getStorerKey())) {
                log.error("PO {} belongs to different storer: {} vs {} - legacy error 68810",
                    poKey, po.getStorerKey(), request.getStorerKey());
                throw BusinessException.storerMismatch(request.getStorerKey(), po.getStorerKey());
            }
        }
    }

    @Override
    public VariationContext resolveTradeReturnContext(TradeReturnRequest request) {
        log.info("Resolving trade return context for storerKey={}, facility={}",
            request.getStorerKey(), request.getFacility());

        try {
            return variationResolver.resolve(
                request.getStorerKey(),
                request.getFacility()
            );
        } catch (Exception e) {
            log.error("Failed to resolve trade return context: {}", e.getMessage(), e);
            throw BusinessException.storerConfigNotFound(request.getStorerKey(), "TradeReturn");
        }
    }
}
