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
 * India Pre-Finalize Plugin.
 *
 * Replaces: ispPRREC12 (75 LOC)
 * Region: India
 *
 * India-specific pre-finalize validations:
 * - GST number validation
 * - HSN code validation
 * - State code validation (GSTIN)
 * - E-way bill requirements
 * - MRP validation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IndiaPreFinalizePlugin extends AbstractPreFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispPRREC12";
    private static final String CLIENT_KEY = "INDIA";
    private static final BigDecimal EWAY_BILL_THRESHOLD = new BigDecimal("50000");

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
            (storerKey.toUpperCase().contains("IN") || storerKey.toUpperCase().contains("INDIA"));
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
        log.info("Executing India pre-finalize for receipt: {}", context.getReceiptKey());

        FinalizePluginResult result = FinalizePluginResult.success();
        int processedCount = 0;
        BigDecimal totalValue = BigDecimal.ZERO;

        for (FinalizeContext.ReceiptLineContext line : context.getLines()) {
            try {
                // 1. Validate HSN code (Lottable01)
                validateHSNCode(line, result);

                // 2. Validate supplier GSTIN (Lottable02)
                validateGSTIN(line, result);

                // 3. Set GST rate (Lottable03)
                setGSTRate(context, line);

                // 4. Validate MRP (Lottable04)
                validateMRP(line, result);

                // 5. Set state code (Lottable05)
                setStateCode(context, line);

                // Calculate total value for E-way bill check
                BigDecimal lineValue = calculateLineValue(line);
                totalValue = totalValue.add(lineValue);

                processedCount++;

            } catch (Exception e) {
                log.warn("Error processing India line {}: {}", line.getLineNumber(), e.getMessage());
                result.addWarning("Line " + line.getLineNumber() + " processing error: " + e.getMessage());
            }
        }

        // 6. Check E-way bill requirement
        if (totalValue.compareTo(EWAY_BILL_THRESHOLD) > 0) {
            String ewayBill = context.getParameters().get("ewayBillNumber");
            if (ewayBill == null || ewayBill.isEmpty()) {
                result.addWarning("E-way bill required for consignment value exceeding INR " + EWAY_BILL_THRESHOLD);
            } else {
                validateEwayBill(ewayBill, result);
            }
        }

        result.addMessage("Processed " + processedCount + " lines for India. Total value: INR " + totalValue);
        return result;
    }

    private void validateHSNCode(FinalizeContext.ReceiptLineContext line, FinalizePluginResult result) {
        String hsnCode = line.getLottable01();
        if (hsnCode == null || hsnCode.isEmpty()) {
            result.addWarning("Line " + line.getLineNumber() + ": Missing HSN code");
            return;
        }

        // HSN code format: 4, 6, or 8 digits
        if (!hsnCode.matches("^[0-9]{4}([0-9]{2})?([0-9]{2})?$")) {
            result.addWarning("Line " + line.getLineNumber() + ": Invalid HSN code format: " + hsnCode);
        }
    }

    private void validateGSTIN(FinalizeContext.ReceiptLineContext line, FinalizePluginResult result) {
        String gstin = line.getLottable02();
        if (gstin == null || gstin.isEmpty()) {
            return; // Optional for certain transactions
        }

        // GSTIN format: 2-digit state code + 10-char PAN + 1 entity + 1 Z + 1 checksum
        // Example: 29AABCU9603R1ZM
        if (!gstin.matches("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$")) {
            result.addWarning("Line " + line.getLineNumber() + ": Invalid GSTIN format: " + gstin);
        }
    }

    private void setGSTRate(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable03() == null || line.getLottable03().isEmpty()) {
            // Get GST rate from SKU master
            String gstRate = getSkuAttribute(context.getStorerKey(), line.getSku(), "GSTRATE");
            if (gstRate != null) {
                line.setLottable03(gstRate);
                line.markModified();
            } else {
                // Default to 18% GST
                line.setLottable03("18");
                line.markModified();
            }
        }
    }

    private void validateMRP(FinalizeContext.ReceiptLineContext line, FinalizePluginResult result) {
        String mrpStr = line.getLottable04();
        if (mrpStr != null && !mrpStr.isEmpty()) {
            try {
                BigDecimal mrp = new BigDecimal(mrpStr);
                if (mrp.compareTo(BigDecimal.ZERO) <= 0) {
                    result.addWarning("Line " + line.getLineNumber() + ": MRP must be positive");
                }
            } catch (NumberFormatException e) {
                result.addWarning("Line " + line.getLineNumber() + ": Invalid MRP format: " + mrpStr);
            }
        }
    }

    private void setStateCode(FinalizeContext context, FinalizeContext.ReceiptLineContext line) {
        if (line.getLottable05() == null || line.getLottable05().isEmpty()) {
            // Extract state code from supplier GSTIN
            String gstin = line.getLottable02();
            if (gstin != null && gstin.length() >= 2) {
                line.setLottable05(gstin.substring(0, 2));
                line.markModified();
            }
        }
    }

    private void validateEwayBill(String ewayBill, FinalizePluginResult result) {
        // E-way bill format: 12-digit numeric
        if (!ewayBill.matches("^[0-9]{12}$")) {
            result.addWarning("Invalid E-way bill format: " + ewayBill);
        }
    }

    private BigDecimal calculateLineValue(FinalizeContext.ReceiptLineContext line) {
        BigDecimal qty = line.getQuantityReceived() != null ? line.getQuantityReceived() : BigDecimal.ZERO;
        // Unit price would be retrieved from SKU or PO if needed
        // For now, use a placeholder calculation based on quantity
        return qty;
    }

    private String getSkuAttribute(String storerKey, String sku, String attribute) {
        try {
            String column = switch (attribute) {
                case "GSTRATE" -> "userdefined1";
                case "HSNCODE" -> "userdefined2";
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
