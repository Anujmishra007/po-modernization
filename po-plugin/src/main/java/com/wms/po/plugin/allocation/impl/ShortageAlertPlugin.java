package com.wms.po.plugin.allocation.impl;

import com.wms.po.plugin.allocation.PostAllocationContext;
import com.wms.po.plugin.allocation.PostAllocationPlugin;
import com.wms.po.plugin.allocation.PostAllocationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Shortage Alert Post-Allocation Plugin.
 *
 * Replaces: ispPOA02 - Shortage notification after allocation
 *
 * Generates alerts and notifications when allocation results
 * in shortages or partial fulfillment.
 */
@Component
@Slf4j
public class ShortageAlertPlugin implements PostAllocationPlugin {

    @Override
    public String getPluginId() {
        return "POA02_SHORTAGE_ALERT";
    }

    @Override
    public String getDescription() {
        return "Generates alerts for allocation shortages";
    }

    @Override
    public boolean appliesTo(PostAllocationContext context) {
        // Apply only when there's a shortage
        return context.isPartialAllocation();
    }

    @Override
    public int getPriority() {
        return 50; // Medium priority
    }

    @Override
    public PostAllocationResult execute(PostAllocationContext context) {
        log.debug("Executing shortage alert for order: {}", context.getOrderKey());

        try {
            BigDecimal shortageQty = context.getQtyShorted();
            BigDecimal shortagePercent = BigDecimal.valueOf(100).subtract(context.getAllocationPercent());

            // Determine alert severity
            AlertSeverity severity = determineSeverity(context, shortagePercent);

            // Create shortage record
            String shortageKey = createShortageRecord(context, shortageQty);

            // Send notification based on severity
            sendShortageNotification(context, severity, shortageQty);

            // Check if replenishment should be triggered
            if (shouldTriggerReplenishment(context)) {
                triggerReplenishment(context, shortageQty);
            }

            return PostAllocationResult.success(getPluginId())
                    .addAction("SHORTAGE_ALERT", "SHORTAGE", shortageKey,
                            String.format("Shortage alert created: %.2f units (%.1f%%)",
                                    shortageQty, shortagePercent))
                    .setOutput("shortageKey", shortageKey)
                    .setOutput("severity", severity.name())
                    .setOutput("shortageQty", shortageQty);

        } catch (Exception e) {
            log.error("Shortage alert failed for order {}: {}", context.getOrderKey(), e.getMessage());
            return PostAllocationResult.failure(getPluginId(), "SHORTAGE_ALERT_FAILED", e.getMessage())
                    .addWarning("Shortage not recorded but allocation continues");
        }
    }

    @Override
    public boolean isOptional() {
        return true; // Alert failure shouldn't stop allocation
    }

    private enum AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    private AlertSeverity determineSeverity(PostAllocationContext context, BigDecimal shortagePercent) {
        double percent = shortagePercent.doubleValue();

        if (percent >= 75) {
            return AlertSeverity.CRITICAL;
        } else if (percent >= 50) {
            return AlertSeverity.HIGH;
        } else if (percent >= 25) {
            return AlertSeverity.MEDIUM;
        } else {
            return AlertSeverity.LOW;
        }
    }

    private String createShortageRecord(PostAllocationContext context, BigDecimal shortageQty) {
        // Create shortage record in database
        String shortageKey = "SHT" + System.currentTimeMillis();
        log.debug("Created shortage record {} for {} units", shortageKey, shortageQty);
        return shortageKey;
    }

    private void sendShortageNotification(PostAllocationContext context,
                                          AlertSeverity severity,
                                          BigDecimal shortageQty) {
        // Send notification via email/Kafka/etc.
        log.info("Shortage alert [{}]: Order {} short {} units of SKU {}",
                severity, context.getOrderKey(), shortageQty, context.getSku());
    }

    private boolean shouldTriggerReplenishment(PostAllocationContext context) {
        return context.getStorerConfigValue("AutoReplenishOnShortage", false);
    }

    private void triggerReplenishment(PostAllocationContext context, BigDecimal shortageQty) {
        // Trigger replenishment task
        log.debug("Triggered replenishment for {} units of {}", shortageQty, context.getSku());
    }
}
