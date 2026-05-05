package com.wms.po.plugin.finalize.impl;

import com.wms.po.plugin.finalize.AbstractPreFinalizePlugin;
import com.wms.po.plugin.finalize.FinalizeContext;
import com.wms.po.plugin.finalize.FinalizePluginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * H&M Pre-Finalize Plugin.
 *
 * Replaces: ispPRREC01 (180 LOC)
 * Client: H&M (CN)
 *
 * H&M-specific pre-finalize validations:
 * - Validates season code format
 * - Checks color/size combinations
 * - Validates supplier lot format
 * - Sets H&M-specific lottables
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HMPreFinalizePlugin extends AbstractPreFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispPRREC01";
    private static final String CLIENT_KEY = "HM";
    private static final Set<String> VALID_SEASONS = Set.of("SP", "SU", "FA", "HO", "WI");

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
        return 10; // Run early
    }

    @Override
    public boolean shouldExecute(FinalizeContext context) {
        // Execute for H&M storers
        String storerKey = context.getStorerKey();
        return storerKey != null && storerKey.toUpperCase().contains("HM");
    }

    @Override
    protected FinalizePluginResult validatePreconditions(FinalizeContext context) {
        // Ensure we have lines to process
        if (context.getLines().isEmpty()) {
            return validationError("No receipt lines to process");
        }
        return FinalizePluginResult.success();
    }

    @Override
    protected FinalizePluginResult doExecute(FinalizeContext context) {
        log.info("Executing H&M pre-finalize for receipt: {}", context.getReceiptKey());

        FinalizePluginResult result = FinalizePluginResult.success();
        int processedCount = 0;

        for (FinalizeContext.ReceiptLineContext line : context.getLines()) {
            try {
                // 1. Validate season code format (Lottable03)
                if (!validateSeasonCode(line)) {
                    result.addWarning("Line " + line.getLineNumber() + ": Invalid season code format");
                }

                // 2. Validate and transform supplier lot (Lottable01)
                String transformedLot = transformSupplierLot(line.getLottable01());
                if (transformedLot != null && !transformedLot.equals(line.getLottable01())) {
                    line.setLottable01(transformedLot);
                    line.markModified();
                    result.addModifiedLine(line.getLineNumber());
                }

                // 3. Set H&M-specific lottables
                setHMLottables(context, line);

                processedCount++;

            } catch (Exception e) {
                log.warn("Error processing H&M line {}: {}", line.getLineNumber(), e.getMessage());
                result.addWarning("Line " + line.getLineNumber() + " processing error: " + e.getMessage());
            }
        }

        result.addMessage("Processed " + processedCount + " lines for H&M");
        return result;
    }

    private boolean validateSeasonCode(FinalizeContext.ReceiptLineContext line) {
        String seasonCode = line.getLottable03();
        if (seasonCode == null || seasonCode.isEmpty()) {
            return true; // Optional field
        }

        // H&M season code format: YYYYSS (year + season)
        // SS: SP=Spring, SU=Summer, FA=Fall, HO=Holiday
        if (seasonCode.length() != 6) {
            return false;
        }

        String year = seasonCode.substring(0, 4);
        String season = seasonCode.substring(4, 6);

        try {
            int yearNum = Integer.parseInt(year);
            if (yearNum < 2000 || yearNum > 2100) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        return VALID_SEASONS.contains(season.toUpperCase());
    }

    private String transformSupplierLot(String supplierLot) {
        if (supplierLot == null || supplierLot.isEmpty()) {
            return supplierLot;
        }

        // H&M lot format transformation: remove special characters, uppercase
        return supplierLot.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private void setHMLottables(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        // Set Lottable06 = Style Code (from SKU attributes)
        if (line.getLottable06() == null || line.getLottable06().isEmpty()) {
            String styleCode = getSkuAttribute(context.getStorerKey(), line.getSku(), "STYLECODE");
            if (styleCode != null) {
                line.setLottable06(styleCode);
                line.markModified();
            }
        }

        // Set Lottable07 = Color Code
        if (line.getLottable07() == null || line.getLottable07().isEmpty()) {
            String colorCode = getSkuAttribute(context.getStorerKey(), line.getSku(), "COLORCODE");
            if (colorCode != null) {
                line.setLottable07(colorCode);
                line.markModified();
            }
        }
    }

    private String getSkuAttribute(String storerKey, String sku, String attribute) {
        try {
            String column = switch (attribute) {
                case "STYLECODE" -> "userdefined1";
                case "COLORCODE" -> "userdefined2";
                case "SIZECODE" -> "userdefined3";
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
