package com.wms.po.rules.service;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
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
 *
 * Error codes:
 * - LOT_002 (69401) - Lottable Rule Execution Failed
 * - LOT_010 (69410) - Lot1 Generation Failed
 * - LOT_011 (69411) - Lot2 Generation Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LottableRulesService {

    private final KieContainer kieContainer;

    /**
     * Compute lottable values for a single receipt detail.
     *
     * Error codes:
     * - LOT_002 (69401) - Lottable Rule Execution Failed
     *
     * @param detail Receipt detail fact with input data
     * @return Updated fact with computed lottables
     * @throws BusinessException if rule execution fails
     */
    public ReceiptDetailFact computeLottables(ReceiptDetailFact detail) {
        if (detail == null) {
            log.error("ReceiptDetailFact is null for lottable computation (legacy error 69401)");
            throw new BusinessException(ErrorCode.LOTTABLE_RULE_EXECUTION_FAILED,
                "Receipt detail is required for lottable computation")
                .withDetail("detail", "null");
        }

        log.debug("Computing lottables for receipt {} line {}",
            detail.getReceiptKey(), detail.getLineNumber());

        KieSession session = null;
        try {
            session = kieContainer.newKieSession();
            session.insert(detail);
            int rulesFired = session.fireAllRules();

            log.info("Computed lottables for receipt {} line {}: fired {} rules, applied: {}",
                detail.getReceiptKey(), detail.getLineNumber(),
                rulesFired, detail.getAppliedRules());

            if (!detail.isValid()) {
                log.warn("Lottable validation errors for receipt {} line {}: {} (legacy warning 69401)",
                    detail.getReceiptKey(), detail.getLineNumber(),
                    detail.getValidationErrors());
            }

            return detail;

        } catch (Exception e) {
            log.error("Lottable rule execution failed for receipt {} line {}: {} (legacy error 69401)",
                detail.getReceiptKey(), detail.getLineNumber(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.LOTTABLE_RULE_EXECUTION_FAILED,
                "Lottable rule execution failed: " + e.getMessage(), e)
                .withDetail("receiptKey", detail.getReceiptKey())
                .withDetail("lineNumber", detail.getLineNumber());
        } finally {
            if (session != null) {
                session.dispose();
            }
        }
    }

    /**
     * Compute lottable values for multiple receipt details.
     *
     * Error codes:
     * - LOT_002 (69401) - Lottable Rule Execution Failed
     *
     * @param details List of receipt detail facts
     * @return Updated facts with computed lottables
     * @throws BusinessException if rule execution fails
     */
    public List<ReceiptDetailFact> computeLottables(List<ReceiptDetailFact> details) {
        if (details == null || details.isEmpty()) {
            log.debug("No receipt details to process for lottable computation");
            return details != null ? details : List.of();
        }

        log.info("Computing lottables for {} receipt details", details.size());

        KieSession session = null;
        try {
            session = kieContainer.newKieSession();

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
                    log.warn("Invalid lottables for line {}: {} (legacy warning 69401)",
                        detail.getLineNumber(), detail.getValidationErrors());
                }
            }

            log.info("Lottable computation complete: {} valid, {} invalid", validCount, invalidCount);

            return details;

        } catch (Exception e) {
            log.error("Batch lottable rule execution failed: {} (legacy error 69401)", e.getMessage(), e);
            throw new BusinessException(ErrorCode.LOTTABLE_RULE_EXECUTION_FAILED,
                "Batch lottable rule execution failed: " + e.getMessage(), e)
                .withDetail("detailCount", details.size());
        } finally {
            if (session != null) {
                session.dispose();
            }
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
