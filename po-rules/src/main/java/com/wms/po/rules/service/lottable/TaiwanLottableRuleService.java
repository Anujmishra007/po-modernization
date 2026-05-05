package com.wms.po.rules.service.lottable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Taiwan Lottable Rule Service.
 *
 * Replaces:
 * - SP-096: ispGenLot12_TW01 (140 LOC) - Combined Lot1/Lot2 generation for Taiwan
 *
 * Handles Taiwan-specific lottable generation:
 * - Lottable01: HS Code (Harmonized System tariff code)
 * - Lottable02: Import permit/batch number
 * - Lottable03: Production date (ROC calendar: Year = Gregorian - 1911)
 * - Lottable04: Expiration date (ROC calendar)
 * - Lottable05: Country of origin code
 * - Lottable06: Customs declaration number
 *
 * Taiwan uses Republic of China (ROC) calendar year.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaiwanLottableRuleService {

    private final JdbcTemplate jdbcTemplate;

    // ROC year offset (ROC year = Gregorian - 1911)
    private static final int ROC_YEAR_OFFSET = 1911;

    // HS Code pattern (6-10 digits)
    private static final Pattern HS_CODE_PATTERN = Pattern.compile("^\\d{6,10}$");

    // ROC date pattern (YYYMMDD where YYY is ROC year)
    private static final Pattern ROC_DATE_PATTERN = Pattern.compile("^(\\d{3})(\\d{2})(\\d{2})$");

    // Import permit pattern
    private static final Pattern IMPORT_PERMIT_PATTERN = Pattern.compile("^[A-Z]{2}\\d{8,12}$");

    /**
     * Generate Lottable01 (HS Code) for Taiwan receipt line.
     *
     * Replaces part of: ispGenLot12_TW01
     *
     * @param context Receipt line context
     * @return HS Code or null
     */
    public String generateLottable01HSCode(TaiwanLotContext context) {
        log.debug("Generating Lottable01 (HS Code) for Taiwan SKU: {}", context.getSku());

        // 1. From receipt detail
        String hsCode = context.getHsCode();
        if (hsCode != null && isValidHSCode(hsCode)) {
            return normalizeHSCode(hsCode);
        }

        // 2. From SKU master
        hsCode = getSkuHSCode(context.getStorerKey(), context.getSku());
        if (hsCode != null && isValidHSCode(hsCode)) {
            return normalizeHSCode(hsCode);
        }

        // 3. From product category default
        hsCode = getCategoryHSCode(context.getStorerKey(), context.getSku());
        if (hsCode != null) {
            return normalizeHSCode(hsCode);
        }

        return null;
    }

    /**
     * Generate Lottable02 (Import Permit/Batch) for Taiwan receipt line.
     *
     * Replaces part of: ispGenLot12_TW01
     *
     * @param context Receipt line context
     * @return Import permit or generated batch number
     */
    public String generateLottable02ImportPermit(TaiwanLotContext context) {
        log.debug("Generating Lottable02 (Import Permit) for Taiwan receipt: {}", context.getReceiptKey());

        // 1. Use existing import permit if provided
        if (context.getImportPermit() != null && !context.getImportPermit().isEmpty()) {
            return normalizePermit(context.getImportPermit());
        }

        // 2. Check receipt for permit number
        String permit = getReceiptImportPermit(context.getReceiptKey());
        if (permit != null && !permit.isEmpty()) {
            return normalizePermit(permit);
        }

        // 3. Check PO for permit number
        if (context.getPoKey() != null) {
            permit = getPOImportPermit(context.getPoKey());
            if (permit != null && !permit.isEmpty()) {
                return normalizePermit(permit);
            }
        }

        // 4. Generate internal batch number
        return generateTaiwanBatchNumber(context.getStorerKey());
    }

    /**
     * Generate both Lot1 and Lot2 together (combined logic).
     *
     * @param context Receipt line context
     * @return LotResult with both values
     */
    public LotResult generateLot12Combined(TaiwanLotContext context) {
        String lot1 = generateLottable01HSCode(context);
        String lot2 = generateLottable02ImportPermit(context);

        return new LotResult(lot1, lot2);
    }

    /**
     * Generate Lottable03 (Production Date) in ROC calendar format.
     *
     * @param context Receipt line context
     * @return Production date in YYYMMDD format (ROC year)
     */
    public String generateLottable03ProductionDate(TaiwanLotContext context) {
        LocalDate prodDate = context.getProductionDate();
        if (prodDate == null) {
            prodDate = getReceiptProductionDate(context.getReceiptKey(), context.getLineNumber());
        }
        if (prodDate == null) {
            return null;
        }

        return formatToROCDate(prodDate);
    }

    /**
     * Generate Lottable04 (Expiration Date) in ROC calendar format.
     *
     * @param context Receipt line context
     * @return Expiration date in YYYMMDD format (ROC year)
     */
    public String generateLottable04ExpirationDate(TaiwanLotContext context) {
        LocalDate expDate = context.getExpirationDate();
        if (expDate == null) {
            expDate = getReceiptExpirationDate(context.getReceiptKey(), context.getLineNumber());
        }
        if (expDate == null && context.getProductionDate() != null) {
            // Calculate from production date + shelf life
            int shelfLifeDays = getSkuShelfLifeDays(context.getStorerKey(), context.getSku());
            if (shelfLifeDays > 0) {
                expDate = context.getProductionDate().plusDays(shelfLifeDays);
            }
        }
        if (expDate == null) {
            return null;
        }

        return formatToROCDate(expDate);
    }

    /**
     * Generate Lottable05 (Country of Origin).
     *
     * @param context Receipt line context
     * @return Country of origin code (2-letter ISO)
     */
    public String generateLottable05CountryOfOrigin(TaiwanLotContext context) {
        // 1. From context
        if (context.getCountryOfOrigin() != null) {
            return normalizeCountryCode(context.getCountryOfOrigin());
        }

        // 2. From receipt
        String coo = getReceiptCOO(context.getReceiptKey(), context.getLineNumber());
        if (coo != null) {
            return normalizeCountryCode(coo);
        }

        // 3. From PO
        if (context.getPoKey() != null) {
            coo = getPOCOO(context.getPoKey());
            if (coo != null) {
                return normalizeCountryCode(coo);
            }
        }

        // 4. From SKU default
        return getSkuDefaultCOO(context.getStorerKey(), context.getSku());
    }

    /**
     * Generate Lottable06 (Customs Declaration Number).
     *
     * @param context Receipt line context
     * @return Customs declaration number
     */
    public String generateLottable06CustomsDeclaration(TaiwanLotContext context) {
        // 1. From receipt header
        String declaration = getReceiptCustomsDeclaration(context.getReceiptKey());
        if (declaration != null) {
            return declaration;
        }

        // 2. From PO
        if (context.getPoKey() != null) {
            return getPOCustomsDeclaration(context.getPoKey());
        }

        return null;
    }

    /**
     * Validate Taiwan HS Code.
     */
    public boolean isValidHSCode(String hsCode) {
        if (hsCode == null) return false;
        String cleaned = hsCode.replaceAll("[^0-9]", "");
        return HS_CODE_PATTERN.matcher(cleaned).matches();
    }

    /**
     * Convert Gregorian date to ROC date string.
     *
     * @param gregorianDate Gregorian date
     * @return ROC date in YYYMMDD format
     */
    public String convertToROCDate(LocalDate gregorianDate) {
        return formatToROCDate(gregorianDate);
    }

    /**
     * Convert ROC date string to Gregorian date.
     *
     * @param rocDate ROC date in YYYMMDD format
     * @return Gregorian date
     */
    public LocalDate convertFromROCDate(String rocDate) {
        if (rocDate == null || rocDate.isEmpty()) {
            return null;
        }

        Matcher matcher = ROC_DATE_PATTERN.matcher(rocDate);
        if (matcher.matches()) {
            int rocYear = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            int gregorianYear = rocYear + ROC_YEAR_OFFSET;
            return LocalDate.of(gregorianYear, month, day);
        }

        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Private Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private String normalizeHSCode(String hsCode) {
        if (hsCode == null) return null;
        // Remove all non-digits and format to 10 digits
        String digits = hsCode.replaceAll("[^0-9]", "");
        if (digits.length() < 6) return null;
        // Right-pad with zeros to 10 digits if needed
        return String.format("%-10s", digits).replace(' ', '0').substring(0, 10);
    }

    private String normalizePermit(String permit) {
        if (permit == null) return null;
        return permit.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null) return null;
        String cleaned = countryCode.trim().toUpperCase();
        // Return first 2 characters
        return cleaned.length() >= 2 ? cleaned.substring(0, 2) : cleaned;
    }

    private String generateTaiwanBatchNumber(String storerKey) {
        // Format: TWYYMMDD-NNNN
        LocalDate now = LocalDate.now();
        String datePart = "TW" + formatToROCDate(now).substring(1); // Remove first digit of year

        int sequence = getNextBatchSequence(storerKey, datePart);
        return datePart + "-" + String.format("%04d", sequence);
    }

    private String formatToROCDate(LocalDate date) {
        int rocYear = date.getYear() - ROC_YEAR_OFFSET;
        return String.format("%03d%02d%02d", rocYear, date.getMonthValue(), date.getDayOfMonth());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Database Lookups
    // ═══════════════════════════════════════════════════════════════════════

    private String getSkuHSCode(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT tariffkey FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                String.class, storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getCategoryHSCode(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT c.hscode FROM dbo.SKU s " +
                "JOIN dbo.COMMODITYCLASS c ON s.commodityclass = c.commodityclass " +
                "WHERE s.storerkey = ? AND s.sku = ?",
                String.class, storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getReceiptImportPermit(String receiptKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr3 FROM dbo.RECEIPT WHERE receiptkey = ?",
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

    private LocalDate getReceiptProductionDate(String receiptKey, int lineNumber) {
        try {
            java.sql.Date date = jdbcTemplate.queryForObject(
                "SELECT tolottable04 FROM dbo.RECEIPTDETAIL " +
                "WHERE receiptkey = ? AND receiptlinenumber = ?",
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
                "SELECT tolottable05 FROM dbo.RECEIPTDETAIL " +
                "WHERE receiptkey = ? AND receiptlinenumber = ?",
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

    private String getReceiptCOO(String receiptKey, int lineNumber) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT tolottable10 FROM dbo.RECEIPTDETAIL " +
                "WHERE receiptkey = ? AND receiptlinenumber = ?",
                String.class, receiptKey, lineNumber
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getPOCOO(String poKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT countryoforigin FROM dbo.PO WHERE pokey = ?",
                String.class, poKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getSkuDefaultCOO(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT countryoforigin FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                String.class, storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getReceiptCustomsDeclaration(String receiptKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr4 FROM dbo.RECEIPT WHERE receiptkey = ?",
                String.class, receiptKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getPOCustomsDeclaration(String poKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr4 FROM dbo.PO WHERE pokey = ?",
                String.class, poKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private int getNextBatchSequence(String storerKey, String prefix) {
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
    // Context and Result Classes
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    public static class TaiwanLotContext {
        private String receiptKey;
        private int lineNumber;
        private String storerKey;
        private String sku;
        private String poKey;
        private Integer poLineNumber;
        private String hsCode;
        private String importPermit;
        private LocalDate productionDate;
        private LocalDate expirationDate;
        private String countryOfOrigin;
    }

    public record LotResult(String lottable01, String lottable02) {}
}
