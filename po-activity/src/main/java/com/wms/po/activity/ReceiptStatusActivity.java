package com.wms.po.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity for managing receipt status transitions.
 * Handles status validation and updates during finalization.
 *
 * WMS Receipt Status Codes:
 * 0 - New (not yet arrived)
 * 1 - Arrived
 * 2 - Checked in
 * 3 - Receiving in progress
 * 5 - Verified
 * 6 - Finalizing (in progress)
 * 9 - Finalized (complete)
 */
@ActivityInterface
public interface ReceiptStatusActivity {

    /**
     * Validate that the receipt is in a state that allows finalization.
     * Receipt must be in status 0-5 to be finalized.
     *
     * @param receiptKey Receipt to validate
     * @return Current status if valid for finalization
     * @throws IllegalStateException if receipt cannot be finalized
     */
    @ActivityMethod
    String validateForFinalization(String receiptKey);

    /**
     * Update receipt status to "Finalizing" (status 6).
     * This marks the receipt as being processed.
     *
     * @param receiptKey Receipt to update
     * @param userId User performing the update
     * @return Previous status (for compensation)
     */
    @ActivityMethod
    String setStatusFinalizing(String receiptKey, String userId);

    /**
     * Update receipt status to "Finalized" (status 9).
     * This is the final status after successful finalization.
     *
     * @param receiptKey Receipt to update
     * @param userId User performing the update
     */
    @ActivityMethod
    void setStatusFinalized(String receiptKey, String userId);

    /**
     * Revert receipt status back to original value.
     * Used for compensation when finalization fails.
     *
     * @param receiptKey Receipt to revert
     * @param originalStatus Status to revert to
     * @param userId User performing the revert
     */
    @ActivityMethod
    void revertStatus(String receiptKey, String originalStatus, String userId);

    /**
     * Close the receipt (set closedate and mark complete).
     * Called when autoClose is enabled.
     *
     * @param receiptKey Receipt to close
     * @param userId User performing the close
     */
    @ActivityMethod
    void closeReceipt(String receiptKey, String userId);
}
