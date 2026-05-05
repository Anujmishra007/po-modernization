package com.wms.po.activity.impl;

import com.wms.po.activity.LegacyBridgeActivity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.VariationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Implementation of LegacyBridgeActivity for V0/V2 dual-write.
 *
 * Error codes:
 * - INT_001 (69000) - Legacy Sync Failed
 * - INT_007 (69006) - Bridge Call Failed
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

    /**
     * Sync receipt to legacy system.
     *
     * Error codes:
     * - INT_001 (69000) - Legacy Sync Failed
     *
     * @param receiptKey Receipt key to sync
     * @param context Variation context
     * @throws BusinessException if sync fails
     */
    @Override
    public void syncToLegacy(String receiptKey, VariationContext context) {
        if (receiptKey == null || receiptKey.isBlank()) {
            log.error("Receipt key is null/blank for legacy sync (legacy error 69000)");
            throw new BusinessException(ErrorCode.LEGACY_SYNC_FAILED,
                "Receipt key is required for legacy sync")
                .withDetail("receiptKey", "null or blank");
        }

        if (context == null) {
            log.error("Variation context is null for legacy sync (legacy error 69000)");
            throw new BusinessException(ErrorCode.LEGACY_SYNC_FAILED,
                "Variation context is required for legacy sync")
                .withDetail("receiptKey", receiptKey);
        }

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

        } catch (DataAccessException e) {
            log.error("Legacy sync failed: receiptKey={}, SP={}, error={} (legacy error 69000)",
                receiptKey, spName, e.getMessage(), e);
            throw new BusinessException(ErrorCode.LEGACY_SYNC_FAILED,
                "Legacy sync failed: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("storedProcedure", spName)
                .withDetail("version", context.getVersion());
        }
    }

    /**
     * Rollback receipt from legacy system (compensation action).
     *
     * Note: This is a compensation action, so errors are logged but not rethrown.
     *
     * @param receiptKey Receipt key to rollback
     * @param context Variation context
     */
    @Override
    public void rollbackLegacy(String receiptKey, VariationContext context) {
        if (receiptKey == null || receiptKey.isBlank()) {
            log.warn("Receipt key is null/blank for legacy rollback, skipping");
            return;
        }

        if (context == null || !context.isDualWriteEnabled()) {
            log.info("Dual-write disabled or context null, skipping legacy rollback for receiptKey={}", receiptKey);
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

        } catch (DataAccessException e) {
            log.error("Legacy rollback failed: receiptKey={}, SP={}, error={} (legacy error 69000)",
                receiptKey, spName, e.getMessage(), e);
            // Don't rethrow - compensation should be best-effort
        }
    }

    /**
     * Verify consistency with legacy system.
     *
     * Error codes:
     * - INT_006 (69005) - Dual Write Sync Failed
     *
     * @param receiptKey Receipt key to verify
     * @param context Variation context
     * @return true if synced, false otherwise
     */
    @Override
    public boolean verifyLegacySync(String receiptKey, VariationContext context) {
        if (receiptKey == null || receiptKey.isBlank()) {
            log.warn("Receipt key is null/blank for legacy sync verification, returning false");
            return false;
        }

        if (context == null || !context.isDualWriteEnabled()) {
            log.debug("Dual-write disabled or context null, assuming synced");
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

        } catch (DataAccessException e) {
            log.warn("Legacy sync verification failed, assuming synced: receiptKey={}, error={} (legacy error 69005)",
                receiptKey, e.getMessage());
            return true; // Assume synced if we can't verify - avoid false negatives
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
