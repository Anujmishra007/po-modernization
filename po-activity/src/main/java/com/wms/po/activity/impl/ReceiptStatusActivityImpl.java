package com.wms.po.activity.impl;

import com.wms.po.activity.ReceiptStatusActivity;
import com.wms.po.domain.service.FinalizeReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implementation of ReceiptStatusActivity.
 * Manages receipt status transitions during finalization.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReceiptStatusActivityImpl implements ReceiptStatusActivity {

    private final FinalizeReceiptService finalizeReceiptService;

    @Override
    public String validateForFinalization(String receiptKey) {
        log.info("Validating receipt {} for finalization", receiptKey);
        return finalizeReceiptService.validateForFinalization(receiptKey);
    }

    @Override
    public String setStatusFinalizing(String receiptKey, String userId) {
        log.info("Setting receipt {} status to FINALIZING, user={}", receiptKey, userId);
        return finalizeReceiptService.setStatusFinalizing(receiptKey, userId);
    }

    @Override
    public void setStatusFinalized(String receiptKey, String userId) {
        log.info("Setting receipt {} status to FINALIZED, user={}", receiptKey, userId);
        finalizeReceiptService.setStatusFinalized(receiptKey, userId);
    }

    @Override
    public void revertStatus(String receiptKey, String originalStatus, String userId) {
        log.warn("COMPENSATION: Reverting receipt {} status to {}", receiptKey, originalStatus);
        finalizeReceiptService.revertStatus(receiptKey, originalStatus, userId);
    }

    @Override
    public void closeReceipt(String receiptKey, String userId) {
        log.info("Closing receipt {}, user={}", receiptKey, userId);
        finalizeReceiptService.closeReceipt(receiptKey, userId);
    }
}
