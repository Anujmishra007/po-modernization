package com.wms.po.activity.impl;

import com.wms.po.activity.ValidationActivity;
import com.wms.po.domain.entity.POEntity;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.TradeReturnRequest;
import com.wms.po.domain.model.ValidationResult;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.domain.repository.PORepository;
import com.wms.po.variation.context.VariationResolver;
import com.wms.po.variation.rule.RuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of ValidationActivity
 */
@Component
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

        return variationResolver.resolve(
            request.getStorerKey(),
            request.getFacility()
        );
    }

    @Override
    public ValidationResult validate(PopulateRequest request, VariationContext context) {
        log.info("Validating populate request: poKeys={}, context={}",
            request.getPoKeys(), context);

        ValidationResult result = new ValidationResult();

        // Basic validations
        validateBasicRequirements(request, result);

        if (!result.isValid()) {
            return result;
        }

        // PO existence and status validation
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
            log.warn("Rule engine validation failed, continuing without rules: {}", e.getMessage());
        }

        log.info("Validation result: valid={}, errors={}, warnings={}",
            result.isValid(), result.getErrors().size(), result.getWarnings().size());

        return result;
    }

    private void validateBasicRequirements(PopulateRequest request, ValidationResult result) {
        if (request.getPoKeys() == null || request.getPoKeys().isEmpty()) {
            result.addError("At least one PO key is required");
        }

        if (request.getStorerKey() == null || request.getStorerKey().isBlank()) {
            result.addError("Storer key is required");
        }

        if (request.getFacility() == null || request.getFacility().isBlank()) {
            result.addError("Facility is required");
        }
    }

    private void validatePOs(PopulateRequest request, ValidationResult result) {
        List<String> poKeys = request.getPoKeys();
        List<POEntity> foundPOs = poRepository.findByPoKeyIn(poKeys);

        // Check all POs exist
        if (foundPOs.size() != poKeys.size()) {
            List<String> foundKeys = foundPOs.stream()
                .map(POEntity::getPoKey)
                .toList();

            List<String> missingKeys = new ArrayList<>(poKeys);
            missingKeys.removeAll(foundKeys);

            for (String missingKey : missingKeys) {
                result.addError("PO not found: " + missingKey);
            }
        }

        // Check PO statuses
        for (POEntity po : foundPOs) {
            String status = po.getStatus();

            // Status 9 = Closed, cannot populate
            if ("9".equals(status)) {
                result.addError("PO " + po.getPoKey() + " is closed (status=9)");
            }

            // Status 8 = Cancelled
            if ("8".equals(status)) {
                result.addError("PO " + po.getPoKey() + " is cancelled (status=8)");
            }

            // Check storer key matches
            if (!request.getStorerKey().equals(po.getStorerKey())) {
                result.addError("PO " + po.getPoKey() + " belongs to different storer: " + po.getStorerKey());
            }
        }
    }

    @Override
    public VariationContext resolveTradeReturnContext(TradeReturnRequest request) {
        log.info("Resolving trade return context for storerKey={}, facility={}",
            request.getStorerKey(), request.getFacility());

        return variationResolver.resolve(
            request.getStorerKey(),
            request.getFacility()
        );
    }
}
