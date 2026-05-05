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
 * Mondelez Lottable Rule Service.
 *
 * Replaces:
 * - SP-097: ispGenLotMondelez (160 LOC) - Lottable generation for Mondelez products
 *
 * Handles Mondelez-specific lottable generation:
 * - Lottable01: Material number (SAP format)
 * - Lottable02: Production batch code (Julian date format)
 * - Lottable03: Production date
 * - Lottable04: Best before date (BBD)
 * - Lottable05: Plant/Factory code
 * - Lottable06: Shift indicator
 *
 * Mondelez uses Julian date format for batch codes: YYDDD (2-digit year + day of year)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MondelezLottableRuleService {

    private final JdbcTemplate jdbcTemplate;

    // Mondelez batch code pattern: YYDDDSL (Year, Julian Day, Shift, Line)
    private static final Pattern BATCH_CODE_PATTERN = Pattern.compile("^(\\d{2})(\\d{3})([A-Z])(\\d)$");

    // Material number pattern (8-18 digits)
    private static final Pattern MATERIAL_PATTERN = Pattern.compile("^\\d{8,18}$");

    // Shift codes
    private static final String SHIFT_DAY = "D";
    private static final String SHIFT_NIGHT = "N";
    private static final String SHIFT_MORNING = "M";
    private static final String SHIFT_AFTERNOON = "A";

    /**
     * Generate Lottable01 (Material Number) for Mondelez receipt line.
     *
     * @param context Receipt line context
     * @return Material number or SKU
     */
    public String generateLottable01MaterialNumber(MondelezLotContext context) {
        log.debug("Generating Lottable01 (Material) for Mondelez SKU: {}", context.getSku());

        // 1. From context
        if (context.getMaterialNumber() != null && isValidMaterial(context.getMaterialNumber())) {
            return normalizeMaterial(context.getMaterialNumber());
        }

        // 2. From SKU master (userdefined1)
        String material = getSkuMaterialNumber(context.getStorerKey(), context.getSku());
        if (material != null && isValidMaterial(material)) {
            return normalizeMaterial(material);
        }

        // 3. From receipt detail
        material = getReceiptMaterialNumber(context.getReceiptKey(), context.getLineNumber());
        if (material != null && isValidMaterial(material)) {
            return normalizeMaterial(material);
        }

        // 4. Use SKU as material
        return context.getSku();
    }

    /**
     * Generate Lottable02 (Batch Code) for Mondelez receipt line.
     * Format: YYDDDSL (Year, Julian Day, Shift, Line)
     *
     * Replaces: ispGenLotMondelez
     *
     * @param context Receipt line context
     * @return Batch code
     */
    public String generateLottable02BatchCode(MondelezLotContext context) {
        log.debug("Generating Lottable02 (Batch) for Mondelez receipt: {}", context.getReceiptKey());

        // 1. Use existing batch if provided and valid
        if (context.getBatchCode() != null && isValidBatchCode(context.getBatchCode())) {
            return context.getBatchCode().toUpperCase();
        }

        // 2. Check receipt for supplier lot
        String supplierLot = getReceiptSupplierLot(context.getReceiptKey(), context.getLineNumber());
        if (supplierLot != null && isValidBatchCode(supplierLot)) {
            return supplierLot.toUpperCase();
        }

        // 3. Generate new batch code based on production date
        LocalDate prodDate = context.getProductionDate() != null
            ? context.getProductionDate()
            : LocalDate.now();

        String shift = context.getShift() != null ? context.getShift() : SHIFT_DAY;
        int line = context.getProductionLine() > 0 ? context.getProductionLine() : 1;

        return generateJulianBatchCode(prodDate, shift, line);
    }

    /**
     * Generate Lottable03 (Production Date).
     *
     * @param context Receipt line context
     * @return Production date in YYYYMMDD format
     */
    public String generateLottable03ProductionDate(MondelezLotContext context) {
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
     * Generate Lottable04 (Best Before Date).
     *
     * @param context Receipt line context
     * @return Best before date in YYYYMMDD format
     */
    public String generateLottable04BestBefore(MondelezLotContext context) {
        LocalDate bbd = context.getBestBeforeDate();

        if (bbd == null) {
            bbd = getReceiptBestBeforeDate(context.getReceiptKey(), context.getLineNumber());
        }

        // Calculate from production date + shelf life
        if (bbd == null && context.getProductionDate() != null) {
            int shelfLifeDays = getSkuShelfLifeDays(context.getStorerKey(), context.getSku());
            if (shelfLifeDays > 0) {
                bbd = context.getProductionDate().plusDays(shelfLifeDays);
            }
        }

        if (bbd == null) {
            return null;
        }

        return bbd.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    /**
     * Generate Lottable05 (Plant/Factory Code).
     *
     * @param context Receipt line context
     * @return Plant code
     */
    public String generateLottable05PlantCode(MondelezLotContext context) {
        // 1. From context
        if (context.getPlantCode() != null) {
            return context.getPlantCode();
        }

        // 2. From receipt header
        String plant = getReceiptPlantCode(context.getReceiptKey());
        if (plant != null) {
            return plant;
        }

        // 3. From PO
        if (context.getPoKey() != null) {
            plant = getPOPlantCode(context.getPoKey());
            if (plant != null) {
                return plant;
            }
        }

        // 4. From storer default
        return getStorerDefaultPlant(context.getStorerKey());
    }

    /**
     * Generate Lottable06 (Shift Indicator).
     *
     * @param context Receipt line context
     * @return Shift code (D/N/M/A)
     */
    public String generateLottable06Shift(MondelezLotContext context) {
        // 1. From context
        if (context.getShift() != null) {
            return normalizeShift(context.getShift());
        }

        // 2. Try to parse from batch code
        if (context.getBatchCode() != null) {
            String shift = parseShiftFromBatchCode(context.getBatchCode());
            if (shift != null) {
                return shift;
            }
        }

        // 3. From receipt
        String shift = getReceiptShift(context.getReceiptKey(), context.getLineNumber());
        if (shift != null) {
            return normalizeShift(shift);
        }

        return SHIFT_DAY; // Default
    }

    /**
     * Parse all lottables from batch code.
     *
     * @param batchCode Mondelez batch code (YYDDDSL)
     * @return Parsed batch info
     */
    public BatchCodeInfo parseBatchCode(String batchCode) {
        if (batchCode == null || !isValidBatchCode(batchCode)) {
            return null;
        }

        Matcher matcher = BATCH_CODE_PATTERN.matcher(batchCode.toUpperCase());
        if (!matcher.matches()) {
            return null;
        }

        int year = Integer.parseInt(matcher.group(1));
        int dayOfYear = Integer.parseInt(matcher.group(2));
        String shift = matcher.group(3);
        int line = Integer.parseInt(matcher.group(4));

        // Convert to full year (assume 2000s)
        int fullYear = 2000 + year;
        LocalDate productionDate = LocalDate.ofYearDay(fullYear, dayOfYear);

        return new BatchCodeInfo(batchCode, productionDate, shift, line);
    }

    /**
     * Calculate remaining shelf life in days.
     *
     * @param bestBeforeDate Best before date
     * @return Days until expiration (negative if expired)
     */
    public long calculateRemainingShelfLife(LocalDate bestBeforeDate) {
        if (bestBeforeDate == null) {
            return -1;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), bestBeforeDate);
    }

    /**
     * Check if batch is within acceptable shelf life.
     *
     * @param bestBeforeDate Best before date
     * @param minDaysRequired Minimum days required
     * @return true if acceptable
     */
    public boolean isShelfLifeAcceptable(LocalDate bestBeforeDate, int minDaysRequired) {
        long remaining = calculateRemainingShelfLife(bestBeforeDate);
        return remaining >= minDaysRequired;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Private Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private boolean isValidMaterial(String material) {
        if (material == null) return false;
        String cleaned = material.replaceAll("[^0-9]", "");
        return MATERIAL_PATTERN.matcher(cleaned).matches();
    }

    private String normalizeMaterial(String material) {
        if (material == null) return null;
        // Remove leading zeros and non-numeric, keep 18 char max
        String cleaned = material.replaceAll("[^0-9]", "");
        return cleaned.length() > 18 ? cleaned.substring(0, 18) : cleaned;
    }

    private boolean isValidBatchCode(String batchCode) {
        if (batchCode == null) return false;
        return BATCH_CODE_PATTERN.matcher(batchCode.toUpperCase()).matches();
    }

    private String generateJulianBatchCode(LocalDate date, String shift, int line) {
        int year = date.getYear() % 100; // Last 2 digits
        int dayOfYear = date.getDayOfYear();
        String shiftCode = normalizeShift(shift);
        int lineNum = Math.min(line, 9); // Max 1 digit

        return String.format("%02d%03d%s%d", year, dayOfYear, shiftCode, lineNum);
    }

    private LocalDate parseDateFromBatchCode(String batchCode) {
        BatchCodeInfo info = parseBatchCode(batchCode);
        return info != null ? info.productionDate() : null;
    }

    private String parseShiftFromBatchCode(String batchCode) {
        BatchCodeInfo info = parseBatchCode(batchCode);
        return info != null ? info.shift() : null;
    }

    private String normalizeShift(String shift) {
        if (shift == null) return SHIFT_DAY;
        String upper = shift.toUpperCase().trim();
        return switch (upper) {
            case "D", "DAY", "1" -> SHIFT_DAY;
            case "N", "NIGHT", "2" -> SHIFT_NIGHT;
            case "M", "MORNING", "3" -> SHIFT_MORNING;
            case "A", "AFTERNOON", "4" -> SHIFT_AFTERNOON;
            default -> upper.isEmpty() ? SHIFT_DAY : String.valueOf(upper.charAt(0));
        };
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Database Lookups
    // ═══════════════════════════════════════════════════════════════════════

    private String getSkuMaterialNumber(String storerKey, String sku) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT userdefined1 FROM dbo.SKU WHERE storerkey = ? AND sku = ?",
                String.class, storerKey, sku
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getReceiptMaterialNumber(String receiptKey, int lineNumber) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr1 FROM dbo.RECEIPTDETAIL WHERE receiptkey = ? AND receiptlinenumber = ?",
                String.class, receiptKey, lineNumber
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

    private LocalDate getReceiptBestBeforeDate(String receiptKey, int lineNumber) {
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

    private String getReceiptPlantCode(String receiptKey) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr2 FROM dbo.RECEIPT WHERE receiptkey = ?",
                String.class, receiptKey
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getPOPlantCode(String poKey) {
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
            return null;
        }
    }

    private String getReceiptShift(String receiptKey, int lineNumber) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT susr2 FROM dbo.RECEIPTDETAIL WHERE receiptkey = ? AND receiptlinenumber = ?",
                String.class, receiptKey, lineNumber
            );
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Context and Result Classes
    // ═══════════════════════════════════════════════════════════════════════

    @lombok.Data
    @lombok.Builder
    public static class MondelezLotContext {
        private String receiptKey;
        private int lineNumber;
        private String storerKey;
        private String sku;
        private String poKey;
        private Integer poLineNumber;
        private String materialNumber;
        private String batchCode;
        private LocalDate productionDate;
        private LocalDate bestBeforeDate;
        private String plantCode;
        private String shift;
        private int productionLine;
    }

    public record BatchCodeInfo(
        String batchCode,
        LocalDate productionDate,
        String shift,
        int productionLine
    ) {}
}
