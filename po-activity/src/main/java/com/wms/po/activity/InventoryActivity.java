package com.wms.po.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

/**
 * Activity for inventory operations with compensation methods
 */
@ActivityInterface
public interface InventoryActivity {

    /**
     * Create inventory reservations for incoming receipt
     * @return list of reservation IDs
     */
    @ActivityMethod
    List<String> createReservations(String receiptKey, List<String> detailKeys);

    /**
     * Release inventory reservations (COMPENSATION)
     */
    @ActivityMethod
    void releaseReservations(List<String> reservationIds);

    /**
     * Pre-allocate inventory for expected receipt
     */
    @ActivityMethod
    void preAllocateInventory(String receiptKey, List<String> detailKeys);

    /**
     * Release pre-allocated inventory (COMPENSATION)
     */
    @ActivityMethod
    void releasePreAllocation(String receiptKey);
}
