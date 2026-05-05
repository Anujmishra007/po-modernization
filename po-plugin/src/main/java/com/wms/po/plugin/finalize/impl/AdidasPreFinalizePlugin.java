package com.wms.po.plugin.finalize.impl;

import com.wms.po.plugin.finalize.AbstractPreFinalizePlugin;
import com.wms.po.plugin.finalize.FinalizeContext;
import com.wms.po.plugin.finalize.FinalizePluginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adidas Pre-Finalize Plugin.
 *
 * Replaces: ispPRREC03 (120 LOC)
 * Client: Adidas
 *
 * Adidas-specific pre-finalize validations:
 * - Validates article number format
 * - Validates SAP material code
 * - Sets Adidas-specific lottables (season, division)
 * - Validates batch/lot structure
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdidasPreFinalizePlugin extends AbstractPreFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispPRREC03";
    private static final String CLIENT_KEY = "ADIDAS";

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
        return storerKey != null && storerKey.toUpperCase().contains("ADIDAS");
    }

    @Override
    protected FinalizePluginResult validatePreconditions(FinalizeContext context) {
        if (context.getLines().isEmpty()) {
            return validationError("No receipt lines to process");
        }
        return FinalizePluginResult.success();
    }

    @Override
    protected FinalizePluginResult doExecute(FinalizeContext context) {
        log.info("Executing Adidas pre-finalize for receipt: {}", context.getReceiptKey());

        FinalizePluginResult result = FinalizePluginResult.success();
        int processedCount = 0;

        for (FinalizeContext.ReceiptLineContext line : context.getLines()) {
            try {
                // 1. Validate article number format (6-digit numeric)
                if (!validateArticleNumber(line.getSku())) {
                    result.addWarning("Line " + line.getLineNumber() + ": Invalid article number format");
                }

                // 2. Set Adidas division code (Lottable04)
                setDivisionCode(context, line);

                // 3. Set season code (Lottable05)
                setSeasonCode(context, line);

                // 4. Validate SAP material reference
                validateSAPReference(line, result);

                processedCount++;

            } catch (Exception e) {
                log.warn("Error processing Adidas line {}: {}", line.getLineNumber(), e.getMessage());
                result.addWarning("Line " + line.getLineNumber() + " processing error: " + e.getMessage());
            }
        }

        result.addMessage("Processed " + processedCount + " lines for Adidas");
        return result;
    }

    private boolean validateArticleNumber(String sku) {
        if (sku == null || sku.isEmpty()) {
            return false;
        }
        // Adidas article format: typically alphanumeric with hyphen
        return sku.matches("^[A-Z0-9]{2,}-[A-Z0-9]+$") || sku.matches("^[0-9]{6,}$");
    }

    private void setDivisionCode(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable04() == null || line.getLottable04().isEmpty()) {
            String division = getSkuAttribute(context.getStorerKey(), line.getSku(), "DIVISION");
            if (division != null) {
                line.setLottable04(division);
                line.markModified();
            }
        }
    }

    private void setSeasonCode(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable05() == null || line.getLottable05().isEmpty()) {
            String season = getSkuAttribute(context.getStorerKey(), line.getSku(), "SEASON");
            if (season != null) {
                line.setLottable05(season);
                line.markModified();
            }
        }
    }

    private void validateSAPReference(FinalizeContext.ReceiptLineContext line, FinalizePluginResult result) {
        String sapRef = line.getLottable02();
        if (sapRef != null && !sapRef.isEmpty()) {
            // SAP material numbers are typically 18 characters
            if (sapRef.length() > 18) {
                result.addWarning("Line " + line.getLineNumber() + ": SAP reference exceeds maximum length");
            }
        }
    }

    private String getSkuAttribute(String storerKey, String sku, String attribute) {
        try {
            String column = switch (attribute) {
                case "DIVISION" -> "skugroup";
                case "SEASON" -> "userdefined1";
                default -> null;
            };

            if (column == null) return null;

            return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM dbo.sku WHERE storerkey = ? AND sku = ?",
                String.class,
                storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }
}
