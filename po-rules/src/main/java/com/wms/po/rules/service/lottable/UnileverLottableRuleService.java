package com.wms.po.rules.service.lottable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unilever Lottable Rule Service.
 *
 * Replaces:
 * - SP-098: ispGenLotUNILEVER (170 LOC) - Lottable generation for Unilever products
 *
 * Handles Unilever-specific lottable generation:
 * - Lottable01: Brand/Product code
 * - Lottable02: Manufacturing batch (Unilever code format)
 * - Lottable03: Production date
 * - Lottable04: Expiration date
 * - Lottable05: Manufacturing site code
 * - Lottable06: Production line/shift
 *
 * Unilever batch format: PYMMDDHHL
 * P = Plant code (letter)
 * YMMDD = Year (last digit) + Month + Day
 * HH = Hour (00-23)
 * L = Line (1-9)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnileverLottableRuleService {

    private final JdbcTemplate jdbcTemplate;

    // Unilever batch code pattern: PYMMDDHHL
    private static final Pattern BATCH_CODE_PATTERN = Pattern.compile("^([A-Z])(\\d)(\\d{2})(\\d{2})(\\d{2})(\\d)$");

    // Alternative pattern: YMMDD-PPPP-LL
    private static final Pattern ALT_BATCH_PATTERN = Pattern.compile("^(\\d{5})-([A-Z]{4})-(\\d{2})$");

    // SAP material pattern
    private static final Pattern MATERIAL_PATTERN = Pattern.compile("^\\d{8,18}$");

    // Minimum remaining shelf life percentage for acceptance
    private static final int MIN_SHELF_LIFE_PERCENT = 75;

    /**
     * Generate Lottable01 (Brand/Product Code) for Unilever receipt line.
     *
     * @param context Receipt line context
     * @return Brand/Product code
     */
    public String generateLottable01BrandCode(UnileverLotContext context) {
        log.debug("Generating Lottable01 (Brand) for Unilever SKU: {}", context.getSku());

        // 1. From context
        if (context.getBrandCode() != null && !context.getBrandCode().isEmpty()) {
            return normalizeBrandCode(context.getBrandCode());
        }

        // 2. From SKU master
        String brand = getSkuBrandCode(context.getStorerKey(), context.getSku());
        if (brand != null) {
            return normalizeBrandCode(brand);
        }

        // 3. From SKU prefix (first 4 chars)
        if (context.getSku() != null && context.getSku().length() >= 4) {
            return context.getSku().substring(0, 4).toUpperCase();
        }

        return context.getSku();
    }

    /**
     * Generate Lottable02 (Manufacturing Batch) for Unilever receipt line.
     *
     * Replaces: ispGenLotUNILEVER
     *
     * @param context Receipt line context
     * @return Batch code
     */
    public String generateLottable02BatchCode(UnileverLotContext context) {
        log.debug("Generating Lottable02 (Batch) for Unilever receipt: {}", context.getReceiptKey());

        // 1. Use existing batch if provided and valid
        if (context.getBatchCode() != null && isValidBatchCode(context.getBatchCode())) {
            return context.getBatchCode().toUpperCase();
        }

        // 2. Check receipt for supplier lot
        String supplierLot = getReceiptSupplierLot(context.getReceiptKey(), context.getLineNumber());
        if (supplierLot != null && isValidBatchCode(supplierLot)) {
            return supplierLot.toUpperCase();
        }

        // 3. Generate new batch code based on production parameters
        LocalDate prodDate = context.getProductionDate() != null
            ? context.getProductionDate()
            : LocalDate.now();

        String plantCode = context.getPlantCode() != null
            ? context.getPlantCode()
            : getStorerDefaultPlant(context.getStorerKey());

        int hour = context.getProductionHour() >= 0 ? context.getProductionHour() : 8;
        int line = context.getProductionLine() > 0 ? context.getProductionLine() : 1;

        return generateUnileverBatchCode(prodDate, plantCode, hour, line);
    }

    /**
     * Generate Lottable03 (Production Date).
     *
     * @param context Receipt line context
     * @return Production date in YYYYMMDD format
     */
    public String generateLottable03ProductionDate(UnileverLotContext context) {
        LocalDate prodDate = context.getProductionDate();

        // Try to parse from batch code if not provided
        if (prodDate == null && context.getBatchCode() != null) {
            prodDate = parseDateFromBatchCode(context.getBatchCode());
        }

        // Try from receipt
        if (prodDate == null) {
            prodDate = getReceiptProductionDate(context.getReceiptKey(), context.getLineNumber());
        }

        if (prodDate == null) {
            return null;
        }

        return prodDate.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    /**
     * Generate Lottable04 (Expiration Date).
     *
     * @param context Receipt line context
     * @return Expiration date in YYYYMMDD format
     */
    public String generateLottable04ExpirationDate(UnileverLotContext context) {
        LocalDate expDate = context.getExpirationDate();

        if (expDate == null) {
            expDate = getReceiptExpirationDate(context.getReceiptKey(), context.getLineNumber());
        }

        // Calculate from production date + shelf life
        if (expDate == null) {
            LocalDate prodDate = context.getProductionDate();
            if (prodDate == null && context.getBatchCode() != null) {
                prodDate = parseDateFromBatchCode(context.getBatchCode());
            }
            if (prodDate != null) {
                int shelfLifeDays = getSkuShelfLifeDays(context.getStorerKey(), context.getSku());
                if (shelfLifeDays > 0) {
                    expDate = prodDate.plusDays(shelfLifeDays);
                }
            }
        }

        if (expDate == null) {
            return null;
        }

        return expDate.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    /**
     * Generate Lottable05 (Manufacturing Site Code).
     *
     * @param context Receipt line context
     * @return Site code
     */
    public String generateLottable05SiteCode(UnileverLotContext context) {
        // 1. From context
        if (context.getPlantCode() != null) {
            return context.getPlantCode();
        }

        // 2. Try to parse from batch code
        if (context.getBatchCode() != null) {
            String plant = parsePlantFromBatchCode(context.getBatchCode());
            if (plant != null) {
                return plant;
            }
        }

        // 3. From receipt
        String site = getReceiptSiteCode(context.getReceiptKey());
        if (site != null) {
            return site;
        }

        // 4. From PO
        if (context.getPoKey() != null) {
            site = getPOSiteCode(context.getPoKey());
            if (site != null) {
                return site;
            }
        }

        // 5. From storer default
        return getStorerDefaultPlant(context.getStorerKey());
    }

    /**
     * Generate Lottable06 (Production Line/Shift).
     *
     * @param context Receipt line context
     * @return Line/shift code
     */
    public String generateLottable06LineShift(UnileverLotContext context) {
        StringBuilder lineShift = new StringBuilder();

        // Line number
        int line = context.getProductionLine();
        if (line <= 0 && context.getBatchCode() != null) {
            line = parseLineFromBatchCode(context.getBatchCode());
        }
        if (line > 0) {
            lineShift.append("L").append(line);
        }

        // Hour/shift
        int hour = context.getProductionHour();
        if (hour < 0 && context.getBatchCode() != null) {
            hour = parseHourFromBatchCode(context.getBatchCode());
        }
        if (hour >= 0) {
            String shift = hourToShift(hour);
            if (lineShift.length() > 0) {
                lineShift.append("-");
            }
            lineShift.append(shift);
        }

        return lineShift.length() > 0 ? lineShift.toString() : null;
    }

    /**
     * Validate Unilever batch code meets shelf life requirements.
     *
     * @param context Receipt line context
     * @return Validation result
     */
    public ShelfLifeValidation validateShelfLife(UnileverLotContext context) {
        LocalDate prodDate = context.getProductionDate();
        if (prodDate == null && context.getBatchCode() != null) {
            prodDate = parseDateFromBatchCode(context.getBatchCode());
        }

        LocalDate expDate = context.getExpirationDate();
        if (expDate == null) {
            int shelfLifeDays = getSkuShelfLifeDays(context.getStorerKey(), context.getSku());
            if (prodDate != null && shelfLifeDays > 0) {
                expDate = prodDate.plusDays(shelfLifeDays);
            }
        }

        if (prodDate == null || expDate == null) {
            return new ShelfLifeValidation(false, 0, 0, "Unable to determine dates");
        }

        long totalDays = ChronoUnit.DAYS.between(prodDate, expDate);
        long remainingDays = ChronoUnit.DAYS.between(LocalDate.now(), expDate);
        int remainingPercent = totalDays > 0 ? (int) ((remainingDays * 100) / totalDays) : 0;

        boolean valid = remainingPercent >= MIN_SHELF_LIFE_PERCENT && remainingDays > 0;
        String message = valid
            ? String.format("Shelf life OK: %d%% remaining (%d days)", remainingPercent, remainingDays)
            : String.format("Shelf life insufficient: %d%% remaining (%d days), minimum %d%%",
                remainingPercent, remainingDays, MIN_SHELF_LIFE_PERCENT);

        return new ShelfLifeValidation(valid, remainingDays, remainingPercent, message);
    }

    /**
     * Parse Unilever batch code into components.
     *
     * @param batchCode Batch code
     * @return Parsed info or null
     */
    public BatchCodeInfo parseBatchCode(String batchCode) {
        if (batchCode == null) return null;

        // Try primary format: PYMMDDHHL
        Matcher matcher = BATCH_CODE_PATTERN.matcher(batchCode.toUpperCase());
        if (matcher.matches()) {
            String plant = matcher.group(1);
            int year = 2020 + Integer.parseInt(matcher.group(2)); // Assume 2020s
            int month = Integer.parseInt(matcher.group(3));
            int day = Integer.parseInt(matcher.group(4));
            int hour = Integer.parseInt(matcher.group(5));
            int line = Integer.parseInt(matcher.group(6));

            LocalDate prodDate = LocalDate.of(year, month, day);
            return new BatchCodeInfo(batchCode, plant, prodDate, hour, line);
        }

        // Try alternate format: YMMDD-PPPP-LL
        Matcher altMatcher = ALT_BATCH_PATTERN.matcher(batchCode.toUpperCase());
        if (altMatcher.matches()) {
            String datePart = altMatcher.group(1);
            String plant = altMatcher.group(2);
            int line = Integer.parseInt(altMatcher.group(3));

            int year = 2020 + Integer.parseInt(datePart.substring(0, 1));
            int month = Integer.parseInt(datePart.substring(1, 3));
            int day = Integer.parseInt(datePart.substring(3, 5));

            LocalDate prodDate = LocalDate.of(year, month, day);
            return new BatchCodeInfo(batchCode, plant, prodDate, -1, line);
        }

        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Private Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private boolean isValidBatchCode(String batchCode) {
        if (batchCode == null) return false;
        String upper = batchCode.toUpperCase();
        return BATCH_CODE_PATTERN.matcher(upper).matches()
            || ALT_BATCH_PATTERN.matcher(upper).matches();
    }

    private String normalizeBrandCode(String brand) {
        if (brand == null) return null;
        return brand.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private String generateUnileverBatchCode(LocalDate date, String plantCode, int hour, int line) {
        // Format: PYMMDDHHL
        char plant = (plantCode != null && !plantCode.isEmpty())
            ? plantCode.charAt(0)
            : 'A';
        int year = date.getYear() % 10;
        String datePart = String.format("%d%02d%02d", year, date.getMonthValue(), date.getDayOfMonth());
        String hourPart = String.format("%02d", Math.min(hour, 23));
        int lineNum = Math.min(line, 9);

        return plant + datePart + hourPart + lineNum;
    }

    private LocalDate parseDateFromBatchCode(String batchCode) {
        BatchCodeInfo info = parseBatchCode(batchCode);
        return info != null ? info.productionDate() : null;
    }

    private String parsePlantFromBatchCode(String batchCode) {
        BatchCodeInfo info = parseBatchCode(batchCode);
        return info != null ? info.plantCode() : null;
    }

    private int parseHourFromBatchCode(String batchCode) {
        BatchCodeInfo info = parseBatchCode(batchCode);
        return info != null ? info.productionHour() : -1;
    }

    private int parseLineFromBatchCode(String batchCode) {
        BatchCodeInfo info = parseBatchCode(batchCode);
        return info != null ? info.productionLine() : -1;
    }

    private String hourToShift(int hour) {
        if (hour < 6) return "N3"; // Night shift (00-06)
        if (hour < 14) return "D1"; // Day shift (06-14)
        if (hour < 22) return "E2"; // Evening shift (14-22)
        return "N3"; // Night shift (22-24)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Database Lookups
    // ═══════════════════════════════════════════════════════════════════════

    private String getSkuBrandCode(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT brand FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                String.class, storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getReceiptSupplierLot(String receiptKey, int lineNumber) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT supplierlotxid FROM dbo.RECEIPTDETAIL WHERE receiptkey = ? AND receiptlinenumber = ?",
                String.class, receiptKey, lineNumber
            );
        } catch (Exception e) {
            return null;
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

    private String getReceiptSiteCode(String receiptKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr2 FROM dbo.RECEIPT WHERE receiptkey = ?",
                String.class, receiptKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getPOSiteCode(String poKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr2 FROM dbo.PO WHERE pokey = ?",
                String.class, poKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getStorerDefaultPlant(String storerKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT configvalue FROM dbo.STORERCONFIG " +
                "WHERE storerkey = ? AND configkey = 'DEFAULTPLANT'",
                String.class, storerKey
            );
        } catch (Exception e) {
            return "A"; // Default plant code
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Context and Result Classes
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    public static class UnileverLotContext {
        private String receiptKey;
        private int lineNumber;
        private String storerKey;
        private String sku;
        private String poKey;
        private Integer poLineNumber;
        private String brandCode;
        private String batchCode;
        private LocalDate productionDate;
        private LocalDate expirationDate;
        private String plantCode;
        private int productionHour;
        private int productionLine;
    }

    public record BatchCodeInfo(
        String batchCode,
        String plantCode,
        LocalDate productionDate,
        int productionHour,
        int productionLine
    ) {}

    public record ShelfLifeValidation(
        boolean valid,
        long remainingDays,
        int remainingPercent,
        String message
    ) {}
}
