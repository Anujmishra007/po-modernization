package com.wms.po.workflow.impl;

import com.wms.po.activity.NotificationActivity;
import com.wms.po.activity.TradeReturnActivity;
import com.wms.po.activity.ValidationActivity;
import com.wms.po.domain.exception.CancelledException;
import com.wms.po.domain.exception.ValidationException;
import com.wms.po.domain.model.TradeReturnRequest;
import com.wms.po.domain.model.TradeReturnResult;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.domain.model.WorkflowStatus;
import com.wms.po.workflow.TradeReturnWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of TradeReturnWorkflow with full Saga pattern.
 *
 * Replaces:
 * - SP-004: WM.lsp_ASN_PopulateSOs_Wrapper
 * - SP-005: WM.lsp_ASN_PopulateSODs_Wrapper
 *
 * Steps:
 * 1. Resolve Context (no compensation)
 * 2. Validate Trade Return (no compensation)
 * 3. Map Receipt to Sales Order (no compensation)
 * 4. Create SO Header (WITH compensation: delete header)
 * 5. Create SO Details (WITH compensation: delete details)
 * 6. Create Inventory Reservations (WITH compensation: release)
 * 7. Update Receipt Status (no compensation - best effort)
 * 8. Auto Release (optional, no compensation)
 * 9. Send Notifications (best effort)
 */
@Slf4j
public class TradeReturnWorkflowImpl implements TradeReturnWorkflow {

    // Workflow state for queries
    private WorkflowStatus status = WorkflowStatus.STARTED;
    private String currentStep = "INITIALIZING";
    private final List<String> completedSteps = new ArrayList<>();
    private boolean cancelRequested = false;
    private static final int TOTAL_STEPS = 9;
    private int completedStepCount = 0;

