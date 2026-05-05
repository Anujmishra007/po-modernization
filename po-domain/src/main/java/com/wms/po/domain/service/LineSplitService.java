package com.wms.po.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    }

    /**
     * Split a receipt line into multiple lines based on different lots.
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

        List<Integer> newLines = new ArrayList<>();

        if (lotSplits.size() <= 1) {
            log.debug("Single lot, no split needed");
            return newLines;
        }

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
    }

    /**
     * Split a receipt line for quality segregation (QC hold vs good).
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

        // Update original line with good quantity
        updateLineQuantity(receiptKey, lineNumber, goodQty, userId);

        // Create new line for rejected quantity
        int rejectLineNumber = createRejectLine(receiptKey, lineNumber, rejectQty, rejectReason, userId);

        log.info("Created reject line {} with qty {}", rejectLineNumber, rejectQty);
        return rejectLineNumber;
    }

    /**
     * Get the next available line number for a receipt.
     */
    private int getNextLineNumber(String receiptKey) {
        Integer maxLine = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(receiptlinenumber), 0) FROM dbo.receiptdetail WHERE receiptkey = ?",
            Integer.class,
            receiptKey
        );
        return (maxLine != null ? maxLine : 0) + 1;
    }

    /**
     * Create a new line for over-receipt quantity.
     */
    private int createOverReceiptLine(String receiptKey, int sourceLineNumber,
            BigDecimal overQty, String userId) {
        int newLineNumber = getNextLineNumber(receiptKey);

        jdbcTemplate.update(
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

        return newLineNumber;
    }

    /**
     * Handle short receipt by updating original line.
     */
    private void handleShortReceipt(String receiptKey, int lineNumber,
            BigDecimal shortageQty, String userId) {
        // Update notes to indicate shortage
        jdbcTemplate.update(
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
    }

    /**
     * Create a new line for lot split.
     */
    private int createLotSplitLine(String receiptKey, int sourceLineNumber,
            LotSplit lot, String userId) {
        int newLineNumber = getNextLineNumber(receiptKey);

        jdbcTemplate.update(
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

        return newLineNumber;
    }

    /**
     * Create a new line for rejected quantity.
     */
    private int createRejectLine(String receiptKey, int sourceLineNumber,
            BigDecimal rejectQty, String rejectReason, String userId) {
        int newLineNumber = getNextLineNumber(receiptKey);

        jdbcTemplate.update(
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

        return newLineNumber;
    }

    /**
     * Update line quantity.
     */
    private void updateLineQuantity(String receiptKey, int lineNumber,
            BigDecimal newQty, String userId) {
        jdbcTemplate.update(
            """
            UPDATE dbo.receiptdetail
            SET qtyreceived = ?,
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE receiptkey = ? AND receiptlinenumber = ?
            """,
            newQty, userId, receiptKey, lineNumber
        );
    }

    /**
     * Update line quantity and lottable02.
     */
    private void updateLineQuantityAndLot(String receiptKey, int lineNumber,
            BigDecimal newQty, String lottable02, String userId) {
        jdbcTemplate.update(
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
