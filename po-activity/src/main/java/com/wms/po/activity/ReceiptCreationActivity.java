package com.wms.po.activity;

import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity for creating receipts from POs
 * Maps to legacy: nsp_CreateReceipt
 */
@ActivityInterface
public interface ReceiptCreationActivity {

    /**
     * Create receipt from PO
     */
    @ActivityMethod
    String createReceipt(PopulateRequest request, VariationContext context);

    /**
     * Compensation: Delete receipt
     */
    @ActivityMethod
    void compensateCreateReceipt(String receiptKey, VariationContext context);
}
