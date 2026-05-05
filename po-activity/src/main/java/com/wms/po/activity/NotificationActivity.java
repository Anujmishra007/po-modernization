package com.wms.po.activity;

import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.PopulateRequest;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity for sending notifications (best-effort, no compensation)
 */
@ActivityInterface
public interface NotificationActivity {

    // ═══════════════════════════════════════════════════════════════
    // Population Notifications
    // ═══════════════════════════════════════════════════════════════

    /**
     * Send notification that population is complete
     */
    @ActivityMethod
    void sendPopulationComplete(String receiptKey, PopulateRequest request);

    /**
     * Send notification for workflow failure
     */
    @ActivityMethod
    void sendPopulationFailed(String receiptKey, String errorMessage, PopulateRequest request);

    /**
     * Send notification for workflow cancellation
     */
    @ActivityMethod
    void sendPopulationCancelled(String receiptKey, PopulateRequest request);

    // ═══════════════════════════════════════════════════════════════
    // Finalization Notifications
    // ═══════════════════════════════════════════════════════════════

    /**
     * Send notification that finalization is complete
     */
    @ActivityMethod
    void sendFinalizeComplete(String receiptKey, FinalizeRequest request);

    /**
     * Send notification for finalization failure
     */
    @ActivityMethod
    void sendFinalizeFailed(String receiptKey, String errorMessage, FinalizeRequest request);

    /**
     * Send notification for finalization cancellation
     */
    @ActivityMethod
    void sendFinalizeCancelled(String receiptKey, FinalizeRequest request);

    // ═══════════════════════════════════════════════════════════════
    // Trade Return Notifications
    // ═══════════════════════════════════════════════════════════════

    /**
     * Send notification that trade return processing is complete
     */
    @ActivityMethod
    void sendTradeReturnComplete(String orderKey, String receiptKey);

    /**
     * Send notification for trade return failure
     */
    @ActivityMethod
    void sendTradeReturnFailed(String receiptKey, String errorMessage);
}
