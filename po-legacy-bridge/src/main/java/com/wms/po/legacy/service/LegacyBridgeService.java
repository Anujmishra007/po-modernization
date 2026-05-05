package com.wms.po.legacy.service;

import com.wms.po.domain.model.VariationContext;
import com.wms.po.legacy.adapter.V0Adapter;
import com.wms.po.legacy.adapter.V2Adapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for bridging between new system and legacy V0/V2 systems
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyBridgeService {

    private final V0Adapter v0Adapter;
    private final V2Adapter v2Adapter;

    /**
     * Sync receipt to appropriate legacy system based on context
     */
    public void syncReceipt(String receiptKey, VariationContext context) {
        log.info("Syncing receipt {} to legacy system (version={})", receiptKey, context.getVersion());

        if (context.isV0()) {
            v0Adapter.syncReceipt(receiptKey);
        } else {
            v2Adapter.syncReceipt(receiptKey);
        }
    }

    /**
     * Rollback receipt from appropriate legacy system
     */
    public void rollbackReceipt(String receiptKey, VariationContext context) {
        log.warn("Rolling back receipt {} from legacy system (version={})", receiptKey, context.getVersion());

        if (context.isV0()) {
            v0Adapter.rollbackReceipt(receiptKey);
        } else {
            v2Adapter.rollbackReceipt(receiptKey);
        }
    }

    /**
     * Verify consistency with legacy system
     */
    public boolean verifyConsistency(String receiptKey, VariationContext context) {
        if (context.isV0()) {
            return v0Adapter.verifyConsistency(receiptKey);
        } else {
            return v2Adapter.verifyConsistency(receiptKey);
        }
    }

    /**
     * Execute legacy populate for shadow run comparison
     */
    public void executeLegacyPopulate(String poKey, String storerKey, String facility, VariationContext context) {
        if (context.isV0()) {
            v0Adapter.callLegacyPopulate(poKey, storerKey, facility);
        } else {
            v2Adapter.callLegacyPopulate(poKey, storerKey, facility);
        }
    }
}
