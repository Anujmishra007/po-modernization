package com.wms.po.rules.service;

import com.wms.po.rules.model.POValidationFact;
import com.wms.po.rules.model.SKUEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rules executor service for running Drools rules
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RulesExecutor {

    private final KieContainer kieContainer;

    /**
     * Execute validation rules on PO
     */
    public POValidationFact validatePO(POValidationFact fact) {
        log.info("Executing PO validation rules for PO: {}", fact.getPoKey());

        KieSession session = kieContainer.newKieSession();
        try {
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
        } finally {
            session.dispose();
        }
    }

    /**
     * Execute SKU validation rules
     */
    public SKUEntity validateSKU(SKUEntity sku) {
        log.info("Executing SKU validation rules for SKU: {}", sku.getSku());

        KieSession session = kieContainer.newKieSession();
        try {
            session.insert(sku);
            int rulesFired = session.fireAllRules();
            log.info("Fired {} rules for SKU: {}", rulesFired, sku.getSku());

            return sku;
        } finally {
            session.dispose();
        }
    }

    /**
     * Execute batch SKU validation
     */
    public List<SKUEntity> validateSKUs(List<SKUEntity> skus) {
        log.info("Executing batch SKU validation for {} SKUs", skus.size());

        KieSession session = kieContainer.newKieSession();
        try {
            skus.forEach(session::insert);
            int rulesFired = session.fireAllRules();
            log.info("Fired {} rules for {} SKUs", rulesFired, skus.size());

            return skus;
        } finally {
            session.dispose();
        }
    }

    /**
     * Execute rules with specific agenda group
     */
    public POValidationFact executeRulesWithAgenda(POValidationFact fact, String agendaGroup) {
        log.info("Executing rules with agenda group: {} for PO: {}", agendaGroup, fact.getPoKey());

        KieSession session = kieContainer.newKieSession();
        try {
            session.insert(fact);
            if (fact.getLines() != null) {
                fact.getLines().forEach(session::insert);
            }

            session.getAgenda().getAgendaGroup(agendaGroup).setFocus();
            int rulesFired = session.fireAllRules();
            log.info("Fired {} rules with agenda: {}", rulesFired, agendaGroup);

            return fact;
        } finally {
            session.dispose();
        }
    }
}
