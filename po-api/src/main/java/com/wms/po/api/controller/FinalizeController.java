package com.wms.po.api.controller;

import com.wms.po.api.service.ReceiptFinalizationService;
import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.FinalizeResult;
import com.wms.po.domain.model.WorkflowStatus;
import com.wms.po.workflow.FinalizeReceiptWorkflow;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Receipt Finalization operations.
 * Disabled during E2E tests where mock controller handles requests.
 *
 * Provides endpoints to:
 * - Finalize receipts (sync and async)
 * - Query finalization workflow status
 * - Cancel/pause/resume workflows
 */
@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
@Slf4j
@Profile("!test & !e2e-test")
public class FinalizeController {

    private final ReceiptFinalizationService finalizationService;

    /**
     * Finalize a receipt synchronously.
     * Waits for the workflow to complete and returns the result.
     *
     * POST /api/v1/receipts/{receiptKey}/finalize
     */
    @PostMapping("/{receiptKey}/finalize")
    public ResponseEntity<FinalizeResult> finalize(
            @PathVariable String receiptKey,
            @RequestBody(required = false) FinalizeRequestDTO requestDto,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId,
            @RequestHeader(value = "X-Facility", required = false) String facility,
            @RequestHeader(value = "X-Storer-Key", required = false) String storerKey) {

        log.info("Finalize request: receiptKey={}, user={}", receiptKey, userId);

        FinalizeRequest request = buildRequest(receiptKey, requestDto, userId, facility, storerKey);
        FinalizeResult result = finalizationService.finalize(request);

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Finalize a receipt asynchronously.
     * Returns immediately with a workflow ID for status polling.
     *
     * POST /api/v1/receipts/{receiptKey}/finalize/async
     */
    @PostMapping("/{receiptKey}/finalize/async")
    public ResponseEntity<AsyncResponse> finalizeAsync(
            @PathVariable String receiptKey,
            @RequestBody(required = false) FinalizeRequestDTO requestDto,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId,
            @RequestHeader(value = "X-Facility", required = false) String facility,
            @RequestHeader(value = "X-Storer-Key", required = false) String storerKey) {

        log.info("Async finalize request: receiptKey={}", receiptKey);

        FinalizeRequest request = buildRequest(receiptKey, requestDto, userId, facility, storerKey);
        String workflowId = finalizationService.finalizeAsync(request);

        return ResponseEntity.accepted().body(new AsyncResponse(
            workflowId,
            "/api/v1/receipts/" + receiptKey + "/finalize/" + workflowId + "/status"
        ));
    }

    /**
     * Query finalization workflow status.
     *
     * GET /api/v1/receipts/{receiptKey}/finalize/{workflowId}/status
     */
    @GetMapping("/{receiptKey}/finalize/{workflowId}/status")
    public ResponseEntity<WorkflowStatusResponse> getStatus(
            @PathVariable String receiptKey,
            @PathVariable String workflowId) {

        WorkflowStatusResponse status = finalizationService.getStatus(workflowId);
        return ResponseEntity.ok(status);
    }

    /**
     * Query inventory posting progress for a running workflow.
     *
     * GET /api/v1/receipts/{receiptKey}/finalize/{workflowId}/progress
     */
    @GetMapping("/{receiptKey}/finalize/{workflowId}/progress")
    public ResponseEntity<ProgressResponse> getProgress(
            @PathVariable String receiptKey,
            @PathVariable String workflowId) {

        FinalizeReceiptWorkflow.InventoryProgress progress = finalizationService.getInventoryProgress(workflowId);
        return ResponseEntity.ok(new ProgressResponse(
            workflowId,
            progress.totalLines(),
            progress.processedLines(),
            progress.successfulPosts(),
            progress.failedPosts(),
            progress.percentComplete()
        ));
    }

    /**
     * Cancel a running finalization workflow.
     *
     * POST /api/v1/receipts/{receiptKey}/finalize/{workflowId}/cancel
     */
    @PostMapping("/{receiptKey}/finalize/{workflowId}/cancel")
    public ResponseEntity<CancelResponse> cancel(
            @PathVariable String receiptKey,
            @PathVariable String workflowId) {

        boolean canCancel = finalizationService.canCancel(workflowId);
        if (!canCancel) {
            return ResponseEntity.badRequest().body(new CancelResponse(
                false,
                "Workflow cannot be cancelled - past point of no return"
            ));
        }

        finalizationService.cancel(workflowId);
        return ResponseEntity.accepted().body(new CancelResponse(
            true,
            "Cancellation requested - compensation will run"
        ));
    }

    /**
     * Pause a running finalization workflow.
     *
     * POST /api/v1/receipts/{receiptKey}/finalize/{workflowId}/pause
     */
    @PostMapping("/{receiptKey}/finalize/{workflowId}/pause")
    public ResponseEntity<Void> pause(
            @PathVariable String receiptKey,
            @PathVariable String workflowId) {

        finalizationService.pause(workflowId);
        return ResponseEntity.accepted().build();
    }

    /**
     * Resume a paused finalization workflow.
     *
     * POST /api/v1/receipts/{receiptKey}/finalize/{workflowId}/resume
     */
    @PostMapping("/{receiptKey}/finalize/{workflowId}/resume")
    public ResponseEntity<Void> resume(
            @PathVariable String receiptKey,
            @PathVariable String workflowId) {

        finalizationService.resume(workflowId);
        return ResponseEntity.accepted().build();
    }

    // Helper to build FinalizeRequest from DTO and headers
    private FinalizeRequest buildRequest(
            String receiptKey,
            FinalizeRequestDTO dto,
            String userId,
            String facility,
            String storerKey) {

        FinalizeRequest.FinalizeRequestBuilder builder = FinalizeRequest.builder()
            .receiptKey(receiptKey)
            .userId(userId);

        if (dto != null) {
            builder.autoClose(dto.autoClose())
                   .releasePutaway(dto.releasePutaway())
                   .applyHolds(dto.applyHolds())
                   .varianceTolerance(dto.varianceTolerance())
                   .lineNumbers(dto.lineNumbers());

            if (dto.facility() != null) {
                builder.facility(dto.facility());
            }
            if (dto.storerKey() != null) {
                builder.storerKey(dto.storerKey());
            }
        }

        // Headers take precedence
        if (facility != null) {
            builder.facility(facility);
        }
        if (storerKey != null) {
            builder.storerKey(storerKey);
        }

        return builder.build();
    }

    // Request/Response DTOs
    public record FinalizeRequestDTO(
        String facility,
        String storerKey,
        Boolean autoClose,
        Boolean releasePutaway,
        Boolean applyHolds,
        java.math.BigDecimal varianceTolerance,
        List<Integer> lineNumbers
    ) {}

    public record AsyncResponse(String workflowId, String statusUrl) {}

    public record WorkflowStatusResponse(
        String workflowId,
        WorkflowStatus status,
        String currentStep,
        List<String> completedSteps,
        int progress,
        boolean canCancel
    ) {}

    public record ProgressResponse(
        String workflowId,
        int totalLines,
        int processedLines,
        int successfulPosts,
        int failedPosts,
        int percentComplete
    ) {}

    public record CancelResponse(boolean accepted, String message) {}
}
