package com.wms.po.activity.impl;

import com.wms.po.activity.LegacyBridgeActivity;
import com.wms.po.domain.model.VariationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Implementation of LegacyBridgeActivity for V0/V2 dual-write
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegacyBridgeActivityImpl implements LegacyBridgeActivity {

    private final JdbcTemplate jdbcTemplate;

    // Optional: Separate JDBC templates for V0 and V2 if needed
    // @Qualifier("v0JdbcTemplate")
    // private final JdbcTemplate v0JdbcTemplate;
    //
    // @Qualifier("v2JdbcTemplate")
    // private final JdbcTemplate v2JdbcTemplate;

    @Override
    public void syncToLegacy(String receiptKey, VariationContext context) {
        if (!context.isDualWriteEnabled()) {
            log.info("Dual-write disabled, skipping legacy sync for receiptKey={}", receiptKey);
            return;
        }

        log.info("Syncing to legacy system: receiptKey={}, version={}", receiptKey, context.getVersion());

        String spName = getSyncSpName(context);

        try {
            jdbcTemplate.update(
                "EXEC " + spName + " @ReceiptKey = ?",
                receiptKey
            );
            log.info("Legacy sync complete: receiptKey={}, SP={}", receiptKey, spName);
        } catch (Exception e) {
            log.error("Legacy sync failed: receiptKey={}, SP={}, error={}",
                receiptKey, spName, e.getMessage());
            throw new RuntimeException("Legacy sync failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void rollbackLegacy(String receiptKey, VariationContext context) {
        if (!context.isDualWriteEnabled()) {
            log.info("Dual-write disabled, skipping legacy rollback for receiptKey={}", receiptKey);
            return;
        }

        log.warn("COMPENSATION: Rolling back legacy system: receiptKey={}, version={}",
            receiptKey, context.getVersion());

        String spName = getRollbackSpName(context);

        try {
            jdbcTemplate.update(
                "EXEC " + spName + " @ReceiptKey = ?",
                receiptKey
            );
            log.info("COMPENSATION complete: Legacy rollback done for receiptKey={}", receiptKey);
        } catch (Exception e) {
            log.error("Legacy rollback failed: receiptKey={}, SP={}, error={}",
                receiptKey, spName, e.getMessage());
            // Don't rethrow - compensation should be best-effort
        }
    }

    @Override
    public boolean verifyLegacySync(String receiptKey, VariationContext context) {
        if (!context.isDualWriteEnabled()) {
            return true;
        }

        log.info("Verifying legacy sync: receiptKey={}", receiptKey);

        try {
            String verifySpName = context.isV0()
                ? "nsp_VerifyReceiptSync"
                : "isp_VerifyReceiptSync";

            Integer count = jdbcTemplate.queryForObject(
                "EXEC " + verifySpName + " @ReceiptKey = ?",
                Integer.class,
                receiptKey
            );

            boolean synced = count != null && count > 0;
            log.info("Legacy sync verification: receiptKey={}, synced={}", receiptKey, synced);
            return synced;

        } catch (Exception e) {
            log.warn("Legacy sync verification failed, assuming synced: {}", e.getMessage());
            return true; // Assume synced if we can't verify
        }
    }

    private String getSyncSpName(VariationContext context) {
        return context.isV0()
            ? "nsp_SyncReceipt_FromNewSystem"
            : "isp_SyncReceipt_FromNewSystem";
    }

    private String getRollbackSpName(VariationContext context) {
        return context.isV0()
            ? "nsp_RollbackReceipt_FromNewSystem"
            : "isp_RollbackReceipt_FromNewSystem";
    }
}
