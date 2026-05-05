package com.wms.po.plugin.allocation;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of Post-Allocation Plugin execution.
 */
@Data
@Builder
public class PostAllocationResult {

    /**
     * Whether the plugin executed successfully.
     */
    private boolean success;

    /**
     * Plugin ID that generated this result.
     */
    private String pluginId;

    /**
     * Error message if execution failed.
     */
    private String errorMessage;

    /**
     * Error code for programmatic handling.
     */
    private String errorCode;

    /**
     * Execution timestamp.
     */
    @Builder.Default
    private LocalDateTime executionTime = LocalDateTime.now();

    /**
     * Execution duration in milliseconds.
     */
    private long executionDurationMs;

    /**
     * Actions performed by the plugin.
     */
    @Builder.Default
    private List<PluginAction> actions = new ArrayList<>();

    /**
     * Warnings generated during execution.
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * Additional output data from the plugin.
     */
    @Builder.Default
    private Map<String, Object> outputData = new HashMap<>();

    /**
     * Whether processing should continue if this plugin fails.
     */
    @Builder.Default
    private boolean continueOnFailure = false;

    // ═══════════════════════════════════════════════════════════════════════
    // Nested Types
    // ═══════════════════════════════════════════════════════════════════════

    @Data
    @Builder
    public static class PluginAction {
        private String actionType;
        private String tableName;
        private String keyValue;
        private String description;
        private Map<String, Object> details;
        private LocalDateTime timestamp;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Factory Methods
    // ═══════════════════════════════════════════════════════════════════════

    public static PostAllocationResult success(String pluginId) {
        return PostAllocationResult.builder()
                .success(true)
                .pluginId(pluginId)
                .build();
    }

    public static PostAllocationResult success(String pluginId, String message) {
        return PostAllocationResult.builder()
                .success(true)
                .pluginId(pluginId)
                .outputData(Map.of("message", message))
                .build();
    }

    public static PostAllocationResult failure(String pluginId, String errorMessage) {
        return PostAllocationResult.builder()
                .success(false)
                .pluginId(pluginId)
                .errorMessage(errorMessage)
                .build();
    }

    public static PostAllocationResult failure(String pluginId, String errorCode, String errorMessage) {
        return PostAllocationResult.builder()
                .success(false)
                .pluginId(pluginId)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    public static PostAllocationResult skipped(String pluginId, String reason) {
        return PostAllocationResult.builder()
                .success(true)
                .pluginId(pluginId)
                .outputData(Map.of("skipped", true, "reason", reason))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    public PostAllocationResult addAction(String actionType, String description) {
        actions.add(PluginAction.builder()
                .actionType(actionType)
                .description(description)
                .timestamp(LocalDateTime.now())
                .build());
        return this;
    }

    public PostAllocationResult addAction(String actionType, String tableName,
                                           String keyValue, String description) {
        actions.add(PluginAction.builder()
                .actionType(actionType)
                .tableName(tableName)
                .keyValue(keyValue)
                .description(description)
                .timestamp(LocalDateTime.now())
                .build());
        return this;
    }

    public PostAllocationResult addWarning(String warning) {
        warnings.add(warning);
        return this;
    }

    public PostAllocationResult setOutput(String key, Object value) {
        outputData.put(key, value);
        return this;
    }

    public boolean isSkipped() {
        return Boolean.TRUE.equals(outputData.get("skipped"));
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public int getActionCount() {
        return actions.size();
    }
}
