package com.wms.po.service;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.dto.PopulateResponse;
import com.wms.po.workflow.PopulatePOWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowException;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Service for PO Population workflow management.
 *
 * Error codes:
 * - INT_003 (69002) - Temporal Workflow Failed
 * - ASN_008 (68808) - ASN Creation Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PopulationService {

    private final WorkflowClient workflowClient;

    private static final String TASK_QUEUE = "po-population-queue";

    /**
     * Start PO population workflow.
     *
     * Error codes:
     * - INT_003 (69002) - Temporal Workflow Failed
     * - ASN_008 (68808) - ASN Creation Failed
     */
    public PopulateResponse populatePOs(com.wms.po.dto.PopulateRequest dtoRequest) {
        String workflowId = "populate-po-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("Starting PopulatePOWorkflow with ID: {}", workflowId);

        try {
            WorkflowOptions options = WorkflowOptions.newBuilder()
                    .setTaskQueue(TASK_QUEUE)
                    .setWorkflowId(workflowId)
                    .setWorkflowExecutionTimeout(Duration.ofMinutes(30))
                    .build();

            PopulatePOWorkflow workflow = workflowClient.newWorkflowStub(PopulatePOWorkflow.class, options);

            // Convert DTO to domain model
            PopulateRequest domainRequest = PopulateRequest.builder()
                    .poKeys(dtoRequest.getPoKeys())
                    .storerKey(dtoRequest.getStorerKey())
                    .facility(dtoRequest.getFacility())
                    .userId(dtoRequest.getUserId())
                    .metadata(dtoRequest.getMetadata())
                    .skipValidation(dtoRequest.getSkipValidation())
                    .dryRun(dtoRequest.getDryRun())
                    .async(dtoRequest.getAsync())
                    .build();

            // Start workflow asynchronously
            WorkflowClient.start(workflow::populate, domainRequest);

            log.info("Workflow {} started successfully", workflowId);

            return PopulateResponse.builder()
                    .workflowId(workflowId)
                    .status("RUNNING")
                    .message("PO population workflow started successfully")
                    .build();

        } catch (WorkflowException e) {
            log.error("Failed to start populate workflow: {} (legacy error 69002)", e.getMessage(), e);
            throw BusinessException.temporalWorkflowFailed(workflowId, e);
        } catch (Exception e) {
            log.error("Failed to start PO population: {} (legacy error 68808)", e.getMessage(), e);
            throw new BusinessException(ErrorCode.ASN_CREATION_FAILED,
                "Failed to start PO population workflow: " + e.getMessage(), e)
                .withDetail("workflowId", workflowId)
                .withDetail("poKeys", dtoRequest.getPoKeys());
        }
    }

    /**
     * Get workflow status.
     *
     * Error codes:
     * - INT_003 (69002) - Temporal Workflow Failed (workflow not found)
     */
    public PopulateResponse getWorkflowStatus(String workflowId) {
        try {
            WorkflowStub workflowStub = workflowClient.newUntypedWorkflowStub(workflowId);

            // Try to get the result (non-blocking check)
            String status = "RUNNING";
            String receiptKey = null;
            String message = "Workflow is in progress";

            try {
                // This will throw if workflow is still running
                var result = workflowStub.getResult(com.wms.po.domain.model.PopulateResult.class);
                if (result.isSuccess()) {
                    status = "COMPLETED";
                    receiptKey = result.getReceiptKey();
                    message = "Workflow completed successfully";
                } else {
                    status = "FAILED";
                    message = "Workflow failed: " + String.join(", ", result.getErrors());
                }
            } catch (Exception e) {
                // Workflow still running or failed
                if (e.getMessage() != null && e.getMessage().contains("FAILED")) {
                    status = "FAILED";
                    message = "Workflow failed: " + e.getMessage();
                }
            }

            return PopulateResponse.builder()
                    .workflowId(workflowId)
                    .status(status)
                    .receiptKey(receiptKey)
                    .message(message)
                    .build();

        } catch (WorkflowNotFoundException e) {
            log.error("Workflow not found: {} (legacy error 69002)", workflowId);
            throw new BusinessException(ErrorCode.TEMPORAL_WORKFLOW_FAILED,
                "Workflow not found: " + workflowId)
                .withDetail("workflowId", workflowId);
        } catch (Exception e) {
            log.error("Error getting workflow status for {}: {} (legacy error 69002)",
                workflowId, e.getMessage());
            throw BusinessException.temporalWorkflowFailed(workflowId, e);
        }
    }
}
