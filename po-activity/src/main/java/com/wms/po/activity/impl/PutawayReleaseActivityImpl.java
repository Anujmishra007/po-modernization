package com.wms.po.activity.impl;

import com.wms.po.activity.PutawayReleaseActivity;
import com.wms.po.domain.service.PutawayTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of PutawayReleaseActivity.
 * Generates and releases putaway tasks for finalized inventory.
 *
 * Maps to:
 * - lsp_ASNReleasePATask_Wrapper
 * - ispPARL* series (putaway release variants)
 * - nspPASTD (putaway strategy)
 */
@Component
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
            log.warn("No inventory items to create putaway tasks for");
            return ReleaseResult.builder()
                .success(true)
                .taskIds(new ArrayList<>())
                .tasksCreated(0)
                .warnings(List.of("No inventory items provided"))
                .build();
        }

        List<String> warnings = new ArrayList<>();

        try {
            // Convert to service request objects
            List<PutawayTaskService.PutawayTaskRequest> taskRequests = request.getInventoryItems().stream()
                .map(item -> convertToServiceRequest(request, item))
                .collect(Collectors.toList());

            // Create tasks in batch
            List<String> taskIds = putawayTaskService.createPutawayTasksBatch(taskRequests);

            // Check for partial success
            if (taskIds.size() < request.getInventoryItems().size()) {
                warnings.add(String.format("Created %d of %d tasks",
                    taskIds.size(), request.getInventoryItems().size()));
            }

            log.info("Released {} putaway tasks", taskIds.size());

            return ReleaseResult.builder()
                .success(true)
                .taskIds(taskIds)
                .tasksCreated(taskIds.size())
                .warnings(warnings)
                .build();

        } catch (Exception e) {
            log.error("Failed to release putaway tasks: {}", e.getMessage(), e);
            return ReleaseResult.failed("Failed to release putaway tasks: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void cancelPutawayTasks(List<String> taskIds, String reason) {
        log.warn("COMPENSATION: Cancelling {} putaway tasks - {}", taskIds.size(), reason);

        int cancelledCount = putawayTaskService.cancelTasksBatch(taskIds, "SYSTEM", reason);

        log.info("COMPENSATION complete: Cancelled {}/{} putaway tasks", cancelledCount, taskIds.size());
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
