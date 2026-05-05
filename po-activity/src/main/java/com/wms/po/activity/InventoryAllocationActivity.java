package com.wms.po.activity;

import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity for allocating inventory from receipt
 * Maps to legacy: nsp_AllocateInventory
 */
@ActivityInterface
public interface InventoryAllocationActivity {

    /**
     * Allocate inventory for receipt
     */
    @ActivityMethod
    void allocateInventory(String receiptKey, PopulateRequest request, VariationContext context);

    /**
     * Compensation: Deallocate inventory
     */
    @ActivityMethod
    void compensateAllocateInventory(String receiptKey, VariationContext context);
}
