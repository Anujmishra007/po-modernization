package com.wms.po.plugin.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of plugin execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginResult {

    @Builder.Default
    private boolean success = true;

    @Builder.Default
    private boolean shouldContinue = true;

    private String message;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public static PluginResult success() {
        return PluginResult.builder()
            .success(true)
            .shouldContinue(true)
            .build();
    }

    public static PluginResult success(String message) {
        return PluginResult.builder()
            .success(true)
            .shouldContinue(true)
            .message(message)
            .build();
    }

    public static PluginResult failure(String error) {
        return PluginResult.builder()
            .success(false)
            .shouldContinue(false)
            .errors(List.of(error))
            .build();
    }

    public static PluginResult failure(List<String> errors) {
        return PluginResult.builder()
            .success(false)
            .shouldContinue(false)
            .errors(errors)
            .build();
    }

    public static PluginResult skip(String reason) {
        return PluginResult.builder()
            .success(true)
            .shouldContinue(false)
            .message(reason)
            .build();
    }

    public void addError(String error) {
        this.success = false;
        this.errors.add(error);
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
}
