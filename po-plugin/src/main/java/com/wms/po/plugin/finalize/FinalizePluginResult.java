package com.wms.po.plugin.finalize;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result object returned by finalization plugins.
 *
 * Contains execution status, modifications made, and any errors/warnings.
 */
@Data
@Builder
public class FinalizePluginResult {

    /**
     * Whether the plugin executed successfully.
     */
    private boolean success;

    /**
     * Error message if plugin failed.
     */
    private String errorMessage;

    /**
     * Error code for programmatic handling.
     */
    private String errorCode;

    /**
     * Warning messages (non-fatal issues).
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * Informational messages.
     */
    @Builder.Default
    private List<String> messages = new ArrayList<>();

    /**
     * Whether to continue with finalization.
     * Set to false to abort the process.
     */
    @Builder.Default
    private boolean continueProcessing = true;

    /**
     * Whether to skip remaining plugins.
     */
    private boolean skipRemainingPlugins;

    /**
     * Data to pass to subsequent plugins or workflow.
     */
    @Builder.Default
    private Map<String, Object> outputData = new HashMap<>();

    /**
     * Lines modified by this plugin.
     */
    @Builder.Default
    private List<Integer> modifiedLines = new ArrayList<>();

    /**
     * Lines to skip from finalization.
     */
    @Builder.Default
    private List<Integer> skippedLines = new ArrayList<>();

    /**
     * Execution time in milliseconds.
     */
    private long executionTimeMs;

    // ═══════════════════════════════════════════════════════════════════════
    // Factory Methods
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Create a successful result.
     */
    public static FinalizePluginResult success() {
        return FinalizePluginResult.builder()
            .success(true)
            .continueProcessing(true)
            .build();
    }

    /**
     * Create a successful result with a message.
     */
    public static FinalizePluginResult success(String message) {
        FinalizePluginResult result = success();
        result.getMessages().add(message);
        return result;
    }

    /**
     * Create a failed result.
     */
    public static FinalizePluginResult failure(String errorMessage) {
        return FinalizePluginResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .continueProcessing(false)
            .build();
    }

    /**
     * Create a failed result with error code.
     */
    public static FinalizePluginResult failure(String errorCode, String errorMessage) {
        return FinalizePluginResult.builder()
            .success(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .continueProcessing(false)
            .build();
    }

    /**
     * Create a result that skips remaining plugins.
     */
    public static FinalizePluginResult skipRemaining() {
        return FinalizePluginResult.builder()
            .success(true)
            .continueProcessing(true)
            .skipRemainingPlugins(true)
            .build();
    }

    /**
     * Create a result that aborts finalization.
     */
    public static FinalizePluginResult abort(String reason) {
        return FinalizePluginResult.builder()
            .success(false)
            .errorMessage(reason)
            .continueProcessing(false)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Add a warning message.
     */
    public FinalizePluginResult addWarning(String warning) {
        this.warnings.add(warning);
        return this;
    }

    /**
     * Add an informational message.
     */
    public FinalizePluginResult addMessage(String message) {
        this.messages.add(message);
        return this;
    }

    /**
     * Record a modified line.
     */
    public FinalizePluginResult addModifiedLine(int lineNumber) {
        this.modifiedLines.add(lineNumber);
        return this;
    }

    /**
     * Record a skipped line.
     */
    public FinalizePluginResult addSkippedLine(int lineNumber) {
        this.skippedLines.add(lineNumber);
        return this;
    }

    /**
     * Set output data.
     */
    public FinalizePluginResult setOutputData(String key, Object value) {
        this.outputData.put(key, value);
        return this;
    }

    /**
     * Check if there are any warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Check if any lines were modified.
     */
    public boolean hasModifications() {
        return !modifiedLines.isEmpty();
    }

    /**
     * Check if any lines were skipped.
     */
    public boolean hasSkippedLines() {
        return !skippedLines.isEmpty();
    }
}
