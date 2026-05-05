package com.wms.po.domain.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Dimension and volume calculation utilities.
 *
 * Replaces SQL function: fnc_CalculateCube (128 LOC)
 *
 * Calculates volume (cube) for inventory items, cartons, and pallets.
 * Used for putaway optimization and storage capacity planning.
 *
 * Supports multiple UOM conversions:
 * - CM to M³ (cubic centimeters to cubic meters)
 * - IN to FT³ (inches to cubic feet)
 * - MM to M³ (millimeters to cubic meters)
 */
public final class DimensionCalculator {

    // Conversion factors
    private static final BigDecimal CM_TO_M3_FACTOR = new BigDecimal("0.000001"); // 1 cm³ = 0.000001 m³
    private static final BigDecimal IN_TO_FT3_FACTOR = new BigDecimal("0.000578704"); // 1 in³ = 0.000578704 ft³
    private static final BigDecimal MM_TO_M3_FACTOR = new BigDecimal("0.000000001"); // 1 mm³ = 0.000000001 m³
    private static final BigDecimal IN_TO_CM_FACTOR = new BigDecimal("2.54");
    private static final BigDecimal FT_TO_IN_FACTOR = new BigDecimal("12");
    private static final BigDecimal CM_TO_IN_FACTOR = new BigDecimal("0.393701");

    // Standard pallet dimensions (in cm)
    private static final BigDecimal STANDARD_PALLET_LENGTH = new BigDecimal("120");
    private static final BigDecimal STANDARD_PALLET_WIDTH = new BigDecimal("100");
    private static final BigDecimal STANDARD_PALLET_HEIGHT = new BigDecimal("144");

    // Default scale for calculations
    private static final int DECIMAL_SCALE = 6;

    private DimensionCalculator() {
        // Utility class - no instantiation
    }

    /**
     * Calculate volume (cube) from dimensions.
     * Equivalent to fnc_CalculateCube in SQL.
     *
     * @param length Item length
     * @param width Item width
     * @param height Item height
     * @param uom Unit of measure (CM, IN, MM, FT)
     * @return Volume in cubic meters (m³)
     */
    public static BigDecimal calculateCube(BigDecimal length, BigDecimal width, BigDecimal height, String uom) {
        if (length == null || width == null || height == null) {
            return BigDecimal.ZERO;
        }
        if (length.compareTo(BigDecimal.ZERO) <= 0 ||
            width.compareTo(BigDecimal.ZERO) <= 0 ||
            height.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Calculate raw volume
        BigDecimal volume = length.multiply(width).multiply(height);

        // Convert to cubic meters based on UOM
        String normalizedUom = normalizeUom(uom);
        return switch (normalizedUom) {
            case "CM" -> volume.multiply(CM_TO_M3_FACTOR).setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
            case "MM" -> volume.multiply(MM_TO_M3_FACTOR).setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
            case "IN" -> convertInchesToCubicMeters(volume);
            case "FT" -> convertFeetToCubicMeters(volume);
            case "M" -> volume.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP); // Already in m³
            default -> volume.multiply(CM_TO_M3_FACTOR).setScale(DECIMAL_SCALE, RoundingMode.HALF_UP); // Default to CM
        };
    }

    /**
     * Calculate volume with default UOM (CM).
     *
     * @param length Item length in cm
     * @param width Item width in cm
     * @param height Item height in cm
     * @return Volume in cubic meters
     */
    public static BigDecimal calculateCube(BigDecimal length, BigDecimal width, BigDecimal height) {
        return calculateCube(length, width, height, "CM");
    }

