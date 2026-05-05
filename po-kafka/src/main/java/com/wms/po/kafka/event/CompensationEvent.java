package com.wms.po.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Event for compensation/rollback operations.
 * Maps to legacy stored procedure rollback logic.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompensationEvent {

    private String compensationId;
    private String sagaId;
    private String originalEventId;
    private String compensationType;
    private CompensationStatus status;
    private Instant triggeredAt;
    private Instant completedAt;
    private String reason;

    // What to compensate
    private String targetAggregateId;
    private String targetAggregateType;
    private List<CompensationAction> actions;

    // Legacy mapping
    private String legacyRollbackProcedure;
    private Map<String, Object> legacyRollbackParams;

    public enum CompensationStatus {
        PENDING,
        EXECUTING,
        COMPLETED,
        FAILED,
        SKIPPED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompensationAction {
        private String actionId;
        private String actionType;
        private int order;
        private String targetTable;
        private String operation;      // DELETE, UPDATE, INSERT
        private Map<String, Object> originalValues;
        private Map<String, Object> compensationValues;
        private CompensationStatus status;
        private String error;

        // Legacy SP mapping
        private String legacyProcedure;
    }

    // Compensation types mapped to legacy SPs
    public static final String DELETE_RECEIPT_HEADER = "DELETE_RECEIPT_HEADER";      // Compensates: isp_Receipt
    public static final String DELETE_RECEIPT_DETAILS = "DELETE_RECEIPT_DETAILS";    // Compensates: isp_ReceiptDetail
    public static final String RELEASE_INVENTORY = "RELEASE_INVENTORY";              // Compensates: usp_AllocateInventory
    public static final String RESTORE_PO_STATUS = "RESTORE_PO_STATUS";              // Compensates: usp_UpdatePOStatus
    public static final String ROLLBACK_LOTXLOCXID = "ROLLBACK_LOTXLOCXID";          // Compensates: isp_LOTxLOCxID
    public static final String ROLLBACK_LEGACY_SYNC = "ROLLBACK_LEGACY_SYNC";        // Compensates: legacy dual-write
}
