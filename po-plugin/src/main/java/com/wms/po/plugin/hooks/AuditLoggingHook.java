package com.wms.po.plugin.hooks;

import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Audit logging hook - logs all workflow events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLoggingHook implements LifecycleHook {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public int getOrder() {
        return 1; // Run first
    }

    @Override
    public PluginResult onPrePopulate(PopulateRequest request, VariationContext context) {
        log.info("AUDIT: Pre-populate started - poKeys={}, storer={}, facility={}, user={}",
            request.getPoKeys(), request.getStorerKey(), request.getFacility(), request.getUserId());

        try {
            logAuditEvent("PRE_POPULATE", request, context, null);
        } catch (Exception e) {
            log.warn("Audit logging failed: {}", e.getMessage());
        }

        return PluginResult.success();
    }

    @Override
    public void onPostPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        log.info("AUDIT: Populate completed - receiptKey={}, poKeys={}, storer={}",
            receiptKey, request.getPoKeys(), request.getStorerKey());

        try {
            logAuditEvent("POST_POPULATE", request, context, receiptKey);
        } catch (Exception e) {
            log.warn("Audit logging failed: {}", e.getMessage());
        }
    }

    @Override
    public void onError(String error, PopulateRequest request, VariationContext context) {
        log.error("AUDIT: Populate failed - poKeys={}, error={}",
            request.getPoKeys(), error);

        try {
            logAuditEvent("ERROR", request, context, error);
        } catch (Exception e) {
            log.warn("Audit logging failed: {}", e.getMessage());
        }
    }

    @Override
    public void onCancelled(PopulateRequest request, VariationContext context) {
        log.warn("AUDIT: Populate cancelled - poKeys={}", request.getPoKeys());

        try {
            logAuditEvent("CANCELLED", request, context, null);
        } catch (Exception e) {
            log.warn("Audit logging failed: {}", e.getMessage());
        }
    }

    private void logAuditEvent(String eventType, PopulateRequest request,
                               VariationContext context, String details) {
        try {
            jdbcTemplate.update(
                "INSERT INTO POPAUDITLOG (EVENTTYPE, POKEYS, STORERKEY, FACILITY, USERID, VERSION, REGION, CLIENT, DETAILS, ADDDATE) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventType,
                String.join(",", request.getPoKeys()),
                request.getStorerKey(),
                request.getFacility(),
                request.getUserId(),
                context.getVersion(),
                context.getRegion(),
                context.getClient(),
                details,
                LocalDateTime.now()
            );
        } catch (Exception e) {
            // Audit table may not exist
            log.debug("Could not write to audit log table: {}", e.getMessage());
        }
    }
}
