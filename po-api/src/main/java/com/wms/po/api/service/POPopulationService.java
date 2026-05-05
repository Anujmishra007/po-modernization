package com.wms.po.api.service;

import com.wms.po.api.controller.PopulateController.WorkflowStatusResponse;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.PopulateResult;
import com.wms.po.domain.model.WorkflowStatus;
import com.wms.po.workflow.PopulatePOWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service for starting and managing PO population workflows.
 *
 * Error codes:
 * - INT_003 (69002) - Temporal Workflow Failed
 * - INT_004 (69003) - Temporal Activity Failed
 * - ASN_008 (68808) - ASN Creation Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class POPopulationService {

    private final WorkflowClient workflowClient;

    @Value("${temporal.task-queue:po-task-queue}")
    private String taskQueue;

    @Value("${temporal.workflow.timeout-minutes:30}")
    private int workflowTimeoutMinutes;

    /**
     * Start PO population workflow (synchronous - wait for result).
     *
     * Error codes:
     * - INT_003 (69002) - Temporal Workflow Failed
     * - ASN_008 (68808) - ASN Creation Failed
     */
    public PopulateResult populate(PopulateRequest request) {
        String workflowId = generateWorkflowId(request);

        log.info("Starting synchronous populate workflow: {}", workflowId);

        try {
            PopulatePOWorkflow workflow = workflowClient.newWorkflowStub(
                PopulatePOWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(taskQueue)
                    .setWorkflowId(workflowId)
                    .setWorkflowExecutionTimeout(Duration.ofMinutes(workflowTimeoutMinutes))
                    .build()
            );

            // Execute workflow and wait for result
            PopulateResult result = workflow.populate(request);

            log.info("Workflow completed: {} - success={}, receiptKey={}",
                workflowId, result.isSuccess(), result.getReceiptKey());

            return result;

        } catch (WorkflowException e) {
            log.error("Populate workflow failed: {} - {} (legacy error 69002)",
                workflowId, e.getMessage(), e);
            throw BusinessException.temporalWorkflowFailed(workflowId, e);
        } catch (Exception e) {
            log.error("Failed to start populate workflow: {} (legacy error 68808)",
                e.getMessage(), e);
            throw new BusinessException(ErrorCode.ASN_CREATION_FAILED,
                "Failed to populate POs: " + e.getMessage(), e)
                .withDetail("workflowId", workflowId)
                .withDetail("poKeys", request.getPoKeys());
        }
    }

    /**
     * Start PO population workflow (asynchronous - return immediately)
     */
    public String populateAsync(PopulateRequest request) {
        String workflowId = generateWorkflowId(request);

        log.info("Starting asynchronous populate workflow: {}", workflowId);

        PopulatePOWorkflow workflow = workflowClient.newWorkflowStub(
            PopulatePOWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(taskQueue)
                .setWorkflowId(workflowId)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(workflowTimeoutMinutes))
                .build()
        );

        // Start workflow asynchronously
        WorkflowClient.start(workflow::populate, request);

        log.info("Workflow started asynchronously: {}", workflowId);

        return workflowId;
    }

    /**
     * Query workflow status
     */
    public WorkflowStatusResponse getStatus(String workflowId) {
        log.debug("Getting status for workflow: {}", workflowId);

        try {
            PopulatePOWorkflow workflow = workflowClient.newWorkflowStub(
                PopulatePOWorkflow.class, workflowId);

            return new WorkflowStatusResponse(
                workflowId,
                workflow.getStatus(),
                workflow.getCurrentStep(),
                workflow.getCompletedSteps(),
                workflow.getProgress()
            );
        } catch (Exception e) {
            log.warn("Failed to get workflow status: {}", e.getMessage());
            return new WorkflowStatusResponse(
                workflowId,
                WorkflowStatus.FAILED,
                "UNKNOWN",
                java.util.List.of(),
                0
            );
        }
    }

    /**
     * Cancel running workflow.
     *
     * Error codes:
     * - INT_003 (69002) - Temporal Workflow Failed
     */
    public void cancel(String workflowId) {
        log.info("Cancelling workflow: {}", workflowId);

        try {
            PopulatePOWorkflow workflow = workflowClient.newWorkflowStub(
                PopulatePOWorkflow.class, workflowId);

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
     * Generate a unique workflow ID
     */
    private String generateWorkflowId(PopulateRequest request) {
        String poKeyPart = request.getPoKeys().isEmpty() ? "unknown" : request.getPoKeys().get(0);
        return String.format("populate-%s-%s-%d",
            request.getStorerKey(),
            poKeyPart,
            System.currentTimeMillis()
        );
    }
}
