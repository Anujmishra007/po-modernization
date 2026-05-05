package com.wms.po.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Activity for applying inventory holds during finalization.
 * Maps to nspInventoryHoldWrapper stored procedure.
 *
 * Holds can be applied based on:
 * - Quality inspection requirements
 * - Customs clearance
 * - Client-specific rules
 * - Quarantine requirements
 */
@ActivityInterface
public interface InventoryHoldActivity {

    /**
     * Determine which holds should be applied based on rules.
     * Evaluates Drools rules and client configuration.
     *
     * @param request Hold evaluation request
     * @return List of holds to apply
     */
    @ActivityMethod
    List<HoldToApply> evaluateHolds(HoldEvaluationRequest request);

    /**
     * Apply holds to inventory records.
     *
     * @param request Hold application request
     * @return Result with applied hold IDs
     */
    @ActivityMethod
    HoldResult applyHolds(HoldApplicationRequest request);

    /**
     * Remove holds (compensation).
     * Releases holds that were applied during finalization.
     *
     * @param holdIds IDs of holds to remove
     * @param reason Reason for removal
     */
    @ActivityMethod
    void removeHolds(List<String> holdIds, String reason);

    /**
     * Request for evaluating holds
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class HoldEvaluationRequest {
        private String receiptKey;
        private String storerKey;
        private String facility;
        private List<String> inventoryIds;

        // Attributes for rule evaluation
        private String sku;
        private String supplierKey;
        private String countryOfOrigin;
        private boolean requiresInspection;
        private boolean customsRequired;
    }

    /**
     * Hold to be applied
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class HoldToApply {
        private String holdCode;
        private String holdReason;
        private String holdType;        // QC, CUSTOMS, QUARANTINE, etc.
        private boolean blockPutaway;   // If true, prevents putaway task release
        private boolean blockAllocation; // If true, prevents allocation
        private LocalDateTime expiryDate; // Optional auto-release date
    }

    /**
     * Request for applying holds
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class HoldApplicationRequest {
        private List<String> inventoryIds;
        private List<HoldToApply> holds;
        private String userId;
    }

    /**
     * Result of hold application
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class HoldResult {
        private boolean success;
        private List<String> holdIds; // Created hold record IDs
        private int holdsApplied;
        private List<String> errors;

        public static HoldResult success(List<String> holdIds) {
            return HoldResult.builder()
                .success(true)
                .holdIds(holdIds)
                .holdsApplied(holdIds.size())
                .build();
        }

        public static HoldResult noHoldsRequired() {
            return HoldResult.builder()
                .success(true)
                .holdIds(List.of())
                .holdsApplied(0)
                .build();
        }

        public static HoldResult failed(String error) {
            return HoldResult.builder()
                .success(false)
                .errors(List.of(error))
                .build();
        }
    }
}
