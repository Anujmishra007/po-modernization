package com.wms.po.workflow;

import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.FinalizeResult;
import com.wms.po.domain.model.WorkflowStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.List;

/**
 * Workflow interface for Receipt Finalization.
 *
 * This workflow implements the ispFinalizeReceipt stored procedure logic
 * using the Saga pattern for compensation.
 *
 * Steps:
 * 1. Validate receipt state (must be status 0-5)
 * 2. Run pre-finalize plugins (ispPRREC*)
 * 3. Update receipt status to "In Progress" (status 6)
 * 4. Calculate received quantities and variances
 * 5. Post inventory to LOTxLOCxID
 * 6. Apply inventory holds if configured
 * 7. Update PO received quantities (PODETAIL.QTYRECEIVED)
 * 8. Generate putaway tasks
 * 9. Run post-finalize plugins (ispASNFZ*)
 * 10. Update receipt to final status (status 9)
 * 11. Optionally close PO if all lines received
 *
 * Compensation points:
 * - Step 3: Revert status back to original
 * - Step 5: Delete inventory records
 * - Step 6: Remove holds
 * - Step 7: Revert PO quantities
 * - Step 8: Cancel putaway tasks
 */
@WorkflowInterface
public interface FinalizeReceiptWorkflow {

    /**
     * Main workflow method - orchestrates the entire finalization flow.
     * Equivalent to ispFinalizeReceipt stored procedure.
     *
     * @param request Finalization request containing receiptKey and options
     * @return Result with finalization details or errors
     */
    @WorkflowMethod
    FinalizeResult finalize(FinalizeRequest request);

    /**
     * Signal to pause the workflow (if not yet past point of no return)
     */
    @SignalMethod
    void pause();

    /**
     * Signal to resume a paused workflow
     */
    @SignalMethod
    void resume();

    /**
     * Signal to cancel the workflow (triggers compensation)
     */
    @SignalMethod
    void cancel();

    /**
     * Query current workflow status
     */
    @QueryMethod
    WorkflowStatus getStatus();

    /**
     * Query which step we're currently on
     */
    @QueryMethod
    String getCurrentStep();

    /**
     * Query list of completed steps with details
     */
    @QueryMethod
    List<String> getCompletedSteps();

    /**
     * Query progress percentage (0-100)
     */
    @QueryMethod
    int getProgress();

    /**
     * Query whether the workflow can still be cancelled
     */
    @QueryMethod
    boolean canCancel();

    /**
     * Query current inventory posting progress
     */
    @QueryMethod
    InventoryProgress getInventoryProgress();

    /**
     * Progress information for inventory posting
     */
    record InventoryProgress(
        int totalLines,
        int processedLines,
        int successfulPosts,
        int failedPosts
    ) {
        public int percentComplete() {
            return totalLines > 0 ? (processedLines * 100) / totalLines : 0;
        }
    }
}
