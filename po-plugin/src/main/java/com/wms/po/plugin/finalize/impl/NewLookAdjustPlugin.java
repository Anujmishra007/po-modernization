package com.wms.po.plugin.finalize.impl;

import com.wms.po.plugin.finalize.AbstractPostFinalizePlugin;
import com.wms.po.plugin.finalize.FinalizeContext;
import com.wms.po.plugin.finalize.FinalizePluginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * New Look Adjustment Post-Finalize Plugin.
 *
 * Replaces: ispASNFZ09 (280 LOC)
 * Client: New Look (CN)
 * Purpose: CN New Look auto-adjustments
 *
 * Performs New Look specific post-finalization adjustments:
 * - Auto-adjusts variances within tolerance
 * - Creates adjustment transactions
 * - Updates inventory records
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NewLookAdjustPlugin extends AbstractPostFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispASNFZ09";
    private static final String CLIENT_KEY = "NEWLOOK";
    private static final BigDecimal VARIANCE_TOLERANCE = new BigDecimal("0.02"); // 2%

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public String getClientKey() {
        return CLIENT_KEY;
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean shouldExecute(FinalizeContext context) {
        String storerKey = context.getStorerKey();
        return storerKey != null &&
            (storerKey.toUpperCase().contains("NEWLOOK") || storerKey.toUpperCase().contains("NL"));
    }

    @Override
    protected FinalizePluginResult doExecute(FinalizeContext context) {
        log.info("Executing New Look Adjustments for receipt: {}", context.getReceiptKey());

        FinalizePluginResult result = FinalizePluginResult.success();
        int adjustmentCount = 0;

        for (FinalizeContext.ReceiptLineContext line : context.getLines()) {
            if (line.isSkipped()) continue;

            try {
                // Check for variance
                BigDecimal variance = calculateVariance(line);

                if (variance != null && variance.abs().compareTo(VARIANCE_TOLERANCE) <= 0) {
                    // Auto-adjust if within tolerance
                    if (variance.compareTo(BigDecimal.ZERO) != 0) {
                        createAdjustment(context, line, variance);
                        adjustmentCount++;
                        result.addModifiedLine(line.getLineNumber());
                    }
                } else if (variance != null && variance.compareTo(BigDecimal.ZERO) != 0) {
                    // Flag for manual review
                    flagForReview(context, line, variance);
                    result.addWarning("Line " + line.getLineNumber() +
                        " variance " + variance + " exceeds tolerance");
                }

            } catch (Exception e) {
                log.warn("Error processing adjustment for line {}: {}", line.getLineNumber(), e.getMessage());
                result.addWarning("Line " + line.getLineNumber() + " adjustment error: " + e.getMessage());
            }
        }

        if (adjustmentCount > 0) {
            result.addMessage("Created " + adjustmentCount + " auto-adjustments");
        }
        return result;
    }

    private BigDecimal calculateVariance(FinalizeContext.ReceiptLineContext line) {
        BigDecimal expected = line.getQuantityExpected();
        BigDecimal received = line.getQuantityReceived();

        if (expected == null || received == null || expected.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return received.subtract(expected).divide(expected, 4, java.math.RoundingMode.HALF_UP);
    }

    private void createAdjustment(FinalizeContext context, FinalizeContext.ReceiptLineContext line,
                                   BigDecimal variance) {
        try {
            BigDecimal adjustmentQty = line.getQuantityExpected().subtract(line.getQuantityReceived());

            jdbcTemplate.update(
                "INSERT INTO dbo.itrnheader (storerkey, sku, fromloc, toloc, qty, " +
                "trantype, status, receiptkey, receiptlinenumber, adddate, addwho, notes) " +
                "VALUES (?, ?, ?, ?, ?, 'ADJ', '5', ?, ?, GETDATE(), ?, ?)",
                context.getStorerKey(),
                line.getSku(),
                line.getToLocation(),
                line.getToLocation(),
                adjustmentQty,
                context.getReceiptKey(),
                line.getLineNumber(),
                context.getUserId(),
                "Auto-adjustment - New Look variance within tolerance"
            );
        } catch (Exception e) {
            log.debug("Could not create adjustment: {}", e.getMessage());
        }
    }

    private void flagForReview(FinalizeContext context, FinalizeContext.ReceiptLineContext line,
                                BigDecimal variance) {
        try {
            jdbcTemplate.update(
                "INSERT INTO dbo.variancereview (receiptkey, linenumber, storerkey, sku, " +
                "expectedqty, receivedqty, variancepct, status, adddate, addwho) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', GETDATE(), ?)",
                context.getReceiptKey(),
                line.getLineNumber(),
                context.getStorerKey(),
                line.getSku(),
                line.getQuantityExpected(),
                line.getQuantityReceived(),
                variance.multiply(new BigDecimal("100")),
                context.getUserId()
            );
        } catch (Exception e) {
            log.debug("Could not flag for review: {}", e.getMessage());
        }
    }
}
