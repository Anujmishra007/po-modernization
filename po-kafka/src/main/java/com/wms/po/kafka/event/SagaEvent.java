package com.wms.po.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Event for saga coordination and distributed transaction management.
 * Maps to legacy stored procedure transaction boundaries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaEvent {

    private String sagaId;
    private String sagaType;
    private SagaStatus status;
    private String currentStep;
    private int stepIndex;
    private int totalSteps;
    private Instant startedAt;
    private Instant completedAt;
    private String failureReason;
    private List<SagaStep> completedSteps;
    private List<SagaStep> pendingCompensations;
    private Map<String, Object> sagaData;

    // Legacy SP mapping
    private String legacyTransactionId;
    private String legacyProcedure;        // e.g., "lsp_PopulatePO"
    private String legacyDatabase;         // V0 or V2

    public enum SagaStatus {
        STARTED,
        RUNNING,
        STEP_COMPLETED,
        STEP_FAILED,
        COMPENSATING,
        COMPENSATION_COMPLETED,
        COMPLETED,
        FAILED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SagaStep {
        private String stepId;
        private String stepName;
        private String activityType;
        private int order;
        private Instant startedAt;
        private Instant completedAt;
        private boolean compensatable;
        private String compensationActivity;
        private Map<String, Object> input;
        private Map<String, Object> output;
        private String error;

        // Legacy SP mapping for this step
        private String legacyProcedure;
        private List<String> legacyTables;
    }
}
