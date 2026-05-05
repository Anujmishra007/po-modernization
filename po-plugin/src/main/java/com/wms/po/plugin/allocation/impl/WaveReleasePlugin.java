package com.wms.po.plugin.allocation.impl;

import com.wms.po.plugin.allocation.PostAllocationContext;
import com.wms.po.plugin.allocation.PostAllocationPlugin;
import com.wms.po.plugin.allocation.PostAllocationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Wave Release Post-Allocation Plugin.
 *
 * Replaces: ispPOA01 - Wave release after allocation
 *
 * Automatically releases allocated orders to wave processing
 * based on configuration and allocation completeness.
 */
@Component
@Slf4j
public class WaveReleasePlugin implements PostAllocationPlugin {

    @Override
    public String getPluginId() {
        return "POA01_WAVE_RELEASE";
    }

    @Override
    public String getDescription() {
        return "Automatically releases fully allocated orders to wave processing";
    }

    @Override
    public boolean appliesTo(PostAllocationContext context) {
        // Apply to fully allocated orders with auto-wave enabled
        Boolean autoWave = context.getStorerConfigValue("AutoWaveRelease", false);
        return autoWave && context.isFullyAllocated();
    }

    @Override
    public int getPriority() {
        return 10; // High priority - execute early
    }

    @Override
    public PostAllocationResult execute(PostAllocationContext context) {
        log.debug("Executing wave release for order: {}", context.getOrderKey());

        try {
            // Check wave release eligibility
            if (!isEligibleForWaveRelease(context)) {
                return PostAllocationResult.skipped(getPluginId(), "Order not eligible for wave release");
            }

            // Create wave assignment
            String waveKey = createWaveAssignment(context);

            // Update order status
            updateOrderForWave(context, waveKey);

            return PostAllocationResult.success(getPluginId())
                    .addAction("WAVE_ASSIGN", "ORDERDETAIL", context.getOrderDetailKey(),
                            "Assigned to wave: " + waveKey)
                    .setOutput("waveKey", waveKey);

        } catch (Exception e) {
            log.error("Wave release failed for order {}: {}", context.getOrderKey(), e.getMessage());
            return PostAllocationResult.failure(getPluginId(), "WAVE_RELEASE_FAILED", e.getMessage());
        }
    }

    private boolean isEligibleForWaveRelease(PostAllocationContext context) {
        // Check minimum allocation threshold
        Double minThreshold = context.getStorerConfigValue("WaveReleaseMinPercent", 100.0);
        return context.getAllocationPercent().doubleValue() >= minThreshold;
    }

    private String createWaveAssignment(PostAllocationContext context) {
        // Generate wave key - would call WaveService in real implementation
        return "WAVE" + System.currentTimeMillis();
    }

    private void updateOrderForWave(PostAllocationContext context, String waveKey) {
        // Update order status - would use JdbcTemplate in real implementation
        log.debug("Order {} assigned to wave {}", context.getOrderKey(), waveKey);
    }

    @Override
    public Map<String, Object> getConfiguration() {
        return Map.of(
                "autoWaveEnabled", true,
                "minAllocationPercent", 100.0,
                "maxWaveSize", 500
        );
    }
}
