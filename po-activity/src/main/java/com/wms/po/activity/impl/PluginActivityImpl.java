package com.wms.po.activity.impl;

import com.wms.po.activity.PluginActivity;
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
 * Implementation of PluginActivity - runs client-specific plugins
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PluginActivityImpl implements PluginActivity {

    private final PluginRegistry pluginRegistry;

    @Override
    public PluginResult runPrePopulate(PopulateRequest request, VariationContext context) {
        log.info("Running pre-populate plugins for client={}", context.getClient());

        List<PrePopulatePlugin> plugins = pluginRegistry.getPrePopulatePlugins(context.getClient());

        if (plugins.isEmpty()) {
            log.info("No pre-populate plugins found for client={}", context.getClient());
            return PluginResult.success();
        }

        for (PrePopulatePlugin plugin : plugins) {
            log.debug("Executing pre-populate plugin: {}", plugin.getClass().getSimpleName());

            try {
                PluginResult result = plugin.prePopulate(request, context);

                if (!result.isShouldContinue()) {
                    log.warn("Plugin {} stopped workflow: {}",
                        plugin.getClass().getSimpleName(), result.getReason());
                    return result;
                }

                if (result.isSkipped()) {
                    log.debug("Plugin {} skipped: {}",
                        plugin.getClass().getSimpleName(), result.getReason());
                }

            } catch (Exception e) {
                log.error("Plugin {} failed: {}",
                    plugin.getClass().getSimpleName(), e.getMessage(), e);
                return PluginResult.fail("Plugin failed: " + e.getMessage());
            }
        }

        log.info("All {} pre-populate plugins completed successfully", plugins.size());
        return PluginResult.success();
    }

    @Override
    public PluginResult runPostPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        log.info("Running post-populate plugins for client={}, receiptKey={}",
            context.getClient(), receiptKey);

        List<PostPopulatePlugin> plugins = pluginRegistry.getPostPopulatePlugins(context.getClient());

        if (plugins.isEmpty()) {
            log.info("No post-populate plugins found for client={}", context.getClient());
            return PluginResult.success();
        }

        for (PostPopulatePlugin plugin : plugins) {
            log.debug("Executing post-populate plugin: {}", plugin.getClass().getSimpleName());

            try {
                PluginResult result = plugin.postPopulate(receiptKey, request, context);

                if (!result.isShouldContinue()) {
                    log.warn("Post-populate plugin {} indicated failure: {}",
                        plugin.getClass().getSimpleName(), result.getReason());
                    // Post-populate failures are logged but don't stop the workflow
                }

            } catch (Exception e) {
                log.error("Post-populate plugin {} failed: {}",
                    plugin.getClass().getSimpleName(), e.getMessage(), e);
                // Don't fail workflow for post-populate plugin errors
            }
        }

        log.info("All {} post-populate plugins completed", plugins.size());
        return PluginResult.success();
    }
}
