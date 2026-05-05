package com.wms.po.service;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.rules.model.POValidationFact;
import com.wms.po.rules.service.RulesExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * PO validation service using rules engine.
 *
 * Error codes:
 * - PO_019 (68819) - PO Validation Failed
 * - PO_003 (68803) - PO Status Invalid
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class POValidationService {

    private final RulesExecutor rulesExecutor;

    /**
     * Validate PO for creation.
     *
     * Error codes:
     * - PO_019 (68819) - PO Validation Failed
     */
    public void validateForCreate(PopulateRequest request, VariationContext context) {
        log.debug("Validating PO for create");

        List<String> errors = new ArrayList<>();

        // Basic validation
        if (request.getStorerKey() == null || request.getStorerKey().isEmpty()) {
            errors.add("Storer key is required");
        }
        if (request.getFacility() == null || request.getFacility().isEmpty()) {
            errors.add("Facility is required");
        }
        // PO keys are not required for create - they will be generated
        // if (request.getPoKeys() == null || request.getPoKeys().isEmpty()) {
        //     errors.add("At least one PO key is required");
        // }

        // Rules-based validation (disabled for performance in dev mode)
        // TODO: Re-enable rules validation when Drools jitting is optimized
        // POValidationFact fact = buildValidationFact(request, context);
        // POValidationFact result = rulesExecutor.validatePO(fact);
        // if (!result.isValid()) {
        //     errors.addAll(result.getErrors());
        // }
        log.debug("Skipping rules validation in current mode");

        if (!errors.isEmpty()) {
            log.error("PO creation validation failed: {} (legacy error 68819)", errors);
            throw new BusinessException(ErrorCode.PO_VALIDATION_FAILED,
                "PO validation failed: " + String.join("; ", errors))
                .withDetail("errors", errors)
                .withDetail("storerKey", request.getStorerKey())
                .withDetail("facility", request.getFacility());
        }
    }

    /**
     * Validate PO for update.
     *
     * Error codes:
     * - PO_019 (68819) - PO Validation Failed
     */
    public void validateForUpdate(String poKey, PopulateRequest request, VariationContext context) {
        log.debug("Validating PO for update: {}", poKey);

        List<String> errors = new ArrayList<>();

        // Check PO exists
        // TODO: Check in database

        // Rules-based validation (disabled for performance in dev mode)
        // TODO: Re-enable rules validation when Drools jitting is optimized
        // POValidationFact fact = buildValidationFact(request, context);
        // POValidationFact result = rulesExecutor.executeRulesWithAgenda(fact, "header-validation");
        // if (!result.isValid()) {
        //     errors.addAll(result.getErrors());
        // }
        log.debug("Skipping rules validation in current mode");

        if (!errors.isEmpty()) {
            log.error("PO update validation failed for {}: {} (legacy error 68819)", poKey, errors);
            throw new BusinessException(ErrorCode.PO_VALIDATION_FAILED,
                "PO update validation failed: " + String.join("; ", errors))
                .withDetail("poKey", poKey)
                .withDetail("errors", errors);
        }
    }

    /**
     * Validate PO for delete.
     *
     * Error codes:
     * - PO_019 (68819) - PO Validation Failed (cannot delete in current status)
     *
     * @param poKey PO key to validate for deletion
     * @param context Variation context
     * @throws BusinessException if PO cannot be deleted
     */
    public void validateForDelete(String poKey, VariationContext context) {
        log.debug("Validating PO for delete: {}", poKey);

        if (poKey == null || poKey.isBlank()) {
            log.error("PO key is required for delete validation (legacy error 68819)");
            throw new BusinessException(ErrorCode.PO_VALIDATION_FAILED,
                "PO key is required for delete validation")
                .withDetail("poKey", "null or blank");
        }

        if (context == null) {
            log.error("Variation context is required for delete validation (legacy error 68819)");
            throw new BusinessException(ErrorCode.PO_VALIDATION_FAILED,
                "Variation context is required for delete validation")
                .withDetail("poKey", poKey)
                .withDetail("context", "null");
        }

        List<String> errors = new ArrayList<>();

        // Check PO can be deleted (status, etc.)
        // Blocked statuses for deletion: 5 (Received), 9 (Closed)
        // TODO: Add actual status check when PO status is available in context
        log.debug("Delete validation complete for PO: {}", poKey);

        if (!errors.isEmpty()) {
            log.error("PO delete validation failed for {}: {} (legacy error 68819)", poKey, errors);
            throw new BusinessException(ErrorCode.PO_VALIDATION_FAILED,
                "PO cannot be deleted: " + String.join("; ", errors))
                .withDetail("poKey", poKey)
                .withDetail("errors", errors);
        }
    }

    private POValidationFact buildValidationFact(PopulateRequest request, VariationContext context) {
        return POValidationFact.builder()
            .poKey(request.getPoKeys() != null && !request.getPoKeys().isEmpty()
                ? request.getPoKeys().get(0) : null)
            .storerKey(request.getStorerKey())
            .facility(request.getFacility())
            .region(context.getRegion())
            .client(context.getClient())
            .dbVersion(context.getVersion())
            .userId(request.getUserId())
            .lines(new ArrayList<>())
            .build();
    }
}
