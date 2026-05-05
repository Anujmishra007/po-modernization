package com.wms.po.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Activity for posting inventory to the central LOTxLOCxID table.
 * This is the core inventory repository in WMS.
 *
 * LOTxLOCxID structure:
 * - LOT: Lottable attributes (lot number, expiry, supplier lot, etc.)
 * - LOC: Physical location in warehouse
 * - ID: License plate number (container/pallet ID)
 */
@ActivityInterface
public interface InventoryPostingActivity {

    /**
     * Post inventory for all finalized receipt lines.
     * Creates records in LOTxLOCxID table.
     *
     * @param request Posting request with line details
     * @return Result with created inventory IDs
     */
    @ActivityMethod
    PostingResult postInventory(PostingRequest request);

    /**
     * Delete inventory records created during finalization.
     * Used for compensation when finalization fails.
     *
     * @param inventoryIds IDs of records to delete
     */
    @ActivityMethod
    void deleteInventory(List<String> inventoryIds);

    /**
     * Adjust inventory quantity for a single record.
     *
     * @param inventoryId ID of record to adjust
     * @param adjustment Quantity adjustment (can be negative)
     * @param reason Reason code for adjustment
     * @param userId User making the adjustment
     */
    @ActivityMethod
    void adjustInventory(String inventoryId, BigDecimal adjustment, String reason, String userId);

    /**
     * Request for posting inventory
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class PostingRequest {
        private String receiptKey;
        private String storerKey;
        private String facility;
        private String userId;
        private List<LinePosting> lines;
    }

    /**
     * Single line to post
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class LinePosting {
        private int lineNumber;
        private String sku;
        private BigDecimal quantity;
        private String packKey;
        private String uom;
        private String location;     // Target location (LOC)
        private String licensePlate; // Container ID (ID)

        // Lottable attributes
        private String lottable01;   // Typically: Style/Item number
        private String lottable02;   // Typically: Color
        private String lottable03;   // Typically: Size
        private String lottable04;   // Typically: Lot number
        private String lottable05;   // Typically: Supplier lot
        private String lottable06;   // Typically: Expiry date
        private String lottable07;
        private String lottable08;
        private String lottable09;
        private String lottable10;

        // Optional: Custom attributes
        private Map<String, String> customAttributes;
    }

    /**
     * Result of inventory posting
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class PostingResult {
        private boolean success;
        private List<String> inventoryIds;
        private int recordsCreated;
        private BigDecimal totalQuantity;
        private List<String> errors;
        private List<String> warnings;

        public static PostingResult success(List<String> ids, BigDecimal totalQty) {
            return PostingResult.builder()
                .success(true)
                .inventoryIds(ids)
                .recordsCreated(ids.size())
                .totalQuantity(totalQty)
                .build();
        }

        public static PostingResult failed(String error) {
            return PostingResult.builder()
                .success(false)
                .errors(List.of(error))
                .build();
        }
    }
}
