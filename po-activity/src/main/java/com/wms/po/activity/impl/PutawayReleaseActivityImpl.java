package com.wms.po.activity.impl;

import com.wms.po.activity.PutawayReleaseActivity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.service.PutawayTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of PutawayReleaseActivity.
 * Generates and releases putaway tasks for finalized inventory.
 *
 * Maps to legacy SPs:
 * - SP-006: WM.lsp_ASNReleasePATask_Wrapper (error code 69205)
 * - SP-061: nspPASTD (error codes 69200-69207)
 * - SP-070-078: ispPARL01-08 (error codes 69220-69227)
 *
 * Error codes:
 * - PA_001 (69200) - Putaway Strategy Not Found
 * - PA_002 (69201) - Putaway Location Not Found
 * - PA_003 (69202) - Putaway Location Full
 * - PA_005 (69204) - Putaway Task Creation Failed
 * - PA_006 (69205) - Putaway Task Release Failed
 * - RCV_024 (68924) - Finalize Putaway Release Failed
 */
@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class PutawayReleaseActivityImpl implements PutawayReleaseActivity {

    private final PutawayTaskService putawayTaskService;

    @Override
    @Transactional
    public ReleaseResult releasePutawayTasks(ReleaseRequest request) {
        log.info("Releasing putaway tasks for receipt {}: {} items",
            request.getReceiptKey(),
            request.getInventoryItems() != null ? request.getInventoryItems().size() : 0);

        if (request.getInventoryItems() == null || request.getInventoryItems().isEmpty()) {
            log.warn("No inventory items to create putaway tasks for receipt {}",
                request.getReceiptKey());
            return ReleaseResult.builder()
                .success(true)
                .taskIds(new ArrayList<>())
                .tasksCreated(0)
                .warnings(List.of("No inventory items provided"))
                .build();
        }

        List<String> warnings = new ArrayList<>();
        List<String> taskIds = new ArrayList<>();

        try {
            // Validate required fields
            validateReleaseRequest(request);

            // Convert to service request objects
            List<PutawayTaskService.PutawayTaskRequest> taskRequests = request.getInventoryItems().stream()
                .map(item -> convertToServiceRequest(request, item))
                .collect(Collectors.toList());

            // Create tasks in batch
            try {
                taskIds = putawayTaskService.createPutawayTasksBatch(taskRequests);
            } catch (Exception e) {
                log.error("Putaway task batch creation failed for receipt {}: {} (legacy error 69204)",
                    request.getReceiptKey(), e.getMessage(), e);
                throw BusinessException.putawayTaskCreateFailed(request.getReceiptKey(), e);
            }

            // Check for partial success
            if (taskIds.size() < request.getInventoryItems().size()) {
                String warning = String.format("Created %d of %d tasks",
                    taskIds.size(), request.getInventoryItems().size());
                warnings.add(warning);
                log.warn("Partial putaway release for receipt {}: {}", request.getReceiptKey(), warning);
            }

            log.info("Released {} putaway tasks for receipt {}", taskIds.size(), request.getReceiptKey());

            return ReleaseResult.builder()
                .success(true)
                .taskIds(taskIds)
                .tasksCreated(taskIds.size())
                .warnings(warnings)
                .build();

        } catch (BusinessException e) {
            // Re-throw BusinessExceptions as-is
            throw e;
        } catch (Exception e) {
            log.error("Failed to release putaway tasks for receipt {}: {} (legacy error 68924)",
                request.getReceiptKey(), e.getMessage(), e);
            throw BusinessException.finalizePutawayReleaseFailed(request.getReceiptKey(), e);
        }
    }

    /**
     * Validate release request.
     * Error codes:
     * - PA_002 (69201) - Putaway Location Not Found
     * - VAL_001 (69100) - Required Field Missing
     */
    private void validateReleaseRequest(ReleaseRequest request) {
        if (request.getStorerKey() == null || request.getStorerKey().isBlank()) {
            log.error("Missing storer key for putaway release - legacy error 69100");
            throw BusinessException.validationFailed("storerKey", "Storer key is required for putaway");
        }

        if (request.getFacility() == null || request.getFacility().isBlank()) {
            log.error("Missing facility for putaway release - legacy error 69100");
            throw BusinessException.validationFailed("facility", "Facility is required for putaway");
        }

        // Validate each inventory item
        for (InventoryItem item : request.getInventoryItems()) {
            if (item.getSku() == null || item.getSku().isBlank()) {
                log.error("Item missing SKU - legacy error 68813");
                throw BusinessException.skuNotFound("null");
            }

            if (item.getCurrentLocation() == null || item.getCurrentLocation().isBlank()) {
                log.error("Item {} missing current location - legacy error 69201",
                    item.getInventoryId());
                throw BusinessException.putawayLocationNotFound(request.getReceiptKey());
            }
        }
    }

    @Override
    @Transactional
    public void cancelPutawayTasks(List<String> taskIds, String reason) {
        log.warn("COMPENSATION: Cancelling {} putaway tasks - {}", taskIds.size(), reason);

        int cancelledCount = 0;
        int failedCount = 0;

        for (String taskId : taskIds) {
            try {
                boolean cancelled = putawayTaskService.cancelTask(taskId, "SYSTEM", reason);
                if (cancelled) {
                    cancelledCount++;
                } else {
                    failedCount++;
                    log.warn("Task {} could not be cancelled (may already be completed)", taskId);
                }
            } catch (Exception e) {
                failedCount++;
                log.error("Failed to cancel putaway task {}: {}", taskId, e.getMessage());
            }
        }

        log.info("COMPENSATION complete: Cancelled {}/{} putaway tasks ({} failed)",
            cancelledCount, taskIds.size(), failedCount);
    }

    /**
     * Convert activity inventory item to service task request.
     */
    private PutawayTaskService.PutawayTaskRequest convertToServiceRequest(
            ReleaseRequest request, InventoryItem item) {

        return PutawayTaskService.PutawayTaskRequest.builder()
            .storerKey(request.getStorerKey())
            .facility(request.getFacility())
            .sku(item.getSku())
            .quantity(item.getQuantity())
            .packKey(item.getPackKey())
            .uom(item.getUom())
            .fromLocation(item.getCurrentLocation() != null ? item.getCurrentLocation() : "RECV")
            .fromLicensePlate(item.getLicensePlate())
            .lotxlocxidKey(item.getInventoryId())
            .receiptKey(request.getReceiptKey())
            .userId(request.getUserId())
            .priority(request.getPriority())
            .lottable01(item.getLottable01())
            .lottable02(item.getLottable02())
            .lottable03(item.getLottable03())
            .build();
    }
}
