package com.wms.po.workflow.impl;

import com.wms.po.activity.*;
import com.wms.po.domain.exception.CancelledException;
import com.wms.po.domain.exception.ValidationException;
import com.wms.po.domain.model.*;
import com.wms.po.workflow.FinalizeReceiptWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of FinalizeReceiptWorkflow with full Saga pattern.
 *
 * This workflow orchestrates the receipt finalization flow, equivalent to
 * the ispFinalizeReceipt stored procedure but with proper compensation.
 *
 * Steps with compensation:
 * 1. Validate receipt state → No compensation needed
 * 2. Run pre-finalize plugins → Rollback plugin effects
 * 3. Update status to "Finalizing" → Revert status
 * 4. Post inventory to LOTxLOCxID → Delete inventory records
 * 5. Apply inventory holds → Remove holds
 * 6. Update PO quantities → Revert quantities
 * 7. Release putaway tasks → Cancel tasks
 * 8. Run post-finalize plugins → Best effort, no compensation
 * 9. Update status to "Finalized" → No compensation (success path)
 */
@Slf4j
public class FinalizeReceiptWorkflowImpl implements FinalizeReceiptWorkflow {

    // Workflow state
    private WorkflowStatus status = WorkflowStatus.STARTED;
    private String currentStep = "INITIALIZING";
    private final List<String> completedSteps = new ArrayList<>();
    private boolean cancelRequested = false;
    private boolean pauseRequested = false;
    private boolean canStillCancel = true;
    private static final int TOTAL_STEPS = 9;
    private int completedStepCount = 0;
    private int totalLines = 0;
    private int processedLines = 0;
    private int successfulPosts = 0;
    private int failedPosts = 0;

