package com.wms.po.activity;

import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity for updating PO status
 * Maps to legacy: nsp_UpdatePOStatus
 */
@ActivityInterface
public interface POStatusUpdateActivity {

    /**
     * Update PO status after population
     */
    @ActivityMethod
    void updatePOStatus(String receiptKey, PopulateRequest request, VariationContext context, String targetStatus);

    /**
     * Compensation: Revert PO status
     */
    @ActivityMethod
    void compensateUpdatePOStatus(String receiptKey, PopulateRequest request, VariationContext context, String previousStatus);
}
