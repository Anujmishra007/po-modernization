package com.wms.po.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing inventory holds.
 *
 * Implements the logic from nspInventoryHoldWrapper stored procedure.
 *
 * Hold Types:
 * - QC: Quality control inspection required
 * - CUSTOMS: Customs clearance required
 * - QUARANTINE: Quarantine period for food/perishables
 * - DAMAGE: Damaged goods hold
 * - SUPPLIER: Supplier-initiated hold
 * - CLIENT: Client-specific hold
 *
 * Hold Behavior:
 * - blockPutaway: Prevents putaway task release
 * - blockAllocation: Prevents order allocation
 * - expiryDate: Auto-release date (optional)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryHoldService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;

    // Hold type constants
    public static final String HOLD_TYPE_QC = "QC";
    public static final String HOLD_TYPE_CUSTOMS = "CUSTOMS";
    public static final String HOLD_TYPE_QUARANTINE = "QUARANTINE";
    public static final String HOLD_TYPE_DAMAGE = "DAMAGE";
    public static final String HOLD_TYPE_SUPPLIER = "SUPPLIER";
    public static final String HOLD_TYPE_CLIENT = "CLIENT";

    // Status constants
    public static final String HOLD_STATUS_ACTIVE = "1";
    public static final String HOLD_STATUS_RELEASED = "9";
    public static final String HOLD_STATUS_CANCELLED = "C";

    /**
     * Create a hold record for an inventory item.
     *
     * @param request Hold creation request
     * @return Created hold ID
     */
    @Transactional
    public String createHold(HoldCreateRequest request) {
        log.info("Creating hold: type={}, inventory={}", request.getHoldType(), request.getInventoryId());

        String holdKey = keyGeneratorService.generateKey("INVENTORYHOLD");

        jdbcTemplate.update(
            """
            INSERT INTO dbo.inventoryhold (
                holdkey, storerkey, sku, lot, loc, id,
                holdcode, holdtype, holdreason, status,
                holddate, holdwho, releasedate, releasewho,
                notes, qty, blockpick, blockputaway,
                lottable01, lottable02, lottable03, lottable04, lottable05,
                lottable06, lottable07, lottable08, lottable09, lottable10,
                adddate, addwho, editdate, editwho
            )
            SELECT
                ?, l.storerkey, l.sku, l.lot, l.loc, l.id,
                ?, ?, ?, '1',
                CURRENT_TIMESTAMP, ?, NULL, NULL,
                ?, l.qty, ?, ?,
                l.lottable01, l.lottable02, l.lottable03, l.lottable04, l.lottable05,
                l.lottable06, l.lottable07, l.lottable08, l.lottable09, l.lottable10,
                CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?
            FROM dbo.lotxlocxid l
            WHERE l.lotxlocxidkey = ?
            """,
            holdKey,
            request.getHoldCode(),
            request.getHoldType(),
            request.getHoldReason(),
            request.getUserId(),
            request.getNotes(),
            request.isBlockAllocation() ? "1" : "0",
            request.isBlockPutaway() ? "1" : "0",
            request.getUserId(),
            request.getUserId(),
            request.getInventoryId()
        );

        // Update the inventory record to mark as held
        updateInventoryHoldFlag(request.getInventoryId(), true);

        log.info("Created hold: {} for inventory {}", holdKey, request.getInventoryId());
        return holdKey;
    }

    /**
     * Create multiple holds for a batch of inventory records.
     *
     * @param inventoryIds Inventory IDs to hold
     * @param holdSpec Hold specification
     * @param userId User creating the holds
     * @return List of created hold IDs
     */
    @Transactional
    public List<String> createBatchHolds(
            List<String> inventoryIds,
            HoldSpec holdSpec,
            String userId) {

        log.info("Creating batch holds: {} inventory records, type={}",
            inventoryIds.size(), holdSpec.getHoldType());

        List<String> holdIds = new ArrayList<>();

        for (String inventoryId : inventoryIds) {
            HoldCreateRequest request = HoldCreateRequest.builder()
                .inventoryId(inventoryId)
                .holdCode(holdSpec.getHoldCode())
                .holdType(holdSpec.getHoldType())
                .holdReason(holdSpec.getHoldReason())
                .blockAllocation(holdSpec.isBlockAllocation())
                .blockPutaway(holdSpec.isBlockPutaway())
                .notes(holdSpec.getNotes())
                .userId(userId)
                .build();

            String holdId = createHold(request);
            holdIds.add(holdId);
        }

        log.info("Created {} batch holds", holdIds.size());
        return holdIds;
    }

    /**
     * Release a hold.
     *
     * @param holdKey Hold to release
     * @param userId User releasing the hold
     * @param reason Reason for release
     * @return true if released successfully
     */
    @Transactional
    public boolean releaseHold(String holdKey, String userId, String reason) {
        log.info("Releasing hold: {}", holdKey);

        // Get the inventory ID before updating
        String inventoryId = getInventoryIdForHold(holdKey);

        int updated = jdbcTemplate.update(
            """
            UPDATE dbo.inventoryhold
            SET status = ?,
                releasedate = CURRENT_TIMESTAMP,
                releasewho = ?,
                notes = COALESCE(notes, '') || ' Release: ' || ?,
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE holdkey = ?
            AND status = ?
            """,
            HOLD_STATUS_RELEASED,
            userId,
            reason,
            userId,
            holdKey,
            HOLD_STATUS_ACTIVE
        );

        if (updated > 0 && inventoryId != null) {
            // Check if there are other active holds on this inventory
            if (!hasActiveHolds(inventoryId)) {
                updateInventoryHoldFlag(inventoryId, false);
            }
        }

        log.info("Released hold: {} (updated={})", holdKey, updated > 0);
        return updated > 0;
    }

    /**
     * Release multiple holds (for compensation).
     *
     * @param holdKeys Holds to release
     * @param userId User releasing
     * @param reason Reason for release
     * @return Number of holds released
     */
    @Transactional
    public int releaseHolds(List<String> holdKeys, String userId, String reason) {
        log.info("Releasing {} holds: {}", holdKeys.size(), reason);

        int releasedCount = 0;
        for (String holdKey : holdKeys) {
            if (releaseHold(holdKey, userId, reason)) {
                releasedCount++;
            }
        }

        log.info("Released {}/{} holds", releasedCount, holdKeys.size());
        return releasedCount;
    }

    /**
     * Cancel a hold (different from release - indicates error/invalid hold).
     *
     * @param holdKey Hold to cancel
     * @param userId User cancelling
     * @param reason Reason for cancellation
     * @return true if cancelled
     */
    @Transactional
    public boolean cancelHold(String holdKey, String userId, String reason) {
        log.info("Cancelling hold: {} - {}", holdKey, reason);

        String inventoryId = getInventoryIdForHold(holdKey);

        int updated = jdbcTemplate.update(
            """
            UPDATE dbo.inventoryhold
            SET status = ?,
                notes = COALESCE(notes, '') || ' CANCELLED: ' || ?,
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE holdkey = ?
            AND status = ?
            """,
            HOLD_STATUS_CANCELLED,
            reason,
            userId,
            holdKey,
            HOLD_STATUS_ACTIVE
        );

        if (updated > 0 && inventoryId != null && !hasActiveHolds(inventoryId)) {
            updateInventoryHoldFlag(inventoryId, false);
        }

        return updated > 0;
    }

    /**
     * Check if inventory has any active holds.
     *
     * @param inventoryId Inventory to check
     * @return true if has active holds
     */
    @Transactional(readOnly = true)
    public boolean hasActiveHolds(String inventoryId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM dbo.inventoryhold h
            JOIN dbo.lotxlocxid l ON h.storerkey = l.storerkey
                AND h.sku = l.sku AND h.lot = l.lot
                AND h.loc = l.loc AND h.id = l.id
            WHERE l.lotxlocxidkey = ?
            AND h.status = '1'
            """,
            Integer.class,
            inventoryId
        );
        return count != null && count > 0;
    }

    /**
     * Check if inventory is blocked for allocation.
     *
     * @param inventoryId Inventory to check
     * @return true if allocation is blocked
     */
    @Transactional(readOnly = true)
    public boolean isBlockedForAllocation(String inventoryId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM dbo.inventoryhold h
            JOIN dbo.lotxlocxid l ON h.storerkey = l.storerkey
                AND h.sku = l.sku AND h.lot = l.lot
                AND h.loc = l.loc AND h.id = l.id
            WHERE l.lotxlocxidkey = ?
            AND h.status = '1'
            AND h.blockpick = '1'
            """,
            Integer.class,
            inventoryId
        );
        return count != null && count > 0;
    }

    /**
     * Check if inventory is blocked for putaway.
     *
     * @param inventoryId Inventory to check
     * @return true if putaway is blocked
     */
    @Transactional(readOnly = true)
    public boolean isBlockedForPutaway(String inventoryId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM dbo.inventoryhold h
            JOIN dbo.lotxlocxid l ON h.storerkey = l.storerkey
                AND h.sku = l.sku AND h.lot = l.lot
                AND h.loc = l.loc AND h.id = l.id
            WHERE l.lotxlocxidkey = ?
            AND h.status = '1'
            AND h.blockputaway = '1'
            """,
            Integer.class,
            inventoryId
        );
        return count != null && count > 0;
    }

    /**
     * Get all active holds for an inventory item.
     *
     * @param inventoryId Inventory to check
     * @return List of active holds
     */
    @Transactional(readOnly = true)
    public List<HoldInfo> getActiveHolds(String inventoryId) {
        return jdbcTemplate.query(
            """
            SELECT h.holdkey, h.holdcode, h.holdtype, h.holdreason,
                   h.holddate, h.holdwho, h.blockpick, h.blockputaway,
                   h.qty, h.notes
            FROM dbo.inventoryhold h
            JOIN dbo.lotxlocxid l ON h.storerkey = l.storerkey
                AND h.sku = l.sku AND h.lot = l.lot
                AND h.loc = l.loc AND h.id = l.id
            WHERE l.lotxlocxidkey = ?
            AND h.status = '1'
            ORDER BY h.holddate
            """,
            (rs, rowNum) -> HoldInfo.builder()
                .holdKey(rs.getString("holdkey"))
                .holdCode(rs.getString("holdcode"))
                .holdType(rs.getString("holdtype"))
                .holdReason(rs.getString("holdreason"))
                .holdDate(rs.getTimestamp("holddate").toLocalDateTime())
                .holdBy(rs.getString("holdwho"))
                .blockAllocation("1".equals(rs.getString("blockpick")))
                .blockPutaway("1".equals(rs.getString("blockputaway")))
                .quantity(rs.getBigDecimal("qty"))
                .notes(rs.getString("notes"))
                .build(),
            inventoryId
        );
    }

    /**
     * Evaluate which holds should be applied based on rules.
     *
     * @param request Evaluation request
     * @return List of holds to apply
     */
    @Transactional(readOnly = true)
    public List<HoldSpec> evaluateHolds(HoldEvaluationRequest request) {
        log.debug("Evaluating holds for storer={}, sku={}",
            request.getStorerKey(), request.getSku());

        List<HoldSpec> holdsToApply = new ArrayList<>();

        // Check QC hold requirement
        if (requiresQCHold(request.getStorerKey(), request.getSku())) {
            holdsToApply.add(HoldSpec.builder()
                .holdCode("QC")
                .holdType(HOLD_TYPE_QC)
                .holdReason("Quality inspection required per storer configuration")
                .blockAllocation(true)
                .blockPutaway(true)
                .build());
        }

        // Check customs hold requirement
        if (request.isCustomsRequired()) {
            holdsToApply.add(HoldSpec.builder()
                .holdCode("CUSTOMS")
                .holdType(HOLD_TYPE_CUSTOMS)
                .holdReason("Customs clearance required")
                .blockAllocation(true)
                .blockPutaway(false)
                .build());
        }

        // Check quarantine requirement (food items, perishables)
        if (requiresQuarantine(request.getStorerKey(), request.getSku())) {
            int quarantineDays = getQuarantineDays(request.getStorerKey(), request.getSku());
            holdsToApply.add(HoldSpec.builder()
                .holdCode("QUARANTINE")
                .holdType(HOLD_TYPE_QUARANTINE)
                .holdReason("Quarantine period: " + quarantineDays + " days")
                .blockAllocation(true)
                .blockPutaway(true)
                .expiryDate(LocalDateTime.now().plusDays(quarantineDays))
                .build());
        }

        // Check supplier-specific holds
        if (request.getSupplierKey() != null) {
            HoldSpec supplierHold = checkSupplierHold(request.getSupplierKey());
            if (supplierHold != null) {
                holdsToApply.add(supplierHold);
            }
        }

        // Check client-specific holds
        HoldSpec clientHold = checkClientHold(request.getStorerKey(), request.getSku());
        if (clientHold != null) {
            holdsToApply.add(clientHold);
        }

        log.info("Evaluated holds: {} holds to apply", holdsToApply.size());
        return holdsToApply;
    }

    /**
     * Process expired holds (auto-release).
     * Should be called by a scheduled job.
     *
     * @return Number of holds released
     */
    @Transactional
    public int processExpiredHolds() {
        log.info("Processing expired holds");

        List<String> expiredHoldKeys = jdbcTemplate.queryForList(
            """
            SELECT holdkey FROM dbo.inventoryhold
            WHERE status = '1'
            AND expirydate IS NOT NULL
            AND expirydate <= CURRENT_TIMESTAMP
            """,
            String.class
        );

        int releasedCount = 0;
        for (String holdKey : expiredHoldKeys) {
            if (releaseHold(holdKey, "SYSTEM", "Auto-released due to expiry")) {
                releasedCount++;
            }
        }

        log.info("Auto-released {} expired holds", releasedCount);
        return releasedCount;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private void updateInventoryHoldFlag(String inventoryId, boolean held) {
        jdbcTemplate.update(
            """
            UPDATE dbo.lotxlocxid
            SET hold = ?,
                editdate = CURRENT_TIMESTAMP
            WHERE lotxlocxidkey = ?
            """,
            held ? "1" : "0",
            inventoryId
        );
    }

    private String getInventoryIdForHold(String holdKey) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT l.lotxlocxidkey
                FROM dbo.inventoryhold h
                JOIN dbo.lotxlocxid l ON h.storerkey = l.storerkey
                    AND h.sku = l.sku AND h.lot = l.lot
                    AND h.loc = l.loc AND h.id = l.id
                WHERE h.holdkey = ?
                """,
                String.class,
                holdKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private boolean requiresQCHold(String storerKey, String sku) {
        try {
            // Check storer-level QC requirement
            Integer storerQC = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM dbo.codelkup
                WHERE listname = 'QCREQUIRED'
                AND code = ?
                AND value1 = '1'
                """,
                Integer.class,
                storerKey
            );
            if (storerQC != null && storerQC > 0) {
                return true;
            }

            // Check SKU-level QC requirement
            Integer skuQC = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM dbo.sku
                WHERE storerkey = ?
                AND sku = ?
                AND inspectionrequired = '1'
                """,
                Integer.class,
                storerKey, sku
            );
            return skuQC != null && skuQC > 0;

        } catch (Exception e) {
            return false;
        }
    }

    private boolean requiresQuarantine(String storerKey, String sku) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM dbo.sku
                WHERE storerkey = ?
                AND sku = ?
                AND quarantine = '1'
                """,
                Integer.class,
                storerKey, sku
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private int getQuarantineDays(String storerKey, String sku) {
        try {
            Integer days = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(quarantinedays, 7) FROM dbo.sku
                WHERE storerkey = ?
                AND sku = ?
                """,
                Integer.class,
                storerKey, sku
            );
            return days != null ? days : 7;
        } catch (Exception e) {
            return 7; // Default 7 days
        }
    }

    private HoldSpec checkSupplierHold(String supplierKey) {
        try {
            Map<String, Object> hold = jdbcTemplate.queryForMap(
                """
                SELECT holdcode, holdreason
                FROM dbo.codelkup
                WHERE listname = 'SUPPLIERHOLD'
                AND code = ?
                AND status = '1'
                """,
                supplierKey
            );

            if (hold != null && !hold.isEmpty()) {
                return HoldSpec.builder()
                    .holdCode((String) hold.get("holdcode"))
                    .holdType(HOLD_TYPE_SUPPLIER)
                    .holdReason((String) hold.get("holdreason"))
                    .blockAllocation(true)
                    .blockPutaway(true)
                    .build();
            }
        } catch (Exception e) {
            // No supplier hold configured
        }
        return null;
    }

    private HoldSpec checkClientHold(String storerKey, String sku) {
        try {
            Map<String, Object> hold = jdbcTemplate.queryForMap(
                """
                SELECT c.value1 as holdcode, c.value2 as holdreason,
                       c.value3 as blockallocation, c.value4 as blockputaway
                FROM dbo.codelkup c
                WHERE c.listname = 'CLIENTHOLD'
                AND c.code = ?
                AND c.status = '1'
                """,
                storerKey
            );

            if (hold != null && !hold.isEmpty()) {
                return HoldSpec.builder()
                    .holdCode((String) hold.get("holdcode"))
                    .holdType(HOLD_TYPE_CLIENT)
                    .holdReason((String) hold.get("holdreason"))
                    .blockAllocation("1".equals(hold.get("blockallocation")))
                    .blockPutaway("1".equals(hold.get("blockputaway")))
                    .build();
            }
        } catch (Exception e) {
            // No client hold configured
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HoldCreateRequest {
        private String inventoryId;
        private String holdCode;
        private String holdType;
        private String holdReason;
        private boolean blockAllocation;
        private boolean blockPutaway;
        private String notes;
        private String userId;
        private LocalDateTime expiryDate;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HoldSpec {
        private String holdCode;
        private String holdType;
        private String holdReason;
        private boolean blockAllocation;
        private boolean blockPutaway;
        private String notes;
        private LocalDateTime expiryDate;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HoldInfo {
        private String holdKey;
        private String holdCode;
        private String holdType;
        private String holdReason;
        private LocalDateTime holdDate;
        private String holdBy;
        private boolean blockAllocation;
        private boolean blockPutaway;
        private BigDecimal quantity;
        private String notes;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HoldEvaluationRequest {
        private String storerKey;
        private String sku;
        private String supplierKey;
        private String countryOfOrigin;
        private boolean customsRequired;
        private boolean inspectionRequired;
    }
}
