package com.wms.po.domain.service;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Service for posting inventory to LOTxLOCxID table.
 *
 * LOTxLOCxID is the central inventory repository in WMS:
 * - LOT: Lot attributes (lot number, expiry, supplier lot, etc.)
 * - LOC: Physical location in warehouse
 * - ID: License plate number (container/pallet identifier)
 *
 * This service implements the core inventory posting logic from
 * ispFinalizeReceipt stored procedure.
 *
 * Error codes:
 * - INV_007 (68706) - Inventory Posting Failed
 * - INV_002 (68701) - Insufficient Inventory
 * - INV_003 (68702) - Inventory Already Allocated
 * - INV_005 (68704) - Inventory Adjustment Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryPostingService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;

    // Status constants
    public static final String STATUS_AVAILABLE = "OK";
    public static final String STATUS_HOLD = "HOLD";
    public static final String STATUS_DAMAGE = "DAMAGE";

    /**
     * Post a single inventory record to LOTxLOCxID.
     *
     * Error codes:
     * - INV_007 (68706) - Inventory Posting Failed
     *
     * @param posting Inventory posting details
     * @return Created inventory key
     */
    @Transactional
    public String postInventory(InventoryPosting posting) {
        log.debug("Posting inventory: sku={}, qty={}, loc={}, lp={}",
            posting.getSku(), posting.getQuantity(),
            posting.getLocation(), posting.getLicensePlate());

        String lotxlocxidKey = null;

        try {
            // Generate unique key for LOTxLOCxID
            lotxlocxidKey = keyGeneratorService.generateKey("LOTXLOCXID");

            // Generate or use provided LOT key
            String lotKey = posting.getLotKey();
            if (lotKey == null || lotKey.isEmpty()) {
                lotKey = getOrCreateLotKey(posting);
            }

            // Insert into LOTxLOCxID
            jdbcTemplate.update(
            """
            INSERT INTO dbo.lotxlocxid (
                lotxlocxidkey, storerkey, sku, lot, loc, id,
                qty, qtyallocated, qtypicked, status,
                lottable01, lottable02, lottable03, lottable04, lottable05,
                lottable06, lottable07, lottable08, lottable09, lottable10,
                packkey, uom, createdate, hold,
                adddate, addwho, editdate, editwho
            )
            VALUES (?, ?, ?, ?, ?, ?,
                    ?, 0, 0, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, CURRENT_TIMESTAMP, '0',
                    CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?)
            """,
            lotxlocxidKey,
            posting.getStorerKey(),
            posting.getSku(),
            lotKey,
            posting.getLocation(),
            posting.getLicensePlate(),
            posting.getQuantity(),
            posting.getStatus() != null ? posting.getStatus() : STATUS_AVAILABLE,
            posting.getLottable01(),
            posting.getLottable02(),
            posting.getLottable03(),
            posting.getLottable04(),
            posting.getLottable05(),
            posting.getLottable06(),
            posting.getLottable07(),
            posting.getLottable08(),
            posting.getLottable09(),
            posting.getLottable10(),
            posting.getPackKey(),
            posting.getUom(),
            posting.getUserId(),
            posting.getUserId()
        );

            // Create transaction record (ITRN)
            createInventoryTransaction(lotxlocxidKey, posting, "RCPT");

            log.info("Inventory posted: key={}, sku={}, qty={}", lotxlocxidKey, posting.getSku(), posting.getQuantity());
            return lotxlocxidKey;

        } catch (DataAccessException e) {
            log.error("Failed to post inventory for sku {}: {} (legacy error 68706)",
                posting.getSku(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.INVENTORY_POSTING_FAILED,
                "Failed to post inventory for SKU: " + posting.getSku(), e)
                .withDetail("sku", posting.getSku())
                .withDetail("location", posting.getLocation())
                .withDetail("quantity", posting.getQuantity());
        }
    }

    /**
     * Post multiple inventory records in batch.
     *
     * @param postings List of inventory postings
     * @return List of created inventory keys
     */
    @Transactional
    public List<String> postInventoryBatch(List<InventoryPosting> postings) {
        log.info("Posting batch inventory: {} records", postings.size());

        List<String> inventoryKeys = new ArrayList<>();
        BigDecimal totalQuantity = BigDecimal.ZERO;

        for (InventoryPosting posting : postings) {
            String key = postInventory(posting);
            inventoryKeys.add(key);
            totalQuantity = totalQuantity.add(posting.getQuantity());
        }

        log.info("Batch inventory posted: {} records, total qty={}", inventoryKeys.size(), totalQuantity);
        return inventoryKeys;
    }

    /**
     * Delete an inventory record (for compensation).
     *
     * Error codes:
     * - INV_004 (68651) - Insufficient Inventory (has allocations/picks)
     * - INV_005 (68652) - Inventory Deletion Failed
     *
     * @param lotxlocxidKey Inventory key to delete
     * @param userId User performing deletion
     * @param reason Reason for deletion
     * @return true if deleted
     */
    @Transactional
    public boolean deleteInventory(String lotxlocxidKey, String userId, String reason) {
        log.warn("COMPENSATION: Deleting inventory record: {}", lotxlocxidKey);

        // First check if record exists and has no allocations
        Map<String, Object> record = null;
        try {
            record = jdbcTemplate.queryForMap(
                """
                SELECT storerkey, sku, lot, loc, id, qty, qtyallocated, qtypicked
                FROM dbo.lotxlocxid
                WHERE lotxlocxidkey = ?
                """,
                lotxlocxidKey
            );
        } catch (Exception e) {
            log.warn("Inventory record not found: {} (legacy error 68652)", lotxlocxidKey);
            return false;
        }

        BigDecimal qtyAllocated = (BigDecimal) record.get("qtyallocated");
        BigDecimal qtyPicked = (BigDecimal) record.get("qtypicked");

        if (qtyAllocated != null && qtyAllocated.compareTo(BigDecimal.ZERO) > 0) {
            log.error("Cannot delete inventory {} - has allocated quantity: {} (legacy error 68702)",
                lotxlocxidKey, qtyAllocated);
            throw new BusinessException(ErrorCode.INVENTORY_ALREADY_ALLOCATED,
                "Cannot delete inventory with allocated quantity")
                .withDetail("lotxlocxidKey", lotxlocxidKey)
                .withDetail("qtyAllocated", qtyAllocated);
        }

        if (qtyPicked != null && qtyPicked.compareTo(BigDecimal.ZERO) > 0) {
            log.error("Cannot delete inventory {} - has picked quantity: {} (legacy error 68701)",
                lotxlocxidKey, qtyPicked);
            throw new BusinessException(ErrorCode.INVENTORY_INSUFFICIENT,
                "Cannot delete inventory with picked quantity")
                .withDetail("lotxlocxidKey", lotxlocxidKey)
                .withDetail("qtyPicked", qtyPicked);
        }

        // Create reversal transaction before deletion
        createInventoryTransaction(lotxlocxidKey, buildPostingFromRecord(record, userId), "RCPT_REV");

        // Delete the record
        int deleted = jdbcTemplate.update(
            "DELETE FROM dbo.lotxlocxid WHERE lotxlocxidkey = ?",
            lotxlocxidKey
        );

        if (deleted > 0) {
            log.info("Inventory deleted: {} (reason: {})", lotxlocxidKey, reason);
            return true;
        }

        return false;
    }

    /**
     * Delete multiple inventory records (for compensation).
     *
     * @param lotxlocxidKeys Keys to delete
     * @param userId User performing deletion
     * @param reason Reason for deletion
     * @return Number of records deleted
     */
    @Transactional
    public int deleteInventoryBatch(List<String> lotxlocxidKeys, String userId, String reason) {
        log.warn("COMPENSATION: Deleting {} inventory records", lotxlocxidKeys.size());

        int deletedCount = 0;
        for (String key : lotxlocxidKeys) {
            if (deleteInventory(key, userId, reason)) {
                deletedCount++;
            }
        }

        log.info("Batch deletion complete: {}/{} records deleted", deletedCount, lotxlocxidKeys.size());
        return deletedCount;
    }

    /**
     * Adjust inventory quantity.
     *
     * @param lotxlocxidKey Inventory key to adjust
     * @param adjustment Quantity adjustment (can be negative)
     * @param reason Reason code
     * @param userId User making adjustment
     * @return true if adjusted
     */
    @Transactional
    public boolean adjustInventory(String lotxlocxidKey, BigDecimal adjustment, String reason, String userId) {
        log.info("Adjusting inventory {}: qty={}, reason={}", lotxlocxidKey, adjustment, reason);

        // Update quantity
        int updated = jdbcTemplate.update(
            """
            UPDATE dbo.lotxlocxid
            SET qty = qty + ?,
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE lotxlocxidkey = ?
            AND (qty + ?) >= 0
            """,
            adjustment, userId, lotxlocxidKey, adjustment
        );

        if (updated > 0) {
            // Create adjustment transaction
            Map<String, Object> record = jdbcTemplate.queryForMap(
                "SELECT storerkey, sku, lot, loc, id FROM dbo.lotxlocxid WHERE lotxlocxidkey = ?",
                lotxlocxidKey
            );

            InventoryPosting posting = InventoryPosting.builder()
                .storerKey((String) record.get("storerkey"))
                .sku((String) record.get("sku"))
                .quantity(adjustment)
                .userId(userId)
                .build();

            createInventoryTransaction(lotxlocxidKey, posting, adjustment.compareTo(BigDecimal.ZERO) > 0 ? "ADJ_IN" : "ADJ_OUT");

            log.info("Inventory adjusted: key={}, adjustment={}", lotxlocxidKey, adjustment);
            return true;
        }

        log.warn("Inventory adjustment failed (insufficient qty or not found): {}", lotxlocxidKey);
        return false;
    }

    /**
     * Get inventory record details.
     *
     * @param lotxlocxidKey Inventory key
     * @return Inventory details or null if not found
     */
    @Transactional(readOnly = true)
    public InventoryRecord getInventory(String lotxlocxidKey) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT lotxlocxidkey, storerkey, sku, lot, loc, id,
                       qty, qtyallocated, qtypicked, status, hold,
                       lottable01, lottable02, lottable03, lottable04, lottable05,
                       lottable06, lottable07, lottable08, lottable09, lottable10,
                       packkey, uom, createdate
                FROM dbo.lotxlocxid
                WHERE lotxlocxidkey = ?
                """,
                (rs, rowNum) -> InventoryRecord.builder()
                    .lotxlocxidKey(rs.getString("lotxlocxidkey"))
                    .storerKey(rs.getString("storerkey"))
                    .sku(rs.getString("sku"))
                    .lot(rs.getString("lot"))
                    .location(rs.getString("loc"))
                    .licensePlate(rs.getString("id"))
                    .quantity(rs.getBigDecimal("qty"))
                    .quantityAllocated(rs.getBigDecimal("qtyallocated"))
                    .quantityPicked(rs.getBigDecimal("qtypicked"))
                    .status(rs.getString("status"))
                    .hold("1".equals(rs.getString("hold")))
                    .lottable01(rs.getString("lottable01"))
                    .lottable02(rs.getString("lottable02"))
                    .lottable03(rs.getString("lottable03"))
                    .lottable04(rs.getString("lottable04"))
                    .lottable05(rs.getString("lottable05"))
                    .lottable06(rs.getString("lottable06"))
                    .lottable07(rs.getString("lottable07"))
                    .lottable08(rs.getString("lottable08"))
                    .lottable09(rs.getString("lottable09"))
                    .lottable10(rs.getString("lottable10"))
                    .packKey(rs.getString("packkey"))
                    .uom(rs.getString("uom"))
                    .createDate(rs.getTimestamp("createdate").toLocalDateTime())
                    .build(),
                lotxlocxidKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private String getOrCreateLotKey(InventoryPosting posting) {
        // Check if LOT already exists with these attributes
        try {
            String existingLot = jdbcTemplate.queryForObject(
                """
                SELECT lot FROM dbo.lot
                WHERE storerkey = ? AND sku = ?
                AND COALESCE(lottable01, '') = COALESCE(?, '')
                AND COALESCE(lottable02, '') = COALESCE(?, '')
                AND COALESCE(lottable03, '') = COALESCE(?, '')
                """,
                String.class,
                posting.getStorerKey(), posting.getSku(),
                posting.getLottable01(), posting.getLottable02(), posting.getLottable03()
            );

            if (existingLot != null) {
                return existingLot;
            }
        } catch (Exception e) {
            // No matching lot found, create new one
        }

        // Create new LOT record
        String lotKey = keyGeneratorService.generateKey("LOT");

        jdbcTemplate.update(
            """
            INSERT INTO dbo.lot (
                lot, storerkey, sku,
                lottable01, lottable02, lottable03, lottable04, lottable05,
                lottable06, lottable07, lottable08, lottable09, lottable10,
                adddate, addwho
            )
            VALUES (?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    CURRENT_TIMESTAMP, ?)
            """,
            lotKey,
            posting.getStorerKey(),
            posting.getSku(),
            posting.getLottable01(),
            posting.getLottable02(),
            posting.getLottable03(),
            posting.getLottable04(),
            posting.getLottable05(),
            posting.getLottable06(),
            posting.getLottable07(),
            posting.getLottable08(),
            posting.getLottable09(),
            posting.getLottable10(),
            posting.getUserId()
        );

        return lotKey;
    }

    private void createInventoryTransaction(String lotxlocxidKey, InventoryPosting posting, String transactionType) {
        String itrnKey = keyGeneratorService.generateKey("ITRN");

        jdbcTemplate.update(
            """
            INSERT INTO dbo.itrn (
                itrnkey, storerkey, sku, lot, loc, id,
                qty, trantype, status, receiptkey,
                adddate, addwho
            )
            VALUES (?, ?, ?, ?, ?, ?,
                    ?, ?, 'OK', ?,
                    CURRENT_TIMESTAMP, ?)
            """,
            itrnKey,
            posting.getStorerKey(),
            posting.getSku(),
            posting.getLotKey(),
            posting.getLocation(),
            posting.getLicensePlate(),
            posting.getQuantity(),
            transactionType,
            posting.getReceiptKey(),
            posting.getUserId()
        );
    }

    private InventoryPosting buildPostingFromRecord(Map<String, Object> record, String userId) {
        return InventoryPosting.builder()
            .storerKey((String) record.get("storerkey"))
            .sku((String) record.get("sku"))
            .lotKey((String) record.get("lot"))
            .location((String) record.get("loc"))
            .licensePlate((String) record.get("id"))
            .quantity((BigDecimal) record.get("qty"))
            .userId(userId)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class InventoryPosting {
        private String storerKey;
        private String sku;
        private BigDecimal quantity;
        private String packKey;
        private String uom;
        private String location;
        private String licensePlate;
        private String lotKey;
        private String status;
        private String receiptKey;
        private int lineNumber;
        private String userId;

        // Lottable attributes
        private String lottable01;
        private String lottable02;
        private String lottable03;
        private String lottable04;
        private String lottable05;
        private String lottable06;
        private String lottable07;
        private String lottable08;
        private String lottable09;
        private String lottable10;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class InventoryRecord {
        private String lotxlocxidKey;
        private String storerKey;
        private String sku;
        private String lot;
        private String location;
        private String licensePlate;
        private BigDecimal quantity;
        private BigDecimal quantityAllocated;
        private BigDecimal quantityPicked;
        private String status;
        private boolean hold;
        private String packKey;
        private String uom;
        private java.time.LocalDateTime createDate;

        // Lottable attributes
        private String lottable01;
        private String lottable02;
        private String lottable03;
        private String lottable04;
        private String lottable05;
        private String lottable06;
        private String lottable07;
        private String lottable08;
        private String lottable09;
        private String lottable10;
    }
}
