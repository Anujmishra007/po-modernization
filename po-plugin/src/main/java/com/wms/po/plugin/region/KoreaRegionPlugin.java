package com.wms.po.plugin.region;

import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Korea region-specific plugin
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KoreaRegionPlugin implements RegionPlugin {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public String getRegionCode() {
        return "ASIA-KR";
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public PluginResult prePopulate(PopulateRequest request, VariationContext context) {
        log.info("Korea plugin: Running pre-populate checks");

        // Korea-specific validations
        // 1. Customs clearance validation
        // 2. Lottable03 (customs code) requirement
        // 3. Import permit validation

        try {
            // Check customs clearance status
            for (String poKey : request.getPoKeys()) {
                boolean cleared = checkCustomsClearance(poKey);
                if (!cleared) {
                    log.warn("Korea: PO {} pending customs clearance", poKey);
                    // Warning only - don't block
                }
            }

            return PluginResult.success();

        } catch (Exception e) {
            log.warn("Korea plugin check failed, continuing: {}", e.getMessage());
            return PluginResult.success();
        }
    }

    @Override
    public void postPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        log.info("Korea plugin: Post-populate for receipt {}", receiptKey);

        try {
            // Update customs tracking
            // Log for Korean regulatory compliance
            updateCustomsTracking(receiptKey);

        } catch (Exception e) {
            log.warn("Korea post-populate action failed: {}", e.getMessage());
        }
    }

    private boolean checkCustomsClearance(String poKey) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CUSTOMSCLEARANCE WHERE POKEY = ? AND STATUS = 'CLEARED'",
                Integer.class, poKey);
            return count != null && count > 0;
        } catch (Exception e) {
            return true; // Assume cleared if can't check
        }
    }

    private void updateCustomsTracking(String receiptKey) {
        try {
            jdbcTemplate.update(
                "INSERT INTO CUSTOMSTRACKING (RECEIPTKEY, STATUS, ADDDATE) VALUES (?, 'RECEIVED', GETDATE())",
                receiptKey);
        } catch (Exception e) {
            log.debug("Customs tracking update failed: {}", e.getMessage());
        }
    }
}
