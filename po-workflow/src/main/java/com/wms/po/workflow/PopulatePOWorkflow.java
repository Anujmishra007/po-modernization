package com.wms.po.workflow;

import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.PopulateResult;
import com.wms.po.domain.model.WorkflowStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.List;

/**
 * Workflow interface for PO population with Saga pattern.
 * Orchestrates the entire PO to ASN/Receipt flow.
 */
@WorkflowInterface
public interface PopulatePOWorkflow {

    /**
     * Main workflow method - orchestrates the entire PO population flow
     */
    @WorkflowMethod
    PopulateResult populate(PopulateRequest request);

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
