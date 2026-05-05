package com.wms.po.rules.service;

import com.wms.po.rules.model.ReceiptDetailFact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for executing lottable generation rules.
 * Implements the logic from:
 * - ispDefLot1FrRcptDtl (Lottable01 default)
 * - ispDefLot2FrRcptDtl (Lottable02 default)
 * - ispGenLot2BySuppLot (Lottable02 from supplier lot)
 * - And other lottable generation SPs
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LottableRulesService {

    private final KieContainer kieContainer;

    /**
     * Compute lottable values for a single receipt detail.
     *
     * @param detail Receipt detail fact with input data
     * @return Updated fact with computed lottables
     */
    public ReceiptDetailFact computeLottables(ReceiptDetailFact detail) {
        log.debug("Computing lottables for receipt {} line {}",
            detail.getReceiptKey(), detail.getLineNumber());

        KieSession session = kieContainer.newKieSession();
        try {
            session.insert(detail);
            int rulesFired = session.fireAllRules();

            log.info("Computed lottables for receipt {} line {}: fired {} rules, applied: {}",
                detail.getReceiptKey(), detail.getLineNumber(),
                rulesFired, detail.getAppliedRules());

            if (!detail.isValid()) {
                log.warn("Lottable validation errors for receipt {} line {}: {}",
                    detail.getReceiptKey(), detail.getLineNumber(),
                    detail.getValidationErrors());
            }

            return detail;
        } finally {
            session.dispose();
        }
    }

    /**
     * Compute lottable values for multiple receipt details.
     *
     * @param details List of receipt detail facts
     * @return Updated facts with computed lottables
     */
    public List<ReceiptDetailFact> computeLottables(List<ReceiptDetailFact> details) {
        log.info("Computing lottables for {} receipt details", details.size());

        KieSession session = kieContainer.newKieSession();
        try {
            // Insert all facts
            for (ReceiptDetailFact detail : details) {
                session.insert(detail);
            }

            // Fire all rules
            int rulesFired = session.fireAllRules();
            log.info("Fired {} rules for {} details", rulesFired, details.size());

            // Log results
            int validCount = 0;
            int invalidCount = 0;
            for (ReceiptDetailFact detail : details) {
                if (detail.isValid()) {
                    validCount++;
                } else {
                    invalidCount++;
                    log.warn("Invalid lottables for line {}: {}",
                        detail.getLineNumber(), detail.getValidationErrors());
                }
            }

            log.info("Lottable computation complete: {} valid, {} invalid", validCount, invalidCount);

            return details;
        } finally {
            session.dispose();
        }
    }

    /**
     * Validate lottable values for a receipt detail.
     *
     * @param detail Receipt detail to validate
     * @return true if valid, false otherwise
     */
    public boolean validateLottables(ReceiptDetailFact detail) {
        if (!detail.isLottablesComputed()) {
            computeLottables(detail);
        }
        return detail.isValid();
    }

    /**
     * Build a ReceiptDetailFact from receipt detail data.
     * Helper method for creating facts from database records.
     */
    public static ReceiptDetailFact.ReceiptDetailFactBuilder factBuilder() {
        return ReceiptDetailFact.builder();
    }
}
