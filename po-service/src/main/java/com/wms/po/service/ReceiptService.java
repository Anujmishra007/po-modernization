package com.wms.po.service;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.domain.service.KeyGeneratorService;
import com.wms.po.dto.ReceiptResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Receipt service for managing receipts.
 *
 * Error codes:
 * - RCV_001 (68900) - Receipt Not Found
 * - RCV_003 (68902) - Receipt Invalid Status
 * - RCV_005 (68904) - Receipt Header Creation Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;

    /**
     * Get receipt by key.
     *
     * Error codes:
     * - RCV_001 (68900) - Receipt Not Found
     */
    public ReceiptResponse getReceipt(String receiptKey) {
        log.debug("Getting receipt: {}", receiptKey);

        try {
            String sql = """
                SELECT RECEIPTKEY, STORERKEY, FACILITY, EXTERNRECEIPTKEY,
                       STATUS, TYPE, CARRIERKEY, TRAILERNUMBER,
                       RECEIPTDATE, ADDDATE, ADDWHO
                FROM dbo.receipt WHERE RECEIPTKEY = ?
                """;

            List<ReceiptResponse> results = jdbcTemplate.query(sql, (rs, rowNum) ->
                ReceiptResponse.builder()
                    .receiptKey(rs.getString("RECEIPTKEY"))
                    .storerKey(rs.getString("STORERKEY"))
                    .facility(rs.getString("FACILITY"))
                    .externReceiptKey(rs.getString("EXTERNRECEIPTKEY"))
                    .status(rs.getString("STATUS"))
                    .type(rs.getString("TYPE"))
                    .carrierKey(rs.getString("CARRIERKEY"))
                    .trailerNumber(rs.getString("TRAILERNUMBER"))
                    .receiptDate(rs.getTimestamp("RECEIPTDATE") != null ?
                        rs.getTimestamp("RECEIPTDATE").toLocalDateTime() : null)
                    .addDate(rs.getTimestamp("ADDDATE") != null ?
                        rs.getTimestamp("ADDDATE").toLocalDateTime() : null)
                    .addWho(rs.getString("ADDWHO"))
                    .build(),
                receiptKey);

            if (results.isEmpty()) {
                log.error("Receipt not found: {} (legacy error 68900)", receiptKey);
                throw BusinessException.receiptNotFound(receiptKey);
            }
            return results.get(0);

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error getting receipt {}: {} (legacy error 68900)", receiptKey, e.getMessage());
            throw BusinessException.receiptNotFound(receiptKey);
        }
    }

    /**
     * Get receipts by storer and facility.
     *
     * Error codes:
     * - RCV_005 (68904) - Database query failed
     *
     * @param storerKey Storer key (required)
     * @param facility Facility code (required)
     * @return List of receipts matching criteria
     * @throws BusinessException if query fails
     */
    public List<ReceiptResponse> getReceiptsByStorerAndFacility(String storerKey, String facility) {
        log.debug("Getting receipts for storer: {}, facility: {}", storerKey, facility);

        if (storerKey == null || storerKey.isBlank()) {
            log.error("Storer key is required for receipt search (legacy error 68904)");
            throw new BusinessException(ErrorCode.RECEIPT_HEADER_CREATE_FAILED,
                "Storer key is required for receipt search")
                .withDetail("storerKey", "null or blank");
        }

        if (facility == null || facility.isBlank()) {
            log.error("Facility is required for receipt search (legacy error 68904)");
            throw new BusinessException(ErrorCode.RECEIPT_HEADER_CREATE_FAILED,
                "Facility is required for receipt search")
                .withDetail("facility", "null or blank");
        }

        try {
            String sql = """
                SELECT RECEIPTKEY, STORERKEY, FACILITY, EXTERNRECEIPTKEY,
                       STATUS, TYPE, CARRIERKEY, TRAILERNUMBER,
                       RECEIPTDATE, ADDDATE, ADDWHO
                FROM dbo.receipt WHERE STORERKEY = ? AND FACILITY = ?
                ORDER BY ADDDATE DESC
                """;

            return jdbcTemplate.query(sql, (rs, rowNum) ->
                ReceiptResponse.builder()
                    .receiptKey(rs.getString("RECEIPTKEY"))
                    .storerKey(rs.getString("STORERKEY"))
                    .facility(rs.getString("FACILITY"))
                    .externReceiptKey(rs.getString("EXTERNRECEIPTKEY"))
                    .status(rs.getString("STATUS"))
                    .type(rs.getString("TYPE"))
                    .carrierKey(rs.getString("CARRIERKEY"))
                    .trailerNumber(rs.getString("TRAILERNUMBER"))
                    .receiptDate(rs.getTimestamp("RECEIPTDATE") != null ?
                        rs.getTimestamp("RECEIPTDATE").toLocalDateTime() : null)
                    .addDate(rs.getTimestamp("ADDDATE") != null ?
                        rs.getTimestamp("ADDDATE").toLocalDateTime() : null)
                    .addWho(rs.getString("ADDWHO"))
                    .build(),
                storerKey, facility);

        } catch (DataAccessException e) {
            log.error("Database error querying receipts for storer {}, facility {}: {} (legacy error 68904)",
                storerKey, facility, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_HEADER_CREATE_FAILED,
                "Failed to query receipts: " + e.getMessage(), e)
                .withDetail("storerKey", storerKey)
                .withDetail("facility", facility);
        }
    }

    /**
     * Get receipts by PO key.
     *
     * Error codes:
     * - RCV_005 (68904) - Database query failed
     *
     * @param poKey PO key to search receipts for
     * @return List of receipts linked to the PO
     * @throws BusinessException if query fails
     */
    public List<ReceiptResponse> getReceiptsByPO(String poKey) {
        log.debug("Getting receipts for PO: {}", poKey);

        if (poKey == null || poKey.isBlank()) {
            log.error("PO key is required for receipt search (legacy error 68904)");
            throw new BusinessException(ErrorCode.RECEIPT_HEADER_CREATE_FAILED,
                "PO key is required for receipt search")
                .withDetail("poKey", "null or blank");
        }

        try {
            String sql = """
                SELECT r.RECEIPTKEY, r.STORERKEY, r.FACILITY, r.EXTERNRECEIPTKEY,
                       r.STATUS, r.TYPE, r.CARRIERKEY, r.TRAILERNUMBER,
                       r.RECEIPTDATE, r.ADDDATE, r.ADDWHO
                FROM dbo.receipt r
                INNER JOIN dbo.receiptpo rp ON r.RECEIPTKEY = rp.RECEIPTKEY
                WHERE rp.POKEY = ?
                ORDER BY r.ADDDATE DESC
                """;

            return jdbcTemplate.query(sql, (rs, rowNum) ->
                ReceiptResponse.builder()
                    .receiptKey(rs.getString("RECEIPTKEY"))
                    .storerKey(rs.getString("STORERKEY"))
                    .facility(rs.getString("FACILITY"))
                    .externReceiptKey(rs.getString("EXTERNRECEIPTKEY"))
                    .status(rs.getString("STATUS"))
                    .type(rs.getString("TYPE"))
                    .carrierKey(rs.getString("CARRIERKEY"))
                    .trailerNumber(rs.getString("TRAILERNUMBER"))
                    .receiptDate(rs.getTimestamp("RECEIPTDATE") != null ?
                        rs.getTimestamp("RECEIPTDATE").toLocalDateTime() : null)
                    .addDate(rs.getTimestamp("ADDDATE") != null ?
                        rs.getTimestamp("ADDDATE").toLocalDateTime() : null)
                    .addWho(rs.getString("ADDWHO"))
                    .build(),
                poKey);

        } catch (DataAccessException e) {
            log.error("Database error querying receipts for PO {}: {} (legacy error 68904)",
                poKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_HEADER_CREATE_FAILED,
                "Failed to query receipts for PO: " + e.getMessage(), e)
                .withDetail("poKey", poKey);
        }
    }

    /**
     * Create a new receipt.
     *
     * Error codes:
     * - RCV_005 (68904) - Receipt Header Creation Failed
     */
    @Transactional
    public String createReceipt(String storerKey, String facility, List<String> poKeys,
                                String userId, VariationContext context) {
        String receiptKey = null;

        try {
            receiptKey = keyGeneratorService.generateKey("RECEIPT");
            log.info("Creating receipt {} for {} POs", receiptKey, poKeys.size());

            String sql = """
                INSERT INTO dbo.receipt (RECEIPTKEY, STORERKEY, FACILITY, STATUS, TYPE,
                    ADDDATE, ADDWHO, EDITDATE, EDITWHO)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?)
                """;

            jdbcTemplate.update(sql,
                receiptKey,
                storerKey,
                facility,
                "0", // Initial status
                "PO", // Receipt type
                userId,
                userId
            );

            // Link POs to receipt
            for (String poKey : poKeys) {
                linkPOToReceipt(receiptKey, poKey, userId);
            }

            return receiptKey;

        } catch (DataAccessException e) {
            log.error("Failed to create receipt: {} (legacy error 68904)", e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_HEADER_CREATE_FAILED,
                "Failed to create receipt for storer: " + storerKey, e)
                .withDetail("storerKey", storerKey)
                .withDetail("facility", facility)
                .withDetail("poKeys", poKeys);
        }
    }

    /**
     * Update receipt status.
     *
     * Error codes:
     * - RCV_001 (68900) - Receipt Not Found
     * - RCV_003 (68902) - Receipt Invalid Status
     *
     * @param receiptKey Receipt key to update
     * @param status New status value
     * @param userId User performing the update
     * @throws BusinessException if status update fails
     */
    @Transactional
    public void updateReceiptStatus(String receiptKey, String status, String userId) {
        log.info("Updating receipt {} status to {}", receiptKey, status);

        if (receiptKey == null || receiptKey.isBlank()) {
            log.error("Receipt key is required for status update (legacy error 68900)");
            throw BusinessException.receiptNotFound("null or blank");
        }

        if (status == null || status.isBlank()) {
            log.error("Status is required for receipt status update (legacy error 68902)");
            throw new BusinessException(ErrorCode.RECEIPT_INVALID_STATUS,
                "Status is required for receipt status update")
                .withDetail("receiptKey", receiptKey)
                .withDetail("status", "null or blank");
        }

        try {
            String sql = """
                UPDATE dbo.receipt SET STATUS = ?, EDITDATE = CURRENT_TIMESTAMP, EDITWHO = ?
                WHERE RECEIPTKEY = ?
                """;

            int rowsUpdated = jdbcTemplate.update(sql, status, userId, receiptKey);

            if (rowsUpdated == 0) {
                log.error("Receipt not found for status update: {} (legacy error 68900)", receiptKey);
                throw BusinessException.receiptNotFound(receiptKey);
            }

            log.info("Updated receipt {} status to {}", receiptKey, status);

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error updating receipt {} status to {}: {} (legacy error 68902)",
                receiptKey, status, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_INVALID_STATUS,
                "Failed to update receipt status: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("status", status);
        }
    }

    /**
     * Delete receipt.
     *
     * Error codes:
     * - RCV_001 (68900) - Receipt Not Found
     * - RCV_005 (68904) - Receipt Deletion Failed
     *
     * @param receiptKey Receipt key to delete
     * @throws BusinessException if deletion fails
     */
    @Transactional
    public void deleteReceipt(String receiptKey) {
        log.info("Deleting receipt {}", receiptKey);

        if (receiptKey == null || receiptKey.isBlank()) {
            log.error("Receipt key is required for delete (legacy error 68900)");
            throw BusinessException.receiptNotFound("null or blank");
        }

        try {
            // Delete receipt details first
            int detailsDeleted = jdbcTemplate.update("DELETE FROM dbo.receiptdetail WHERE RECEIPTKEY = ?", receiptKey);
            log.debug("Deleted {} detail records for receipt: {}", detailsDeleted, receiptKey);

            // Delete PO links
            int linksDeleted = jdbcTemplate.update("DELETE FROM dbo.receiptpo WHERE RECEIPTKEY = ?", receiptKey);
            log.debug("Deleted {} PO links for receipt: {}", linksDeleted, receiptKey);

            // Delete receipt
            int receiptDeleted = jdbcTemplate.update("DELETE FROM dbo.receipt WHERE RECEIPTKEY = ?", receiptKey);
            if (receiptDeleted == 0) {
                log.error("Receipt not found during delete: {} (legacy error 68900)", receiptKey);
                throw BusinessException.receiptNotFound(receiptKey);
            }

            log.info("Deleted receipt: {} ({} details, {} PO links)", receiptKey, detailsDeleted, linksDeleted);

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error deleting receipt {}: {} (legacy error 68904)",
                receiptKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_HEADER_CREATE_FAILED,
                "Failed to delete receipt: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey);
        }
    }

    /**
     * Link a PO to a receipt.
     *
     * Error codes:
     * - RCV_005 (68904) - PO Link Failed
     *
     * @param receiptKey Receipt key
     * @param poKey PO key to link
     * @param userId User performing the link
     * @throws BusinessException if linking fails
     */
    private void linkPOToReceipt(String receiptKey, String poKey, String userId) {
        if (receiptKey == null || receiptKey.isBlank()) {
            log.error("Receipt key is required for PO linking (legacy error 68904)");
            throw new BusinessException(ErrorCode.RECEIPT_HEADER_CREATE_FAILED,
                "Receipt key is required for PO linking")
                .withDetail("receiptKey", "null or blank")
                .withDetail("poKey", poKey);
        }

        if (poKey == null || poKey.isBlank()) {
            log.error("PO key is required for PO linking (legacy error 68904)");
            throw new BusinessException(ErrorCode.RECEIPT_HEADER_CREATE_FAILED,
                "PO key is required for PO linking")
                .withDetail("receiptKey", receiptKey)
                .withDetail("poKey", "null or blank");
        }

        try {
            String sql = """
                INSERT INTO dbo.receiptPO (RECEIPTKEY, POKEY, ADDDATE, ADDWHO)
                VALUES (?, ?, CURRENT_TIMESTAMP, ?)
                """;

            jdbcTemplate.update(sql, receiptKey, poKey, userId);
            log.debug("Linked PO {} to receipt {}", poKey, receiptKey);

        } catch (DataAccessException e) {
            log.error("Failed to link PO {} to receipt {}: {} (legacy error 68904)",
                poKey, receiptKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_HEADER_CREATE_FAILED,
                "Failed to link PO to receipt: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("poKey", poKey);
        }
    }
}
