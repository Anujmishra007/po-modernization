package com.wms.po.legacy.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of reconciliation between legacy and new system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationResult {

    private String reconciliationId;
    private LocalDateTime timestamp;

    // Source identifiers
    private String poKey;
    private String storerKey;
    private String facility;

    // Results from both systems
    private String legacyReceiptKey;
    private String newReceiptKey;

    // Reconciliation status
    @Builder.Default
    private ReconciliationStatus status = ReconciliationStatus.PENDING;

    // Differences found
    @Builder.Default
    private List<Difference> differences = new ArrayList<>();

    // Statistics
    private int legacyLineCount;
    private int newLineCount;
    private int matchedLines;
    private int mismatchedLines;

    public enum ReconciliationStatus {
        PENDING,
        IN_PROGRESS,
        MATCHED,
        MISMATCHED,
        LEGACY_ONLY,
        NEW_ONLY,
        ERROR
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Difference {
        private String field;
        private String legacyValue;
        private String newValue;
        private String lineKey;
        private DifferenceType type;
        private String description;
    }

    public enum DifferenceType {
        VALUE_MISMATCH,
        MISSING_IN_LEGACY,
        MISSING_IN_NEW,
        TYPE_MISMATCH,
        COUNT_MISMATCH
    }

    public void addDifference(String field, String legacyValue, String newValue, DifferenceType type) {
        differences.add(Difference.builder()
            .field(field)
            .legacyValue(legacyValue)
            .newValue(newValue)
            .type(type)
            .build());

        if (type != DifferenceType.VALUE_MISMATCH || !legacyValue.equals(newValue)) {
            this.status = ReconciliationStatus.MISMATCHED;
        }
    }

    public boolean isMatched() {
        return status == ReconciliationStatus.MATCHED;
    }

    public boolean hasDifferences() {
        return !differences.isEmpty();
    }
}
