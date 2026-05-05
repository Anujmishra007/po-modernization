package com.wms.po.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Activity for releasing putaway tasks after receipt finalization.
 * Generates and dispatches tasks to warehouse workers/equipment.
 *
 * Maps to:
 * - lsp_ASNReleasePATask_Wrapper
 * - ispPARL* series (putaway release variants)
 * - nspPASTD (putaway strategy)
 */
@ActivityInterface
public interface PutawayReleaseActivity {

    /**
     * Generate and release putaway tasks for finalized inventory.
     * Uses putaway strategy rules to determine target locations.
     *
     * @param request Release request with inventory details
     * @return Result with task IDs
     */
    @ActivityMethod
    ReleaseResult releasePutawayTasks(ReleaseRequest request);

    /**
     * Cancel putaway tasks (compensation).
     * Sets tasks to cancelled status.
     *
     * @param taskIds Task IDs to cancel
     * @param reason Cancellation reason
     */
    @ActivityMethod
    void cancelPutawayTasks(List<String> taskIds, String reason);

    /**
     * Request for releasing putaway tasks
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ReleaseRequest {
        private String receiptKey;
        private String storerKey;
        private String facility;
        private String userId;
        private List<InventoryItem> inventoryItems;

        /**
         * Putaway strategy code (optional override)
         */
        private String strategyCode;

        /**
         * Priority for the tasks (1=highest)
         */
        @Builder.Default
        private int priority = 5;

        /**
         * Whether to use batch putaway
         */
        @Builder.Default
        private boolean batchMode = false;
    }

    /**
     * Inventory item to putaway
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class InventoryItem {
        private String inventoryId;
        private String sku;
        private BigDecimal quantity;
        private String packKey;
        private String uom;
        private String currentLocation;
        private String licensePlate;

        // Lottables for strategy matching
        private String lottable01;
        private String lottable02;
        private String lottable03;
    }

    /**
     * Result of putaway release
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ReleaseResult {
        private boolean success;
        private List<String> taskIds;
        private int tasksCreated;
        private List<String> errors;
        private List<String> warnings;

        public static ReleaseResult success(List<String> taskIds) {
            return ReleaseResult.builder()
                .success(true)
                .taskIds(taskIds)
                .tasksCreated(taskIds.size())
                .build();
        }

        public static ReleaseResult failed(String error) {
            return ReleaseResult.builder()
                .success(false)
                .errors(List.of(error))
                .build();
        }
    }
}