    /**
     * Calculate total volume for multiple items.
     *
     * @param length Item length
     * @param width Item width
     * @param height Item height
     * @param quantity Number of items
     * @param uom Unit of measure
     * @return Total volume in cubic meters
     */
    public static BigDecimal calculateTotalCube(BigDecimal length, BigDecimal width, BigDecimal height,
                                                 BigDecimal quantity, String uom) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal unitCube = calculateCube(length, width, height, uom);
        return unitCube.multiply(quantity).setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate how many items fit in a carton.
     *
     * @param itemLength Item length
     * @param itemWidth Item width
     * @param itemHeight Item height
     * @param cartonLength Carton length
     * @param cartonWidth Carton width
     * @param cartonHeight Carton height
     * @return Maximum items that fit (conservative estimate)
     */
    public static int calculateItemsPerCarton(BigDecimal itemLength, BigDecimal itemWidth, BigDecimal itemHeight,
                                               BigDecimal cartonLength, BigDecimal cartonWidth, BigDecimal cartonHeight) {
        if (itemLength == null || itemWidth == null || itemHeight == null ||
            cartonLength == null || cartonWidth == null || cartonHeight == null) {
            return 0;
        }

        // Calculate how many items fit in each dimension
        int fitLength = cartonLength.divide(itemLength, 0, RoundingMode.DOWN).intValue();
        int fitWidth = cartonWidth.divide(itemWidth, 0, RoundingMode.DOWN).intValue();
        int fitHeight = cartonHeight.divide(itemHeight, 0, RoundingMode.DOWN).intValue();

        return fitLength * fitWidth * fitHeight;
    }

    /**
     * Calculate how many cartons fit on a standard pallet.
     *
     * @param cartonLength Carton length (cm)
     * @param cartonWidth Carton width (cm)
     * @param cartonHeight Carton height (cm)
     * @return Maximum cartons per pallet
     */
    public static int calculateCartonsPerPallet(BigDecimal cartonLength, BigDecimal cartonWidth, BigDecimal cartonHeight) {
        return calculateItemsPerCarton(
            cartonLength, cartonWidth, cartonHeight,
            STANDARD_PALLET_LENGTH, STANDARD_PALLET_WIDTH, STANDARD_PALLET_HEIGHT
        );
    }

