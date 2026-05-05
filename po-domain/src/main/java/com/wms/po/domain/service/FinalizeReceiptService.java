package com.wms.po.domain.service;

import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.FinalizeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Domain service for receipt finalization logic.
 * Implements core business logic from ispFinalizeReceipt stored procedure.
 *
 * Key responsibilities:
 * - Validate receipt can be finalized
 * - Calculate received quantities and variances
 * - Build inventory posting records
 * - Determine putaway strategies
 * - Check PO completion status
 *
 * Error codes:
 * - RCV_001 (68900) - Receipt Not Found
 * - RCV_002 (68901) - Receipt Already Finalized
 * - RCV_003 (68902) - Receipt Invalid Status
 * - RCV_004 (68903) - Receipt No Details
 * - RCV_019 (68919) - Finalize Status Update Failed
 * - RCV_020 (68920) - Finalize Validation Failed
 * - RCV_022 (68922) - PO Update Failed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinalizeReceiptService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;

    // Status constants (matching WMS conventions)
    private static final String STATUS_NEW = "0";
    private static final String STATUS_ARRIVED = "1";
    private static final String STATUS_CHECKED_IN = "2";
    private static final String STATUS_RECEIVING = "3";
    private static final String STATUS_VERIFIED = "5";
    private static final String STATUS_FINALIZING = "6";
    private static final String STATUS_FINALIZED = "9";

    /**
     * Validate that a receipt can be finalized.
     *
     * Error codes:
     * - RCV_001 (68900) - Receipt Not Found
     * - RCV_002 (68901) - Receipt Already Finalized
     * - RCV_003 (68902) - Receipt Invalid Status
     * - RCV_004 (68903) - Receipt No Details
     * - RCV_020 (68920) - Finalize Validation Failed
     *
     * @param receiptKey Receipt to validate
     * @return Current status if valid
     */
    @Transactional(readOnly = true)
    public String validateForFinalization(String receiptKey) {
        log.debug("Validating receipt {} for finalization", receiptKey);

        String status;
        try {
            // Get current receipt status
            status = jdbcTemplate.queryForObject(
                "SELECT status FROM dbo.receipt WHERE receiptkey = ?",
                String.class,
                receiptKey
            );
        } catch (EmptyResultDataAccessException e) {
            log.error("Receipt not found: {} (legacy error 68900)", receiptKey);
            throw BusinessException.receiptNotFound(receiptKey);
        } catch (DataAccessException e) {
            log.error("Database error validating receipt {}: {} (legacy error 68920)",
                receiptKey, e.getMessage(), e);
            throw BusinessException.finalizeValidationFailed(receiptKey, e.getMessage());
        }

        if (status == null) {
            log.error("Receipt not found: {} (legacy error 68900)", receiptKey);
            throw BusinessException.receiptNotFound(receiptKey);
        }

        // Check if status allows finalization (0-5)
        Set<String> validStatuses = Set.of(STATUS_NEW, STATUS_ARRIVED, STATUS_CHECKED_IN,
            STATUS_RECEIVING, STATUS_VERIFIED);

        if (!validStatuses.contains(status)) {
            if (STATUS_FINALIZING.equals(status)) {
                log.error("Receipt {} is already being finalized (legacy error 68902)", receiptKey);
                throw new BusinessException(ErrorCode.RECEIPT_INVALID_STATUS,
                    "Receipt is already being finalized")
                    .withDetail("receiptKey", receiptKey)
                    .withDetail("status", status);
            }
            if (STATUS_FINALIZED.equals(status)) {
                log.error("Receipt {} is already finalized (legacy error 68901)", receiptKey);
                throw new BusinessException(ErrorCode.RECEIPT_ALREADY_FINALIZED,
                    "Receipt is already finalized")
                    .withDetail("receiptKey", receiptKey)
                    .withDetail("status", status);
            }
            log.error("Receipt {} has invalid status: {} (legacy error 68902)", receiptKey, status);
            throw new BusinessException(ErrorCode.RECEIPT_INVALID_STATUS,
                "Receipt has invalid status for finalization: " + status)
                .withDetail("receiptKey", receiptKey)
                .withDetail("status", status)
                .withDetail("validStatuses", validStatuses);
        }

        // Check if receipt has any details
        Integer detailCount;
        try {
            detailCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.receiptdetail WHERE receiptkey = ?",
                Integer.class,
                receiptKey
            );
        } catch (DataAccessException e) {
            log.error("Database error checking receipt details {}: {} (legacy error 68920)",
                receiptKey, e.getMessage(), e);
            throw BusinessException.finalizeValidationFailed(receiptKey, e.getMessage());
        }

        if (detailCount == null || detailCount == 0) {
            log.error("Receipt {} has no detail lines (legacy error 68903)", receiptKey);
            throw new BusinessException(ErrorCode.RECEIPT_DETAIL_NOT_FOUND,
                "Receipt has no detail lines")
                .withDetail("receiptKey", receiptKey);
        }

        log.info("Receipt {} validated for finalization: status={}, lines={}", receiptKey, status, detailCount);
        return status;
    }

    /**
     * Update receipt status to "Finalizing".
     *
     * Error codes:
     * - RCV_001 (68900) - Receipt Not Found
     * - RCV_019 (68919) - Finalize Status Update Failed
     *
     * @param receiptKey Receipt to update
     * @param userId User performing the update
     * @return Previous status (for compensation)
     */
    @Transactional
    public String setStatusFinalizing(String receiptKey, String userId) {
        String previousStatus;

        try {
            // Get current status first
            previousStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM dbo.receipt WHERE receiptkey = ?",
                String.class,
                receiptKey
            );
        } catch (EmptyResultDataAccessException e) {
            log.error("Receipt not found for status update: {} (legacy error 68900)", receiptKey);
            throw BusinessException.receiptNotFound(receiptKey);
        } catch (DataAccessException e) {
            log.error("Database error getting receipt status {}: {} (legacy error 68919)",
                receiptKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FINALIZE_STATUS_UPDATE_FAILED,
                "Failed to get receipt status: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey);
        }

        try {
            // Update to finalizing status
            int updated = jdbcTemplate.update(
                "UPDATE dbo.receipt SET status = ?, editdate = CURRENT_TIMESTAMP, editwho = ? WHERE receiptkey = ?",
                STATUS_FINALIZING, userId, receiptKey
            );

            if (updated == 0) {
                log.error("Receipt not found for status update: {} (legacy error 68900)", receiptKey);
                throw BusinessException.receiptNotFound(receiptKey);
            }

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to update receipt {} to finalizing status: {} (legacy error 68919)",
                receiptKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FINALIZE_STATUS_UPDATE_FAILED,
                "Failed to update receipt status to finalizing: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("previousStatus", previousStatus);
        }

        log.info("Receipt {} status updated: {} → {}", receiptKey, previousStatus, STATUS_FINALIZING);
        return previousStatus;
    }

    /**
     * Update receipt status to "Finalized".
     *
     * Error codes:
     * - RCV_001 (68900) - Receipt Not Found
     * - RCV_019 (68919) - Finalize Status Update Failed
     *
     * @param receiptKey Receipt to update
     * @param userId User performing the update
     */
    @Transactional
    public void setStatusFinalized(String receiptKey, String userId) {
        try {
            int updated = jdbcTemplate.update(
                "UPDATE dbo.receipt SET status = ?, editdate = CURRENT_TIMESTAMP, editwho = ? WHERE receiptkey = ?",
                STATUS_FINALIZED, userId, receiptKey
            );

            if (updated == 0) {
                log.error("Receipt not found for finalization: {} (legacy error 68900)", receiptKey);
                throw BusinessException.receiptNotFound(receiptKey);
            }

            log.info("Receipt {} finalized: status={}", receiptKey, STATUS_FINALIZED);

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to set receipt {} as finalized: {} (legacy error 68919)",
                receiptKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FINALIZE_STATUS_UPDATE_FAILED,
                "Failed to set receipt as finalized: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey);
        }
    }

    /**
     * Revert receipt status back to original value.
     *
     * @param receiptKey Receipt to revert
     * @param originalStatus Status to revert to
     * @param userId User performing the revert
     */
    @Transactional
    public void revertStatus(String receiptKey, String originalStatus, String userId) {
        log.warn("COMPENSATION: Reverting receipt {} status to {}", receiptKey, originalStatus);

        jdbcTemplate.update(
            "UPDATE dbo.receipt SET status = ?, editdate = CURRENT_TIMESTAMP, editwho = ? WHERE receiptkey = ?",
            originalStatus, userId, receiptKey
        );

        log.info("Receipt {} status reverted to {}", receiptKey, originalStatus);
    }

    /**
     * Close the receipt.
     *
     * @param receiptKey Receipt to close
     * @param userId User performing the close
     */
    @Transactional
    public void closeReceipt(String receiptKey, String userId) {
        jdbcTemplate.update(
            "UPDATE dbo.receipt SET closedate = CURRENT_DATE, editdate = CURRENT_TIMESTAMP, editwho = ? WHERE receiptkey = ?",
            userId, receiptKey
        );

        log.info("Receipt {} closed", receiptKey);
    }

    /**
     * Get receipt details for inventory posting.
     *
     * Error codes:
     * - RCV_004 (68903) - Receipt No Details
     * - RCV_020 (68920) - Finalize Validation Failed
     *
     * @param receiptKey Receipt to get details for
     * @return List of detail records
     */
    @Transactional(readOnly = true)
    public List<ReceiptDetailRecord> getReceiptDetails(String receiptKey) {
        try {
            List<ReceiptDetailRecord> details = jdbcTemplate.query(
                """
                SELECT receiptlinenumber, sku, qtyexpected, qtyreceived, packkey, uom,
                       toloc, toid, storerkey,
                       lottable01, lottable02, lottable03, lottable04, lottable05,
                       lottable06, lottable07, lottable08, lottable09, lottable10
                FROM dbo.receiptdetail
                WHERE receiptkey = ?
                ORDER BY receiptlinenumber
                """,
                (rs, rowNum) -> new ReceiptDetailRecord(
                    rs.getInt("receiptlinenumber"),
                    rs.getString("sku"),
                    rs.getBigDecimal("qtyexpected"),
                    rs.getBigDecimal("qtyreceived"),
                    rs.getString("packkey"),
                    rs.getString("uom"),
                    rs.getString("toloc"),
                    rs.getString("toid"),
                    rs.getString("storerkey"),
                    rs.getString("lottable01"),
                    rs.getString("lottable02"),
                    rs.getString("lottable03"),
                    rs.getString("lottable04"),
                    rs.getString("lottable05"),
                    rs.getString("lottable06"),
                    rs.getString("lottable07"),
                    rs.getString("lottable08"),
                    rs.getString("lottable09"),
                    rs.getString("lottable10")
                ),
                receiptKey
            );

            if (details.isEmpty()) {
                log.warn("No details found for receipt {} (legacy error 68903)", receiptKey);
                throw new BusinessException(ErrorCode.RECEIPT_DETAIL_NOT_FOUND,
                    "Receipt has no detail lines")
                    .withDetail("receiptKey", receiptKey);
            }

            return details;

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to get receipt details for {}: {} (legacy error 68920)",
                receiptKey, e.getMessage(), e);
            throw BusinessException.finalizeValidationFailed(receiptKey, e.getMessage());
        }
    }

    /**
     * Calculate variance between expected and received quantities.
     *
     * @param expected Expected quantity
     * @param received Received quantity
     * @return Variance information
     */
    public FinalizeResult.VarianceInfo calculateVariance(
            int lineNumber, String sku,
            BigDecimal expected, BigDecimal received) {

        BigDecimal variance = received.subtract(expected);
        BigDecimal variancePercent = expected.compareTo(BigDecimal.ZERO) != 0
            ? variance.multiply(new BigDecimal("100")).divide(expected, 2, BigDecimal.ROUND_HALF_UP)
            : BigDecimal.ZERO;

        String varianceType;
        if (variance.compareTo(BigDecimal.ZERO) > 0) {
            varianceType = "OVER";
        } else if (variance.compareTo(BigDecimal.ZERO) < 0) {
            varianceType = "SHORT";
        } else {
            varianceType = "EXACT";
        }

        return FinalizeResult.VarianceInfo.builder()
            .lineNumber(lineNumber)
            .sku(sku)
            .expectedQty(expected)
            .receivedQty(received)
            .varianceQty(variance)
            .variancePercent(variancePercent)
            .varianceType(varianceType)
            .build();
    }

    /**
     * Get the PO key associated with a receipt.
     *
     * @param receiptKey Receipt to look up
     * @return PO key or null if not linked
     */
    @Transactional(readOnly = true)
    public String getPoKeyFromReceipt(String receiptKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT pokey FROM dbo.receipt WHERE receiptkey = ?",
                String.class,
                receiptKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if all PO lines are fully received.
     *
     * @param poKey PO to check
     * @return true if all lines are fully received
     */
    @Transactional(readOnly = true)
    public boolean isPoFullyReceived(String poKey) {
        Integer incompleteLines = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM dbo.podetail
            WHERE pokey = ?
            AND COALESCE(qtyreceived, 0) < COALESCE(qtyordered, 0)
            """,
            Integer.class,
            poKey
        );

        return incompleteLines != null && incompleteLines == 0;
    }

    /**
     * Update PO received quantities based on finalized receipt.
     *
     * Error codes:
     * - PO_004 (68803) - PO Line Not Found
     * - RCV_022 (68922) - PO Update Failed
     *
     * @param poKey PO to update
     * @param lineQuantities Map of line number to received quantity
     * @param userId User performing the update
     */
    @Transactional
    public void updatePoReceivedQuantities(String poKey, Map<Integer, BigDecimal> lineQuantities, String userId) {
        int updatedLines = 0;

        for (Map.Entry<Integer, BigDecimal> entry : lineQuantities.entrySet()) {
            try {
                int updated = jdbcTemplate.update(
                    """
                    UPDATE dbo.podetail
                    SET qtyreceived = COALESCE(qtyreceived, 0) + ?,
                        editdate = CURRENT_TIMESTAMP,
                        editwho = ?
                    WHERE pokey = ? AND polinenumber = ?
                    """,
                    entry.getValue(), userId, poKey, entry.getKey()
                );

                if (updated == 0) {
                    log.warn("PO line not found: {} line {} (legacy error 68803)", poKey, entry.getKey());
                    throw new BusinessException(ErrorCode.PO_LINE_NOT_FOUND,
                        "PO line not found for quantity update")
                        .withDetail("poKey", poKey)
                        .withDetail("lineNumber", entry.getKey());
                }

                updatedLines++;

            } catch (BusinessException e) {
                throw e;
            } catch (DataAccessException e) {
                log.error("Failed to update PO {} line {}: {} (legacy error 68922)",
                    poKey, entry.getKey(), e.getMessage(), e);
                throw new BusinessException(ErrorCode.FINALIZE_PO_UPDATE_FAILED,
                    "Failed to update PO received quantity: " + e.getMessage(), e)
                    .withDetail("poKey", poKey)
                    .withDetail("lineNumber", entry.getKey())
                    .withDetail("quantity", entry.getValue());
            }
        }

        log.info("Updated {} PO lines with received quantities for PO {}", updatedLines, poKey);
    }

    /**
     * Close a PO when fully received.
     *
     * @param poKey PO to close
     * @param userId User closing the PO
     */
    @Transactional
    public void closePo(String poKey, String userId) {
        jdbcTemplate.update(
            """
            UPDATE dbo.po
            SET status = '9',
                closedate = CURRENT_TIMESTAMP,
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE pokey = ?
            """,
            userId, poKey
        );

        log.info("PO {} closed - fully received", poKey);
    }

    /**
     * Record representing a receipt detail line.
     */
    public record ReceiptDetailRecord(
        int lineNumber,
        String sku,
        BigDecimal qtyExpected,
        BigDecimal qtyReceived,
        String packKey,
        String uom,
        String location,
        String licensePlate,
        String storerKey,
        String lottable01,
        String lottable02,
        String lottable03,
        String lottable04,
        String lottable05,
        String lottable06,
        String lottable07,
        String lottable08,
        String lottable09,
        String lottable10
    ) {}
}
