package com.wms.po.activity;

import io.temporal.activity.ActivityInterface;

/**
 * Combined marker interface for all receipt finalization activities.
 * This interface extends all individual activity interfaces to provide
 * a single point of registration for the Temporal worker.
 */
@ActivityInterface
public interface FinalizeReceiptActivities extends
    ValidationActivity,
    ReceiptStatusActivity,
    InventoryPostingActivity,
    InventoryHoldActivity,
    POQuantityActivity,
    PutawayReleaseActivity,
    FinalizePluginActivity,
    NotificationActivity {
    // Combined interface - no additional methods needed
}
