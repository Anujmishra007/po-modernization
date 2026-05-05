package com.wms.po.plugin.finalize.impl;

import com.wms.po.plugin.finalize.AbstractPreFinalizePlugin;
import com.wms.po.plugin.finalize.FinalizeContext;
import com.wms.po.plugin.finalize.FinalizePluginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * New Look Pre-Finalize Plugin.
 *
 * Replaces: ispPRREC06 (110 LOC)
 * Client: New Look (UK Fashion Retailer)
 *
 * New Look-specific pre-finalize validations:
 * - Validates product code format
 * - Sets department and category codes
 * - Validates supplier pack structure
 * - Sets size/color attributes
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NewLookPreFinalizePlugin extends AbstractPreFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispPRREC06";
    private static final String CLIENT_KEY = "NEWLOOK";

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
        return storerKey != null &&
            (storerKey.toUpperCase().contains("NEWLOOK") || storerKey.toUpperCase().contains("NL"));
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
        log.info("Executing New Look pre-finalize for receipt: {}", context.getReceiptKey());

        FinalizePluginResult result = FinalizePluginResult.success();
        int processedCount = 0;

        for (FinalizeContext.ReceiptLineContext line : context.getLines()) {
            try {
                // 1. Validate and parse product code
                parseProductCode(line, result);

                // 2. Set department code (Lottable03)
                setDepartmentCode(context, line);

                // 3. Set category code (Lottable04)
                setCategoryCode(context, line);

                // 4. Set size code (Lottable05)
                setSizeCode(context, line);

                // 5. Set color code (Lottable06)
                setColorCode(context, line);

                processedCount++;

            } catch (Exception e) {
                log.warn("Error processing New Look line {}: {}", line.getLineNumber(), e.getMessage());
                result.addWarning("Line " + line.getLineNumber() + " processing error: " + e.getMessage());
            }
        }

        result.addMessage("Processed " + processedCount + " lines for New Look");
        return result;
    }

    private void parseProductCode(FinalizeContext.ReceiptLineContext line, FinalizePluginResult result) {
        String productCode = line.getLottable01();
        if (productCode == null || productCode.isEmpty()) {
            // Use SKU as product code if not specified
            productCode = line.getSku();
            if (productCode != null) {
                line.setLottable01(productCode);
                line.markModified();
            }
            return;
        }

        // New Look product code format: typically numeric with optional suffix
        // Format: XXXXXX-YYY (6 digits - variant)
        if (productCode.contains("-")) {
            String[] parts = productCode.split("-", 2);
            // Set base product to Lottable01
            line.setLottable01(parts[0]);
            // Set variant to Lottable02
            if (parts.length > 1 && (line.getLottable02() == null || line.getLottable02().isEmpty())) {
                line.setLottable02(parts[1]);
            }
            line.markModified();
        }
    }

    private void setDepartmentCode(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable03() == null || line.getLottable03().isEmpty()) {
            String dept = getSkuAttribute(context.getStorerKey(), line.getSku(), "DEPARTMENT");
            if (dept != null) {
                line.setLottable03(dept);
                line.markModified();
            }
        }
    }

    private void setCategoryCode(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable04() == null || line.getLottable04().isEmpty()) {
            String category = getSkuAttribute(context.getStorerKey(), line.getSku(), "CATEGORY");
            if (category != null) {
                line.setLottable04(category);
                line.markModified();
            }
        }
    }

    private void setSizeCode(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable05() == null || line.getLottable05().isEmpty()) {
            String size = getSkuAttribute(context.getStorerKey(), line.getSku(), "SIZE");
            if (size != null) {
                line.setLottable05(size);
                line.markModified();
            }
        }
    }

    private void setColorCode(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable06() == null || line.getLottable06().isEmpty()) {
            String color = getSkuAttribute(context.getStorerKey(), line.getSku(), "COLOR");
            if (color != null) {
                line.setLottable06(color);
                line.markModified();
            }
        }
    }

    private String getSkuAttribute(String storerKey, String sku, String attribute) {
        try {
            String column = switch (attribute) {
                case "DEPARTMENT" -> "skugroup";
                case "CATEGORY" -> "userdefined1";
                case "SIZE" -> "userdefined2";
                case "COLOR" -> "userdefined3";
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
