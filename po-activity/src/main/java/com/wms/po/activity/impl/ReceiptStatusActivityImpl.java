package com.wms.po.activity.impl;

import com.wms.po.activity.ReceiptStatusActivity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.service.FinalizeReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implementation of ReceiptStatusActivity.
 * Manages receipt status transitions during finalization.
 *
 * Maps to legacy SPs:
 * - SP-060: ispFinalizeReceipt (status management portions, error codes 68900-68905)
 * - SP-003: WM.lsp_FinalizeReceipt_Wrapper (entry point)
 *
 * Error codes:
 * - RCV_001 (68900) - Receipt Not Found
 * - RCV_002 (68901) - Receipt Already Finalized
 * - RCV_003 (68902) - Receipt Invalid Status
 * - RCV_020 (68920) - Finalize Status Update Failed
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReceiptStatusActivityImpl implements ReceiptStatusActivity {

    private final FinalizeReceiptService finalizeReceiptService;

    /**
     * Validate receipt for finalization.
     *
     * Error codes:
     * - RCV_001 (68900) - Receipt Not Found
     * - RCV_002 (68901) - Receipt Already Finalized
     * - RCV_003 (68902) - Receipt Invalid Status
     */
    @Override
    public String validateForFinalization(String receiptKey) {
        log.info("Validating receipt {} for finalization", receiptKey);

        try {
            String validationResult = finalizeReceiptService.validateForFinalization(receiptKey);

            // Check for validation failures
            if (validationResult == null) {
                log.error("Receipt not found: {} (legacy error 68900)", receiptKey);
                throw BusinessException.receiptNotFound(receiptKey);
            }

            if ("FINALIZED".equals(validationResult)) {
                log.error("Receipt already finalized: {} (legacy error 68901)", receiptKey);
                throw BusinessException.receiptAlreadyFinalized(receiptKey);
            }

            if (validationResult.startsWith("INVALID_STATUS:")) {
                String currentStatus = validationResult.substring("INVALID_STATUS:".length());
                log.error("Receipt {} has invalid status {} (legacy error 68902)",
                    receiptKey, currentStatus);
                throw BusinessException.receiptInvalidStatus(receiptKey, currentStatus, "0,1,5");
            }

            return validationResult;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Receipt validation failed: {} (legacy error 68900)", receiptKey, e);
            throw BusinessException.receiptNotFound(receiptKey);
        }
    }

    /**
     * Set receipt status to FINALIZING (in progress).
     *
     * Error codes:
     * - RCV_020 (68920) - Finalize Status Update Failed
     */
    @Override
    public String setStatusFinalizing(String receiptKey, String userId) {
        log.info("Setting receipt {} status to FINALIZING, user={}", receiptKey, userId);

        try {
            String originalStatus = finalizeReceiptService.setStatusFinalizing(receiptKey, userId);

            if (originalStatus == null) {
                log.error("Failed to set FINALIZING status for receipt {} (legacy error 68920)", receiptKey);
                throw new BusinessException(ErrorCode.FINALIZE_STATUS_UPDATE_FAILED,
                    "Failed to set FINALIZING status for receipt: " + receiptKey)
                    .withDetail("receiptKey", receiptKey)
                    .withDetail("targetStatus", "FINALIZING");
            }

            return originalStatus;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to set FINALIZING status for receipt {}: {} (legacy error 68920)",
                receiptKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FINALIZE_STATUS_UPDATE_FAILED,
                "Failed to set FINALIZING status: " + receiptKey, e)
                .withDetail("receiptKey", receiptKey);
        }
    }

    /**
     * Set receipt status to FINALIZED (complete).
     *
     * Error codes:
     * - RCV_020 (68920) - Finalize Status Update Failed
     */
    @Override
    public void setStatusFinalized(String receiptKey, String userId) {
        log.info("Setting receipt {} status to FINALIZED, user={}", receiptKey, userId);

        try {
            finalizeReceiptService.setStatusFinalized(receiptKey, userId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to set FINALIZED status for receipt {}: {} (legacy error 68920)",
                receiptKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FINALIZE_STATUS_UPDATE_FAILED,
                "Failed to set FINALIZED status: " + receiptKey, e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("targetStatus", "FINALIZED");
        }
    }

    /**
     * Revert receipt status (compensation action).
     *
     * Error codes:
     * - RCV_020 (68920) - Finalize Status Update Failed
     */
    @Override
    public void revertStatus(String receiptKey, String originalStatus, String userId) {
        log.warn("COMPENSATION: Reverting receipt {} status to {}", receiptKey, originalStatus);

        try {
            finalizeReceiptService.revertStatus(receiptKey, originalStatus, userId);
        } catch (Exception e) {
            log.error("COMPENSATION failed: Could not revert receipt {} status: {} (legacy error 68920)",
                receiptKey, e.getMessage(), e);
            // Don't throw during compensation - log and continue
        }
    }

    /**
     * Close receipt.
     *
     * Error codes:
     * - RCV_003 (68902) - Receipt Invalid Status (if can't close)
     */
    @Override
    public void closeReceipt(String receiptKey, String userId) {
        log.info("Closing receipt {}, user={}", receiptKey, userId);

        try {
            finalizeReceiptService.closeReceipt(receiptKey, userId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to close receipt {}: {} (legacy error 68902)",
                receiptKey, e.getMessage(), e);
            throw BusinessException.receiptInvalidStatus(receiptKey, "UNKNOWN", "9");
        }
    }
}
