package com.wms.po.domain.service;

import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.FinalizeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * @param receiptKey Receipt to validate
     * @return Current status if valid
     * @throws IllegalStateException if receipt cannot be finalized
     */
    @Transactional(readOnly = true)
    public String validateForFinalization(String receiptKey) {
        log.debug("Validating receipt {} for finalization", receiptKey);

        // Get current receipt status
        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM dbo.receipt WHERE receiptkey = ?",
            String.class,
            receiptKey
        );

        if (status == null) {
            throw new IllegalStateException("Receipt not found: " + receiptKey);
        }

        // Check if status allows finalization (0-5)
        Set<String> validStatuses = Set.of(STATUS_NEW, STATUS_ARRIVED, STATUS_CHECKED_IN,
            STATUS_RECEIVING, STATUS_VERIFIED);

        if (!validStatuses.contains(status)) {
            if (STATUS_FINALIZING.equals(status)) {
                throw new IllegalStateException("Receipt " + receiptKey + " is already being finalized");
            }
            if (STATUS_FINALIZED.equals(status)) {
                throw new IllegalStateException("Receipt " + receiptKey + " is already finalized");
            }
            throw new IllegalStateException("Receipt " + receiptKey + " has invalid status: " + status);
        }

        // Check if receipt has any details
        Integer detailCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dbo.receiptdetail WHERE receiptkey = ?",
            Integer.class,
            receiptKey
        );

        if (detailCount == null || detailCount == 0) {
            throw new IllegalStateException("Receipt " + receiptKey + " has no detail lines");
        }

        log.info("Receipt {} validated for finalization: status={}, lines={}", receiptKey, status, detailCount);
        return status;
    }

    /**
     * Update receipt status to "Finalizing".
     *
     * @param receiptKey Receipt to update
     * @param userId User performing the update
     * @return Previous status (for compensation)
     */
    @Transactional
    public String setStatusFinalizing(String receiptKey, String userId) {
        // Get current status first
        String previousStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM dbo.receipt WHERE receiptkey = ?",
            String.class,
            receiptKey
        );

        // Update to finalizing status
        jdbcTemplate.update(
            "UPDATE dbo.receipt SET status = ?, editdate = CURRENT_TIMESTAMP, editwho = ? WHERE receiptkey = ?",
            STATUS_FINALIZING, userId, receiptKey
        );

        log.info("Receipt {} status updated: {} → {}", receiptKey, previousStatus, STATUS_FINALIZING);
        return previousStatus;
    }

    /**
     * Update receipt status to "Finalized".
     *
     * @param receiptKey Receipt to update
     * @param userId User performing the update
     */
    @Transactional
    public void setStatusFinalized(String receiptKey, String userId) {
        jdbcTemplate.update(
            "UPDATE dbo.receipt SET status = ?, editdate = CURRENT_TIMESTAMP, editwho = ? WHERE receiptkey = ?",
            STATUS_FINALIZED, userId, receiptKey
        );

        log.info("Receipt {} finalized: status={}", receiptKey, STATUS_FINALIZED);
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
     * @param receiptKey Receipt to get details for
     * @return List of detail records
     */
    @Transactional(readOnly = true)
    public List<ReceiptDetailRecord> getReceiptDetails(String receiptKey) {
        return jdbcTemplate.query(
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
     * @param poKey PO to update
     * @param lineQuantities Map of line number to received quantity
     * @param userId User performing the update
     */
    @Transactional
    public void updatePoReceivedQuantities(String poKey, Map<Integer, BigDecimal> lineQuantities, String userId) {
        for (Map.Entry<Integer, BigDecimal> entry : lineQuantities.entrySet()) {
            jdbcTemplate.update(
                """
                UPDATE dbo.podetail
                SET qtyreceived = COALESCE(qtyreceived, 0) + ?,
                    editdate = CURRENT_TIMESTAMP,
                    editwho = ?
                WHERE pokey = ? AND polinenumber = ?
                """,
                entry.getValue(), userId, poKey, entry.getKey()
            );
        }

        log.info("Updated {} PO lines with received quantities for PO {}", lineQuantities.size(), poKey);
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