    /**
     * Calculate pallet utilization percentage.
     *
     * @param cartonLength Carton length (cm)
     * @param cartonWidth Carton width (cm)
     * @param cartonHeight Carton height (cm)
     * @param cartonsOnPallet Number of cartons stacked
     * @return Utilization percentage (0-100)
     */
    public static BigDecimal calculatePalletUtilization(BigDecimal cartonLength, BigDecimal cartonWidth,
                                                         BigDecimal cartonHeight, int cartonsOnPallet) {
        BigDecimal palletVolume = calculateCube(STANDARD_PALLET_LENGTH, STANDARD_PALLET_WIDTH, STANDARD_PALLET_HEIGHT);
        BigDecimal cartonVolume = calculateCube(cartonLength, cartonWidth, cartonHeight);
        BigDecimal usedVolume = cartonVolume.multiply(new BigDecimal(cartonsOnPallet));

        if (palletVolume.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return usedVolume.divide(palletVolume, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Convert dimensions from one UOM to another.
     *
     * @param value Dimension value
     * @param fromUom Source UOM
     * @param toUom Target UOM
     * @return Converted value
     */
    public static BigDecimal convertDimension(BigDecimal value, String fromUom, String toUom) {
        if (value == null) {
            return null;
        }

        String from = normalizeUom(fromUom);
        String to = normalizeUom(toUom);

        if (from.equals(to)) {
            return value;
        }

        // Convert to CM first (base unit)
        BigDecimal inCm = switch (from) {
            case "IN" -> value.multiply(IN_TO_CM_FACTOR);
            case "FT" -> value.multiply(FT_TO_IN_FACTOR).multiply(IN_TO_CM_FACTOR);
            case "MM" -> value.divide(new BigDecimal("10"), DECIMAL_SCALE, RoundingMode.HALF_UP);
            case "M" -> value.multiply(new BigDecimal("100"));
            default -> value; // Already CM
        };

        // Convert from CM to target
        return switch (to) {
            case "IN" -> inCm.multiply(CM_TO_IN_FACTOR).setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
            case "FT" -> inCm.multiply(CM_TO_IN_FACTOR).divide(FT_TO_IN_FACTOR, DECIMAL_SCALE, RoundingMode.HALF_UP);
            case "MM" -> inCm.multiply(new BigDecimal("10")).setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
            case "M" -> inCm.divide(new BigDecimal("100"), DECIMAL_SCALE, RoundingMode.HALF_UP);
            default -> inCm.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP); // CM
        };
    }

    /**
     * Calculate gross weight from dimensions and density.
     *
     * @param length Item length (cm)
     * @param width Item width (cm)
     * @param height Item height (cm)
     * @param densityKgPerM3 Density in kg/m³
     * @return Weight in kg
     */
    public static BigDecimal calculateWeight(BigDecimal length, BigDecimal width, BigDecimal height,
                                              BigDecimal densityKgPerM3) {
        if (densityKgPerM3 == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal volumeM3 = calculateCube(length, width, height, "CM");
        return volumeM3.multiply(densityKgPerM3).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * Calculate dimensional weight (for shipping).
     * Dimensional weight = Volume / DIM factor
     *
     * @param length Item length (cm)
     * @param width Item width (cm)
     * @param height Item height (cm)
     * @param dimFactor DIM factor (typically 5000 for kg/cm or 166 for lb/in)
     * @return Dimensional weight
     */
    public static BigDecimal calculateDimensionalWeight(BigDecimal length, BigDecimal width, BigDecimal height,
                                                         int dimFactor) {
        if (length == null || width == null || height == null || dimFactor <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal volume = length.multiply(width).multiply(height);
        return volume.divide(new BigDecimal(dimFactor), 3, RoundingMode.HALF_UP);
    }

    /**
     * Calculate dimensional weight with standard DIM factor of 5000.
     *
     * @param length Item length (cm)
     * @param width Item width (cm)
     * @param height Item height (cm)
     * @return Dimensional weight in kg
     */
    public static BigDecimal calculateDimensionalWeight(BigDecimal length, BigDecimal width, BigDecimal height) {
        return calculateDimensionalWeight(length, width, height, 5000);
    }

    /**
     * Get billable weight (higher of actual or dimensional).
     *
     * @param actualWeight Actual weight
     * @param dimWeight Dimensional weight
     * @return Billable weight
     */
    public static BigDecimal getBillableWeight(BigDecimal actualWeight, BigDecimal dimWeight) {
        if (actualWeight == null && dimWeight == null) {
            return BigDecimal.ZERO;
        }
        if (actualWeight == null) {
            return dimWeight;
        }
        if (dimWeight == null) {
            return actualWeight;
        }
        return actualWeight.max(dimWeight);
    }

    /**
     * Validate that dimensions are within acceptable ranges.
     *
     * @param length Item length
     * @param width Item width
     * @param height Item height
     * @param maxDimension Maximum allowed single dimension
     * @return true if dimensions are valid
     */
    public static boolean validateDimensions(BigDecimal length, BigDecimal width, BigDecimal height,
                                              BigDecimal maxDimension) {
        if (length == null || width == null || height == null) {
            return false;
        }
        if (length.compareTo(BigDecimal.ZERO) <= 0 ||
            width.compareTo(BigDecimal.ZERO) <= 0 ||
            height.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (maxDimension != null) {
            if (length.compareTo(maxDimension) > 0 ||
                width.compareTo(maxDimension) > 0 ||
                height.compareTo(maxDimension) > 0) {
                return false;
            }
        }
        return true;
    }

    private static BigDecimal convertInchesToCubicMeters(BigDecimal cubicInches) {
        // 1 in³ = 16.387064 cm³ = 0.000016387064 m³
        return cubicInches.multiply(new BigDecimal("0.000016387064"))
            .setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal convertFeetToCubicMeters(BigDecimal cubicFeet) {
        // 1 ft³ = 0.0283168 m³
        return cubicFeet.multiply(new BigDecimal("0.0283168"))
            .setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    private static String normalizeUom(String uom) {
        if (uom == null || uom.isBlank()) {
            return "CM";
        }
        return uom.trim().toUpperCase();
    }
}
