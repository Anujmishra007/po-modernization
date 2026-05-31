package com.wms.po.api.service;

import com.wms.po.api.controller.FinalizeController.WorkflowStatusResponse;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.FinalizeResult;
import com.wms.po.domain.model.WorkflowStatus;
import com.wms.po.workflow.FinalizeReceiptWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowException;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Service for starting and managing receipt finalization workflows.
 *
 * Maps to legacy SPs:
 * - SP-003: WM.lsp_FinalizeReceipt_Wrapper (error codes 68900-68928)
 *
 * Error codes:
 * - INT_003 (69002) - Temporal Workflow Failed
 * - INT_004 (69003) - Temporal Activity Failed
 * - RCV_020 (68920) - Finalize Validation Failed
 *
 * Provides both synchronous and asynchronous finalization operations,
 * as well as workflow lifecycle management (status, cancel, pause, resume).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptFinalizationService {

    private final WorkflowClient workflowClient;

    @Value("${temporal.finalize-task-queue:po-finalize-queue}")
    private String taskQueue;

    @Value("${temporal.workflow.finalize-timeout-minutes:60}")
    private int workflowTimeoutMinutes;

    /**
     * Start finalization workflow (synchronous - wait for result).
     *
     * Error codes:
     * - INT_003 (69002) - Temporal Workflow Failed
     * - RCV_020 (68920) - Finalize Validation Failed
     *
     * @param request Finalization request
     * @return Finalization result
     */
    public FinalizeResult finalize(FinalizeRequest request) {
        String workflowId = generateWorkflowId(request);

        log.info("Starting synchronous finalize workflow: {} for receipt {}",
            workflowId, request.getReceiptKey());

        try {
            FinalizeReceiptWorkflow workflow = workflowClient.newWorkflowStub(
                FinalizeReceiptWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(taskQueue)
                    .setWorkflowId(workflowId)
                    .setWorkflowExecutionTimeout(Duration.ofMinutes(workflowTimeoutMinutes))
                    .build()
            );

            // Execute workflow and wait for result
            FinalizeResult result = workflow.finalize(request);

            log.info("Workflow completed: {} - success={}, status={}",
                workflowId, result.isSuccess(), result.getFinalStatus());

            return result;

        } catch (WorkflowException e) {
            log.error("Finalize workflow failed: {} - {} (legacy error 69002)",
                workflowId, e.getMessage(), e);
            throw BusinessException.temporalWorkflowFailed(workflowId, e);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to finalize receipt {}: {} (legacy error 68920)",
                request.getReceiptKey(), e.getMessage(), e);
            throw BusinessException.finalizeValidationFailed(request.getReceiptKey(), e.getMessage());
        }
    }

    /**
     * Start finalization workflow (asynchronous - return immediately).
     *
     * @param request Finalization request
     * @return Workflow ID for status polling
     */
    public String finalizeAsync(FinalizeRequest request) {
        String workflowId = generateWorkflowId(request);

        log.info("Starting asynchronous finalize workflow: {} for receipt {}",
            workflowId, request.getReceiptKey());

        FinalizeReceiptWorkflow workflow = workflowClient.newWorkflowStub(
            FinalizeReceiptWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(taskQueue)
                .setWorkflowId(workflowId)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(workflowTimeoutMinutes))
                .build()
        );

        // Start workflow asynchronously
        WorkflowClient.start(workflow::finalize, request);

        log.info("Workflow started asynchronously: {}", workflowId);

        return workflowId;
    }

    /**
     * Query workflow status.
     *
     * @param workflowId Workflow ID
     * @return Workflow status response
     */
    public WorkflowStatusResponse getStatus(String workflowId) {
        log.debug("Getting status for workflow: {}", workflowId);

        try {
            FinalizeReceiptWorkflow workflow = workflowClient.newWorkflowStub(
                FinalizeReceiptWorkflow.class, workflowId);

            return new WorkflowStatusResponse(
                workflowId,
                workflow.getStatus(),
                workflow.getCurrentStep(),
                workflow.getCompletedSteps(),
                workflow.getProgress(),
                workflow.canCancel()
            );
        } catch (Exception e) {
            log.warn("Failed to get workflow status: {}", e.getMessage());
            return new WorkflowStatusResponse(
                workflowId,
                WorkflowStatus.FAILED,
                "UNKNOWN",
                List.of(),
                0,
                false
            );
        }
    }

    /**
     * Get inventory posting progress for a running workflow.
     *
     * @param workflowId Workflow ID
     * @return Inventory progress
     */
    public FinalizeReceiptWorkflow.InventoryProgress getInventoryProgress(String workflowId) {
        log.debug("Getting inventory progress for workflow: {}", workflowId);

        try {
            FinalizeReceiptWorkflow workflow = workflowClient.newWorkflowStub(
                FinalizeReceiptWorkflow.class, workflowId);

            return workflow.getInventoryProgress();
        } catch (Exception e) {
            log.warn("Failed to get inventory progress: {}", e.getMessage());
            return new FinalizeReceiptWorkflow.InventoryProgress(0, 0, 0, 0);
        }
    }

    /**
     * Check if a workflow can still be cancelled.
     *
     * @param workflowId Workflow ID
     * @return true if cancellation is possible
     */
    public boolean canCancel(String workflowId) {
        try {
            FinalizeReceiptWorkflow workflow = workflowClient.newWorkflowStub(
                FinalizeReceiptWorkflow.class, workflowId);

            return workflow.canCancel();
        } catch (Exception e) {
            log.warn("Failed to check cancel status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Cancel a running workflow.
     *
     * Error codes:
     * - INT_003 (69002) - Temporal Workflow Failed
     *
     * @param workflowId Workflow ID
     */
    public void cancel(String workflowId) {
        log.info("Cancelling workflow: {}", workflowId);

        try {
            FinalizeReceiptWorkflow workflow = workflowClient.newWorkflowStub(
                FinalizeReceiptWorkflow.class, workflowId);

            workflow.cancel();

            log.info("Cancel signal sent to workflow: {}", workflowId);
        } catch (WorkflowNotFoundException e) {
            log.error("Workflow not found for cancellation: {} (legacy error 69002)", workflowId);
            throw new BusinessException(ErrorCode.TEMPORAL_WORKFLOW_FAILED,
                "Workflow not found: " + workflowId)
                .withDetail("workflowId", workflowId);
        } catch (Exception e) {
            log.error("Failed to cancel workflow: {} (legacy error 69002)", e.getMessage());
            throw BusinessException.temporalWorkflowFailed(workflowId, e);
        }
    }

    /**
     * Pause a running workflow.
     *
     * Error codes:
     * - INT_003 (69002) - Temporal Workflow Failed
     *
     * @param workflowId Workflow ID
     */
    public void pause(String workflowId) {
        log.info("Pausing workflow: {}", workflowId);

        try {
            FinalizeReceiptWorkflow workflow = workflowClient.newWorkflowStub(
                FinalizeReceiptWorkflow.class, workflowId);

            workflow.pause();

            log.info("Pause signal sent to workflow: {}", workflowId);
        } catch (WorkflowNotFoundException e) {
            log.error("Workflow not found for pause: {} (legacy error 69002)", workflowId);
            throw new BusinessException(ErrorCode.TEMPORAL_WORKFLOW_FAILED,
                "Workflow not found: " + workflowId)
                .withDetail("workflowId", workflowId);
        } catch (Exception e) {
            log.error("Failed to pause workflow: {} (legacy error 69002)", e.getMessage());
            throw BusinessException.temporalWorkflowFailed(workflowId, e);
        }
    }

    /**
     * Resume a paused workflow.
     *
     * Error codes:
     * - INT_003 (69002) - Temporal Workflow Failed
     *
     * @param workflowId Workflow ID
     */
    public void resume(String workflowId) {
        log.info("Resuming workflow: {}", workflowId);

        try {
            FinalizeReceiptWorkflow workflow = workflowClient.newWorkflowStub(
                FinalizeReceiptWorkflow.class, workflowId);

            workflow.resume();

            log.info("Resume signal sent to workflow: {}", workflowId);
        } catch (WorkflowNotFoundException e) {
            log.error("Workflow not found for resume: {} (legacy error 69002)", workflowId);
            throw new BusinessException(ErrorCode.TEMPORAL_WORKFLOW_FAILED,
                "Workflow not found: " + workflowId)
                .withDetail("workflowId", workflowId);
        } catch (Exception e) {
            log.error("Failed to resume workflow: {} (legacy error 69002)", e.getMessage());
            throw BusinessException.temporalWorkflowFailed(workflowId, e);
        }
    }

    /**
     * Generate a unique workflow ID.
     */
    private String generateWorkflowId(FinalizeRequest request) {
        return String.format("finalize-%s-%s-%d",
            request.getStorerKey() != null ? request.getStorerKey() : "unknown",
            request.getReceiptKey(),
            System.currentTimeMillis()
        );
    }
}
