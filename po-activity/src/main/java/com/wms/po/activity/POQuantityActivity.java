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
 * Activity for updating PO received quantities.
 * Keeps PODETAIL.QTYRECEIVED in sync with finalized receipts.
 */
@ActivityInterface
public interface POQuantityActivity {

    /**
     * Update received quantities on PO detail lines.
     * Increments QTYRECEIVED based on finalized receipt quantities.
     *
     * @param request Update request with line quantities
     * @return Result with updated line info
     */
    @ActivityMethod
    UpdateResult updateReceivedQuantities(UpdateRequest request);

    /**
     * Revert received quantities (compensation).
     * Decrements QTYRECEIVED to previous values.
     *
     * @param updates List of updates to revert
     */
    @ActivityMethod
    void revertReceivedQuantities(List<LineUpdate> updates);

    /**
     * Check if all PO lines are fully received.
     * Used to determine if PO should be auto-closed.
     *
     * @param poKey PO to check
     * @return true if all lines have QTYRECEIVED >= QTYORDERED
     */
    @ActivityMethod
    boolean isFullyReceived(String poKey);

    /**
     * Close a PO when fully received.
     * Updates PO status and closedate.
     *
     * @param poKey PO to close
     * @param userId User closing the PO
     */
    @ActivityMethod
    void closePO(String poKey, String userId);

    /**
     * Request for updating quantities
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class UpdateRequest {
        private String receiptKey;
        private String poKey;
        private String userId;
        private List<LineUpdate> lineUpdates;
    }

    /**
     * Single line update
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class LineUpdate {
        private int poLineNumber;
        private String sku;
        private BigDecimal receivedQuantity;
        private BigDecimal previousQuantity; // For compensation
    }

    /**
     * Result of quantity update
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class UpdateResult {
        private boolean success;
        private int linesUpdated;
        private List<LineUpdate> updates; // Contains previousQuantity for compensation
        private List<String> errors;

        public static UpdateResult success(List<LineUpdate> updates) {
            return UpdateResult.builder()
                .success(true)
                .linesUpdated(updates.size())
                .updates(updates)
                .build();
        }

        public static UpdateResult failed(String error) {
            return UpdateResult.builder()
                .success(false)
                .errors(List.of(error))
                .build();
        }
    }
}
