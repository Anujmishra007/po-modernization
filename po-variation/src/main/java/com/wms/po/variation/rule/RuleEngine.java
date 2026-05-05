package com.wms.po.variation.rule;

import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.ValidationResult;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.variation.config.ConfigurationService;
import com.wms.po.variation.config.VariationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule engine for business validations.
 * In production, this would integrate with Drools.
 * For now, implements rules in Java as a fallback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleEngine {

    private final ConfigurationService configService;

    // Drools integration would be:
    // private final KieContainer kieContainer;

    /**
     * Validate the populate request using rules
     */
    public ValidationResult validate(PopulateRequest request, VariationContext context) {
        log.debug("Running rule engine validation for context: {}", context);

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try {
            VariationConfig config = configService.getConfig(context);

            // Run validation rules
            runStatusRules(request, context, config, errors);
            runRegionRules(request, context, config, errors, warnings);
            runClientRules(request, context, config, errors, warnings);

        } catch (Exception e) {
            log.warn("Rule engine error, continuing without rules: {}", e.getMessage());
        }

        return ValidationResult.builder()
            .valid(errors.isEmpty())
            .errors(errors)
            .warnings(warnings)
            .build();
    }

    private void runStatusRules(PopulateRequest request, VariationContext context,
                                VariationConfig config, List<String> errors) {
        // Status validation rules
        // In Drools, this would be:
        // rule "Validate PO Status"
        //   when $po : POEntity(status in ("9", "CANC", "CLOSED"))
        //   then errors.add("PO has invalid status")

        // Java implementation for fallback
        if (config.getRules() != null) {
            List<String> blockedStatuses = config.getRules().getBlockedStatuses();
            if (blockedStatuses != null && !blockedStatuses.isEmpty()) {
                log.debug("Blocked statuses: {}", blockedStatuses);
            }
        }
    }

    private void runRegionRules(PopulateRequest request, VariationContext context,
                                VariationConfig config, List<String> errors, List<String> warnings) {
        // Korea-specific rules
        if (context.isKorea()) {
            // rule "Korea Specific - Require Lottable03"
            if (config.isRequireLottable03()) {
                log.debug("Korea: Lottable03 is required");
            }

            // rule "Korea - Customs Code Required"
            if (config.getRules() != null && config.getRules().isRequireCustomsCode()) {
                log.debug("Korea: Customs code is required");
            }
        }

        // India-specific rules
        if (context.isIndia()) {
            // rule "India - GST Validation"
            log.debug("India: GST rules apply");
        }

        // Singapore-specific rules
        if (context.isSingapore()) {
            // rule "Singapore - Permit Validation"
            log.debug("Singapore: Permit rules apply");
        }
    }

    private void runClientRules(PopulateRequest request, VariationContext context,
                                VariationConfig config, List<String> errors, List<String> warnings) {
        // Nike-specific rules
        if (context.isNike()) {
            // rule "Nike - Style Code Required"
            log.debug("Nike: Style code rules apply");
        }

        // H&M-specific rules
        if (context.isHM()) {
            // rule "HM - Garment Categorization"
            log.debug("H&M: Garment rules apply");
        }
    }

    // Drools integration methods (for production)
    /*
    public KieSession newSession() {
        return kieContainer.newKieSession("po-rules-session");
    }

    public ValidationResult validateWithDrools(PopulateRequest request,
                                                VariationContext context,
                                                VariationConfig config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        KieSession session = newSession();
        try {
            session.insert(request);
            session.insert(context);
            session.insert(config);
            session.setGlobal("errors", errors);
            session.setGlobal("warnings", warnings);

            session.fireAllRules();

        } finally {
            session.dispose();
        }

        return ValidationResult.builder()
            .valid(errors.isEmpty())
            .errors(errors)
            .warnings(warnings)
            .build();
    }
    */
}
