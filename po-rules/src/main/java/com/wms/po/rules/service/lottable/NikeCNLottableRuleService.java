package com.wms.po.rules.service.lottable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nike China Lottable Rule Service.
 *
 * Replaces:
 * - SP-099: ispDefLot1FrRcptDtl_NIKECN (190 LOC) - Lot1 generation
 * - SP-100: ispGenLottable02Pre_NikeCN (180 LOC) - Lot2 pre-generation
 *
 * Handles Nike China specific lottable generation:
 * - Lottable01: Style-Color-Size code generation
 * - Lottable02: Pre-finalize lot code generation
 * - Lottable03: Season code parsing
 * - Lottable04: Factory code lookup
 * - Lottable05: Launch date formatting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NikeCNLottableRuleService {

    private final JdbcTemplate jdbcTemplate;

    // Nike style-color-size pattern
    private static final Pattern STYLE_COLOR_PATTERN = Pattern.compile("^([A-Z0-9]{6})-([0-9]{3})$");
    private static final Pattern FULL_SKU_PATTERN = Pattern.compile("^([A-Z0-9]{6})-([0-9]{3})-([A-Z0-9]+)$");

    // Nike season codes
    private static final Map<String, String> SEASON_MAP = Map.of(
        "SP", "Spring",
        "SU", "Summer",
        "FA", "Fall",
        "HO", "Holiday",
        "01", "Spring",
        "02", "Summer",
        "03", "Fall",
        "04", "Holiday"
    );

    /**
     * Generate Lottable01 for Nike CN receipt line.
     * Format: STYLE-COLOR or STYLE-COLOR-SIZE
     *
     * Replaces: ispDefLot1FrRcptDtl_NIKECN
     *
     * @param context Receipt line context
     * @return Generated Lottable01 value
     */
    public String generateLottable01(NikeCNLotContext context) {
        log.debug("Generating Lottable01 for Nike CN SKU: {}", context.getSku());

        // 1. Try to parse from SKU directly
        String styleColor = parseStyleColorFromSku(context.getSku());
        if (styleColor != null) {
            return styleColor;
        }

        // 2. Try to get from receipt detail user fields
        String receiptStyleColor = getReceiptStyleColor(context.getReceiptKey(), context.getLineNumber());
        if (receiptStyleColor != null) {
            return receiptStyleColor;
        }

        // 3. Try to get from PO detail
        String poStyleColor = getPOStyleColor(context.getPoKey(), context.getPoLineNumber());
        if (poStyleColor != null) {
            return poStyleColor;
        }

        // 4. Try to get from SKU master
        String skuStyleColor = getSkuStyleColor(context.getStorerKey(), context.getSku());
        if (skuStyleColor != null) {
            return skuStyleColor;
        }

        // 5. Default: use SKU as-is (truncate to 20 chars)
        return truncate(context.getSku(), 20);
    }

    /**
     * Generate Lottable02 for Nike CN receipt line (pre-finalize).
     * Format: YYMM-SEQ or FACTORY-YYMM
     *
     * Replaces: ispGenLottable02Pre_NikeCN
     *
     * @param context Receipt line context
     * @return Generated Lottable02 value
     */
    public String generateLottable02Pre(NikeCNLotContext context) {
        log.debug("Generating Lottable02 Pre for Nike CN receipt: {}", context.getReceiptKey());

        // 1. If already provided on receipt, use it
        if (context.getExistingLottable02() != null && !context.getExistingLottable02().isEmpty()) {
            return normalizeNikeLot2(context.getExistingLottable02());
        }

        // 2. Try to get factory code
        String factoryCode = getFactoryCode(context.getReceiptKey());

        // 3. Generate date portion (YYMM)
        String datePortion = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"));

        // 4. Combine factory + date or just date + sequence
        if (factoryCode != null && !factoryCode.isEmpty()) {
            return factoryCode + "-" + datePortion;
        }

        // 5. Generate sequence-based lot
        int sequence = getNextSequence(context.getStorerKey(), datePortion);
        return datePortion + "-" + String.format("%04d", sequence);
    }

    /**
     * Parse Lottable03 (Season) for Nike CN.
     *
     * @param context Receipt line context
     * @return Season code (e.g., "FA24" for Fall 2024)
     */
    public String parseLottable03Season(NikeCNLotContext context) {
        // 1. Try from receipt detail
        String seasonRaw = getReceiptSeason(context.getReceiptKey(), context.getLineNumber());
        if (seasonRaw != null) {
            return normalizeSeasonCode(seasonRaw);
        }

        // 2. Try from PO
        String poSeason = getPOSeason(context.getPoKey());
        if (poSeason != null) {
            return normalizeSeasonCode(poSeason);
        }

        // 3. Try from SKU
        String skuSeason = getSkuSeason(context.getStorerKey(), context.getSku());
        if (skuSeason != null) {
            return normalizeSeasonCode(skuSeason);
        }

        // 4. Default to current season
        return getCurrentSeasonCode();
    }

    /**
     * Get Lottable04 (Factory Code) for Nike CN.
     */
    public String getLottable04FactoryCode(NikeCNLotContext context) {
        // 1. From receipt header
        String factoryCode = getFactoryCode(context.getReceiptKey());
        if (factoryCode != null) {
            return factoryCode;
        }

        // 2. From PO
        return getPOFactoryCode(context.getPoKey());
    }

    /**
     * Parse Lottable05 (Launch Date) for Nike CN.
     *
     * @param context Receipt line context
     * @return Formatted launch date (YYYYMMDD)
     */
    public String parseLottable05LaunchDate(NikeCNLotContext context) {
        // 1. From receipt detail
        String launchDate = getReceiptLaunchDate(context.getReceiptKey(), context.getLineNumber());
        if (launchDate != null) {
            return formatLaunchDate(launchDate);
        }

        // 2. From SKU master
        String skuLaunchDate = getSkuLaunchDate(context.getStorerKey(), context.getSku());
        if (skuLaunchDate != null) {
            return formatLaunchDate(skuLaunchDate);
        }

        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Private Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private String parseStyleColorFromSku(String sku) {
        if (sku == null) return null;

        // Try full SKU pattern first (STYLE-COLOR-SIZE)
        Matcher fullMatcher = FULL_SKU_PATTERN.matcher(sku.toUpperCase());
        if (fullMatcher.matches()) {
            return fullMatcher.group(1) + "-" + fullMatcher.group(2);
        }

        // Try style-color pattern
        Matcher scMatcher = STYLE_COLOR_PATTERN.matcher(sku.toUpperCase());
        if (scMatcher.matches()) {
            return scMatcher.group(1) + "-" + scMatcher.group(2);
        }

        // Check if SKU contains hyphen in expected position
        if (sku.length() >= 10 && sku.charAt(6) == '-') {
            return sku.substring(0, 10).toUpperCase();
        }

        return null;
    }

    private String normalizeNikeLot2(String lot2) {
        if (lot2 == null) return null;
        // Remove any non-alphanumeric except hyphen
        return lot2.replaceAll("[^A-Za-z0-9-]", "").toUpperCase();
    }

    private String normalizeSeasonCode(String rawSeason) {
        if (rawSeason == null || rawSeason.isEmpty()) return null;

        String upper = rawSeason.toUpperCase().trim();

        // Already in correct format (e.g., FA24, SP25)
        if (upper.matches("^(SP|SU|FA|HO)\\d{2}$")) {
            return upper;
        }

        // Try to parse verbose format
        String seasonCode = null;
        String year = null;

        // Extract season
        for (Map.Entry<String, String> entry : SEASON_MAP.entrySet()) {
            if (upper.contains(entry.getValue().toUpperCase()) || upper.contains(entry.getKey())) {
                seasonCode = entry.getKey();
                break;
            }
        }

        // Extract year (look for 2-digit or 4-digit year)
        Matcher yearMatcher = Pattern.compile("(20\\d{2}|\\d{2})").matcher(upper);
        if (yearMatcher.find()) {
            String yearStr = yearMatcher.group(1);
            year = yearStr.length() == 4 ? yearStr.substring(2) : yearStr;
        }

        if (seasonCode != null && year != null) {
            return seasonCode + year;
        }

        return rawSeason; // Return original if can't normalize
    }

    private String getCurrentSeasonCode() {
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        String year = String.valueOf(now.getYear()).substring(2);

        if (month >= 3 && month <= 5) return "SP" + year;
        if (month >= 6 && month <= 8) return "SU" + year;
        if (month >= 9 && month <= 11) return "FA" + year;
        return "HO" + year;
    }

    private String formatLaunchDate(String rawDate) {
        if (rawDate == null) return null;

        // Try to parse various formats and convert to YYYYMMDD
        try {
            // If already in YYYYMMDD format
            if (rawDate.matches("^\\d{8}$")) {
                return rawDate;
            }

            // If in YYYY-MM-DD format
            if (rawDate.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
                return rawDate.substring(0, 10).replace("-", "");
            }

            // If in MM/DD/YYYY format
            if (rawDate.matches("^\\d{2}/\\d{2}/\\d{4}$")) {
                String[] parts = rawDate.split("/");
                return parts[2] + parts[0] + parts[1];
            }
        } catch (Exception e) {
            log.debug("Could not parse launch date: {}", rawDate);
        }

        return rawDate;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Database Lookups
    // ═══════════════════════════════════════════════════════════════════════

    private String getReceiptStyleColor(String receiptKey, int lineNumber) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr1 FROM dbo.RECEIPTDETAIL WHERE receiptkey = ? AND receiptlinenumber = ?",
                String.class,
                receiptKey, lineNumber
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getPOStyleColor(String poKey, Integer poLineNumber) {
        if (poKey == null || poLineNumber == null) return null;
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr1 FROM dbo.PODETAIL WHERE pokey = ? AND polinenumber = ?",
                String.class,
                poKey, poLineNumber
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getSkuStyleColor(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT userdefined1 FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                String.class,
                storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getFactoryCode(String receiptKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr4 FROM dbo.RECEIPT WHERE receiptkey = ?",
                String.class,
                receiptKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getPOFactoryCode(String poKey) {
        if (poKey == null) return null;
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr1 FROM dbo.PO WHERE pokey = ?",
                String.class,
                poKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private int getNextSequence(String storerKey, String prefix) {
        try {
            Integer maxSeq = jdbcTemplate.queryForObject(
                "SELECT ISNULL(MAX(CAST(SUBSTRING(lottable02, LEN(?) + 2, 4) AS INT)), 0) " +
                "FROM dbo.LOTXLOCXID WHERE storerkey = ? AND lottable02 LIKE ? + '-%'",
                Integer.class,
                prefix, storerKey, prefix
            );
            return (maxSeq != null ? maxSeq : 0) + 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private String getReceiptSeason(String receiptKey, int lineNumber) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr2 FROM dbo.RECEIPTDETAIL WHERE receiptkey = ? AND receiptlinenumber = ?",
                String.class,
                receiptKey, lineNumber
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getPOSeason(String poKey) {
        if (poKey == null) return null;
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr2 FROM dbo.PO WHERE pokey = ?",
                String.class,
                poKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getSkuSeason(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT userdefined2 FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                String.class,
                storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getReceiptLaunchDate(String receiptKey, int lineNumber) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr3 FROM dbo.RECEIPTDETAIL WHERE receiptkey = ? AND receiptlinenumber = ?",
                String.class,
                receiptKey, lineNumber
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getSkuLaunchDate(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT CONVERT(VARCHAR, launchdate, 112) FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                String.class,
                storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Context Class
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    public static class NikeCNLotContext {
        private String receiptKey;
        private int lineNumber;
        private String storerKey;
        private String sku;
        private String poKey;
        private Integer poLineNumber;
        private String existingLottable01;
        private String existingLottable02;
        private String existingLottable03;
    }
}
