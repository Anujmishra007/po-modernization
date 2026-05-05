package com.wms.po.plugin.populate;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
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
 *
 * Error codes:
 * - PLG_040 (69540) - Pre-Populate Hook Failed
 * - PLG_002 (69501) - Plugin Execution Failed
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
     * Error codes:
     * - PLG_040 (69540) - Pre-Populate Hook Failed
     *
     * @param request The populate request
     * @param context The context
     * @return Combined result
     * @throws BusinessException if a critical plugin fails
     */
    public PluginResult executePrePopulate(PopulateRequest request, PopulateContext context) {
        if (request == null) {
            log.error("Populate request is null for pre-populate plugins (legacy error 69540)");
            throw new BusinessException(ErrorCode.PRE_POPULATE_HOOK_FAILED,
                "Populate request is required for pre-populate plugins")
                .withDetail("request", "null");
        }

        if (context == null) {
            log.error("Populate context is null for pre-populate plugins (legacy error 69540)");
            throw new BusinessException(ErrorCode.PRE_POPULATE_HOOK_FAILED,
                "Populate context is required for pre-populate plugins")
                .withDetail("context", "null");
        }

        log.info("Running pre-populate plugins for client={}, region={}",
            context.getClient(), context.getRegion());

        List<PrePopulatePlugin> applicable = getApplicablePrePlugins(context);
        List<String> failedPlugins = new ArrayList<>();

        for (PrePopulatePlugin plugin : applicable) {
            try {
                log.debug("Executing pre-populate plugin: {}", plugin.getClass().getSimpleName());

                PluginResult result = plugin.prePopulate(request, context);

                if (!result.isShouldContinue()) {
                    log.warn("Plugin {} stopped workflow: {} (legacy warning 69540)",
                        plugin.getClass().getSimpleName(), result.getMessage());
                    return result;
                }

            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Plugin {} failed: {} (legacy error 69540)",
                    plugin.getClass().getSimpleName(), e.getMessage(), e);
                failedPlugins.add(plugin.getClass().getSimpleName());
                // Continue with other plugins unless configured otherwise
            }
        }

        if (!failedPlugins.isEmpty()) {
            log.warn("Pre-populate completed with {} plugin failures: {}", failedPlugins.size(), failedPlugins);
        }

        return PluginResult.success();
    }

    /**
     * Execute all applicable post-populate plugins.
     *
     * Error codes:
     * - PLG_002 (69501) - Plugin Execution Failed (logged, continues)
     *
     * @param receiptKey The created receipt key
     * @param request The populate request
     * @param context The context
     */
    public void executePostPopulate(String receiptKey, PopulateRequest request, PopulateContext context) {
        if (receiptKey == null || receiptKey.isBlank()) {
            log.warn("Receipt key is null/blank for post-populate plugins, skipping");
            return;
        }

        log.info("Running post-populate plugins for receiptKey={}", receiptKey);

        List<PostPopulatePlugin> applicable = getApplicablePostPlugins(context);
        int successCount = 0;
        int failureCount = 0;

        for (PostPopulatePlugin plugin : applicable) {
            try {
                log.debug("Executing post-populate plugin: {}", plugin.getClass().getSimpleName());
                plugin.postPopulate(receiptKey, request, context);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("Plugin {} failed in post-populate for receipt {}: {} (legacy error 69501)",
                    plugin.getClass().getSimpleName(), receiptKey, e.getMessage(), e);
                // Continue with other plugins
            }
        }

        log.info("Post-populate plugins completed for receipt {}: {} succeeded, {} failed",
            receiptKey, successCount, failureCount);
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
