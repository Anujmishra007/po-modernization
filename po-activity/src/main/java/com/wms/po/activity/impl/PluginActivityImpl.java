package com.wms.po.activity.impl;

import com.wms.po.activity.PluginActivity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.variation.plugin.PluginRegistry;
import com.wms.po.variation.plugin.PostPopulatePlugin;
import com.wms.po.variation.plugin.PrePopulatePlugin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementation of PluginActivity - runs client-specific plugins.
 *
 * Maps to legacy SPs:
 * - SP-110-116: ispPRPPLPO* (Pre-Populate, error codes 69540-69546)
 * - SP-120-123: Client Auto-ASN SPs (error codes 69550-69554)
 *
 * Error codes:
 * - PLG_001 (69500) - Plugin Not Found
 * - PLG_002 (69501) - Plugin Execution Failed
 * - PLG_040 (69540) - Pre-Populate Hook Failed
 * - PLG_041-046 - Specific pre-populate plugin errors
 * - PLG_050-054 - Client auto-ASN errors
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PluginActivityImpl implements PluginActivity {

    private final PluginRegistry pluginRegistry;

    @Override
    public PluginResult runPrePopulate(PopulateRequest request, VariationContext context) {
        log.info("Running pre-populate plugins for client={}, storer={}",
            context.getClient(), request.getStorerKey());

        List<PrePopulatePlugin> plugins;
        try {
            plugins = pluginRegistry.getPrePopulatePlugins(context.getClient());
        } catch (Exception e) {
            log.error("Failed to get pre-populate plugins for client {}: {} (legacy error 69500)",
                context.getClient(), e.getMessage(), e);
            throw BusinessException.pluginNotFound(context.getClient() + "_PrePopulate");
        }

        if (plugins.isEmpty()) {
            log.info("No pre-populate plugins found for client={}", context.getClient());
            return PluginResult.success();
        }

        for (PrePopulatePlugin plugin : plugins) {
            String pluginName = plugin.getClass().getSimpleName();
            log.debug("Executing pre-populate plugin: {}", pluginName);

            try {
                PluginResult result = plugin.prePopulate(request, context);

                if (!result.isShouldContinue()) {
                    log.warn("Plugin {} stopped workflow: {} (legacy error 69540)",
                        pluginName, result.getReason());
                    // Throw proper exception based on plugin type
                    throw BusinessException.prePopulateHookFailed(
                        extractPluginType(pluginName),
                        request.getPoKeys() != null ? request.getPoKeys().get(0) : "unknown",
                        new RuntimeException(result.getReason())
                    );
                }

                if (result.isSkipped()) {
                    log.debug("Plugin {} skipped: {}", pluginName, result.getReason());
                }

            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Plugin {} failed: {} (legacy error 69501)",
                    pluginName, e.getMessage(), e);
                throw BusinessException.prePopulateHookFailed(
                    extractPluginType(pluginName),
                    request.getPoKeys() != null ? request.getPoKeys().get(0) : "unknown",
                    e
                );
            }
        }

        log.info("All {} pre-populate plugins completed successfully", plugins.size());
        return PluginResult.success();
    }

    @Override
    public PluginResult runPostPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        log.info("Running post-populate plugins for client={}, receiptKey={}",
            context.getClient(), receiptKey);

        List<PostPopulatePlugin> plugins;
        try {
            plugins = pluginRegistry.getPostPopulatePlugins(context.getClient());
        } catch (Exception e) {
            log.error("Failed to get post-populate plugins for client {}: {}",
                context.getClient(), e.getMessage(), e);
            // Post-populate plugin lookup failure is logged but doesn't stop workflow
            return PluginResult.success();
        }

        if (plugins.isEmpty()) {
            log.info("No post-populate plugins found for client={}", context.getClient());
            return PluginResult.success();
        }

        int successCount = 0;
        int failedCount = 0;

        for (PostPopulatePlugin plugin : plugins) {
            String pluginName = plugin.getClass().getSimpleName();
            log.debug("Executing post-populate plugin: {}", pluginName);

            try {
                PluginResult result = plugin.postPopulate(receiptKey, request, context);

                if (!result.isShouldContinue()) {
                    log.warn("Post-populate plugin {} indicated failure: {} (logged only)",
                        pluginName, result.getReason());
                    failedCount++;
                    // Post-populate failures are logged but don't stop the workflow
                } else {
                    successCount++;
                }

            } catch (Exception e) {
                failedCount++;
                log.error("Post-populate plugin {} failed: {} (logged only, workflow continues)",
                    pluginName, e.getMessage(), e);
                // Don't fail workflow for post-populate plugin errors
            }
        }

        log.info("Post-populate plugins completed: {} success, {} failed", successCount, failedCount);
        return PluginResult.success();
    }

    /**
     * Extract plugin type from class name for error code mapping.
     * Maps plugin class names to error code hook names.
     */
    private String extractPluginType(String pluginClassName) {
        if (pluginClassName.contains("Standard")) {
            return "STANDARD";
        } else if (pluginClassName.contains("DateValidation")) {
            return "DATE_VALIDATION";
        } else if (pluginClassName.contains("Adidas")) {
            return "ADIDAS";
        } else if (pluginClassName.contains("Quantity")) {
            return "QUANTITY";
        } else if (pluginClassName.contains("CrossReference") || pluginClassName.contains("XRef")) {
            return "CROSS_REFERENCE";
        } else if (pluginClassName.contains("JCB")) {
            return "JCB";
        } else if (pluginClassName.contains("Nike")) {
            return "NIKE";
        } else if (pluginClassName.contains("HM") || pluginClassName.contains("H&M")) {
            return "HM";
        } else if (pluginClassName.contains("Columbia")) {
            return "COLUMBIA";
        } else if (pluginClassName.contains("Unilever")) {
            return "UNILEVER";
        } else if (pluginClassName.contains("NewLook")) {
            return "NEWLOOK";
        }
        return pluginClassName;
    }
}
