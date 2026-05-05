package com.wms.po.workflow.impl;

import com.wms.po.activity.*;
import com.wms.po.domain.exception.CancelledException;
import com.wms.po.domain.exception.ValidationException;
import com.wms.po.domain.model.*;
import com.wms.po.workflow.PopulatePOWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of PopulatePOWorkflow with full Saga pattern.
 *
 * Steps:
 * 1. Resolve Context (no compensation)
 * 2. Run Pre-Populate Plugins (no compensation)
 * 3. Validate (no compensation)
 * 4. Map PO to ASN (no compensation)
 * 5. Apply Lottables (no compensation)
 * 6. Create Receipt Header (WITH compensation: delete header)
 * 7. Create Receipt Details (WITH compensation: delete details)
 * 8. Create Inventory Reservations (WITH compensation: release reservations)
 * 9. Sync to Legacy (WITH compensation: rollback legacy)
 * 10. Send Notifications (best effort, no compensation)
 */
@Slf4j
public class PopulatePOWorkflowImpl implements PopulatePOWorkflow {

    // Workflow state for queries
    private WorkflowStatus status = WorkflowStatus.STARTED;
    private String currentStep = "INITIALIZING";
    private final List<String> completedSteps = new ArrayList<>();
    private boolean cancelRequested = false;
    private static final int TOTAL_STEPS = 10;
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

