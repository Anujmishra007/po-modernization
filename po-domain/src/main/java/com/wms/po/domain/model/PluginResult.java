package com.wms.po.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result from plugin execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginResult {

    private boolean shouldContinue;
    private boolean skipped;
    private String reason;
    private Object data;

    /**
     * Number of plugins that were executed
     */
    @Builder.Default
    private int pluginsExecuted = 0;

    /**
     * IDs of plugins that were executed (for rollback purposes)
     */
    @Builder.Default
    private List<String> executedPlugins = new ArrayList<>();

    public static PluginResult success() {
        return PluginResult.builder()
            .shouldContinue(true)
            .skipped(false)
            .build();
    }

    public static PluginResult success(Object data) {
        return PluginResult.builder()
            .shouldContinue(true)
            .skipped(false)
            .data(data)
            .build();
    }

    public static PluginResult success(int pluginsExecuted, List<String> executedPlugins) {
        return PluginResult.builder()
            .shouldContinue(true)
            .skipped(false)
            .pluginsExecuted(pluginsExecuted)
            .executedPlugins(executedPlugins != null ? executedPlugins : new ArrayList<>())
            .build();
    }

    public static PluginResult skip(String reason) {
        return PluginResult.builder()
            .shouldContinue(true)
            .skipped(true)
            .reason(reason)
            .build();
    }

    public static PluginResult fail(String reason) {
        return PluginResult.builder()
            .shouldContinue(false)
            .skipped(false)
            .reason(reason)
            .build();
    }
}
