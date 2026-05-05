package com.wms.po.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of receipt finalization workflow.
 * Contains summary of what was finalized and any issues.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalizeResult {

    private boolean success;
    private String receiptKey;
    private String finalStatus;
    private String workflowId;
    private WorkflowStatus workflowStatus;

    /**
     * Number of lines finalized
     */
    private int linesFinalizedCount;

    /**
     * Total quantity posted to inventory
     */
    private BigDecimal totalQuantityPosted;

    /**
     * Number of inventory records created (LOTxLOCxID)
     */
    private int inventoryRecordsCreated;

    /**
     * Number of putaway tasks released
     */
    private int putawayTasksReleased;

    /**
     * List of hold IDs applied
     */
    @Builder.Default
    private List<String> holdsApplied = new ArrayList<>();

    /**
     * Variances detected during finalization
     */
    @Builder.Default
    private List<VarianceInfo> variances = new ArrayList<>();

    /**
     * Errors that occurred
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * Warnings (non-fatal issues)
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * IDs of updated PO lines
     */
    @Builder.Default
    private List<String> updatedPOLines = new ArrayList<>();

    // Factory methods
    public static FinalizeResult success(String receiptKey, int lineCount, BigDecimal totalQty) {
        return FinalizeResult.builder()
            .success(true)
            .receiptKey(receiptKey)
            .finalStatus("9")
            .linesFinalizedCount(lineCount)
            .totalQuantityPosted(totalQty)
            .workflowStatus(WorkflowStatus.COMPLETED)
            .build();
    }

    public static FinalizeResult failed(String receiptKey, String error) {
        return FinalizeResult.builder()
            .success(false)
            .receiptKey(receiptKey)
            .errors(List.of(error))
            .workflowStatus(WorkflowStatus.FAILED)
            .build();
    }

    public static FinalizeResult failed(String receiptKey, List<String> errors) {
        return FinalizeResult.builder()
            .success(false)
            .receiptKey(receiptKey)
            .errors(errors)
            .workflowStatus(WorkflowStatus.FAILED)
            .build();
    }

    public static FinalizeResult cancelled(String receiptKey, String reason) {
        return FinalizeResult.builder()
            .success(false)
            .receiptKey(receiptKey)
            .errors(List.of(reason))
            .workflowStatus(WorkflowStatus.CANCELLED)
            .build();
    }

    /**
     * Information about quantity variance
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VarianceInfo {
        private int lineNumber;
        private String sku;
        private BigDecimal expectedQty;
        private BigDecimal receivedQty;
        private BigDecimal varianceQty;
        private BigDecimal variancePercent;
        private String varianceType; // OVER, SHORT, EXACT
    }
}
