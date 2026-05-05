package com.wms.po.plugin.finalize.impl;

import com.wms.po.plugin.finalize.AbstractPreFinalizePlugin;
import com.wms.po.plugin.finalize.FinalizeContext;
import com.wms.po.plugin.finalize.FinalizePluginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Nike Pre-Finalize Plugin.
 *
 * Replaces: ispPRREC02 (210 LOC)
 * Client: NIKE (KR, IN, SG)
 *
 * Nike-specific pre-finalize validations:
 * - Validates Nike product codes
 * - Checks license plate format
 * - Validates quantity against PO
 * - Sets Nike-specific lottables (style, color, size codes)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NikePreFinalizePlugin extends AbstractPreFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispPRREC02";
    private static final String CLIENT_KEY = "NIKE";

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
        return 10;
    }

    @Override
    public boolean shouldExecute(FinalizeContext context) {
        String storerKey = context.getStorerKey();
        return storerKey != null && storerKey.toUpperCase().contains("NIKE");
    }

    @Override
    protected FinalizePluginResult doExecute(FinalizeContext context) {
        log.info("Executing Nike pre-finalize for receipt: {}", context.getReceiptKey());

        FinalizePluginResult result = FinalizePluginResult.success();
        int processedCount = 0;
        int errorCount = 0;

        for (FinalizeContext.ReceiptLineContext line : context.getLines()) {
            try {
                // 1. Validate Nike product code format
                if (!validateNikeProductCode(line.getSku())) {
                    result.addWarning("Line " + line.getLineNumber() + ": Invalid Nike product code format");
                }

                // 2. Validate quantity tolerance against PO
                var varianceResult = validateQuantityVariance(context, line);
                if (!varianceResult.isSuccess()) {
                    result.addWarning("Line " + line.getLineNumber() + ": " + varianceResult.getErrorMessage());
                    errorCount++;
                }

                // 3. Set Nike-specific lottables
                setNikeLottables(context, line);

                // 4. Validate LPN format if present
                if (line.getToId() != null && !validateLPNFormat(line.getToId())) {
                    result.addWarning("Line " + line.getLineNumber() + ": Invalid LPN format");
                }

                processedCount++;

            } catch (Exception e) {
                log.warn("Error processing Nike line {}: {}", line.getLineNumber(), e.getMessage());
                result.addWarning("Line " + line.getLineNumber() + ": " + e.getMessage());
                errorCount++;
            }
        }

        // Nike is strict - abort if too many errors
        if (errorCount > context.getLineCount() / 2) {
            return FinalizePluginResult.failure("NIKE_VALIDATION_FAILED",
                "Too many validation errors (" + errorCount + ") for Nike receipt");
        }

        result.addMessage("Nike validation complete: " + processedCount + " lines, " + errorCount + " warnings");
        return result;
    }

    private boolean validateNikeProductCode(String sku) {
        if (sku == null || sku.length() < 10) {
            return false;
        }
        // Nike SKU format: {StyleColor}-{SizeCode} e.g., "CW1234-100-M10"
        return sku.matches("^[A-Z]{2}\\d{4}-\\d{3}(-[A-Z0-9]+)?$");
    }

    private FinalizePluginResult validateQuantityVariance(FinalizeContext context,
                                                           FinalizeContext.ReceiptLineContext line) {
        if (line.getPoKey() == null || line.getPoLineNumber() == null) {
            return FinalizePluginResult.success();
        }

        try {
            BigDecimal expectedQty = jdbcTemplate.queryForObject(
                "SELECT qtyordered - qtyreceived FROM dbo.podetail " +
                "WHERE pokey = ? AND polinenumber = ?",
                BigDecimal.class,
                line.getPoKey(), line.getPoLineNumber()
            );

            if (expectedQty == null) {
                return FinalizePluginResult.success();
            }

            BigDecimal receivedQty = line.getQuantityReceived();
            if (receivedQty == null) {
                receivedQty = BigDecimal.ZERO;
            }

            // Nike allows 5% over-receipt tolerance
            BigDecimal tolerance = expectedQty.multiply(new BigDecimal("0.05"));
            BigDecimal maxAllowed = expectedQty.add(tolerance);

            if (receivedQty.compareTo(maxAllowed) > 0) {
                return FinalizePluginResult.failure("OVER_RECEIPT",
                    "Received qty " + receivedQty + " exceeds allowed " + maxAllowed);
            }

            return FinalizePluginResult.success();

        } catch (Exception e) {
            log.debug("Could not validate quantity variance: {}", e.getMessage());
            return FinalizePluginResult.success();
        }
    }

    private void setNikeLottables(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        String sku = line.getSku();
        if (sku == null) return;

        // Parse Nike SKU to extract style, color, size
        String[] parts = sku.split("-");
        if (parts.length >= 2) {
            // Lottable06 = Style Code
            if (line.getLottable06() == null) {
                line.setLottable06(parts[0]);
                line.markModified();
            }
            // Lottable07 = Color Code
            if (line.getLottable07() == null && parts.length >= 2) {
                line.setLottable07(parts[1]);
                line.markModified();
            }
            // Lottable08 = Size Code
            if (line.getLottable08() == null && parts.length >= 3) {
                line.setLottable08(parts[2]);
                line.markModified();
            }
        }
    }

    private boolean validateLPNFormat(String lpn) {
        // Nike LPN format: "LPN" + 12 alphanumeric
        return lpn.matches("^LPN[A-Z0-9]{12}$");
    }
}
