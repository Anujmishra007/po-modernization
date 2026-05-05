package com.wms.po.plugin.finalize.impl;

import com.wms.po.plugin.finalize.AbstractPostFinalizePlugin;
import com.wms.po.plugin.finalize.FinalizeContext;
import com.wms.po.plugin.finalize.FinalizePluginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * UCC Stamp Post-Finalize Plugin.
 *
 * Replaces: ispASNFZ01 (450 LOC)
 * Client: Universal (all clients with UCC enabled)
 *
 * Creates UCC-128 labels/stamps for finalized receipt lines:
 * - Generates SSCC-18 codes
 * - Creates IDUCC records
 * - Links inventory to UCC labels
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UCCStampPlugin extends AbstractPostFinalizePlugin {

    private final JdbcTemplate jdbcTemplate;

    private static final String PLUGIN_ID = "ispASNFZ01";
    private static final String CLIENT_KEY = "*"; // Universal

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
        return 50; // Run after client-specific plugins
    }

    @Override
    protected boolean shouldProcessReceipt(FinalizeContext context) {
        // Check if UCC generation is enabled for this storer
        return isUCCEnabled(context.getStorerKey());
    }

    @Override
    protected FinalizePluginResult doExecute(FinalizeContext context) {
        log.info("Generating UCC stamps for receipt: {}", context.getReceiptKey());

        int uccCount = 0;

        for (FinalizeContext.ReceiptLineContext line : context.getLines()) {
            if (line.isSkipped() || line.getToId() == null) {
                continue;
            }

            try {
                // Generate SSCC-18 code
                String sscc = generateSSCC(context.getStorerKey(), context.getFacility());

                // Create IDUCC record
                createIDUCCRecord(context, line, sscc);

                // Link to inventory
                linkUCCToInventory(line.getLotxlocxidKey(), sscc);

                uccCount++;

            } catch (Exception e) {
                log.warn("Failed to create UCC for line {}: {}", line.getLineNumber(), e.getMessage());
            }
        }

        FinalizePluginResult result = FinalizePluginResult.success();
        result.addMessage("Created " + uccCount + " UCC stamps");
        result.setOutputData("uccCount", uccCount);
        return result;
    }

    private boolean isUCCEnabled(String storerKey) {
        try {
            String enabled = jdbcTemplate.queryForObject(
                "SELECT configvalue FROM dbo.storerconfig " +
                "WHERE storerkey = ? AND configkey = 'GenerateUCC'",
                String.class,
                storerKey
            );
            return "1".equals(enabled) || "Y".equalsIgnoreCase(enabled);
        } catch (Exception e) {
            return false;
        }
    }

    private String generateSSCC(String storerKey, String facility) {
        // Get company prefix
        String companyPrefix = getCompanyPrefix(storerKey);
        if (companyPrefix == null) {
            companyPrefix = "0000000"; // Default
        }

        // Get next sequence number
        String sequence = getNextSequence("SSCC", storerKey);

        // Build SSCC-18: Extension digit (0) + Company prefix + Serial reference + Check digit
        String ssccBase = "0" + companyPrefix + sequence;
        char checkDigit = calculateMod10CheckDigit(ssccBase);

        return ssccBase + checkDigit;
    }

    private String getCompanyPrefix(String storerKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT configvalue FROM dbo.storerconfig " +
                "WHERE storerkey = ? AND configkey = 'GS1CompanyPrefix'",
                String.class,
                storerKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getNextSequence(String seqType, String storerKey) {
        try {
            // Call nspg_GetKey equivalent
            return jdbcTemplate.queryForObject(
                "SELECT RIGHT('000000000' + CAST(nextval AS VARCHAR), 9) " +
                "FROM dbo.ncounter WHERE tablename = ? AND storerkey = ?",
                String.class,
                seqType, storerKey
            );
        } catch (Exception e) {
            return String.format("%09d", System.currentTimeMillis() % 1000000000);
        }
    }

    private char calculateMod10CheckDigit(String data) {
        int sum = 0;
        boolean multiply = true;

        for (int i = data.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(data.charAt(i));
            if (multiply) {
                digit *= 3;
            }
            sum += digit;
            multiply = !multiply;
        }

        int remainder = sum % 10;
        return remainder == 0 ? '0' : Character.forDigit(10 - remainder, 10);
    }

    private void createIDUCCRecord(FinalizeContext context,
                                    FinalizeContext.ReceiptLineContext line,
                                    String sscc) {
        jdbcTemplate.update(
            "INSERT INTO dbo.iducc (sscc, storerkey, sku, qty, receiptkey, receiptlinenumber, " +
            "lot, loc, id, status, adddate, addwho) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '0', GETDATE(), ?)",
            sscc,
            context.getStorerKey(),
            line.getSku(),
            line.getQuantityReceived(),
            context.getReceiptKey(),
            line.getLineNumber(),
            line.getLottable01(),
            line.getToLocation(),
            line.getToId(),
            context.getUserId()
        );
    }

    private void linkUCCToInventory(String lotxlocxidKey, String sscc) {
        if (lotxlocxidKey == null) return;

        jdbcTemplate.update(
            "UPDATE dbo.lotxlocxid SET uccid = ? WHERE lotxlocxidkey = ?",
            sscc, lotxlocxidKey
        );
    }
}
