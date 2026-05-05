package com.wms.po.plugin.finalize.impl;

import com.wms.po.plugin.finalize.AbstractPreFinalizePlugin;
import com.wms.po.plugin.finalize.FinalizeContext;
import com.wms.po.plugin.finalize.FinalizePluginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DSG Thailand Pre-Finalize Plugin.
 *
 * Replaces: ispPRREC13 (200 LOC)
 * Client: DSG (Thailand)
 *
 * DSG Thailand-specific pre-finalize validations:
 * - Sports category validation
 * - Thai FDA compliance
 * - DSG promotion code handling
 * - Brand/Category hierarchy
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DSGThailandPlugin extends AbstractPreFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispPRREC13";
    private static final String CLIENT_KEY = "DSG_TH";

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
        return 5; // Run early, before regional plugin
    }

    @Override
    public boolean shouldExecute(FinalizeContext context) {
        String storerKey = context.getStorerKey();
        return storerKey != null && storerKey.toUpperCase().contains("DSG");
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
        log.info("Executing DSG Thailand pre-finalize for receipt: {}", context.getReceiptKey());

        FinalizePluginResult result = FinalizePluginResult.success();
        int processedCount = 0;

        for (FinalizeContext.ReceiptLineContext line : context.getLines()) {
            try {
                // 1. Set brand code (Lottable01)
                setBrandCode(context, line);

                // 2. Set sports category (Lottable02)
                setSportsCategory(context, line);

                // 3. Set sub-category (Lottable03)
                setSubCategory(context, line);

                // 4. Handle promotion code (Lottable04)
                handlePromotionCode(line);

                // 5. Set price tier (Lottable05)
                setPriceTier(context, line);

                // 6. Validate Thai FDA if applicable (Lottable06)
                validateThaiFDA(context, line, result);

                processedCount++;

            } catch (Exception e) {
                log.warn("Error processing DSG Thailand line {}: {}", line.getLineNumber(), e.getMessage());
                result.addWarning("Line " + line.getLineNumber() + " processing error: " + e.getMessage());
            }
        }

        result.addMessage("Processed " + processedCount + " lines for DSG Thailand");
        return result;
    }

    private void setBrandCode(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable01() == null || line.getLottable01().isEmpty()) {
            String brand = getSkuAttribute(context.getStorerKey(), line.getSku(), "BRAND");
            if (brand != null) {
                line.setLottable01(brand);
                line.markModified();
            }
        }
    }

    private void setSportsCategory(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable02() == null || line.getLottable02().isEmpty()) {
            String category = getSkuAttribute(context.getStorerKey(), line.getSku(), "SPORTSCATEGORY");
            if (category != null) {
                line.setLottable02(category);
                line.markModified();
            } else {
                // Default to GENERAL
                line.setLottable02("GENERAL");
                line.markModified();
            }
        }
    }

    private void setSubCategory(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable03() == null || line.getLottable03().isEmpty()) {
            String subCategory = getSkuAttribute(context.getStorerKey(), line.getSku(), "SUBCATEGORY");
            if (subCategory != null) {
                line.setLottable03(subCategory);
                line.markModified();
            }
        }
    }

    private void handlePromotionCode(FinalizeContext.ReceiptLineContext line) {
        String promoCode = line.getLottable04();
        if (promoCode != null && !promoCode.isEmpty()) {
            // Standardize promotion code format
            String standardized = promoCode.toUpperCase().replaceAll("[^A-Z0-9]", "");
            if (!standardized.equals(promoCode)) {
                line.setLottable04(standardized);
                line.markModified();
            }
        }
    }

    private void setPriceTier(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable05() == null || line.getLottable05().isEmpty()) {
            // Determine price tier from SKU attributes
            String tier = getSkuAttribute(context.getStorerKey(), line.getSku(), "PRICETIER");
            if (tier == null) {
                tier = "STANDARD";
            }
            line.setLottable05(tier);
            line.markModified();
        }
    }

    private void validateThaiFDA(FinalizeContext context, FinalizeContext.ReceiptLineContext line,
                                  FinalizePluginResult result) {
        // Check if product requires FDA registration
        Boolean requiresFDA = getSkuBooleanAttribute(context.getStorerKey(), line.getSku(), "REQUIRES_FDA");
        if (Boolean.TRUE.equals(requiresFDA)) {
            String fdaNumber = line.getLottable06();
            if (fdaNumber == null || fdaNumber.isEmpty()) {
                result.addWarning("Line " + line.getLineNumber() + ": Missing FDA registration for regulated product");
            } else if (!fdaNumber.matches("^[0-9]{2}-[0-9]-[0-9]{5}-[0-9]-[0-9]{4}$")) {
                result.addWarning("Line " + line.getLineNumber() + ": Invalid Thai FDA number format");
            }
        }
    }

    private String getSkuAttribute(String storerKey, String sku, String attribute) {
        try {
            String column = switch (attribute) {
                case "BRAND" -> "skugroup";
                case "SPORTSCATEGORY" -> "userdefined1";
                case "SUBCATEGORY" -> "userdefined2";
                case "PRICETIER" -> "userdefined3";
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

    private Boolean getSkuBooleanAttribute(String storerKey, String sku, String attribute) {
        try {
            String value = jdbcTemplate.queryForObject(
                "SELECT userdefined5 FROM dbo.sku WHERE storerkey = ? AND sku = ?",
                String.class,
                storerKey, sku
            );
            return "Y".equalsIgnoreCase(value) || "1".equals(value);
        } catch (Exception e) {
            return false;
        }
    }
}
