package com.wms.po.legacy.service;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.legacy.adapter.V0Adapter;
import com.wms.po.legacy.adapter.V2Adapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for bridging between new system and legacy V0/V2 systems.
 *
 * Error codes:
 * - INT_001 (69000) - Legacy Sync Failed
 * - INT_006 (69005) - Dual Write Sync Failed
 * - INT_007 (69006) - Bridge Call Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyBridgeService {

    private final V0Adapter v0Adapter;
    private final V2Adapter v2Adapter;

    /**
     * Sync receipt to appropriate legacy system based on context.
     *
     * Error codes:
     * - INT_001 (69000) - Legacy Sync Failed
     *
     * @param receiptKey Receipt key to sync
     * @param context Variation context
     * @throws BusinessException if sync fails
     */
    public void syncReceipt(String receiptKey, VariationContext context) {
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
                .withDetail("receiptKey", receiptKey)
                .withDetail("context", "null");
        }

        log.info("Syncing receipt {} to legacy system (version={})", receiptKey, context.getVersion());

        try {
            if (context.isV0()) {
                v0Adapter.syncReceipt(receiptKey);
            } else {
                v2Adapter.syncReceipt(receiptKey);
            }
            log.info("Successfully synced receipt {} to legacy system", receiptKey);

        } catch (Exception e) {
            log.error("Failed to sync receipt {} to legacy system: {} (legacy error 69000)",
                receiptKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.LEGACY_SYNC_FAILED,
                "Failed to sync receipt to legacy system: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("version", context.getVersion());
        }
    }

    /**
     * Rollback receipt from appropriate legacy system.
     *
     * Error codes:
     * - INT_001 (69000) - Legacy Sync Failed
     *
     * @param receiptKey Receipt key to rollback
     * @param context Variation context
     * @throws BusinessException if rollback fails
     */
    public void rollbackReceipt(String receiptKey, VariationContext context) {
        if (receiptKey == null || receiptKey.isBlank()) {
            log.warn("Receipt key is null/blank for legacy rollback, skipping");
            return;
        }

        log.warn("Rolling back receipt {} from legacy system (version={})", receiptKey,
            context != null ? context.getVersion() : "null");

        try {
            if (context != null && context.isV0()) {
                v0Adapter.rollbackReceipt(receiptKey);
            } else {
                v2Adapter.rollbackReceipt(receiptKey);
            }
            log.info("Successfully rolled back receipt {} from legacy system", receiptKey);

        } catch (Exception e) {
            log.error("Failed to rollback receipt {} from legacy system: {} (legacy error 69000)",
                receiptKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.LEGACY_SYNC_FAILED,
                "Failed to rollback receipt from legacy system: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("version", context != null ? context.getVersion() : "unknown");
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
     * @return true if consistent, false otherwise
     * @throws BusinessException if verification fails
     */
    public boolean verifyConsistency(String receiptKey, VariationContext context) {
        if (receiptKey == null || receiptKey.isBlank()) {
            log.error("Receipt key is null/blank for consistency verification (legacy error 69005)");
            throw new BusinessException(ErrorCode.DUAL_WRITE_SYNC_FAILED,
                "Receipt key is required for consistency verification")
                .withDetail("receiptKey", "null or blank");
        }

        if (context == null) {
            log.error("Variation context is null for consistency verification (legacy error 69005)");
            throw new BusinessException(ErrorCode.DUAL_WRITE_SYNC_FAILED,
                "Variation context is required for consistency verification")
                .withDetail("receiptKey", receiptKey);
        }

        try {
            if (context.isV0()) {
                return v0Adapter.verifyConsistency(receiptKey);
            } else {
                return v2Adapter.verifyConsistency(receiptKey);
            }
        } catch (Exception e) {
            log.error("Consistency verification failed for receipt {}: {} (legacy error 69005)",
                receiptKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.DUAL_WRITE_SYNC_FAILED,
                "Consistency verification failed: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("version", context.getVersion());
        }
    }

    /**
     * Execute legacy populate for shadow run comparison.
     *
     * Error codes:
     * - INT_007 (69006) - Bridge Call Failed
     *
     * @param poKey PO key
     * @param storerKey Storer key
     * @param facility Facility code
     * @param context Variation context
     * @throws BusinessException if legacy populate fails
     */
    public void executeLegacyPopulate(String poKey, String storerKey, String facility, VariationContext context) {
        if (poKey == null || poKey.isBlank()) {
            log.error("PO key is null/blank for legacy populate (legacy error 69006)");
            throw new BusinessException(ErrorCode.BRIDGE_CALL_FAILED,
                "PO key is required for legacy populate")
                .withDetail("poKey", "null or blank");
        }

        if (context == null) {
            log.error("Variation context is null for legacy populate (legacy error 69006)");
            throw new BusinessException(ErrorCode.BRIDGE_CALL_FAILED,
                "Variation context is required for legacy populate")
                .withDetail("poKey", poKey);
        }

        log.info("Executing legacy populate for PO {} (version={})", poKey, context.getVersion());

        try {
            if (context.isV0()) {
                v0Adapter.callLegacyPopulate(poKey, storerKey, facility);
            } else {
                v2Adapter.callLegacyPopulate(poKey, storerKey, facility);
            }
            log.info("Legacy populate completed for PO {}", poKey);

        } catch (Exception e) {
            log.error("Legacy populate failed for PO {}: {} (legacy error 69006)",
                poKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.BRIDGE_CALL_FAILED,
                "Legacy populate failed: " + e.getMessage(), e)
                .withDetail("poKey", poKey)
                .withDetail("storerKey", storerKey)
                .withDetail("facility", facility)
                .withDetail("version", context.getVersion());
        }
    }
}
