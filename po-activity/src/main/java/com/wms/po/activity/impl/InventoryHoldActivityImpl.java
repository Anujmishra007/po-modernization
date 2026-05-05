package com.wms.po.activity.impl;

import com.wms.po.activity.InventoryHoldActivity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.service.InventoryHoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of InventoryHoldActivity.
 * Manages inventory holds during finalization.
 *
 * Maps to legacy SPs:
 * - SP-055-059: nspInventoryHoldWrapper (error codes 68707-68709)
 *
 * Error codes:
 * - INV_008 (68707) - Hold Application Failed
 * - INV_009 (68708) - Hold Release Failed
 * - INV_010 (68709) - Hold Evaluation Failed
 *
 * Holds can be applied based on:
 * - Quality inspection requirements
 * - Customs clearance
 * - Client-specific rules
 * - Quarantine requirements
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryHoldActivityImpl implements InventoryHoldActivity {

    private final InventoryHoldService holdService;

    @Override
    @Transactional(readOnly = true)
    public List<HoldToApply> evaluateHolds(HoldEvaluationRequest request) {
        log.info("Evaluating holds for receipt {} with {} inventory records",
            request.getReceiptKey(), request.getInventoryIds().size());

        try {
            // Convert to service request
            InventoryHoldService.HoldEvaluationRequest serviceRequest =
                InventoryHoldService.HoldEvaluationRequest.builder()
                    .storerKey(request.getStorerKey())
                    .sku(request.getSku())
                    .supplierKey(request.getSupplierKey())
                    .countryOfOrigin(request.getCountryOfOrigin())
                    .customsRequired(request.isCustomsRequired())
                    .inspectionRequired(request.isRequiresInspection())
                    .build();

            // Evaluate holds using service
            List<InventoryHoldService.HoldSpec> holdSpecs = holdService.evaluateHolds(serviceRequest);

            // Convert to activity response
            List<HoldToApply> holdsToApply = holdSpecs.stream()
                .map(spec -> HoldToApply.builder()
                    .holdCode(spec.getHoldCode())
                    .holdType(spec.getHoldType())
                    .holdReason(spec.getHoldReason())
                    .blockAllocation(spec.isBlockAllocation())
                    .blockPutaway(spec.isBlockPutaway())
                    .expiryDate(spec.getExpiryDate())
                    .build())
                .collect(Collectors.toList());

            log.info("Evaluated holds: {} holds to apply", holdsToApply.size());
            return holdsToApply;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Hold evaluation failed for receipt {}: {} (legacy error 68709)",
                request.getReceiptKey(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.HOLD_EVALUATION_FAILED,
                "Hold evaluation failed for receipt: " + request.getReceiptKey(), e)
                .withDetail("receiptKey", request.getReceiptKey())
                .withDetail("sku", request.getSku());
        }
    }

    @Override
    @Transactional
    public HoldResult applyHolds(HoldApplicationRequest request) {
        log.info("Applying {} holds to {} inventory records",
            request.getHolds().size(), request.getInventoryIds().size());

        List<String> allHoldIds = new ArrayList<>();
        String currentHoldCode = null;

        try {
            for (HoldToApply hold : request.getHolds()) {
                currentHoldCode = hold.getHoldCode();

                // Convert to service hold spec
                InventoryHoldService.HoldSpec holdSpec =
                    InventoryHoldService.HoldSpec.builder()
                        .holdCode(hold.getHoldCode())
                        .holdType(hold.getHoldType())
                        .holdReason(hold.getHoldReason())
                        .blockAllocation(hold.isBlockAllocation())
                        .blockPutaway(hold.isBlockPutaway())
                        .notes("Applied during finalization")
                        .expiryDate(hold.getExpiryDate())
                        .build();

                // Create holds for all inventory records
                List<String> holdIds = holdService.createBatchHolds(
                    request.getInventoryIds(),
                    holdSpec,
                    request.getUserId()
                );

                allHoldIds.addAll(holdIds);
            }

            log.info("Applied {} holds successfully", allHoldIds.size());
            return HoldResult.success(allHoldIds);

        } catch (BusinessException e) {
            log.error("Hold application failed: {} (legacy error 68707)", e.getMessage(), e);
            // Rollback any holds that were created
            rollbackHolds(allHoldIds, request.getUserId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to apply hold {}: {} (legacy error 68707)",
                currentHoldCode, e.getMessage(), e);

            // Rollback any holds that were created
            rollbackHolds(allHoldIds, request.getUserId(), e.getMessage());

            throw BusinessException.holdApplicationFailed(
                request.getInventoryIds().isEmpty() ? "unknown" : request.getInventoryIds().get(0),
                currentHoldCode != null ? currentHoldCode : "unknown",
                e);
        }
    }

    /**
     * Rollback holds on failure.
     */
    private void rollbackHolds(List<String> holdIds, String userId, String reason) {
        if (!holdIds.isEmpty()) {
            try {
                holdService.releaseHolds(holdIds, userId,
                    "Rollback due to partial failure: " + reason);
            } catch (Exception rollbackError) {
                log.error("Failed to rollback holds: {} (legacy error 68708)", rollbackError.getMessage());
            }
        }
    }

    /**
     * Remove holds (compensation action).
     *
     * Error codes:
     * - INV_009 (68708) - Hold Release Failed
     */
    @Override
    @Transactional
    public void removeHolds(List<String> holdIds, String reason) {
        log.warn("COMPENSATION: Removing {} holds - {}", holdIds.size(), reason);

        int releasedCount = 0;
        int failedCount = 0;
        List<String> failedHoldIds = new ArrayList<>();

        for (String holdId : holdIds) {
            try {
                boolean released = holdService.cancelHold(holdId, "SYSTEM", reason);
                if (released) {
                    releasedCount++;
                } else {
                    failedCount++;
                    failedHoldIds.add(holdId);
                    log.warn("Hold {} could not be cancelled (may already be released) - legacy error 68708", holdId);
                }
            } catch (Exception e) {
                failedCount++;
                failedHoldIds.add(holdId);
                log.error("Failed to remove hold {}: {} (legacy error 68708)", holdId, e.getMessage());
            }
        }

        log.info("COMPENSATION complete: Removed {}/{} holds ({} failed)",
            releasedCount, holdIds.size(), failedCount);

        // If all holds failed to release, throw exception
        if (releasedCount == 0 && !holdIds.isEmpty()) {
            throw new BusinessException(ErrorCode.HOLD_RELEASE_FAILED,
                String.format("Failed to release any of %d holds", holdIds.size()))
                .withDetail("holdIds", failedHoldIds)
                .withDetail("reason", reason);
        }
    }

    /**
     * Check if any inventory records have blocking holds for putaway.
     *
     * @param inventoryIds Inventory records to check
     * @return true if any are blocked for putaway
     */
    public boolean hasBlockingHoldsForPutaway(List<String> inventoryIds) {
        for (String inventoryId : inventoryIds) {
            if (holdService.isBlockedForPutaway(inventoryId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if any inventory records have blocking holds for allocation.
     *
     * @param inventoryIds Inventory records to check
     * @return true if any are blocked for allocation
     */
    public boolean hasBlockingHoldsForAllocation(List<String> inventoryIds) {
        for (String inventoryId : inventoryIds) {
            if (holdService.isBlockedForAllocation(inventoryId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all active holds for inventory records.
     *
     * @param inventoryIds Inventory records to check
     * @return Map of inventory ID to list of holds
     */
    public java.util.Map<String, List<InventoryHoldService.HoldInfo>> getActiveHolds(
            List<String> inventoryIds) {

        java.util.Map<String, List<InventoryHoldService.HoldInfo>> holdMap =
            new java.util.HashMap<>();

        for (String inventoryId : inventoryIds) {
            List<InventoryHoldService.HoldInfo> holds = holdService.getActiveHolds(inventoryId);
            if (!holds.isEmpty()) {
                holdMap.put(inventoryId, holds);
            }
        }

        return holdMap;
    }
}
