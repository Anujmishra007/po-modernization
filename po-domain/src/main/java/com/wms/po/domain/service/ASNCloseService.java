package com.wms.po.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Service for ASN/Receipt auto-close logic.
 *
 * Handles:
 * - Receipt closure after finalization
 * - PO closure when fully received
 * - Variance tolerance checks
 * - Status transitions based on configuration
 *
 * Maps to close logic in ispFinalizeReceipt and CODELKUP configurations:
 * - CLOSEASNSTATUS
 * - CloseASNUponFinalize
 * - ChkASNVarianceTolerance
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ASNCloseService {

    private final JdbcTemplate jdbcTemplate;

    // Status constants
    private static final String STATUS_FINALIZED = "9";
    private static final String STATUS_CLOSED = "C";
    private static final String PO_STATUS_CLOSED = "9";
    private static final String PO_STATUS_PARTIAL = "5";

    /**
     * Check if a receipt should be auto-closed based on configuration.
     *
     * @param receiptKey Receipt to check
     * @param storerKey Storer for configuration lookup
     * @return true if auto-close should proceed
     */
    @Transactional(readOnly = true)
    public boolean shouldAutoClose(String receiptKey, String storerKey) {
        // Check storer configuration
        String configValue = getStorerConfig(storerKey, "CloseASNUponFinalize");
        if ("N".equalsIgnoreCase(configValue)) {
            log.debug("Auto-close disabled for storer {}", storerKey);
            return false;
        }

        // Check if all lines are finalized
        Integer unfinalizedCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM dbo.receiptdetail
            WHERE receiptkey = ?
            AND status != '9'
            """,
            Integer.class,
            receiptKey
        );

        if (unfinalizedCount != null && unfinalizedCount > 0) {
            log.debug("Receipt {} has {} unfinalized lines", receiptKey, unfinalizedCount);
            return false;
        }

        return true;
    }

    /**
     * Close a receipt after finalization.
     *
     * @param receiptKey Receipt to close
     * @param userId User performing the close
     * @return true if closed successfully
     */
    @Transactional
    public boolean closeReceipt(String receiptKey, String userId) {
        log.info("Closing receipt {}", receiptKey);

        // Update receipt status and close date
        int updated = jdbcTemplate.update(
            """
            UPDATE dbo.receipt
            SET status = ?,
                closedate = CURRENT_DATE,
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE receiptkey = ?
            AND status = ?
            """,
            STATUS_CLOSED,
            userId,
            receiptKey,
            STATUS_FINALIZED
        );

        if (updated > 0) {
            log.info("Receipt {} closed successfully", receiptKey);
            return true;
        } else {
            log.warn("Receipt {} could not be closed (wrong status or not found)", receiptKey);
            return false;
        }
    }

    /**
     * Check and close PO if fully received.
     *
     * @param poKey PO to check
     * @param userId User performing the close
     * @return CloseResult with details
     */
    @Transactional
    public CloseResult checkAndClosePO(String poKey, String userId) {
        log.info("Checking PO {} for closure", poKey);

        // Get PO line summary
        List<Map<String, Object>> lines = jdbcTemplate.queryForList(
            """
            SELECT polinenumber, sku,
                   COALESCE(qtyordered, 0) as qtyordered,
                   COALESCE(qtyreceived, 0) as qtyreceived
            FROM dbo.podetail
            WHERE pokey = ?
            """,
            poKey
        );

        if (lines.isEmpty()) {
            return CloseResult.noAction("PO has no lines");
        }

        // Calculate totals
        BigDecimal totalOrdered = BigDecimal.ZERO;
        BigDecimal totalReceived = BigDecimal.ZERO;
        int fullyReceivedLines = 0;
        int partiallyReceivedLines = 0;
        int pendingLines = 0;

        for (Map<String, Object> line : lines) {
            BigDecimal ordered = (BigDecimal) line.get("qtyordered");
            BigDecimal received = (BigDecimal) line.get("qtyreceived");

            totalOrdered = totalOrdered.add(ordered);
            totalReceived = totalReceived.add(received);

            int comparison = received.compareTo(ordered);
            if (comparison >= 0) {
                fullyReceivedLines++;
            } else if (received.compareTo(BigDecimal.ZERO) > 0) {
                partiallyReceivedLines++;
            } else {
                pendingLines++;
            }
        }

        log.debug("PO {} status: total ordered={}, received={}, full={}, partial={}, pending={}",
            poKey, totalOrdered, totalReceived, fullyReceivedLines, partiallyReceivedLines, pendingLines);

        // Determine action
        if (fullyReceivedLines == lines.size()) {
            // All lines fully received - close PO
            closePO(poKey, userId);
            return CloseResult.closed(fullyReceivedLines, totalReceived);
        } else if (partiallyReceivedLines > 0 || fullyReceivedLines > 0) {
            // Partial receipt - update status but don't close
            updatePOStatusPartial(poKey, userId);
            return CloseResult.partial(fullyReceivedLines, partiallyReceivedLines, pendingLines);
        } else {
            // No receipts yet
            return CloseResult.noAction("No lines received yet");
        }
    }

    /**
     * Close a PO.
     */
    @Transactional
    public void closePO(String poKey, String userId) {
        log.info("Closing PO {}", poKey);

        jdbcTemplate.update(
            """
            UPDATE dbo.po
            SET status = ?,
                closedate = CURRENT_TIMESTAMP,
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE pokey = ?
            """,
            PO_STATUS_CLOSED,
            userId,
            poKey
        );

        // Update all PO lines to closed status
        jdbcTemplate.update(
            """
            UPDATE dbo.podetail
            SET polinestatus = 'CLOSED',
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE pokey = ?
            """,
            userId,
            poKey
        );
    }

    /**
     * Update PO to partial receipt status.
     */
    private void updatePOStatusPartial(String poKey, String userId) {
        jdbcTemplate.update(
            """
            UPDATE dbo.po
            SET status = ?,
                editdate = CURRENT_TIMESTAMP,
                editwho = ?
            WHERE pokey = ?
            AND status NOT IN (?, ?)
            """,
            PO_STATUS_PARTIAL,
            userId,
            poKey,
            PO_STATUS_CLOSED, PO_STATUS_PARTIAL
        );
    }

    /**
     * Check variance tolerance for a receipt.
     *
     * @param receiptKey Receipt to check
     * @param storerKey Storer for tolerance configuration
     * @return VarianceResult with details
     */
    @Transactional(readOnly = true)
    public VarianceResult checkVarianceTolerance(String receiptKey, String storerKey) {
        // Get tolerance configuration
        String toleranceStr = getStorerConfig(storerKey, "ChkASNVarianceTolerance");
        BigDecimal tolerancePercent = BigDecimal.ZERO;
        try {
            if (toleranceStr != null && !toleranceStr.isEmpty()) {
                tolerancePercent = new BigDecimal(toleranceStr);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid variance tolerance config for storer {}: {}", storerKey, toleranceStr);
        }

        // Calculate variance for all lines
        List<Map<String, Object>> lines = jdbcTemplate.queryForList(
            """
            SELECT receiptlinenumber, sku,
                   COALESCE(qtyexpected, 0) as qtyexpected,
                   COALESCE(qtyreceived, 0) as qtyreceived
            FROM dbo.receiptdetail
            WHERE receiptkey = ?
            """,
            receiptKey
        );

        boolean withinTolerance = true;
        StringBuilder details = new StringBuilder();

        for (Map<String, Object> line : lines) {
            BigDecimal expected = (BigDecimal) line.get("qtyexpected");
            BigDecimal received = (BigDecimal) line.get("qtyreceived");

            if (expected.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal variance = received.subtract(expected);
                BigDecimal variancePercent = variance.abs()
                    .multiply(new BigDecimal("100"))
                    .divide(expected, 2, BigDecimal.ROUND_HALF_UP);

                if (variancePercent.compareTo(tolerancePercent) > 0) {
                    withinTolerance = false;
                    details.append(String.format("Line %s: %.2f%% variance (limit: %.2f%%); ",
                        line.get("receiptlinenumber"), variancePercent, tolerancePercent));
                }
            }
        }

        return new VarianceResult(withinTolerance, tolerancePercent, details.toString());
    }

    /**
     * Get storer configuration value.
     */
    private String getStorerConfig(String storerKey, String configKey) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT value1 FROM dbo.codelkup
                WHERE listname = ?
                AND code = ?
                """,
                String.class,
                configKey, storerKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Result of close operation.
     */
    public record CloseResult(
        boolean closed,
        boolean partial,
        int linesFullyReceived,
        int linesPartiallyReceived,
        int linesPending,
        BigDecimal totalReceived,
        String message
    ) {
        public static CloseResult closed(int fullLines, BigDecimal totalReceived) {
            return new CloseResult(true, false, fullLines, 0, 0, totalReceived, "PO closed - fully received");
        }

        public static CloseResult partial(int fullLines, int partialLines, int pendingLines) {
            return new CloseResult(false, true, fullLines, partialLines, pendingLines, null,
                "PO partially received");
        }

        public static CloseResult noAction(String message) {
            return new CloseResult(false, false, 0, 0, 0, null, message);
        }
    }

    /**
     * Result of variance check.
     */
    public record VarianceResult(
        boolean withinTolerance,
        BigDecimal tolerancePercent,
        String details
    ) {}
}
