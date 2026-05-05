package com.wms.po.rules.service.lottable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thailand Lottable Rule Service.
 *
 * Replaces:
 * - SP-094: ispGenLot1_TH01 (120 LOC) - Lot1 generation for Thailand
 * - SP-095: ispGenLot2_TH02 (130 LOC) - Lot2 generation for Thailand
 * - SP-101: ispGenLottable02Pre_TH (160 LOC) - Lot2 pre-finalize for Thailand
 *
 * Handles Thailand-specific lottable generation:
 * - Lottable01: Product code + FDA registration
 * - Lottable02: Manufacturing batch/lot number
 * - Lottable03: Production date (Buddhist calendar)
 * - Lottable04: Expiration date (Buddhist calendar)
 * - Lottable05: FDA approval number
 * - Lottable06: Import permit number
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThailandLottableRuleService {

    private final JdbcTemplate jdbcTemplate;

    // Thai year offset (Buddhist calendar = Gregorian + 543)
    private static final int BUDDHIST_YEAR_OFFSET = 543;

    // Thai date format pattern
    private static final Pattern THAI_DATE_PATTERN = Pattern.compile("^(\\d{2})(\\d{2})(\\d{4})$");

    // FDA registration pattern
    private static final Pattern FDA_PATTERN = Pattern.compile("^\\d{2}-\\d{1}-\\d{5}-\\d{1}-\\d{4}$");

    /**
     * Generate Lottable01 for Thailand receipt line.
     * Format: SKU-FDA or FDA registration number
     *
     * Replaces: ispGenLot1_TH01
     *
     * @param context Receipt line context
     * @return Generated Lottable01 value
     */
    public String generateLottable01(ThailandLotContext context) {
        log.debug("Generating Lottable01 for Thailand SKU: {}", context.getSku());

        // 1. Check if FDA registration exists on receipt
        String fdaNumber = context.getFdaRegistration();
        if (fdaNumber != null && isFDAFormat(fdaNumber)) {
            return fdaNumber;
        }

        // 2. Look up FDA from SKU master
        fdaNumber = getSkuFDANumber(context.getStorerKey(), context.getSku());
        if (fdaNumber != null && isFDAFormat(fdaNumber)) {
            return fdaNumber;
        }

        // 3. Look up from storer config
        fdaNumber = getStorerFDAPrefix(context.getStorerKey());
        if (fdaNumber != null) {
            return fdaNumber + "-" + context.getSku();
        }

        // 4. Default: use SKU
        return truncate(context.getSku(), 30);
    }

    /**
     * Generate Lottable02 for Thailand receipt line.
     * Format: Manufacturing batch/lot number
     *
     * Replaces: ispGenLot2_TH02
     *
     * @param context Receipt line context
     * @return Generated Lottable02 value
     */
    public String generateLottable02(ThailandLotContext context) {
        log.debug("Generating Lottable02 for Thailand receipt: {}", context.getReceiptKey());

        // 1. Use existing batch/lot if provided
        if (context.getSupplierLot() != null && !context.getSupplierLot().isEmpty()) {
            return normalizeThaiLot(context.getSupplierLot());
        }

        // 2. Check receipt detail for batch
        String receiptBatch = getReceiptBatch(context.getReceiptKey(), context.getLineNumber());
        if (receiptBatch != null && !receiptBatch.isEmpty()) {
            return normalizeThaiLot(receiptBatch);
        }

        // 3. Generate based on manufacturing date
        LocalDate mfgDate = context.getManufacturingDate() != null
            ? context.getManufacturingDate()
            : LocalDate.now();

        return generateThaiLotNumber(mfgDate, context.getStorerKey());
    }

    /**
     * Generate Lottable02 Pre-finalize for Thailand.
     * Called during pre-finalize to set batch before posting.
     *
     * Replaces: ispGenLottable02Pre_TH
     *
     * @param context Receipt line context
     * @return Generated Lottable02 value
     */
    public String generateLottable02Pre(ThailandLotContext context) {
        log.debug("Generating Lottable02 Pre for Thailand: {}", context.getReceiptKey());

        // 1. Already has lot2 - normalize and return
        if (context.getExistingLottable02() != null && !context.getExistingLottable02().isEmpty()) {
            return normalizeThaiLot(context.getExistingLottable02());
        }

        // 2. Check if auto-generate is enabled for storer
        if (!isAutoGenerateLotEnabled(context.getStorerKey())) {
            return null; // Leave as null, will be set later
        }

        // 3. Generate new lot number
        return generateLottable02(context);
    }

    /**
     * Generate Lottable03 (Production Date) in Buddhist calendar format.
     *
     * @param context Receipt line context
     * @return Production date in DDMMYYYY format (Buddhist year)
     */
    public String generateLottable03ProductionDate(ThailandLotContext context) {
        LocalDate prodDate = context.getManufacturingDate();
        if (prodDate == null) {
            // Try to get from receipt
            prodDate = getReceiptProductionDate(context.getReceiptKey(), context.getLineNumber());
        }
        if (prodDate == null) {
            return null;
        }

        return formatToBuddhistDate(prodDate);
    }

    /**
     * Generate Lottable04 (Expiration Date) in Buddhist calendar format.
     *
     * @param context Receipt line context
     * @return Expiration date in DDMMYYYY format (Buddhist year)
     */
    public String generateLottable04ExpirationDate(ThailandLotContext context) {
        LocalDate expDate = context.getExpirationDate();
        if (expDate == null) {
            // Try to get from receipt
            expDate = getReceiptExpirationDate(context.getReceiptKey(), context.getLineNumber());
        }
        if (expDate == null) {
            // Try to calculate from manufacturing date + shelf life
            if (context.getManufacturingDate() != null) {
                int shelfLifeDays = getSkuShelfLifeDays(context.getStorerKey(), context.getSku());
                if (shelfLifeDays > 0) {
                    expDate = context.getManufacturingDate().plusDays(shelfLifeDays);
                }
            }
        }
        if (expDate == null) {
            return null;
        }

        return formatToBuddhistDate(expDate);
    }

    /**
     * Generate Lottable05 (FDA Approval Number).
     *
     * @param context Receipt line context
     * @return FDA approval number
     */
    public String generateLottable05FDA(ThailandLotContext context) {
        // 1. From context
        if (context.getFdaRegistration() != null) {
            return context.getFdaRegistration();
        }

        // 2. From receipt
        String fdaNumber = getReceiptFDA(context.getReceiptKey(), context.getLineNumber());
        if (fdaNumber != null) {
            return fdaNumber;
        }

        // 3. From SKU
        return getSkuFDANumber(context.getStorerKey(), context.getSku());
    }

    /**
     * Generate Lottable06 (Import Permit Number).
     *
     * @param context Receipt line context
     * @return Import permit number
     */
    public String generateLottable06ImportPermit(ThailandLotContext context) {
        // 1. From receipt
        String permit = getReceiptImportPermit(context.getReceiptKey());
        if (permit != null) {
            return permit;
        }

        // 2. From PO
        if (context.getPoKey() != null) {
            permit = getPOImportPermit(context.getPoKey());
            if (permit != null) {
                return permit;
            }
        }

        // 3. From storer default
        return getStorerDefaultPermit(context.getStorerKey());
    }

    /**
     * Validate Thai FDA number format.
     */
    public boolean validateFDANumber(String fdaNumber) {
        if (fdaNumber == null) return false;
        return isFDAFormat(fdaNumber);
    }

    /**
     * Convert Gregorian date to Buddhist date string.
     *
     * @param gregorianDate Gregorian date
     * @return Buddhist date in DDMMYYYY format
     */
    public String convertToBuddhistDate(LocalDate gregorianDate) {
        return formatToBuddhistDate(gregorianDate);
    }

    /**
     * Convert Buddhist date string to Gregorian date.
     *
     * @param buddhistDate Buddhist date in DDMMYYYY format
     * @return Gregorian date
     */
    public LocalDate convertFromBuddhistDate(String buddhistDate) {
        if (buddhistDate == null || buddhistDate.isEmpty()) {
            return null;
        }

        Matcher matcher = THAI_DATE_PATTERN.matcher(buddhistDate);
        if (matcher.matches()) {
            int day = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int buddhistYear = Integer.parseInt(matcher.group(3));
            int gregorianYear = buddhistYear - BUDDHIST_YEAR_OFFSET;
            return LocalDate.of(gregorianYear, month, day);
        }

        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Private Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private boolean isFDAFormat(String fdaNumber) {
        return fdaNumber != null && FDA_PATTERN.matcher(fdaNumber).matches();
    }

    private String normalizeThaiLot(String lot) {
        if (lot == null) return null;
        // Remove special characters, keep alphanumeric and hyphen
        return lot.replaceAll("[^A-Za-z0-9-]", "").toUpperCase();
    }

    private String generateThaiLotNumber(LocalDate date, String storerKey) {
        // Format: THYYMMDD-NNNN where YY is Buddhist year
        int buddhistYear = date.getYear() + BUDDHIST_YEAR_OFFSET;
        String datePart = String.format("%02d%02d%02d",
            buddhistYear % 100, date.getMonthValue(), date.getDayOfMonth());

        int sequence = getNextLotSequence(storerKey, "TH" + datePart);
        return "TH" + datePart + "-" + String.format("%04d", sequence);
    }

    private String formatToBuddhistDate(LocalDate date) {
        int buddhistYear = date.getYear() + BUDDHIST_YEAR_OFFSET;
        return String.format("%02d%02d%04d",
            date.getDayOfMonth(), date.getMonthValue(), buddhistYear);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Database Lookups
    // ═══════════════════════════════════════════════════════════════════════

    private String getSkuFDANumber(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT userdefined3 FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                String.class, storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getStorerFDAPrefix(String storerKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT configvalue FROM dbo.STORERCONFIG " +
                "WHERE storerkey = ? AND configkey = 'FDAPREFIX'",
                String.class, storerKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getReceiptBatch(String receiptKey, int lineNumber) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr1 FROM dbo.RECEIPTDETAIL WHERE receiptkey = ? AND receiptlinenumber = ?",
                String.class, receiptKey, lineNumber
            );
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAutoGenerateLotEnabled(String storerKey) {
        try {
            Integer enabled = jdbcTemplate.queryForObject(
                "SELECT CASE WHEN configvalue = '1' THEN 1 ELSE 0 END " +
                "FROM dbo.STORERCONFIG WHERE storerkey = ? AND configkey = 'AUTOGENLOT'",
                Integer.class, storerKey
            );
            return enabled != null && enabled == 1;
        } catch (Exception e) {
            return true; // Default to enabled
        }
    }

    private LocalDate getReceiptProductionDate(String receiptKey, int lineNumber) {
        try {
            java.sql.Date date = jdbcTemplate.queryForObject(
                "SELECT tolottable04 FROM dbo.RECEIPTDETAIL WHERE receiptkey = ? AND receiptlinenumber = ?",
                java.sql.Date.class, receiptKey, lineNumber
            );
            return date != null ? date.toLocalDate() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate getReceiptExpirationDate(String receiptKey, int lineNumber) {
        try {
            java.sql.Date date = jdbcTemplate.queryForObject(
                "SELECT tolottable05 FROM dbo.RECEIPTDETAIL WHERE receiptkey = ? AND receiptlinenumber = ?",
                java.sql.Date.class, receiptKey, lineNumber
            );
            return date != null ? date.toLocalDate() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int getSkuShelfLifeDays(String storerKey, String sku) {
        try {
            Integer days = jdbcTemplate.queryForObject(
                "SELECT shelflifedays FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                Integer.class, storerKey, sku
            );
            return days != null ? days : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String getReceiptFDA(String receiptKey, int lineNumber) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr3 FROM dbo.RECEIPTDETAIL WHERE receiptkey = ? AND receiptlinenumber = ?",
                String.class, receiptKey, lineNumber
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getReceiptImportPermit(String receiptKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr5 FROM dbo.RECEIPT WHERE receiptkey = ?",
                String.class, receiptKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getPOImportPermit(String poKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr3 FROM dbo.PO WHERE pokey = ?",
                String.class, poKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getStorerDefaultPermit(String storerKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT configvalue FROM dbo.STORERCONFIG " +
                "WHERE storerkey = ? AND configkey = 'DEFAULTIMPORTPERMIT'",
                String.class, storerKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private int getNextLotSequence(String storerKey, String prefix) {
        try {
            Integer maxSeq = jdbcTemplate.queryForObject(
                "SELECT ISNULL(MAX(CAST(SUBSTRING(lottable02, LEN(?) + 2, 4) AS INT)), 0) " +
                "FROM dbo.LOTXLOCXID WHERE storerkey = ? AND lottable02 LIKE ? + '-%'",
                Integer.class, prefix, storerKey, prefix
            );
            return (maxSeq != null ? maxSeq : 0) + 1;
        } catch (Exception e) {
            return 1;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Context Class
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    public static class ThailandLotContext {
        private String receiptKey;
        private int lineNumber;
        private String storerKey;
        private String sku;
        private String poKey;
        private Integer poLineNumber;
        private String supplierLot;
        private LocalDate manufacturingDate;
        private LocalDate expirationDate;
        private String fdaRegistration;
        private String existingLottable02;
    }
}