    // Activity stubs - Validation with strict retry policy
    private final ValidationActivity validationActivity = Workflow.newActivityStub(
        ValidationActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .setInitialInterval(Duration.ofSeconds(1))
                .setDoNotRetry(ValidationException.class.getName())
                .build())
            .build()
    );

    // Receipt status updates
    private final ReceiptStatusActivity receiptStatusActivity = Workflow.newActivityStub(
        ReceiptStatusActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(5)
                .setInitialInterval(Duration.ofSeconds(1))
                .setBackoffCoefficient(2.0)
                .build())
            .build()
    );

    // Inventory posting - longer timeout for bulk operations
    private final InventoryPostingActivity inventoryPostingActivity = Workflow.newActivityStub(
        InventoryPostingActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .setInitialInterval(Duration.ofSeconds(2))
                .setBackoffCoefficient(2.0)
                .build())
            .build()
    );

    // Inventory holds
    private final InventoryHoldActivity inventoryHoldActivity = Workflow.newActivityStub(
        InventoryHoldActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(60))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .build())
            .build()
    );

    // PO quantity updates
    private final POQuantityActivity poQuantityActivity = Workflow.newActivityStub(
        POQuantityActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(60))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(5)
                .build())
            .build()
    );

    // Putaway task release
    private final PutawayReleaseActivity putawayReleaseActivity = Workflow.newActivityStub(
        PutawayReleaseActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(2))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .build())
            .build()
    );

    // Plugin execution
    private final FinalizePluginActivity finalizePluginActivity = Workflow.newActivityStub(
        FinalizePluginActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(2))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .build())
            .build()
    );

    // Notification (best effort)
    private final NotificationActivity notificationActivity = Workflow.newActivityStub(
        NotificationActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .build())
            .build()
    );

    // Demo mode: Add delays between steps for cancellation demo
    private static final boolean DEMO_MODE = true;
    private static final int DEMO_DELAY_SECONDS = 3;

    @Override
    public FinalizeResult finalize(FinalizeRequest request) {
        status = WorkflowStatus.RUNNING;
        String receiptKey = request.getReceiptKey();

        // Saga setup for compensation
        Saga saga = new Saga(new Saga.Options.Builder()
            .setParallelCompensation(false)  // Run compensations sequentially
            .setContinueWithError(false)      // Stop on first compensation failure
            .build());

        try {
            // ═══════════════════════════════════════════════════════════════
            // STEP 1: Resolve Variation Context
            // ═══════════════════════════════════════════════════════════════
            updateStep("RESOLVE_CONTEXT");
            demoDelay();  // Allow cancellation window
            checkCancellation();

            VariationContext context = validationActivity.resolveContext(
                PopulateRequest.builder()
                    .poKeys(List.of())
                    .facility(request.getFacility())
                    .storerKey(request.getStorerKey())
                    .userId(request.getUserId())
                    .build()
            );
            completeStep("RESOLVE_CONTEXT: " + context.getVersion() + "/" + context.getRegion());

            // ═══════════════════════════════════════════════════════════════
            // STEP 2: Validate Receipt State
            // ═══════════════════════════════════════════════════════════════
            updateStep("VALIDATE");
            demoDelay();
            checkCancellation();

            String currentStatus = receiptStatusActivity.validateForFinalization(receiptKey);
            completeStep("VALIDATE: Receipt status=" + currentStatus);

            // ═══════════════════════════════════════════════════════════════
            // STEP 3: Run Pre-Finalize Plugins
            // ═══════════════════════════════════════════════════════════════
            updateStep("PRE_PLUGINS");
            demoDelay();
            checkCancellation();

            PluginResult prePluginResult = finalizePluginActivity.runPreFinalizePlugins(request, context);
            if (!prePluginResult.isShouldContinue()) {
                status = WorkflowStatus.FAILED;
                return FinalizeResult.failed(receiptKey, "Pre-finalize plugin stopped: " + prePluginResult.getReason());
            }
            completeStep("PRE_PLUGINS: " + prePluginResult.getPluginsExecuted() + " plugins");

            // Register compensation for pre-plugins if they made changes
            if (prePluginResult.getPluginsExecuted() > 0) {
                saga.addCompensation(() ->
                    finalizePluginActivity.rollbackPreFinalizePlugins(receiptKey,
                        prePluginResult.getExecutedPlugins()));
            }

            // ═══════════════════════════════════════════════════════════════
            // STEP 4: Update Status to "Finalizing" (WITH COMPENSATION)
            // ═══════════════════════════════════════════════════════════════
            updateStep("SET_STATUS_FINALIZING");
            checkCancellation();

            String previousStatus = receiptStatusActivity.setStatusFinalizing(receiptKey, request.getUserId());

            // Register compensation: Revert status if later steps fail
            saga.addCompensation(() ->
                receiptStatusActivity.revertStatus(receiptKey, previousStatus, request.getUserId()));

            completeStep("SET_STATUS_FINALIZING: " + previousStatus + " → 6");

            // After this point, we're committed - cannot cancel cleanly
            canStillCancel = false;

            // ═══════════════════════════════════════════════════════════════
            // STEP 5: Post Inventory to LOTxLOCxID (WITH COMPENSATION)
            // ═══════════════════════════════════════════════════════════════
            updateStep("POST_INVENTORY");
            waitIfPaused();

            // Build posting request from receipt details
            InventoryPostingActivity.PostingRequest postingRequest = buildPostingRequest(request);
            totalLines = postingRequest.getLines().size();

            InventoryPostingActivity.PostingResult postingResult =
                inventoryPostingActivity.postInventory(postingRequest);

            if (!postingResult.isSuccess()) {
                throw new RuntimeException("Inventory posting failed: " +
                    String.join(", ", postingResult.getErrors()));
            }

            successfulPosts = postingResult.getRecordsCreated();
            processedLines = totalLines;

            // Register compensation: Delete inventory records if later steps fail
            List<String> inventoryIds = postingResult.getInventoryIds();
            saga.addCompensation(() -> inventoryPostingActivity.deleteInventory(inventoryIds));

            completeStep("POST_INVENTORY: " + postingResult.getRecordsCreated() +
                " records, qty=" + postingResult.getTotalQuantity());

            // ═══════════════════════════════════════════════════════════════
            // STEP 6: Apply Inventory Holds (WITH COMPENSATION)
            // ═══════════════════════════════════════════════════════════════
            List<String> holdIds = new ArrayList<>();
            if (request.shouldApplyHolds()) {
                updateStep("APPLY_HOLDS");
                waitIfPaused();

                InventoryHoldActivity.HoldEvaluationRequest holdEvalRequest =
                    InventoryHoldActivity.HoldEvaluationRequest.builder()
                        .receiptKey(receiptKey)
                        .storerKey(request.getStorerKey())
                        .facility(request.getFacility())
                        .inventoryIds(inventoryIds)
                        .build();

                List<InventoryHoldActivity.HoldToApply> holdsToApply =
                    inventoryHoldActivity.evaluateHolds(holdEvalRequest);

                if (!holdsToApply.isEmpty()) {
                    InventoryHoldActivity.HoldApplicationRequest holdAppRequest =
                        InventoryHoldActivity.HoldApplicationRequest.builder()
                            .inventoryIds(inventoryIds)
                            .holds(holdsToApply)
                            .userId(request.getUserId())
                            .build();

                    InventoryHoldActivity.HoldResult holdResult =
                        inventoryHoldActivity.applyHolds(holdAppRequest);

                    holdIds = holdResult.getHoldIds();

                    // Register compensation: Remove holds
                    if (!holdIds.isEmpty()) {
                        List<String> appliedHoldIds = new ArrayList<>(holdIds);
                        saga.addCompensation(() ->
                            inventoryHoldActivity.removeHolds(appliedHoldIds, "Finalization rollback"));
                    }

                    completeStep("APPLY_HOLDS: " + holdResult.getHoldsApplied() + " holds");
                } else {
                    completeStep("APPLY_HOLDS: No holds required");
                }
            } else {
                completeStep("APPLY_HOLDS: Skipped (disabled)");
            }

            // ═══════════════════════════════════════════════════════════════
            // STEP 7: Update PO Received Quantities (WITH COMPENSATION)
            // ═══════════════════════════════════════════════════════════════
            updateStep("UPDATE_PO_QTY");
            waitIfPaused();

            POQuantityActivity.UpdateRequest qtyUpdateRequest = buildQuantityUpdateRequest(request, postingResult);
            POQuantityActivity.UpdateResult qtyResult =
                poQuantityActivity.updateReceivedQuantities(qtyUpdateRequest);

            if (!qtyResult.isSuccess()) {
                throw new RuntimeException("PO quantity update failed: " +
                    String.join(", ", qtyResult.getErrors()));
            }

            // Register compensation: Revert quantities
            List<POQuantityActivity.LineUpdate> appliedUpdates = qtyResult.getUpdates();
            saga.addCompensation(() -> poQuantityActivity.revertReceivedQuantities(appliedUpdates));

            completeStep("UPDATE_PO_QTY: " + qtyResult.getLinesUpdated() + " PO lines updated");

            // ═══════════════════════════════════════════════════════════════
            // STEP 8: Release Putaway Tasks (WITH COMPENSATION)
            // ═══════════════════════════════════════════════════════════════
            List<String> taskIds = new ArrayList<>();
            if (request.shouldReleasePutaway()) {
                updateStep("RELEASE_PUTAWAY");
                waitIfPaused();

                // Only release putaway if inventory is not blocked by holds
                boolean hasBlockingHolds = holdIds.stream().anyMatch(h -> true); // Simplified check

                if (!hasBlockingHolds) {
                    PutawayReleaseActivity.ReleaseRequest paRequest = buildPutawayRequest(
                        request, inventoryIds, postingResult);
                    PutawayReleaseActivity.ReleaseResult paResult =
                        putawayReleaseActivity.releasePutawayTasks(paRequest);

                    if (paResult.isSuccess()) {
                        taskIds = paResult.getTaskIds();

                        // Register compensation: Cancel putaway tasks
                        if (!taskIds.isEmpty()) {
                            List<String> createdTaskIds = new ArrayList<>(taskIds);
                            saga.addCompensation(() ->
                                putawayReleaseActivity.cancelPutawayTasks(createdTaskIds, "Finalization rollback"));
                        }

                        completeStep("RELEASE_PUTAWAY: " + paResult.getTasksCreated() + " tasks");
                    } else {
                        // Log warning but don't fail finalization
                        completeStep("RELEASE_PUTAWAY: Failed (non-fatal) - " +
                            String.join(", ", paResult.getErrors()));
                    }
                } else {
                    completeStep("RELEASE_PUTAWAY: Skipped (holds blocking)");
                }
            } else {
                completeStep("RELEASE_PUTAWAY: Skipped (disabled)");
            }

            // ═══════════════════════════════════════════════════════════════
            // STEP 9: Run Post-Finalize Plugins (Best Effort)
            // ═══════════════════════════════════════════════════════════════
            updateStep("POST_PLUGINS");

            Map<String, Object> finalizationData = new HashMap<>();
            finalizationData.put("inventoryIds", inventoryIds);
            finalizationData.put("holdIds", holdIds);
            finalizationData.put("putawayTaskIds", taskIds);
            finalizationData.put("totalQuantity", postingResult.getTotalQuantity());

            try {
                FinalizePluginActivity.PluginSummary postPluginSummary =
                    finalizePluginActivity.runPostFinalizePlugins(receiptKey, request, context, finalizationData);
                completeStep("POST_PLUGINS: " + postPluginSummary.getSuccessCount() + "/" +
                    postPluginSummary.getPluginsExecuted() + " successful");
            } catch (ActivityFailure e) {
                completeStep("POST_PLUGINS: Skipped (error: " + e.getMessage() + ")");
            }

            // ═══════════════════════════════════════════════════════════════
            // STEP 10: Update Status to "Finalized"
            // ═══════════════════════════════════════════════════════════════
            updateStep("SET_STATUS_FINALIZED");

            receiptStatusActivity.setStatusFinalized(receiptKey, request.getUserId());

            // Auto-close receipt if configured
            if (request.shouldAutoClose()) {
                receiptStatusActivity.closeReceipt(receiptKey, request.getUserId());
            }

            completeStep("SET_STATUS_FINALIZED: status=9" +
                (request.shouldAutoClose() ? " (closed)" : ""));

            // Check if PO should be closed
            String poKey = getPoKeyFromReceipt(receiptKey);
            if (poKey != null && poQuantityActivity.isFullyReceived(poKey)) {
                poQuantityActivity.closePO(poKey, request.getUserId());
                completeStep("CLOSE_PO: " + poKey + " fully received");
            }

            // ═══════════════════════════════════════════════════════════════
            // SUCCESS
            // ═══════════════════════════════════════════════════════════════
            status = WorkflowStatus.COMPLETED;
            currentStep = "COMPLETED";

            // Send success notification
            try {
                notificationActivity.sendFinalizeComplete(receiptKey, request);
            } catch (Exception e) {
                // Ignore notification errors
            }

            return FinalizeResult.builder()
                .success(true)
                .receiptKey(receiptKey)
                .finalStatus("9")
                .workflowId(Workflow.getInfo().getWorkflowId())
                .workflowStatus(WorkflowStatus.COMPLETED)
                .linesFinalizedCount(totalLines)
                .totalQuantityPosted(postingResult.getTotalQuantity())
                .inventoryRecordsCreated(postingResult.getRecordsCreated())
                .putawayTasksReleased(taskIds.size())
                .holdsApplied(holdIds)
                .updatedPOLines(appliedUpdates.stream()
                    .map(u -> u.getPoLineNumber() + ":" + u.getSku())
                    .collect(Collectors.toList()))
                .build();

        } catch (CancelledException e) {
            // ═══════════════════════════════════════════════════════════════
            // CANCELLATION - Run compensations
            // ═══════════════════════════════════════════════════════════════
            status = WorkflowStatus.COMPENSATING;
            currentStep = "COMPENSATING";

            saga.compensate();

            status = WorkflowStatus.CANCELLED;
            currentStep = "CANCELLED";

            return FinalizeResult.cancelled(receiptKey, "Workflow cancelled by user");

        } catch (Exception e) {
            // ═══════════════════════════════════════════════════════════════
            // ERROR - Run compensations in reverse order
            // ═══════════════════════════════════════════════════════════════
            status = WorkflowStatus.COMPENSATING;
            String failedStep = currentStep;
            currentStep = "COMPENSATING";

            saga.compensate();

            status = WorkflowStatus.FAILED;
            currentStep = "FAILED";

            // Send failure notification
            try {
                notificationActivity.sendFinalizeFailed(receiptKey, e.getMessage(), request);
            } catch (Exception notifyError) {
                // Ignore notification errors
            }

            return FinalizeResult.builder()
                .success(false)
                .receiptKey(receiptKey)
                .errors(List.of("Finalization failed at " + failedStep + ": " + e.getMessage()))
                .workflowId(Workflow.getInfo().getWorkflowId())
                .workflowStatus(WorkflowStatus.FAILED)
                .build();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private InventoryPostingActivity.PostingRequest buildPostingRequest(FinalizeRequest request) {
        // In real implementation, this would query receipt details
        // For now, return a minimal request
        return InventoryPostingActivity.PostingRequest.builder()
            .receiptKey(request.getReceiptKey())
            .storerKey(request.getStorerKey())
            .facility(request.getFacility())
            .userId(request.getUserId())
            .lines(new ArrayList<>())
            .build();
    }

    private POQuantityActivity.UpdateRequest buildQuantityUpdateRequest(
            FinalizeRequest request,
            InventoryPostingActivity.PostingResult postingResult) {
        // In real implementation, this would map posted inventory to PO lines
        return POQuantityActivity.UpdateRequest.builder()
            .receiptKey(request.getReceiptKey())
            .userId(request.getUserId())
            .lineUpdates(new ArrayList<>())
            .build();
    }

    private PutawayReleaseActivity.ReleaseRequest buildPutawayRequest(
            FinalizeRequest request,
            List<String> inventoryIds,
            InventoryPostingActivity.PostingResult postingResult) {
        return PutawayReleaseActivity.ReleaseRequest.builder()
            .receiptKey(request.getReceiptKey())
            .storerKey(request.getStorerKey())
            .facility(request.getFacility())
            .userId(request.getUserId())
            .inventoryItems(new ArrayList<>())
            .build();
    }

    private String getPoKeyFromReceipt(String receiptKey) {
        // In real implementation, this would query the receipt
        return null;
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

    private void waitIfPaused() {
        while (pauseRequested && !cancelRequested) {
            Workflow.sleep(Duration.ofSeconds(1));
        }
        checkCancellation();
    }

    /**
     * Demo delay for cancellation demonstration.
     * In production, set DEMO_MODE = false.
     */
    private void demoDelay() {
        if (DEMO_MODE) {
            Workflow.sleep(Duration.ofSeconds(DEMO_DELAY_SECONDS));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Signal and Query Methods
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void pause() {
        this.pauseRequested = true;
    }

    @Override
    public void resume() {
        this.pauseRequested = false;
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

    @Override
    public boolean canCancel() {
        return canStillCancel && !cancelRequested;
    }

    @Override
    public InventoryProgress getInventoryProgress() {
        return new InventoryProgress(totalLines, processedLines, successfulPosts, failedPosts);
    }
}
