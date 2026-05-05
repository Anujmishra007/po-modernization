package com.wms.po.domain.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result from Trade Return workflow.
 * Contains the created Sales Order information.
 *
 * Replaces: SP-004/SP-005 output parameters
 */
@Data
@Builder
public class TradeReturnResult {

    /**
     * Whether the workflow completed successfully
     */
    private boolean success;

    /**
     * Created Sales Order key
     */
    private String orderKey;

    /**
     * Original Receipt key processed
     */
    private String receiptKey;

    /**
     * Number of Sales Order details created
     */
    private int detailCount;

    /**
     * Workflow execution ID (for tracking)
     */
    private String workflowId;

    /**
     * Current workflow status
     */
    private WorkflowStatus status;

    /**
     * Total quantity on order
     */
    private java.math.BigDecimal totalQty;

    /**
     * Errors encountered during processing
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * Warnings (non-fatal issues)
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * Processing messages
     */
    @Builder.Default
    private List<String> messages = new ArrayList<>();

    /**
     * Create a successful result
     */
    public static TradeReturnResult success(String orderKey, String receiptKey, int detailCount) {
        return TradeReturnResult.builder()
            .success(true)
            .orderKey(orderKey)
            .receiptKey(receiptKey)
            .detailCount(detailCount)
            .status(WorkflowStatus.COMPLETED)
            .build();
    }

    /**
     * Create a failed result
     */
    public static TradeReturnResult failed(String error) {
        return TradeReturnResult.builder()
            .success(false)
            .status(WorkflowStatus.FAILED)
            .errors(List.of(error))
            .build();
    }

    /**
     * Create a failed result with multiple errors
     */
    public static TradeReturnResult failed(List<String> errors) {
        return TradeReturnResult.builder()
            .success(false)
            .status(WorkflowStatus.FAILED)
            .errors(new ArrayList<>(errors))
            .build();
    }

    /**
     * Create a cancelled result
     */
    public static TradeReturnResult cancelled(String reason) {
        return TradeReturnResult.builder()
            .success(false)
            .status(WorkflowStatus.CANCELLED)
            .messages(List.of(reason))
            .build();
    }
}
