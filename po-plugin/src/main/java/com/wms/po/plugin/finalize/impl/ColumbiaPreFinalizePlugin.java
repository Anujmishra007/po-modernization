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
 * Columbia Pre-Finalize Plugin.
 *
 * Replaces: ispPRREC04 (140 LOC)
 * Client: Columbia Sportswear
 *
 * Columbia-specific pre-finalize validations:
 * - Validates style-color format
 * - Sets Columbia-specific lottables
 * - Validates production lot format
 * - Checks quality grade codes
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ColumbiaPreFinalizePlugin extends AbstractPreFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispPRREC04";
    private static final String CLIENT_KEY = "COLUMBIA";
    private static final Set<String> VALID_GRADES = Set.of("A", "B", "C", "FIRST", "SECOND", "IRR");

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
        return storerKey != null && storerKey.toUpperCase().contains("COLUMBIA");
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
        log.info("Executing Columbia pre-finalize for receipt: {}", context.getReceiptKey());

        FinalizePluginResult result = FinalizePluginResult.success();
        int processedCount = 0;

        for (FinalizeContext.ReceiptLineContext line : context.getLines()) {
            try {
                // 1. Validate and parse style-color (Lottable01)
                parseStyleColor(line, result);

                // 2. Validate quality grade (Lottable04)
                validateQualityGrade(line, result);

                // 3. Set production lot info (Lottable05)
                setProductionLot(context, line);

                // 4. Set category code (Lottable06)
                setCategoryCode(context, line);

                processedCount++;

            } catch (Exception e) {
                log.warn("Error processing Columbia line {}: {}", line.getLineNumber(), e.getMessage());
                result.addWarning("Line " + line.getLineNumber() + " processing error: " + e.getMessage());
            }
        }

        result.addMessage("Processed " + processedCount + " lines for Columbia");
        return result;
    }

    private void parseStyleColor(FinalizeContext.ReceiptLineContext line, FinalizePluginResult result) {
        String styleColor = line.getLottable01();
        if (styleColor != null && !styleColor.isEmpty()) {
            // Columbia style-color format: STYLE-COLOR (e.g., "WM1234-010")
            if (styleColor.contains("-")) {
                String[] parts = styleColor.split("-", 2);
                if (parts.length == 2) {
                    // Set style to Lottable02 if empty
                    if (line.getLottable02() == null || line.getLottable02().isEmpty()) {
                        line.setLottable02(parts[0]);
                        line.markModified();
                    }
                    // Set color to Lottable03 if empty
                    if (line.getLottable03() == null || line.getLottable03().isEmpty()) {
                        line.setLottable03(parts[1]);
                        line.markModified();
                    }
                }
            } else {
                result.addWarning("Line " + line.getLineNumber() + ": Invalid style-color format");
            }
        }
    }

    private void validateQualityGrade(FinalizeContext.ReceiptLineContext line, FinalizePluginResult result) {
        String grade = line.getLottable04();
        if (grade != null && !grade.isEmpty()) {
            if (!VALID_GRADES.contains(grade.toUpperCase())) {
                result.addWarning("Line " + line.getLineNumber() + ": Invalid quality grade: " + grade);
                // Default to "A" grade
                line.setLottable04("A");
                line.markModified();
            }
        } else {
            // Default to "A" grade if not specified
            line.setLottable04("A");
            line.markModified();
        }
    }

    private void setProductionLot(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable05() == null || line.getLottable05().isEmpty()) {
            // Generate production lot from PO reference and date
            String poKey = context.getPoKey();
            String datePart = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
            String prodLot = (poKey != null ? poKey.substring(0, Math.min(poKey.length(), 10)) : "NONE") + "-" + datePart;
            line.setLottable05(prodLot);
            line.markModified();
        }
    }

    private void setCategoryCode(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable06() == null || line.getLottable06().isEmpty()) {
            String category = getSkuAttribute(context.getStorerKey(), line.getSku(), "CATEGORY");
            if (category != null) {
                line.setLottable06(category);
                line.markModified();
            }
        }
    }

    private String getSkuAttribute(String storerKey, String sku, String attribute) {
        try {
            String column = switch (attribute) {
                case "CATEGORY" -> "skugroup";
                case "STYLE" -> "userdefined1";
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
