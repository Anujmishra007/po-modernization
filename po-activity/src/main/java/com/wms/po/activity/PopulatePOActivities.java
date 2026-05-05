package com.wms.po.activity;

import io.temporal.activity.ActivityInterface;

/**
 * Combined marker interface for all PO population activities.
 * This interface extends all individual activity interfaces to provide
 * a single point of registration for the Temporal worker.
 */
@ActivityInterface
public interface PopulatePOActivities extends
    ValidationActivity,
    MappingActivity,
    PersistenceActivity,
    InventoryActivity,
    LegacyBridgeActivity,
    NotificationActivity,
    PluginActivity {
    // Combined interface - no additional methods needed
}
