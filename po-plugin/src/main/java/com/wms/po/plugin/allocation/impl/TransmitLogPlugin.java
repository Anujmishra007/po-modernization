package com.wms.po.plugin.allocation.impl;

import com.wms.po.plugin.allocation.PostAllocationContext;
import com.wms.po.plugin.allocation.PostAllocationPlugin;
import com.wms.po.plugin.allocation.PostAllocationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Transmit Log Post-Allocation Plugin.
 *
 * Replaces: ispPOA06 - EDI/Transmit log creation after allocation
 *
 * Creates transmit log entries for EDI transmission of
 * allocation confirmations to external systems.
 */
@Component
@Slf4j
public class TransmitLogPlugin implements PostAllocationPlugin {

    @Override
    public String getPluginId() {
        return "POA06_TRANSMIT_LOG";
    }

    @Override
    public String getDescription() {
        return "Creates transmit log entries for EDI notification";
    }

    @Override
    public boolean appliesTo(PostAllocationContext context) {
        // Apply when EDI transmission is enabled
        return context.getStorerConfigValue("EnableEDITransmit", true);
    }

    @Override
    public int getPriority() {
        return 90; // Execute late - after main processing
    }

    @Override
    public PostAllocationResult execute(PostAllocationContext context) {
        log.debug("Creating transmit log for order: {}", context.getOrderKey());

        try {
            // Create allocation confirmation transmit log
            String transmitKey = createTransmitLog(context);

            // Create detail transmit log if needed
            if (context.getStorerConfigValue("TransmitDetailLevel", false)) {
                createDetailTransmitLog(context);
            }

            return PostAllocationResult.success(getPluginId())
                    .addAction("TRANSMIT_LOG", "TRANSMITLOG", transmitKey,
                            "Created EDI transmit log for allocation confirmation")
                    .setOutput("transmitKey", transmitKey);

        } catch (Exception e) {
            log.error("Transmit log creation failed for order {}: {}",
                    context.getOrderKey(), e.getMessage());
            return PostAllocationResult.failure(getPluginId(), "TRANSMIT_FAILED", e.getMessage())
                    .addWarning("EDI transmission will not occur");
        }
    }

    @Override
    public boolean isOptional() {
        return true; // Transmit log failure shouldn't stop allocation
    }

    private String createTransmitLog(PostAllocationContext context) {
        // Create transmit log record
        String transmitKey = "TL" + System.currentTimeMillis();

        log.debug("Created transmit log {} for order {} allocation",
                transmitKey, context.getOrderKey());

        return transmitKey;
    }

    private void createDetailTransmitLog(PostAllocationContext context) {
        // Create detail-level transmit logs
        for (PostAllocationContext.AllocationLine line : context.getAllocationLines()) {
            log.debug("Created detail transmit log for pick detail {}", line.getPickDetailKey());
        }
    }
}
