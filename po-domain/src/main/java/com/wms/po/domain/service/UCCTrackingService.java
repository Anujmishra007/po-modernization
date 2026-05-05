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
 * Service for UCC (Universal Container Code) tracking.
 *
 * Implements the logic from isp_ItrnUCCAdd stored procedure.
 *
 * UCC is used to track:
 * - License plates (LPNs) / Pallets
 * - Cartons / Cases
 * - SSCC (Serial Shipping Container Code)
 *
 * UCC tracks the container through its lifecycle:
 * - Receipt (inbound)
 * - Putaway
 * - Storage
 * - Picking
 * - Packing
 * - Shipping (outbound)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UCCTrackingService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;

    // UCC Type constants
    public static final String UCC_TYPE_LPN = "LPN";      // License Plate Number
    public static final String UCC_TYPE_CARTON = "CARTON";
    public static final String UCC_TYPE_PALLET = "PALLET";
    public static final String UCC_TYPE_SSCC = "SSCC";    // Serial Shipping Container Code

    // UCC Status constants
    public static final String STATUS_CREATED = "0";
    public static final String STATUS_ACTIVE = "1";
    public static final String STATUS_IN_TRANSIT = "5";
    public static final String STATUS_CLOSED = "9";
    public static final String STATUS_VOIDED = "V";

    /**
     * Create a new UCC record during receipt.
     *
     * @param request UCC creation request
     * @return Created UCC key
     */
    @Transactional
    public String createUCC(UCCCreateRequest request) {
        log.debug("Creating UCC: type={}, receiptKey={}",
            request.getUccType(), request.getReceiptKey());

        // Generate UCC key if not provided
        String uccKey = request.getUccNumber();
        if (uccKey == null || uccKey.isEmpty()) {
            uccKey = generateUCCNumber(request.getUccType(), request.getFacility());
        }

        // Insert UCC record
        jdbcTemplate.update(
            """
            INSERT INTO dbo.ucc (
                ucc, ucctype, storerkey, facility, status,
                receiptkey, receiptlinenumber, pokey, polinenumber,
                sku, qty, packkey, uom, loc, parentucc,
                lottable01, lottable02, lottable03, lottable04, lottable05,
                lottable06, lottable07, lottable08, lottable09, lottable10,
                notes, adddate, addwho, editdate, editwho
            )
            VALUES (?, ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?)
            """,
            uccKey,
            request.getUccType() != null ? request.getUccType() : UCC_TYPE_LPN,
            request.getStorerKey(),
            request.getFacility(),
            STATUS_CREATED,
            request.getReceiptKey(),
            request.getReceiptLineNumber(),
            request.getPoKey(),
            request.getPoLineNumber(),
            request.getSku(),
            request.getQuantity(),
            request.getPackKey(),
            request.getUom(),
            request.getLocation(),
            request.getParentUcc(),
            request.getLottable01(),
            request.getLottable02(),
            request.getLottable03(),
            request.getLottable04(),
            request.getLottable05(),
            request.getLottable06(),
            request.getLottable07(),
            request.getLottable08(),
            request.getLottable09(),
            request.getLottable10(),
            request.getNotes(),
            request.getUserId(),
            request.getUserId()
        );

        // Create transaction record
        createUCCTransaction(uccKey, request, "RECEIPT");

        log.info("Created UCC: {}, type={}, sku={}, qty={}",
            uccKey, request.getUccType(), request.getSku(), request.getQuantity());

        return uccKey;
    }

    /**
     * Create multiple UCC records in batch.
     *
     * @param requests List of UCC creation requests
     * @return List of created UCC keys
     */
    @Transactional
    public List<String> createUCCBatch(List<UCCCreateRequest> requests) {
        log.info("Creating {} UCC records in batch", requests.size());

        List<String> uccKeys = new ArrayList<>();
        for (UCCCreateRequest request : requests) {
            try {
                String uccKey = createUCC(request);
                uccKeys.add(uccKey);
            } catch (Exception e) {
                log.error("Failed to create UCC: {}", e.getMessage());
            }
        }

        log.info("Created {} UCC records", uccKeys.size());
        return uccKeys;
    }

    /**
     * Activate a UCC (e.g., after putaway).
     *
     * @param uccKey UCC to activate
     * @param location Current location
     * @param userId User activating
     */
    @Transactional
    public void activateUCC(String uccKey, String location, String userId) {
        log.debug("Activating UCC: {}", uccKey);

        jdbcTemplate.update(
            """
            UPDATE dbo.ucc
            SET status = ?,
                loc = ?,
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE ucc = ?
            AND status IN (?, ?)
            """,
            STATUS_ACTIVE,
            location,
            userId,
            uccKey,
            STATUS_CREATED, STATUS_IN_TRANSIT
        );

        createUCCTransaction(uccKey, null, "ACTIVATE");
    }

    /**
     * Move a UCC to a new location.
     *
     * @param uccKey UCC to move
     * @param toLocation New location
     * @param userId User moving
     */
    @Transactional
    public void moveUCC(String uccKey, String toLocation, String userId) {
        log.debug("Moving UCC {} to {}", uccKey, toLocation);

        String fromLocation = jdbcTemplate.queryForObject(
            "SELECT loc FROM dbo.ucc WHERE ucc = ?",
            String.class,
            uccKey
        );

        jdbcTemplate.update(
            """
            UPDATE dbo.ucc
            SET loc = ?,
                status = ?,
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE ucc = ?
            """,
            toLocation,
            STATUS_IN_TRANSIT,
            userId,
            uccKey
        );

        // Create move transaction with from/to locations
        jdbcTemplate.update(
            """
            INSERT INTO dbo.ucctransaction (
                transactionkey, ucc, transactiontype, fromloc, toloc,
                adddate, addwho
            )
            VALUES (?, ?, 'MOVE', ?, ?, CURRENT_TIMESTAMP, ?)
            """,
            keyGeneratorService.generateKey("UCCTRAN"),
            uccKey,
            fromLocation,
            toLocation,
            userId
        );
    }

    /**
     * Close a UCC (e.g., after shipping).
     *
     * @param uccKey UCC to close
     * @param userId User closing
     * @param reason Reason for closing
     */
    @Transactional
    public void closeUCC(String uccKey, String userId, String reason) {
        log.debug("Closing UCC: {} - {}", uccKey, reason);

        jdbcTemplate.update(
            """
            UPDATE dbo.ucc
            SET status = ?,
                closedate = CURRENT_TIMESTAMP,
                notes = COALESCE(notes, '') || ' CLOSED: ' || ?,
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE ucc = ?
            """,
            STATUS_CLOSED,
            reason,
            userId,
            uccKey
        );

        createUCCTransaction(uccKey, null, "CLOSE");
    }

    /**
     * Void a UCC (for errors/cancellations).
     *
     * @param uccKey UCC to void
     * @param userId User voiding
     * @param reason Reason for voiding
     */
    @Transactional
    public void voidUCC(String uccKey, String userId, String reason) {
        log.warn("Voiding UCC: {} - {}", uccKey, reason);

        jdbcTemplate.update(
            """
            UPDATE dbo.ucc
            SET status = ?,
                notes = COALESCE(notes, '') || ' VOIDED: ' || ?,
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE ucc = ?
            AND status NOT IN (?, ?)
            """,
            STATUS_VOIDED,
            reason,
            userId,
            uccKey,
            STATUS_CLOSED, STATUS_VOIDED
        );

        createUCCTransaction(uccKey, null, "VOID");
    }

    /**
     * Get UCC details.
     *
     * @param uccKey UCC to look up
     * @return UCC details or null
     */
    @Transactional(readOnly = true)
    public UCCRecord getUCC(String uccKey) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT ucc, ucctype, storerkey, facility, status, loc,
                       receiptkey, receiptlinenumber, pokey, polinenumber,
                       sku, qty, packkey, uom, parentucc,
                       lottable01, lottable02, lottable03,
                       closedate, adddate
                FROM dbo.ucc
                WHERE ucc = ?
                """,
                (rs, rowNum) -> UCCRecord.builder()
                    .uccKey(rs.getString("ucc"))
                    .uccType(rs.getString("ucctype"))
                    .storerKey(rs.getString("storerkey"))
                    .facility(rs.getString("facility"))
                    .status(rs.getString("status"))
                    .location(rs.getString("loc"))
                    .receiptKey(rs.getString("receiptkey"))
                    .receiptLineNumber(rs.getObject("receiptlinenumber", Integer.class))
                    .poKey(rs.getString("pokey"))
                    .poLineNumber(rs.getObject("polinenumber", Integer.class))
                    .sku(rs.getString("sku"))
                    .quantity(rs.getBigDecimal("qty"))
                    .packKey(rs.getString("packkey"))
                    .uom(rs.getString("uom"))
                    .parentUcc(rs.getString("parentucc"))
                    .lottable01(rs.getString("lottable01"))
                    .lottable02(rs.getString("lottable02"))
                    .lottable03(rs.getString("lottable03"))
                    .build(),
                uccKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get all UCCs for a receipt.
     *
     * @param receiptKey Receipt to look up
     * @return List of UCC records
     */
    @Transactional(readOnly = true)
    public List<UCCRecord> getUCCsForReceipt(String receiptKey) {
        return jdbcTemplate.query(
            """
            SELECT ucc, ucctype, storerkey, facility, status, loc,
                   receiptkey, receiptlinenumber, pokey, polinenumber,
                   sku, qty, packkey, uom, parentucc,
                   lottable01, lottable02, lottable03,
                   closedate, adddate
            FROM dbo.ucc
            WHERE receiptkey = ?
            ORDER BY adddate
            """,
            (rs, rowNum) -> UCCRecord.builder()
                .uccKey(rs.getString("ucc"))
                .uccType(rs.getString("ucctype"))
                .storerKey(rs.getString("storerkey"))
                .facility(rs.getString("facility"))
                .status(rs.getString("status"))
                .location(rs.getString("loc"))
                .sku(rs.getString("sku"))
                .quantity(rs.getBigDecimal("qty"))
                .build(),
            receiptKey
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private String generateUCCNumber(String uccType, String facility) {
        String prefix;
        switch (uccType != null ? uccType : UCC_TYPE_LPN) {
            case UCC_TYPE_PALLET:
                prefix = "PAL";
                break;
            case UCC_TYPE_CARTON:
                prefix = "CTN";
                break;
            case UCC_TYPE_SSCC:
                prefix = "SSCC";
                break;
            default:
                prefix = "LPN";
        }

        // Generate sequential number from NCOUNTER
        String key = keyGeneratorService.generateKey("UCC");
        return prefix + facility.substring(0, Math.min(2, facility.length())) + key;
    }

    private void createUCCTransaction(String uccKey, UCCCreateRequest request, String transactionType) {
        String transKey = keyGeneratorService.generateKey("UCCTRAN");

        jdbcTemplate.update(
            """
            INSERT INTO dbo.ucctransaction (
                transactionkey, ucc, transactiontype,
                receiptkey, qty, adddate, addwho
            )
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
            """,
            transKey,
            uccKey,
            transactionType,
            request != null ? request.getReceiptKey() : null,
            request != null ? request.getQuantity() : null,
            request != null ? request.getUserId() : "SYSTEM"
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UCCCreateRequest {
        private String uccNumber;  // Optional: auto-generate if null
        private String uccType;    // LPN, CARTON, PALLET, SSCC
        private String storerKey;
        private String facility;
        private String receiptKey;
        private Integer receiptLineNumber;
        private String poKey;
        private Integer poLineNumber;
        private String sku;
        private BigDecimal quantity;
        private String packKey;
        private String uom;
        private String location;
        private String parentUcc;  // For nested containers
        private String userId;
        private String notes;

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
    public static class UCCRecord {
        private String uccKey;
        private String uccType;
        private String storerKey;
        private String facility;
        private String status;
        private String location;
        private String receiptKey;
        private Integer receiptLineNumber;
        private String poKey;
        private Integer poLineNumber;
        private String sku;
        private BigDecimal quantity;
        private String packKey;
        private String uom;
        private String parentUcc;
        private String lottable01;
        private String lottable02;
        private String lottable03;
        private LocalDateTime closeDate;
        private LocalDateTime addDate;
    }
}