    private final PluginActivity pluginActivity = Workflow.newActivityStub(
        PluginActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(60))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .build())
            .build()
    );

    private final MappingActivity mappingActivity = Workflow.newActivityStub(
        MappingActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(60))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .build())
            .build()
    );

    private final PersistenceActivity persistenceActivity = Workflow.newActivityStub(
        PersistenceActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(2))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(5)
                .setInitialInterval(Duration.ofSeconds(2))
                .setBackoffCoefficient(2.0)
                .build())
            .build()
    );

    private final InventoryActivity inventoryActivity = Workflow.newActivityStub(
        InventoryActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(2))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(5)
                .build())
            .build()
    );

    private final LegacyBridgeActivity legacyActivity = Workflow.newActivityStub(
        LegacyBridgeActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(10)
                .setInitialInterval(Duration.ofSeconds(5))
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
    public PopulateResult populate(PopulateRequest request) {
        status = WorkflowStatus.RUNNING;

        // ═══════════════════════════════════════════════════════════════
        // SAGA SETUP - Define compensation options
        // ═══════════════════════════════════════════════════════════════
        Saga saga = new Saga(new Saga.Options.Builder()
            .setParallelCompensation(false)  // Run compensations sequentially
            .setContinueWithError(false)      // Stop on first compensation failure
            .build());

        try {
            // ═══════════════════════════════════════════════════════════════
            // STEP 1: Resolve Variation Context (No compensation needed)
            // ═══════════════════════════════════════════════════════════════
            updateStep("RESOLVE_CONTEXT");
            checkCancellation();

            VariationContext context = validationActivity.resolveContext(request);
            completeStep("RESOLVE_CONTEXT: " + context.getVersion() + "/" + context.getRegion());

            // ═══════════════════════════════════════════════════════════════
            // STEP 2: Run Pre-Populate Plugins (No compensation needed)
            // ═══════════════════════════════════════════════════════════════
            updateStep("PRE_PLUGINS");
            checkCancellation();

            PluginResult pluginResult = pluginActivity.runPrePopulate(request, context);
            if (!pluginResult.isShouldContinue()) {
                status = WorkflowStatus.FAILED;
                return PopulateResult.failed("Plugin stopped workflow: " + pluginResult.getReason());
            }
            completeStep("PRE_PLUGINS");

            // ═══════════════════════════════════════════════════════════════
            // STEP 3: Validate PO (No compensation needed)
            // ═══════════════════════════════════════════════════════════════
            updateStep("VALIDATION");
            checkCancellation();

            ValidationResult validation = validationActivity.validate(request, context);
            if (!validation.isValid()) {
                status = WorkflowStatus.FAILED;
                return PopulateResult.failed(validation.getErrors());
            }
            completeStep("VALIDATION");

            // ═══════════════════════════════════════════════════════════════
            // STEP 4: Map PO to ASN (No compensation needed - just transform)
            // ═══════════════════════════════════════════════════════════════
            updateStep("MAPPING");
            checkCancellation();

            MappingResult mapping = mappingActivity.mapPOToASN(request, context);
            completeStep("MAPPING: " + mapping.getDetails().size() + " lines");

            // ═══════════════════════════════════════════════════════════════
            // STEP 5: Apply Lottables (No compensation needed)
            // ═══════════════════════════════════════════════════════════════
            updateStep("LOTTABLES");
            checkCancellation();

            LottableResult lottables = mappingActivity.applyLottables(mapping, context);
            completeStep("LOTTABLES: " + lottables.getAppliedRules().size() + " rules applied");

            // ═══════════════════════════════════════════════════════════════
            // STEP 6: Create Receipt Header (WITH COMPENSATION)
            // ═══════════════════════════════════════════════════════════════
            updateStep("CREATE_HEADER");
            checkCancellation();

            String receiptKey = persistenceActivity.createReceiptHeader(mapping);

            // Register compensation: Delete header if later steps fail
            saga.addCompensation(() -> persistenceActivity.deleteReceiptHeader(receiptKey));

            completeStep("CREATE_HEADER: " + receiptKey);

            // ═══════════════════════════════════════════════════════════════
            // STEP 7: Create Receipt Details (WITH COMPENSATION)
            // ═══════════════════════════════════════════════════════════════
            updateStep("CREATE_DETAILS");
            checkCancellation();

            List<String> detailKeys = persistenceActivity.createReceiptDetails(
                receiptKey, mapping.getDetails());

            // Register compensation: Delete details if later steps fail
            saga.addCompensation(() -> persistenceActivity.deleteReceiptDetails(detailKeys));

            completeStep("CREATE_DETAILS: " + detailKeys.size() + " records");

            // ═══════════════════════════════════════════════════════════════
            // STEP 8: Create Inventory Reservations (WITH COMPENSATION)
            // ═══════════════════════════════════════════════════════════════
            updateStep("CREATE_RESERVATIONS");
            checkCancellation();

            List<String> reservationIds = inventoryActivity.createReservations(
                receiptKey, detailKeys);

            // Register compensation: Release reservations if later steps fail
            saga.addCompensation(() -> inventoryActivity.releaseReservations(reservationIds));

            completeStep("CREATE_RESERVATIONS: " + reservationIds.size() + " reservations");

            // ═══════════════════════════════════════════════════════════════
            // STEP 9: Sync to Legacy System (WITH COMPENSATION)
            // ═══════════════════════════════════════════════════════════════
            if (context.isDualWriteEnabled()) {
                updateStep("LEGACY_SYNC");
                checkCancellation();

                legacyActivity.syncToLegacy(receiptKey, context);

                // Register compensation: Rollback legacy if later steps fail
                saga.addCompensation(() -> legacyActivity.rollbackLegacy(receiptKey, context));

                completeStep("LEGACY_SYNC");
            } else {
                completeStep("LEGACY_SYNC: SKIPPED (dual-write disabled)");
            }

            // ═══════════════════════════════════════════════════════════════
            // STEP 10: Send Notifications (Best effort - no compensation)
            // ═══════════════════════════════════════════════════════════════
            updateStep("NOTIFICATION");

            try {
                notificationActivity.sendPopulationComplete(receiptKey, request);
                completeStep("NOTIFICATION");
            } catch (ActivityFailure e) {
                // Log but don't fail the workflow for notification errors
                completeStep("NOTIFICATION: SKIPPED (error: " + e.getMessage() + ")");
            }

            // ═══════════════════════════════════════════════════════════════
            // RUN POST-POPULATE PLUGINS
            // ═══════════════════════════════════════════════════════════════
            try {
                pluginActivity.runPostPopulate(receiptKey, request, context);
            } catch (Exception e) {
                // Post-populate plugins are best-effort
            }

            // ═══════════════════════════════════════════════════════════════
            // SUCCESS
            // ═══════════════════════════════════════════════════════════════
            status = WorkflowStatus.COMPLETED;
            currentStep = "COMPLETED";

            return PopulateResult.builder()
                .success(true)
                .receiptKey(receiptKey)
                .detailCount(detailKeys.size())
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

            return PopulateResult.cancelled("Workflow cancelled by user");

        } catch (Exception e) {
            // ═══════════════════════════════════════════════════════════════
            // ERROR - Run compensations in REVERSE order
            // ═══════════════════════════════════════════════════════════════
            status = WorkflowStatus.COMPENSATING;
            String failedStep = currentStep;
            currentStep = "COMPENSATING";

            // This runs compensations in reverse order:
            // Step 9 ↩ Step 8 ↩ Step 7 ↩ Step 6
            saga.compensate();

            status = WorkflowStatus.FAILED;
            currentStep = "FAILED";

            // Send failure notification (best effort)
            try {
                notificationActivity.sendPopulationFailed(null, e.getMessage(), request);
            } catch (Exception notifyError) {
                // Ignore notification errors
            }

            return PopulateResult.builder()
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
