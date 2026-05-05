package com.wms.po.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of PO population workflow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopulateResult {

    private boolean success;
    private String receiptKey;
    private int detailCount;
    private List<String> errors;
    private List<String> warnings;
    private String workflowId;
    private WorkflowStatus status;

    public static PopulateResult success(String receiptKey, int detailCount) {
        return PopulateResult.builder()
            .success(true)
            .receiptKey(receiptKey)
            .detailCount(detailCount)
            .status(WorkflowStatus.COMPLETED)
            .build();
    }

    public static PopulateResult failed(String error) {
        return PopulateResult.builder()
            .success(false)
            .errors(List.of(error))
            .status(WorkflowStatus.FAILED)
            .build();
    }

    public static PopulateResult failed(List<String> errors) {
        return PopulateResult.builder()
            .success(false)
            .errors(errors)
            .status(WorkflowStatus.FAILED)
            .build();
    }

    public static PopulateResult cancelled(String reason) {
        return PopulateResult.builder()
            .success(false)
            .errors(List.of(reason))
            .status(WorkflowStatus.CANCELLED)
            .build();
    }

    public static PopulateResult cancelled() {
        return PopulateResult.builder()
            .success(false)
            .status(WorkflowStatus.CANCELLED)
            .build();
    }
}
