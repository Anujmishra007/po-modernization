package com.wms.po.plugin.finalize.impl;

import com.wms.po.plugin.finalize.AbstractPreFinalizePlugin;
import com.wms.po.plugin.finalize.FinalizeContext;
import com.wms.po.plugin.finalize.FinalizePluginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Unilever Pre-Finalize Plugin.
 *
 * Replaces: ispPRREC05 (130 LOC)
 * Client: Unilever
 *
 * Unilever-specific pre-finalize validations:
 * - Validates batch number format (GTIN-based)
 * - Validates expiry date format and shelf life
 * - Sets Unilever-specific lottables
 * - Validates production plant code
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnileverPreFinalizePlugin extends AbstractPreFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispPRREC05";
    private static final String CLIENT_KEY = "UNILEVER";
    private static final int MIN_SHELF_LIFE_DAYS = 180; // 6 months minimum

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
        return storerKey != null && storerKey.toUpperCase().contains("UNILEVER");
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
        log.info("Executing Unilever pre-finalize for receipt: {}", context.getReceiptKey());

        FinalizePluginResult result = FinalizePluginResult.success();
        int processedCount = 0;

        for (FinalizeContext.ReceiptLineContext line : context.getLines()) {
            try {
                // 1. Validate batch number format (Lottable01)
                validateBatchNumber(line, result);

                // 2. Validate and standardize expiry date (Lottable04)
                validateExpiryDate(line, result);

                // 3. Validate production plant (Lottable05)
                validateProductionPlant(line, result);

                // 4. Set brand code (Lottable06)
                setBrandCode(context, line);

                // 5. Check shelf life compliance
                checkShelfLife(line, result);

                processedCount++;

            } catch (Exception e) {
                log.warn("Error processing Unilever line {}: {}", line.getLineNumber(), e.getMessage());
                result.addWarning("Line " + line.getLineNumber() + " processing error: " + e.getMessage());
            }
        }

        result.addMessage("Processed " + processedCount + " lines for Unilever");
        return result;
    }

    private void validateBatchNumber(FinalizeContext.ReceiptLineContext line, FinalizePluginResult result) {
        String batchNumber = line.getLottable01();
        if (batchNumber == null || batchNumber.isEmpty()) {
            result.addWarning("Line " + line.getLineNumber() + ": Missing batch number");
            return;
        }

        // Unilever batch format: typically alphanumeric, 8-12 characters
        if (batchNumber.length() < 8 || batchNumber.length() > 12) {
            result.addWarning("Line " + line.getLineNumber() + ": Batch number length outside expected range");
        }

        // Transform to uppercase
        if (!batchNumber.equals(batchNumber.toUpperCase())) {
            line.setLottable01(batchNumber.toUpperCase());
            line.markModified();
        }
    }

    private void validateExpiryDate(FinalizeContext.ReceiptLineContext line, FinalizePluginResult result) {
        String expiryStr = line.getLottable04();
        if (expiryStr == null || expiryStr.isEmpty()) {
            result.addWarning("Line " + line.getLineNumber() + ": Missing expiry date");
            return;
        }

        // Try to parse various date formats
        LocalDate expiryDate = parseDate(expiryStr);
        if (expiryDate == null) {
            result.addWarning("Line " + line.getLineNumber() + ": Invalid expiry date format: " + expiryStr);
            return;
        }

        // Standardize to YYYY-MM-DD format
        String standardized = expiryDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (!standardized.equals(expiryStr)) {
            line.setLottable04(standardized);
            line.markModified();
        }
    }

    private LocalDate parseDate(String dateStr) {
        DateTimeFormatter[] formats = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy")
        };

        for (DateTimeFormatter fmt : formats) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        return null;
    }

    private void validateProductionPlant(FinalizeContext.ReceiptLineContext line, FinalizePluginResult result) {
        String plantCode = line.getLottable05();
        if (plantCode != null && !plantCode.isEmpty()) {
            // Unilever plant codes are typically 4-digit
            if (!plantCode.matches("^[A-Z0-9]{2,6}$")) {
                result.addWarning("Line " + line.getLineNumber() + ": Invalid plant code format: " + plantCode);
            }
        }
    }

    private void setBrandCode(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable06() == null || line.getLottable06().isEmpty()) {
            String brand = getSkuAttribute(context.getStorerKey(), line.getSku(), "BRAND");
            if (brand != null) {
                line.setLottable06(brand);
                line.markModified();
            }
        }
    }

    private void checkShelfLife(FinalizeContext.ReceiptLineContext line, FinalizePluginResult result) {
        String expiryStr = line.getLottable04();
        if (expiryStr == null || expiryStr.isEmpty()) {
            return;
        }

        LocalDate expiryDate = parseDate(expiryStr);
        if (expiryDate != null) {
            long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
            if (daysRemaining < MIN_SHELF_LIFE_DAYS) {
                result.addWarning("Line " + line.getLineNumber() +
                    ": Shelf life (" + daysRemaining + " days) below minimum (" + MIN_SHELF_LIFE_DAYS + " days)");
            }
        }
    }

    private String getSkuAttribute(String storerKey, String sku, String attribute) {
        try {
            String column = switch (attribute) {
                case "BRAND" -> "skugroup";
                case "CATEGORY" -> "userdefined1";
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
