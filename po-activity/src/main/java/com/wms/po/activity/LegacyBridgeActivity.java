package com.wms.po.activity;

import com.wms.po.domain.model.VariationContext;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity for legacy system integration (V0/V2 dual-write)
 */
@ActivityInterface
public interface LegacyBridgeActivity {

    /**
     * Sync new receipt to legacy system
     */
    @ActivityMethod
    void syncToLegacy(String receiptKey, VariationContext context);

    /**
     * Rollback legacy sync (COMPENSATION)
     */
    @ActivityMethod
    void rollbackLegacy(String receiptKey, VariationContext context);

    /**
     * Verify legacy data consistency
     */
    @ActivityMethod
    boolean verifyLegacySync(String receiptKey, VariationContext context);
}
