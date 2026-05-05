package com.wms.po.rules.service;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.rules.model.POValidationFact;
import com.wms.po.rules.model.SKUEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rules executor service for running Drools rules.
 *
 * Error codes:
 * - RUL_001 (69600) - Drools Rule Failed
 * - VAL_012 (69112) - SKU Configuration Invalid
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RulesExecutor {

    private final KieContainer kieContainer;

    /**
     * Execute validation rules on PO.
     *
     * Error codes:
     * - RUL_001 (69600) - Drools Rule Failed
     *
     * @param fact PO validation fact to validate
     * @return Updated fact with validation results
     * @throws BusinessException if rule execution fails
     */
    public POValidationFact validatePO(POValidationFact fact) {
        if (fact == null) {
            log.error("POValidationFact is null (legacy error 69600)");
            throw new BusinessException(ErrorCode.JOB_EXECUTION_FAILED,
                "POValidationFact is required for validation")
                .withDetail("fact", "null");
        }

        log.info("Executing PO validation rules for PO: {}", fact.getPoKey());

        KieSession session = null;
        try {
            session = kieContainer.newKieSession();
            session.insert(fact);

            // Insert all line items
            if (fact.getLines() != null) {
                fact.getLines().forEach(session::insert);
            }

            // Execute rules in order by agenda group
            int totalRulesFired = 0;

            // 1. Header validation
            session.getAgenda().getAgendaGroup("header-validation").setFocus();
            totalRulesFired += session.fireAllRules();

            // 2. Line validation
            session.getAgenda().getAgendaGroup("line-validation").setFocus();
            totalRulesFired += session.fireAllRules();

            // 3. Region-specific validation
            session.getAgenda().getAgendaGroup("region-validation").setFocus();
            totalRulesFired += session.fireAllRules();

            // 4. Client-specific validation
            session.getAgenda().getAgendaGroup("client-validation").setFocus();
            totalRulesFired += session.fireAllRules();

            // 5. Compute rules
            session.getAgenda().getAgendaGroup("compute").setFocus();
            totalRulesFired += session.fireAllRules();

            // 6. Finalize - propagate line errors to PO
            session.getAgenda().getAgendaGroup("finalize").setFocus();
            totalRulesFired += session.fireAllRules();

            log.info("Fired {} rules for PO: {}", totalRulesFired, fact.getPoKey());

            return fact;

        } catch (Exception e) {
            log.error("Drools rule execution failed for PO {}: {} (legacy error 69600)",
                fact.getPoKey(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.JOB_EXECUTION_FAILED,
                "Drools rule execution failed: " + e.getMessage(), e)
                .withDetail("poKey", fact.getPoKey())
                .withDetail("storerKey", fact.getStorerKey());
        } finally {
            if (session != null) {
                session.dispose();
            }
        }
    }

    /**
     * Execute SKU validation rules.
     *
     * Error codes:
     * - VAL_012 (69112) - SKU Configuration Invalid
     *
     * @param sku SKU entity to validate
     * @return Updated SKU with validation results
     * @throws BusinessException if rule execution fails
     */
    public SKUEntity validateSKU(SKUEntity sku) {
        if (sku == null) {
            log.error("SKUEntity is null (legacy error 69112)");
            throw new BusinessException(ErrorCode.VALIDATION_SKU_CONFIG,
                "SKUEntity is required for validation")
                .withDetail("sku", "null");
        }

        log.info("Executing SKU validation rules for SKU: {}", sku.getSku());

        KieSession session = null;
        try {
            session = kieContainer.newKieSession();
            session.insert(sku);
            int rulesFired = session.fireAllRules();
            log.info("Fired {} rules for SKU: {}", rulesFired, sku.getSku());

            return sku;

        } catch (Exception e) {
            log.error("SKU validation rule execution failed for {}: {} (legacy error 69112)",
                sku.getSku(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.VALIDATION_SKU_CONFIG,
                "SKU validation rule execution failed: " + e.getMessage(), e)
                .withDetail("sku", sku.getSku())
                .withDetail("storerKey", sku.getStorerKey());
        } finally {
            if (session != null) {
                session.dispose();
            }
        }
    }

    /**
     * Execute batch SKU validation.
     *
     * Error codes:
     * - VAL_012 (69112) - SKU Configuration Invalid
     *
     * @param skus List of SKU entities to validate
     * @return Updated SKUs with validation results
     * @throws BusinessException if rule execution fails
     */
    public List<SKUEntity> validateSKUs(List<SKUEntity> skus) {
        if (skus == null || skus.isEmpty()) {
            log.debug("No SKUs to validate");
            return skus != null ? skus : List.of();
        }

        log.info("Executing batch SKU validation for {} SKUs", skus.size());

        KieSession session = null;
        try {
            session = kieContainer.newKieSession();
            skus.forEach(session::insert);
            int rulesFired = session.fireAllRules();
            log.info("Fired {} rules for {} SKUs", rulesFired, skus.size());

            return skus;

        } catch (Exception e) {
            log.error("Batch SKU validation failed: {} (legacy error 69112)", e.getMessage(), e);
            throw new BusinessException(ErrorCode.VALIDATION_SKU_CONFIG,
                "Batch SKU validation rule execution failed: " + e.getMessage(), e)
                .withDetail("skuCount", skus.size());
        } finally {
            if (session != null) {
                session.dispose();
            }
        }
    }

    /**
     * Execute rules with specific agenda group.
     *
     * Error codes:
     * - RUL_001 (69600) - Drools Rule Failed
     *
     * @param fact PO validation fact
     * @param agendaGroup Agenda group to execute
     * @return Updated fact with validation results
     * @throws BusinessException if rule execution fails
     */
    public POValidationFact executeRulesWithAgenda(POValidationFact fact, String agendaGroup) {
        if (fact == null) {
            log.error("POValidationFact is null for agenda execution (legacy error 69600)");
            throw new BusinessException(ErrorCode.JOB_EXECUTION_FAILED,
                "POValidationFact is required for rule execution")
                .withDetail("fact", "null")
                .withDetail("agendaGroup", agendaGroup);
        }

        if (agendaGroup == null || agendaGroup.isBlank()) {
            log.error("Agenda group is null/blank (legacy error 69600)");
            throw new BusinessException(ErrorCode.JOB_EXECUTION_FAILED,
                "Agenda group is required for rule execution")
                .withDetail("poKey", fact.getPoKey())
                .withDetail("agendaGroup", "null or blank");
        }

        log.info("Executing rules with agenda group: {} for PO: {}", agendaGroup, fact.getPoKey());

        KieSession session = null;
        try {
            session = kieContainer.newKieSession();
            session.insert(fact);
            if (fact.getLines() != null) {
                fact.getLines().forEach(session::insert);
            }

            session.getAgenda().getAgendaGroup(agendaGroup).setFocus();
            int rulesFired = session.fireAllRules();
            log.info("Fired {} rules with agenda: {}", rulesFired, agendaGroup);

            return fact;

        } catch (Exception e) {
            log.error("Rule execution with agenda {} failed for PO {}: {} (legacy error 69600)",
                agendaGroup, fact.getPoKey(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.JOB_EXECUTION_FAILED,
                "Rule execution with agenda failed: " + e.getMessage(), e)
                .withDetail("poKey", fact.getPoKey())
                .withDetail("agendaGroup", agendaGroup);
        } finally {
            if (session != null) {
                session.dispose();
            }
        }
    }
}
