package com.wms.po.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for Unit of Measure (UOM) conversions.
 *
 * Replaces SQL function: fncConvUOM
 *
 * UOM conversions are based on the PACK table which defines:
 * - Base UOM (usually EA - Each)
 * - Conversion factors for each UOM level
 *
 * Common UOMs:
 * - EA (Each) - Base unit
 * - IP (Inner Pack)
 * - CS (Case)
 * - PL (Pallet)
 *
 * Example: If packkey='STD' has casecnt=12, then:
 * 1 CS = 12 EA, so fncConvUOM(1, 'STD', 'CS', 'EA') = 12
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UOMConversionService {

    private final JdbcTemplate jdbcTemplate;

    // UOM constants
    public static final String UOM_EACH = "EA";
    public static final String UOM_INNER_PACK = "IP";
    public static final String UOM_CASE = "CS";
    public static final String UOM_PALLET = "PL";

    // Cache for pack conversion factors (thread-safe)
    private final Map<String, PackConversion> packCache = new ConcurrentHashMap<>();

    // Default scale for decimal calculations
    private static final int DECIMAL_SCALE = 6;

    /**
     * Convert quantity from one UOM to another.
     * Equivalent to fncConvUOM in SQL.
     *
     * @param quantity The quantity to convert
     * @param packKey The pack key defining conversion factors
     * @param fromUOM Source unit of measure
     * @param toUOM Target unit of measure
     * @return Converted quantity
     */
    @Transactional(readOnly = true)
    public BigDecimal convert(BigDecimal quantity, String packKey, String fromUOM, String toUOM) {
        if (quantity == null || packKey == null || fromUOM == null || toUOM == null) {
            return quantity;
        }

        // Same UOM - no conversion needed
        if (fromUOM.equalsIgnoreCase(toUOM)) {
            return quantity;
        }

        // Get conversion factors for this pack
        PackConversion pack = getPackConversion(packKey);
        if (pack == null) {
            log.warn("Pack {} not found, returning original quantity", packKey);
            return quantity;
        }

        // Convert to base UOM (EA), then to target UOM
        BigDecimal baseQty = convertToBase(quantity, fromUOM, pack);
        return convertFromBase(baseQty, toUOM, pack);
    }

    /**
     * Convert quantity to base UOM (EA).
     *
     * @param quantity The quantity to convert
     * @param packKey The pack key
     * @param fromUOM Source UOM
     * @return Quantity in base UOM (EA)
     */
    @Transactional(readOnly = true)
    public BigDecimal convertToEach(BigDecimal quantity, String packKey, String fromUOM) {
        return convert(quantity, packKey, fromUOM, UOM_EACH);
    }

    /**
     * Convert quantity from base UOM (EA) to target UOM.
     *
     * @param quantity The quantity in EA
     * @param packKey The pack key
     * @param toUOM Target UOM
     * @return Converted quantity
     */
    @Transactional(readOnly = true)
    public BigDecimal convertFromEach(BigDecimal quantity, String packKey, String toUOM) {
        return convert(quantity, packKey, UOM_EACH, toUOM);
    }

    /**
     * Get the conversion factor from one UOM to another.
     *
     * @param packKey The pack key
     * @param fromUOM Source UOM
     * @param toUOM Target UOM
     * @return Conversion factor (multiply by this to convert)
     */
    @Transactional(readOnly = true)
    public BigDecimal getConversionFactor(String packKey, String fromUOM, String toUOM) {
        return convert(BigDecimal.ONE, packKey, fromUOM, toUOM);
    }

    /**
     * Check if a UOM is valid for a pack.
     *
     * @param packKey The pack key
     * @param uom The UOM to check
     * @return true if the UOM is defined for this pack
     */
    @Transactional(readOnly = true)
    public boolean isValidUOM(String packKey, String uom) {
        PackConversion pack = getPackConversion(packKey);
        if (pack == null) {
            return false;
        }
        return getConversionFactorToBase(uom, pack) != null;
    }

    /**
     * Get all valid UOMs for a pack.
     *
     * @param packKey The pack key
     * @return List of valid UOM codes
     */
    @Transactional(readOnly = true)
    public List<String> getValidUOMs(String packKey) {
        PackConversion pack = getPackConversion(packKey);
        if (pack == null) {
            return Collections.emptyList();
        }

        List<String> uoms = new ArrayList<>();
        uoms.add(UOM_EACH);

        if (pack.innerPackQty != null && pack.innerPackQty.compareTo(BigDecimal.ZERO) > 0) {
            uoms.add(UOM_INNER_PACK);
        }
        if (pack.caseQty != null && pack.caseQty.compareTo(BigDecimal.ZERO) > 0) {
            uoms.add(UOM_CASE);
        }
        if (pack.palletQty != null && pack.palletQty.compareTo(BigDecimal.ZERO) > 0) {
            uoms.add(UOM_PALLET);
        }

        return uoms;
    }

    /**
     * Calculate weight for a quantity in a given UOM.
     *
     * @param quantity The quantity
     * @param packKey The pack key
     * @param uom The UOM
     * @return Weight (in pack's weight unit)
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateWeight(BigDecimal quantity, String packKey, String uom) {
        PackConversion pack = getPackConversion(packKey);
        if (pack == null || pack.unitWeight == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal eachQty = convert(quantity, packKey, uom, UOM_EACH);
        return eachQty.multiply(pack.unitWeight).setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate cube/volume for a quantity in a given UOM.
     *
     * @param quantity The quantity
     * @param packKey The pack key
     * @param uom The UOM
     * @return Cube (in pack's volume unit)
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateCube(BigDecimal quantity, String packKey, String uom) {
        PackConversion pack = getPackConversion(packKey);
        if (pack == null || pack.unitCube == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal eachQty = convert(quantity, packKey, uom, UOM_EACH);
        return eachQty.multiply(pack.unitCube).setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Clear the pack cache (useful after pack updates).
     */
    public void clearCache() {
        packCache.clear();
        log.info("Pack conversion cache cleared");
    }

    /**
     * Clear cache for a specific pack.
     *
     * @param packKey The pack key to clear
     */
    public void clearCache(String packKey) {
        packCache.remove(packKey);
        log.debug("Pack conversion cache cleared for {}", packKey);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Internal Methods
    // ═══════════════════════════════════════════════════════════════════════

    private PackConversion getPackConversion(String packKey) {
        return packCache.computeIfAbsent(packKey, this::loadPackConversion);
    }

    private PackConversion loadPackConversion(String packKey) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT packkey, packunits1, packunits2, packunits3, packunits4,
                       stdgrosswgt, stdnetwgt, stdcube, packuom1, packuom2, packuom3, packuom4
                FROM dbo.pack
                WHERE packkey = ?
                """,
                (rs, rowNum) -> new PackConversion(
                    rs.getString("packkey"),
                    rs.getBigDecimal("packunits1"),  // EA count per IP
                    rs.getBigDecimal("packunits2"),  // IP count per CS (or EA per CS)
                    rs.getBigDecimal("packunits3"),  // CS count per PL (or EA per PL)
                    rs.getBigDecimal("packunits4"),  // Additional level
                    rs.getBigDecimal("stdgrosswgt"),
                    rs.getBigDecimal("stdnetwgt"),
                    rs.getBigDecimal("stdcube"),
                    rs.getString("packuom1"),
                    rs.getString("packuom2"),
                    rs.getString("packuom3"),
                    rs.getString("packuom4")
                ),
                packKey
            );
        } catch (Exception e) {
            log.debug("Could not load pack {}: {}", packKey, e.getMessage());
            // Try alternate query for simplified pack table
            return loadPackConversionSimple(packKey);
        }
    }

    private PackConversion loadPackConversionSimple(String packKey) {
        try {
            return jdbcTemplate.queryForObject(
                """
                SELECT packkey, casecnt, innerpkcnt, pallet, stdgrosswgt, stdcube
                FROM dbo.pack
                WHERE packkey = ?
                """,
                (rs, rowNum) -> {
                    BigDecimal caseCnt = rs.getBigDecimal("casecnt");
                    BigDecimal innerPkCnt = rs.getBigDecimal("innerpkcnt");
                    BigDecimal palletCnt = rs.getBigDecimal("pallet");

                    return new PackConversion(
                        rs.getString("packkey"),
                        innerPkCnt,     // EA per IP
                        caseCnt,        // EA per CS
                        palletCnt != null && caseCnt != null
                            ? palletCnt.multiply(caseCnt)  // EA per PL
                            : null,
                        null,
                        rs.getBigDecimal("stdgrosswgt"),
                        null,
                        rs.getBigDecimal("stdcube"),
                        null, null, null, null
                    );
                },
                packKey
            );
        } catch (Exception e) {
            log.warn("Pack {} not found in database", packKey);
            return null;
        }
    }

    private BigDecimal convertToBase(BigDecimal quantity, String fromUOM, PackConversion pack) {
        BigDecimal factor = getConversionFactorToBase(fromUOM, pack);
        if (factor == null) {
            log.warn("Unknown UOM: {}, treating as EA", fromUOM);
            return quantity;
        }
        return quantity.multiply(factor).setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal convertFromBase(BigDecimal quantity, String toUOM, PackConversion pack) {
        BigDecimal factor = getConversionFactorToBase(toUOM, pack);
        if (factor == null || factor.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Unknown UOM: {}, treating as EA", toUOM);
            return quantity;
        }
        return quantity.divide(factor, DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal getConversionFactorToBase(String uom, PackConversion pack) {
        if (uom == null) {
            return BigDecimal.ONE;
        }

        return switch (uom.toUpperCase()) {
            case "EA", "EACH" -> BigDecimal.ONE;
            case "IP", "INNERPACK" -> pack.innerPackQty != null ? pack.innerPackQty : BigDecimal.ONE;
            case "CS", "CASE" -> pack.caseQty != null ? pack.caseQty : BigDecimal.ONE;
            case "PL", "PALLET", "PLT" -> pack.palletQty != null ? pack.palletQty : BigDecimal.ONE;
            default -> {
                // Check custom UOM mappings
                if (uom.equalsIgnoreCase(pack.packUom1)) {
                    yield pack.innerPackQty != null ? pack.innerPackQty : BigDecimal.ONE;
                } else if (uom.equalsIgnoreCase(pack.packUom2)) {
                    yield pack.caseQty != null ? pack.caseQty : BigDecimal.ONE;
                } else if (uom.equalsIgnoreCase(pack.packUom3)) {
                    yield pack.palletQty != null ? pack.palletQty : BigDecimal.ONE;
                }
                yield null;
            }
        };
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Data Class
    // ═══════════════════════════════════════════════════════════════════════

    private record PackConversion(
        String packKey,
        BigDecimal innerPackQty,    // EA per IP
        BigDecimal caseQty,         // EA per CS
        BigDecimal palletQty,       // EA per PL
        BigDecimal extraQty,        // Additional level
        BigDecimal unitWeight,      // Weight per EA
        BigDecimal netWeight,       // Net weight per EA
        BigDecimal unitCube,        // Volume per EA
        String packUom1,            // Custom UOM 1
        String packUom2,            // Custom UOM 2
        String packUom3,            // Custom UOM 3
        String packUom4             // Custom UOM 4
    ) {}
}
