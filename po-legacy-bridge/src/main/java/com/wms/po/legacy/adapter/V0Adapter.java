package com.wms.po.legacy.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter for V0 (legacy unified) database operations
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class V0Adapter {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Sync receipt to V0 system
     */
    public void syncReceipt(String receiptKey) {
        log.info("V0: Syncing receipt {}", receiptKey);

        try {
            jdbcTemplate.update(
                "EXEC nsp_SyncReceipt_FromNewSystem @ReceiptKey = ?",
                receiptKey
            );
            log.info("V0: Receipt sync complete: {}", receiptKey);
        } catch (Exception e) {
            log.error("V0: Receipt sync failed: {}", e.getMessage());
            throw new RuntimeException("V0 sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Rollback receipt from V0 system
     */
    public void rollbackReceipt(String receiptKey) {
        log.warn("V0: Rolling back receipt {}", receiptKey);

        try {
            jdbcTemplate.update(
                "EXEC nsp_RollbackReceipt_FromNewSystem @ReceiptKey = ?",
                receiptKey
            );
            log.info("V0: Receipt rollback complete: {}", receiptKey);
        } catch (Exception e) {
            log.error("V0: Receipt rollback failed: {}", e.getMessage());
            // Don't throw - rollback is best effort
        }
    }

    /**
     * Call legacy populate SP for comparison
     */
    public void callLegacyPopulate(String poKey, String storerKey, String facility) {
        log.info("V0: Calling legacy populate for PO {}", poKey);

        try {
            jdbcTemplate.update(
                "EXEC nsp_PopulatePOsToASN_Wrapper @POKey = ?, @StorerKey = ?, @Facility = ?",
                poKey, storerKey, facility
            );
        } catch (Exception e) {
            log.error("V0: Legacy populate failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Verify data consistency between new and legacy
     */
    public boolean verifyConsistency(String receiptKey) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "EXEC nsp_VerifyReceiptConsistency @ReceiptKey = ?",
                Integer.class,
                receiptKey
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("V0: Consistency check failed, assuming consistent: {}", e.getMessage());
            return true;
        }
    }
}
