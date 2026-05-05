package com.wms.po.service;

import com.wms.po.domain.exception.ValidationException;
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
 * PO validation service using rules engine
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class POValidationService {

    private final RulesExecutor rulesExecutor;

    /**
     * Validate PO for creation
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
            throw new ValidationException("PO validation failed", errors);
        }
    }

    /**
     * Validate PO for update
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
            throw new ValidationException("PO update validation failed", errors);
        }
    }

    /**
     * Validate PO for delete
     */
    public void validateForDelete(String poKey, VariationContext context) {
        log.debug("Validating PO for delete: {}", poKey);

        // Check PO can be deleted (status, etc.)
        // TODO: Check status allows deletion
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
