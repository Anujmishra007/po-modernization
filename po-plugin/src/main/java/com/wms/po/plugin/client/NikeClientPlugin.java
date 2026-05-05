package com.wms.po.plugin.client;

import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Nike-specific client plugin
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NikeClientPlugin implements ClientPlugin {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public String getClientCode() {
        return "NIKE";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public PluginResult prePopulate(PopulateRequest request, VariationContext context) {
        log.info("Nike plugin: Running pre-populate checks");

        // Nike-specific validations
        // 1. Validate style codes
        // 2. Check order matching
        // 3. Verify Nike-specific lottable requirements

        try {
            // Check if PO has matching Nike order
            for (String poKey : request.getPoKeys()) {
                boolean hasOrder = checkNikeOrder(poKey);
                if (!hasOrder) {
                    log.warn("Nike: No matching Nike order for PO {}", poKey);
                    // Warning only, don't fail
                }
            }

            // Validate shelf life requirements (Nike requires 90+ days)
            // In production, check SKU shelf life

            return PluginResult.success();

        } catch (Exception e) {
            log.warn("Nike plugin check failed, continuing: {}", e.getMessage());
            return PluginResult.success();
        }
    }

    @Override
    public void postPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        log.info("Nike plugin: Running post-populate actions for receipt {}", receiptKey);

        try {
            // Send Nike-specific notifications
            // Update Nike order status
            // Log to Nike integration table

        } catch (Exception e) {
            log.warn("Nike post-populate action failed: {}", e.getMessage());
        }
    }

    private boolean checkNikeOrder(String poKey) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ORDERS WHERE EXTERNORDERKEY LIKE 'NIKE%' AND EXTERNPOKEY = ?",
                Integer.class, poKey);
            return count != null && count > 0;
        } catch (Exception e) {
            return true; // Assume valid if can't check
        }
    }
}