    // Activity stubs with retry configuration
    private final ValidationActivity validationActivity = Workflow.newActivityStub(
        ValidationActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .setInitialInterval(Duration.ofSeconds(1))
                .setBackoffCoefficient(2.0)
                .setDoNotRetry(ValidationException.class.getName())
                .build())
            .build()
    );

    private final TradeReturnActivity tradeReturnActivity = Workflow.newActivityStub(
        TradeReturnActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(2))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(5)
                .setInitialInterval(Duration.ofSeconds(2))
                .setBackoffCoefficient(2.0)
                .build())
            .build()
    );

    private final NotificationActivity notificationActivity = Workflow.newActivityStub(
        NotificationActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .build())
            .build()
    );

    @Override
    public TradeReturnResult populateSalesOrder(TradeReturnRequest request) {
        status = WorkflowStatus.RUNNING;

        // ═══════════════════════════════════════════════════════════════
        // SAGA SETUP - Define compensation options
        // ═══════════════════════════════════════════════════════════════
        Saga saga = new Saga(new Saga.Options.Builder()
            .setParallelCompensation(false)
            .setContinueWithError(false)
            .build());

        try {
            // ═══════════════════════════════════════════════════════════════
            // STEP 1: Resolve Variation Context
            // ═══════════════════════════════════════════════════════════════
            updateStep("RESOLVE_CONTEXT");
            checkCancellation();

            VariationContext context = validationActivity.resolveTradeReturnContext(request);
            completeStep("RESOLVE_CONTEXT: " + context.getVersion() + "/" + context.getRegion());

            // ═══════════════════════════════════════════════════════════════
            // STEP 2: Validate Trade Return Request
            // ═══════════════════════════════════════════════════════════════
            updateStep("VALIDATION");
            checkCancellation();

            TradeReturnActivity.TradeReturnValidationResult validation =
                tradeReturnActivity.validateTradeReturn(request, context);
            if (!validation.valid()) {
                status = WorkflowStatus.FAILED;
                return TradeReturnResult.failed(validation.errors());
            }
            completeStep("VALIDATION");

            // ═══════════════════════════════════════════════════════════════
            // STEP 3: Map Receipt to Sales Order
            // ═══════════════════════════════════════════════════════════════
            updateStep("MAPPING");
            checkCancellation();

            TradeReturnActivity.TradeReturnMappingResult mapping =
                tradeReturnActivity.mapReceiptToSalesOrder(request, context);
            completeStep("MAPPING: " + mapping.lines().size() + " lines, qty=" + mapping.totalQty());

            // ═══════════════════════════════════════════════════════════════
            // STEP 4: Create Sales Order Header (WITH COMPENSATION)
            // ═══════════════════════════════════════════════════════════════
            updateStep("CREATE_HEADER");
            checkCancellation();

            String orderKey = tradeReturnActivity.createSalesOrderHeader(mapping);

            // Register compensation: Delete header if later steps fail
            saga.addCompensation(() -> tradeReturnActivity.deleteSalesOrderHeader(orderKey));

            completeStep("CREATE_HEADER: " + orderKey);

            // ═══════════════════════════════════════════════════════════════
            // STEP 5: Create Sales Order Details (WITH COMPENSATION)
            // ═══════════════════════════════════════════════════════════════
            updateStep("CREATE_DETAILS");
            checkCancellation();

            List<String> detailKeys = tradeReturnActivity.createSalesOrderDetails(
                orderKey, mapping.lines());

            // Register compensation: Delete details if later steps fail
            saga.addCompensation(() -> tradeReturnActivity.deleteSalesOrderDetails(detailKeys));

            completeStep("CREATE_DETAILS: " + detailKeys.size() + " records");

            // ═══════════════════════════════════════════════════════════════
            // STEP 6: Create Inventory Reservations (WITH COMPENSATION)
            // ═══════════════════════════════════════════════════════════════
            updateStep("CREATE_RESERVATIONS");
            checkCancellation();

            List<String> reservationIds = tradeReturnActivity.createInventoryReservations(
                orderKey, detailKeys);

            // Register compensation: Release reservations if later steps fail
            saga.addCompensation(() -> tradeReturnActivity.releaseReservations(reservationIds));

            completeStep("CREATE_RESERVATIONS: " + reservationIds.size() + " reservations");

            // ═══════════════════════════════════════════════════════════════
            // STEP 7: Update Receipt Status
            // ═══════════════════════════════════════════════════════════════
            updateStep("UPDATE_RECEIPT");

            try {
                tradeReturnActivity.updateReceiptStatus(request.getReceiptKey(), orderKey);
                completeStep("UPDATE_RECEIPT");
            } catch (ActivityFailure e) {
                completeStep("UPDATE_RECEIPT: SKIPPED (error: " + e.getMessage() + ")");
            }

            // ═══════════════════════════════════════════════════════════════
            // STEP 8: Auto Release (Optional)
            // ═══════════════════════════════════════════════════════════════
            if (request.isAutoRelease()) {
                updateStep("AUTO_RELEASE");
                try {
                    tradeReturnActivity.autoReleaseOrder(orderKey, context);
                    completeStep("AUTO_RELEASE");
                } catch (ActivityFailure e) {
                    completeStep("AUTO_RELEASE: SKIPPED (error: " + e.getMessage() + ")");
                }
            } else {
                completeStep("AUTO_RELEASE: SKIPPED (not requested)");
            }

            // ═══════════════════════════════════════════════════════════════
            // STEP 9: Send Notifications
            // ═══════════════════════════════════════════════════════════════
            updateStep("NOTIFICATION");

            try {
                notificationActivity.sendTradeReturnComplete(orderKey, request.getReceiptKey());
                completeStep("NOTIFICATION");
            } catch (ActivityFailure e) {
                completeStep("NOTIFICATION: SKIPPED (error: " + e.getMessage() + ")");
            }

            // ═══════════════════════════════════════════════════════════════
            // SUCCESS
            // ═══════════════════════════════════════════════════════════════
            status = WorkflowStatus.COMPLETED;
            currentStep = "COMPLETED";

            return TradeReturnResult.builder()
                .success(true)
                .orderKey(orderKey)
                .receiptKey(request.getReceiptKey())
                .detailCount(detailKeys.size())
                .totalQty(mapping.totalQty())
                .workflowId(Workflow.getInfo().getWorkflowId())
                .status(WorkflowStatus.COMPLETED)
                .build();

        } catch (CancelledException e) {
            // ═══════════════════════════════════════════════════════════════
            // CANCELLATION REQUESTED - Run compensations
            // ═══════════════════════════════════════════════════════════════
            status = WorkflowStatus.COMPENSATING;
            currentStep = "COMPENSATING";

            saga.compensate();

            status = WorkflowStatus.CANCELLED;
            currentStep = "CANCELLED";

            return TradeReturnResult.cancelled("Workflow cancelled by user");

        } catch (Exception e) {
            // ═══════════════════════════════════════════════════════════════
            // ERROR - Run compensations in REVERSE order
            // ═══════════════════════════════════════════════════════════════
            status = WorkflowStatus.COMPENSATING;
            String failedStep = currentStep;
            currentStep = "COMPENSATING";

            saga.compensate();

            status = WorkflowStatus.FAILED;
            currentStep = "FAILED";

            return TradeReturnResult.builder()
                .success(false)
                .errors(List.of("Workflow failed at " + failedStep + ": " + e.getMessage()))
                .workflowId(Workflow.getInfo().getWorkflowId())
                .status(WorkflowStatus.FAILED)
                .build();
        }
    }

    private void updateStep(String step) {
        if (cancelRequested) {
            throw new CancelledException("Cancellation requested");
        }
        currentStep = step;
    }

    private void completeStep(String stepInfo) {
        completedSteps.add(stepInfo);
        completedStepCount++;
    }

    private void checkCancellation() {
        if (cancelRequested) {
            throw new CancelledException("Cancellation requested");
        }
    }

    @Override
    public void cancel() {
        this.cancelRequested = true;
    }

    @Override
    public WorkflowStatus getStatus() {
        return status;
    }

    @Override
    public String getCurrentStep() {
        return currentStep;
    }

    @Override
    public List<String> getCompletedSteps() {
        return new ArrayList<>(completedSteps);
    }

    @Override
    public int getProgress() {
        return (completedStepCount * 100) / TOTAL_STEPS;
    }
}
