package com.wms.po.plugin.populate;

import com.wms.po.plugin.api.PluginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry for populate plugins.
 *
 * Manages pre-populate and post-populate plugin registration,
 * lookup, and execution by client/region.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PopulatePluginRegistry {

    private final List<PrePopulatePlugin> prePlugins;
    private final List<PostPopulatePlugin> postPlugins;

    @PostConstruct
    public void initialize() {
        log.info("Initialized populate plugin registry: {} pre-populate, {} post-populate",
            prePlugins != null ? prePlugins.size() : 0,
            postPlugins != null ? postPlugins.size() : 0);
    }

    /**
     * Execute all applicable pre-populate plugins.
     *
     * @param request The populate request
     * @param context The context
     * @return Combined result
     */
    public PluginResult executePrePopulate(PopulateRequest request, PopulateContext context) {
        log.info("Running pre-populate plugins for client={}, region={}",
            context.getClient(), context.getRegion());

        List<PrePopulatePlugin> applicable = getApplicablePrePlugins(context);

        for (PrePopulatePlugin plugin : applicable) {
            try {
                log.debug("Executing pre-populate plugin: {}", plugin.getClass().getSimpleName());

                PluginResult result = plugin.prePopulate(request, context);

                if (!result.isShouldContinue()) {
                    log.warn("Plugin {} stopped workflow: {}",
                        plugin.getClass().getSimpleName(), result.getMessage());
                    return result;
                }

            } catch (Exception e) {
                log.error("Plugin {} failed: {}",
                    plugin.getClass().getSimpleName(), e.getMessage(), e);
                // Continue with other plugins unless configured otherwise
            }
        }

        return PluginResult.success();
    }

    /**
     * Execute all applicable post-populate plugins.
     *
     * @param receiptKey The created receipt key
     * @param request The populate request
     * @param context The context
     */
    public void executePostPopulate(String receiptKey, PopulateRequest request, PopulateContext context) {
        log.info("Running post-populate plugins for receiptKey={}", receiptKey);

        List<PostPopulatePlugin> applicable = getApplicablePostPlugins(context);

        for (PostPopulatePlugin plugin : applicable) {
            try {
                log.debug("Executing post-populate plugin: {}", plugin.getClass().getSimpleName());
                plugin.postPopulate(receiptKey, request, context);
            } catch (Exception e) {
                log.warn("Plugin {} failed in post-populate: {}",
                    plugin.getClass().getSimpleName(), e.getMessage());
                // Continue with other plugins
            }
        }
    }

    private List<PrePopulatePlugin> getApplicablePrePlugins(PopulateContext context) {
        if (prePlugins == null) return Collections.emptyList();

        return prePlugins.stream()
            .filter(p -> matchesClient(p, context.getClient()))
            .filter(p -> p.appliesTo(context.getRegion()))
            .sorted(Comparator.comparingInt(PopulatePlugin::getOrder))
            .collect(Collectors.toList());
    }

    private List<PostPopulatePlugin> getApplicablePostPlugins(PopulateContext context) {
        if (postPlugins == null) return Collections.emptyList();

        return postPlugins.stream()
            .filter(p -> matchesClient(p, context.getClient()))
            .filter(p -> p.appliesTo(context.getRegion()))
            .sorted(Comparator.comparingInt(PopulatePlugin::getOrder))
            .collect(Collectors.toList());
    }

    private boolean matchesClient(PopulatePlugin plugin, String client) {
        String pluginClient = plugin.getClientCode();
        if ("STANDARD".equals(pluginClient) || "*".equals(pluginClient)) {
            return true;
        }
        return pluginClient.equalsIgnoreCase(client);
    }

    /**
     * Get registered plugin info for diagnostics.
     */
    public Map<String, List<String>> getRegisteredPlugins() {
        Map<String, List<String>> info = new HashMap<>();

        info.put("prePopulate", prePlugins.stream()
            .map(p -> p.getClass().getSimpleName() + " [" + p.getClientCode() + "]")
            .collect(Collectors.toList()));

        info.put("postPopulate", postPlugins.stream()
            .map(p -> p.getClass().getSimpleName() + " [" + p.getClientCode() + "]")
            .collect(Collectors.toList()));

        return info;
    }
}
