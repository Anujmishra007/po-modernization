package com.wms.po.plugin.region;

import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Singapore region-specific plugin
 */
@Component
@Slf4j
public class SingaporeRegionPlugin implements RegionPlugin {

    @Override
    public String getRegionCode() {
        return "ASIA-SG";
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public PluginResult prePopulate(PopulateRequest request, VariationContext context) {
        log.info("Singapore plugin: Running pre-populate checks");

        // Singapore-specific validations
        // 1. Trade permit validation
        // 2. GST registration check

        return PluginResult.success();
    }

    @Override
    public void postPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        log.info("Singapore plugin: Post-populate for receipt {}", receiptKey);
        // Singapore-specific post-populate actions
    }
}
