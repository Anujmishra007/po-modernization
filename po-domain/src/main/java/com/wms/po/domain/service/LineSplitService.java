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
import java.util.ArrayList;
import java.util.List;

/**
 * Service for splitting receipt detail lines.
 *
 * Line splitting occurs when:
 * - Received quantity differs from expected (variance)
 * - Multiple lots received on same line
 * - Partial receipts with different locations
 * - Quality issues requiring segregation
 *
 * Maps to line split logic in ispFinalizeReceipt and related SPs.
 *
 * Error codes:
 * - RCV_004 (68903) - Receipt No Details (source line not found)
 * - RCV_006 (68905) - Receipt Detail Creation Failed (split line creation failed)
 * - RCV_019 (68919) - Finalize Status Update Failed (line update failed)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineSplitService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGeneratorService;

    /**
     * Split a receipt detail line based on quantity variance.
     *
     * Error codes:
     * - RCV_004 (68903) - Receipt No Details (source line not found)
     * - RCV_006 (68905) - Receipt Detail Creation Failed (split line creation failed)
     *
     * @param receiptKey Receipt containing the line
     * @param lineNumber Original line number
     * @param receivedQty Actual received quantity
     * @param expectedQty Expected quantity
     * @param userId User performing the split
     * @return List of new line numbers created
     */
    @Transactional
    public List<Integer> splitByQuantityVariance(
            String receiptKey,
            int lineNumber,
            BigDecimal receivedQty,
            BigDecimal expectedQty,
            String userId) {

        log.info("Checking line split for receipt {} line {}: expected={}, received={}",
            receiptKey, lineNumber, expectedQty, receivedQty);

        try {
            List<Integer> newLines = new ArrayList<>();
            BigDecimal variance = receivedQty.subtract(expectedQty);

            if (variance.compareTo(BigDecimal.ZERO) == 0) {
                log.debug("No variance, no split needed");
                return newLines;
            }

            if (variance.compareTo(BigDecimal.ZERO) > 0) {
                // Over-receipt: Create new line for excess
                int newLineNumber = createOverReceiptLine(receiptKey, lineNumber, variance, userId);
                newLines.add(newLineNumber);
                log.info("Created over-receipt line {} for variance {}", newLineNumber, variance);
            } else {
                // Short-receipt: Update original line, optionally create backorder line
                handleShortReceipt(receiptKey, lineNumber, variance.abs(), userId);
                log.info("Handled short receipt for line {}, shortage {}", lineNumber, variance.abs());
            }

            return newLines;

        } catch (DataAccessException e) {
            log.error("Failed to split line by variance for receipt {} line {}: {} (legacy error 68905)",
                receiptKey, lineNumber, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_DETAIL_CREATE_FAILED,
                "Failed to split line by variance: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("lineNumber", lineNumber)
                .withDetail("receivedQty", receivedQty)
                .withDetail("expectedQty", expectedQty);
        }
    }

    /**
     * Split a receipt line into multiple lines based on different lots.
     *
     * Error codes:
     * - RCV_004 (68903) - Receipt No Details (source line not found)
     * - RCV_006 (68905) - Receipt Detail Creation Failed (split line creation failed)
     *
     * @param receiptKey Receipt containing the line
     * @param lineNumber Original line number
     * @param lotSplits List of lot quantities
     * @param userId User performing the split
     * @return List of new line numbers created
     */
    @Transactional
    public List<Integer> splitByLot(
            String receiptKey,
            int lineNumber,
            List<LotSplit> lotSplits,
            String userId) {

        log.info("Splitting receipt {} line {} into {} lots",
            receiptKey, lineNumber, lotSplits.size());

        try {
            List<Integer> newLines = new ArrayList<>();

            if (lotSplits.size() <= 1) {
                log.debug("Single lot, no split needed");
                return newLines;
            }

            // Verify source line exists
            verifyLineExists(receiptKey, lineNumber);

            // First lot stays on original line
            LotSplit firstLot = lotSplits.get(0);
            updateLineQuantityAndLot(receiptKey, lineNumber, firstLot.quantity, firstLot.lottable02, userId);

            // Create new lines for remaining lots
            for (int i = 1; i < lotSplits.size(); i++) {
                LotSplit lot = lotSplits.get(i);
                int newLineNumber = createLotSplitLine(receiptKey, lineNumber, lot, userId);
                newLines.add(newLineNumber);
                log.debug("Created lot split line {} with qty {} lot {}",
                    newLineNumber, lot.quantity, lot.lottable02);
            }

            return newLines;

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to split line by lot for receipt {} line {}: {} (legacy error 68905)",
                receiptKey, lineNumber, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_DETAIL_CREATE_FAILED,
                "Failed to split line by lot: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("lineNumber", lineNumber)
                .withDetail("lotSplitCount", lotSplits.size());
        }
    }

    /**
     * Split a receipt line for quality segregation (QC hold vs good).
     *
     * Error codes:
     * - RCV_004 (68903) - Receipt No Details (source line not found)
     * - RCV_006 (68905) - Receipt Detail Creation Failed (reject line creation failed)
     *
     * @param receiptKey Receipt containing the line
     * @param lineNumber Original line number
     * @param goodQty Quantity passing QC
     * @param rejectQty Quantity failing QC
     * @param rejectReason Reason for rejection
     * @param userId User performing the split
     * @return New line number for rejected quantity
     */
    @Transactional
    public int splitForQualitySegregation(
            String receiptKey,
            int lineNumber,
            BigDecimal goodQty,
            BigDecimal rejectQty,
            String rejectReason,
            String userId) {

        log.info("Splitting receipt {} line {} for quality: good={}, reject={}",
            receiptKey, lineNumber, goodQty, rejectQty);

        try {
            // Verify source line exists
            verifyLineExists(receiptKey, lineNumber);

            // Update original line with good quantity
            updateLineQuantity(receiptKey, lineNumber, goodQty, userId);

            // Create new line for rejected quantity
            int rejectLineNumber = createRejectLine(receiptKey, lineNumber, rejectQty, rejectReason, userId);

            log.info("Created reject line {} with qty {}", rejectLineNumber, rejectQty);
            return rejectLineNumber;

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to split line for quality segregation for receipt {} line {}: {} (legacy error 68905)",
                receiptKey, lineNumber, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_DETAIL_CREATE_FAILED,
                "Failed to split line for quality segregation: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("lineNumber", lineNumber)
                .withDetail("goodQty", goodQty)
                .withDetail("rejectQty", rejectQty)
                .withDetail("rejectReason", rejectReason);
        }
    }

    /**
     * Get the next available line number for a receipt.
     *
     * Error codes:
     * - RCV_004 (68903) - Receipt No Details
     */
    private int getNextLineNumber(String receiptKey) {
        try {
            Integer maxLine = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(receiptlinenumber), 0) FROM dbo.receiptdetail WHERE receiptkey = ?",
                Integer.class,
                receiptKey
            );
            return (maxLine != null ? maxLine : 0) + 1;
        } catch (DataAccessException e) {
            log.error("Failed to get next line number for receipt {}: {} (legacy error 68903)",
                receiptKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_DETAIL_NOT_FOUND,
                "Failed to get next line number: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey);
        }
    }

    /**
     * Verify that a receipt line exists.
     *
     * Error codes:
     * - RCV_004 (68903) - Receipt No Details
     */
    private void verifyLineExists(String receiptKey, int lineNumber) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.receiptdetail WHERE receiptkey = ? AND receiptlinenumber = ?",
                Integer.class,
                receiptKey, lineNumber
            );
            if (count == null || count == 0) {
                log.error("Receipt line not found: {} line {} (legacy error 68903)", receiptKey, lineNumber);
                throw new BusinessException(ErrorCode.RECEIPT_DETAIL_NOT_FOUND,
                    "Receipt line not found")
                    .withDetail("receiptKey", receiptKey)
                    .withDetail("lineNumber", lineNumber);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to verify receipt line {} line {}: {} (legacy error 68903)",
                receiptKey, lineNumber, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_DETAIL_NOT_FOUND,
                "Failed to verify receipt line: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("lineNumber", lineNumber);
        }
    }

    /**
     * Create a new line for over-receipt quantity.
     *
     * Error codes:
     * - RCV_006 (68905) - Receipt Detail Creation Failed
     */
    private int createOverReceiptLine(String receiptKey, int sourceLineNumber,
            BigDecimal overQty, String userId) {
        int newLineNumber = getNextLineNumber(receiptKey);

        try {
            int inserted = jdbcTemplate.update(
                """
                INSERT INTO dbo.receiptdetail (
                    receiptkey, receiptlinenumber, receiptdetailkey,
                    pokey, polinenumber, storerkey, sku,
                    qtyexpected, qtyreceived, packkey, uom,
                    toloc, toid, status, notes,
                    lottable01, lottable02, lottable03, lottable04, lottable05,
                    lottable06, lottable07, lottable08, lottable09, lottable10,
                    adddate, addwho
                )
                SELECT
                    receiptkey, ?, ?,
                    pokey, polinenumber, storerkey, sku,
                    ?, ?, packkey, uom,
                    toloc, toid, '0', 'Over-receipt split from line ' || ?,
                    lottable01, lottable02, lottable03, lottable04, lottable05,
                    lottable06, lottable07, lottable08, lottable09, lottable10,
                    CURRENT_TIMESTAMP, ?
                FROM dbo.receiptdetail
                WHERE receiptkey = ? AND receiptlinenumber = ?
                """,
                newLineNumber,
                keyGeneratorService.generateReceiptDetailKey(),
                overQty, overQty,
                sourceLineNumber,
                userId,
                receiptKey, sourceLineNumber
            );

            if (inserted == 0) {
                log.error("Failed to create over-receipt line - source line not found: {} line {} (legacy error 68903)",
                    receiptKey, sourceLineNumber);
                throw new BusinessException(ErrorCode.RECEIPT_DETAIL_NOT_FOUND,
                    "Source line not found for over-receipt split")
                    .withDetail("receiptKey", receiptKey)
                    .withDetail("sourceLineNumber", sourceLineNumber);
            }

            return newLineNumber;

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to create over-receipt line for {} line {}: {} (legacy error 68905)",
                receiptKey, sourceLineNumber, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_DETAIL_CREATE_FAILED,
                "Failed to create over-receipt line: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("sourceLineNumber", sourceLineNumber)
                .withDetail("overQty", overQty);
        }
    }

    /**
     * Handle short receipt by updating original line.
     *
     * Error codes:
     * - RCV_019 (68919) - Finalize Status Update Failed
     */
    private void handleShortReceipt(String receiptKey, int lineNumber,
            BigDecimal shortageQty, String userId) {
        try {
            // Update notes to indicate shortage
            int updated = jdbcTemplate.update(
                """
                UPDATE dbo.receiptdetail
                SET notes = COALESCE(notes, '') || ' Short receipt: ' || ?,
                    editdate = CURRENT_TIMESTAMP,
                    editwho = ?
                WHERE receiptkey = ? AND receiptlinenumber = ?
                """,
                shortageQty.toString(),
                userId,
                receiptKey, lineNumber
            );

            if (updated == 0) {
                log.warn("No rows updated for short receipt notation: {} line {}", receiptKey, lineNumber);
            }
        } catch (DataAccessException e) {
            log.error("Failed to update short receipt notation for {} line {}: {} (legacy error 68919)",
                receiptKey, lineNumber, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FINALIZE_STATUS_UPDATE_FAILED,
                "Failed to handle short receipt: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("lineNumber", lineNumber)
                .withDetail("shortageQty", shortageQty);
        }
    }

    /**
     * Create a new line for lot split.
     *
     * Error codes:
     * - RCV_006 (68905) - Receipt Detail Creation Failed
     */
    private int createLotSplitLine(String receiptKey, int sourceLineNumber,
            LotSplit lot, String userId) {
        int newLineNumber = getNextLineNumber(receiptKey);

        try {
            int inserted = jdbcTemplate.update(
                """
                INSERT INTO dbo.receiptdetail (
                    receiptkey, receiptlinenumber, receiptdetailkey,
                    pokey, polinenumber, storerkey, sku,
                    qtyexpected, qtyreceived, packkey, uom,
                    toloc, toid, status, notes,
                    lottable01, lottable02, lottable03, lottable04, lottable05,
                    lottable06, lottable07, lottable08, lottable09, lottable10,
                    adddate, addwho
                )
                SELECT
                    receiptkey, ?, ?,
                    pokey, polinenumber, storerkey, sku,
                    ?, ?, packkey, uom,
                    toloc, toid, '0', 'Lot split from line ' || ?,
                    lottable01, ?, lottable03, lottable04, lottable05,
                    lottable06, lottable07, lottable08, lottable09, lottable10,
                    CURRENT_TIMESTAMP, ?
                FROM dbo.receiptdetail
                WHERE receiptkey = ? AND receiptlinenumber = ?
                """,
                newLineNumber,
                keyGeneratorService.generateReceiptDetailKey(),
                lot.quantity, lot.quantity,
                sourceLineNumber,
                lot.lottable02,
                userId,
                receiptKey, sourceLineNumber
            );

            if (inserted == 0) {
                log.error("Failed to create lot split line - source line not found: {} line {} (legacy error 68903)",
                    receiptKey, sourceLineNumber);
                throw new BusinessException(ErrorCode.RECEIPT_DETAIL_NOT_FOUND,
                    "Source line not found for lot split")
                    .withDetail("receiptKey", receiptKey)
                    .withDetail("sourceLineNumber", sourceLineNumber);
            }

            return newLineNumber;

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to create lot split line for {} line {}: {} (legacy error 68905)",
                receiptKey, sourceLineNumber, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_DETAIL_CREATE_FAILED,
                "Failed to create lot split line: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("sourceLineNumber", sourceLineNumber)
                .withDetail("lotQuantity", lot.quantity)
                .withDetail("lottable02", lot.lottable02);
        }
    }

    /**
     * Create a new line for rejected quantity.
     *
     * Error codes:
     * - RCV_006 (68905) - Receipt Detail Creation Failed
     */
    private int createRejectLine(String receiptKey, int sourceLineNumber,
            BigDecimal rejectQty, String rejectReason, String userId) {
        int newLineNumber = getNextLineNumber(receiptKey);

        try {
            int inserted = jdbcTemplate.update(
                """
                INSERT INTO dbo.receiptdetail (
                    receiptkey, receiptlinenumber, receiptdetailkey,
                    pokey, polinenumber, storerkey, sku,
                    qtyexpected, qtyreceived, qtyrejected, packkey, uom,
                    toloc, toid, status, notes,
                    lottable01, lottable02, lottable03, lottable04, lottable05,
                    lottable06, lottable07, lottable08, lottable09, lottable10,
                    adddate, addwho
                )
                SELECT
                    receiptkey, ?, ?,
                    pokey, polinenumber, storerkey, sku,
                    ?, 0, ?, packkey, uom,
                    'REJECT', toid, 'R', ?,
                    lottable01, lottable02, lottable03, lottable04, lottable05,
                    lottable06, lottable07, lottable08, lottable09, lottable10,
                    CURRENT_TIMESTAMP, ?
                FROM dbo.receiptdetail
                WHERE receiptkey = ? AND receiptlinenumber = ?
                """,
                newLineNumber,
                keyGeneratorService.generateReceiptDetailKey(),
                rejectQty, rejectQty,
                "REJECT: " + rejectReason,
                userId,
                receiptKey, sourceLineNumber
            );

            if (inserted == 0) {
                log.error("Failed to create reject line - source line not found: {} line {} (legacy error 68903)",
                    receiptKey, sourceLineNumber);
                throw new BusinessException(ErrorCode.RECEIPT_DETAIL_NOT_FOUND,
                    "Source line not found for reject split")
                    .withDetail("receiptKey", receiptKey)
                    .withDetail("sourceLineNumber", sourceLineNumber);
            }

            return newLineNumber;

        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to create reject line for {} line {}: {} (legacy error 68905)",
                receiptKey, sourceLineNumber, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RECEIPT_DETAIL_CREATE_FAILED,
                "Failed to create reject line: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("sourceLineNumber", sourceLineNumber)
                .withDetail("rejectQty", rejectQty)
                .withDetail("rejectReason", rejectReason);
        }
    }

    /**
     * Update line quantity.
     *
     * Error codes:
     * - RCV_019 (68919) - Finalize Status Update Failed
     */
    private void updateLineQuantity(String receiptKey, int lineNumber,
            BigDecimal newQty, String userId) {
        try {
            int updated = jdbcTemplate.update(
                """
                UPDATE dbo.receiptdetail
                SET qtyreceived = ?,
                    editdate = CURRENT_TIMESTAMP,
                    editwho = ?
                WHERE receiptkey = ? AND receiptlinenumber = ?
                """,
                newQty, userId, receiptKey, lineNumber
            );

            if (updated == 0) {
                log.error("Failed to update line quantity - line not found: {} line {} (legacy error 68903)",
                    receiptKey, lineNumber);
                throw new BusinessException(ErrorCode.RECEIPT_DETAIL_NOT_FOUND,
                    "Line not found for quantity update")
                    .withDetail("receiptKey", receiptKey)
                    .withDetail("lineNumber", lineNumber);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to update line quantity for {} line {}: {} (legacy error 68919)",
                receiptKey, lineNumber, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FINALIZE_STATUS_UPDATE_FAILED,
                "Failed to update line quantity: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("lineNumber", lineNumber)
                .withDetail("newQty", newQty);
        }
    }

    /**
     * Update line quantity and lottable02.
     *
     * Error codes:
     * - RCV_019 (68919) - Finalize Status Update Failed
     */
    private void updateLineQuantityAndLot(String receiptKey, int lineNumber,
            BigDecimal newQty, String lottable02, String userId) {
        try {
            int updated = jdbcTemplate.update(
                """
                UPDATE dbo.receiptdetail
                SET qtyreceived = ?,
                    lottable02 = ?,
                    editdate = CURRENT_TIMESTAMP,
                    editwho = ?
                WHERE receiptkey = ? AND receiptlinenumber = ?
                """,
                newQty, lottable02, userId, receiptKey, lineNumber
            );

            if (updated == 0) {
                log.error("Failed to update line quantity and lot - line not found: {} line {} (legacy error 68903)",
                    receiptKey, lineNumber);
                throw new BusinessException(ErrorCode.RECEIPT_DETAIL_NOT_FOUND,
                    "Line not found for quantity and lot update")
                    .withDetail("receiptKey", receiptKey)
                    .withDetail("lineNumber", lineNumber);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Failed to update line quantity and lot for {} line {}: {} (legacy error 68919)",
                receiptKey, lineNumber, e.getMessage(), e);
            throw new BusinessException(ErrorCode.FINALIZE_STATUS_UPDATE_FAILED,
                "Failed to update line quantity and lot: " + e.getMessage(), e)
                .withDetail("receiptKey", receiptKey)
                .withDetail("lineNumber", lineNumber)
                .withDetail("newQty", newQty)
                .withDetail("lottable02", lottable02);
        }
    }

    /**
     * Record for lot split information.
     */
    public record LotSplit(
        BigDecimal quantity,
        String lottable02,
        String lottable04  // Optional expiry
    ) {}
}
