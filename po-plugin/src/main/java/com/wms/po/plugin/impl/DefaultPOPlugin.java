package com.wms.po.plugin.impl;

import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.plugin.api.POPlugin;
import com.wms.po.plugin.api.PluginResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default PO plugin - applies when no other plugin matches
 */
@Component
@Slf4j
public class DefaultPOPlugin implements POPlugin {

    @Override
    public String getPluginId() {
        return "DEFAULT";
    }

    @Override
    public boolean appliesTo(VariationContext context) {
        return true; // Default applies to all
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE; // Run last
    }

    @Override
    public PluginResult prePopulate(PopulateRequest request, VariationContext context) {
        log.debug("Default plugin pre-populate for storer: {}", request.getStorerKey());

        // Basic validations
        if (request.getPoKeys() == null || request.getPoKeys().isEmpty()) {
            return PluginResult.failure("At least one PO key is required");
        }

        if (request.getStorerKey() == null || request.getStorerKey().isEmpty()) {
            return PluginResult.failure("Storer key is required");
        }

        if (request.getFacility() == null || request.getFacility().isEmpty()) {
            return PluginResult.failure("Facility is required");
        }

        return PluginResult.success("Default validation passed");
    }

    @Override
    public void postPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        log.debug("Default plugin post-populate for receipt: {}", receiptKey);
    }

    @Override
    public void onError(String error, PopulateRequest request, VariationContext context) {
        log.warn("Default plugin error handler: {}", error);
    }
}
