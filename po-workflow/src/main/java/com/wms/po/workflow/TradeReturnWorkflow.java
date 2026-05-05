package com.wms.po.workflow;

import com.wms.po.domain.model.TradeReturnRequest;
import com.wms.po.domain.model.TradeReturnResult;
import com.wms.po.domain.model.WorkflowStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.List;

/**
 * Workflow interface for Trade Return (ASN to Sales Order) population.
 *
 * Replaces:
 * - SP-004: WM.lsp_ASN_PopulateSOs_Wrapper
 * - SP-005: WM.lsp_ASN_PopulateSODs_Wrapper
 *
 * Orchestrates the creation of Sales Orders from Return ASNs with
 * Saga pattern for compensations.
 */
@WorkflowInterface
public interface TradeReturnWorkflow {

    /**
     * Main workflow method - orchestrates the ASN to SO population
     *
     * @param request Trade return request containing receipt/ASN to process
     * @return Result containing created order key and details
     */
    @WorkflowMethod
    TradeReturnResult populateSalesOrder(TradeReturnRequest request);

    /**
     * Signal to cancel the workflow mid-execution
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
     * Query list of completed steps
     */
    @QueryMethod
    List<String> getCompletedSteps();

    /**
     * Query progress percentage (0-100)
     */
    @QueryMethod
    int getProgress();
}
