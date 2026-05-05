package com.wms.po.domain.util;

/**
 * Check digit calculation utilities.
 *
 * Replaces SQL function: fnc_CalcCheckDigit_M10 (70 LOC)
 *
 * Calculates check digits for various barcode formats:
 * - Mod-10 (Luhn algorithm) - credit cards, GTIN
 * - Mod-11 - ISBN, UPC
 * - GS1 (SSCC-18, GTIN-13, GTIN-14)
 *
 * Used for UCC/barcode validation and generation.
 */
public final class CheckDigitCalculator {

    private CheckDigitCalculator() {
        // Utility class - no instantiation
    }

    /**
     * Calculate Mod-10 check digit using Luhn algorithm.
     * Equivalent to fnc_CalcCheckDigit_M10 in SQL.
     *
     * @param input Numeric string without check digit
     * @return Check digit (0-9)
     */
    public static int calculateMod10(String input) {
        if (input == null || input.isEmpty()) {
            return 0;
        }

        // Remove any non-numeric characters
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }

        int sum = 0;
        boolean alternate = true;

        // Process digits from right to left
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(digits.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit / 10) + (digit % 10);
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return (10 - (sum % 10)) % 10;
    }

    /**
     * Calculate GS1 check digit (for GTIN-8, GTIN-12, GTIN-13, GTIN-14, SSCC-18).
     * Uses standard GS1 Mod-10 algorithm.
     *
     * @param input Numeric string without check digit
     * @return Check digit (0-9)
     */
    public static int calculateGS1(String input) {
        if (input == null || input.isEmpty()) {
            return 0;
        }

        String digits = input.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }

        int sum = 0;
        boolean multiply3 = true;

        // Process digits from right to left
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(digits.charAt(i));
            sum += multiply3 ? digit * 3 : digit;
            multiply3 = !multiply3;
        }

        return (10 - (sum % 10)) % 10;
    }

    /**
     * Calculate SSCC-18 check digit.
     *
     * @param sscc17 First 17 digits of SSCC
     * @return Complete SSCC-18 with check digit
     */
    public static String calculateSSCC18(String sscc17) {
        if (sscc17 == null || sscc17.length() != 17) {
            throw new IllegalArgumentException("SSCC-17 must be exactly 17 digits");
        }

        String digits = sscc17.replaceAll("[^0-9]", "");
        if (digits.length() != 17) {
            throw new IllegalArgumentException("SSCC-17 must contain exactly 17 numeric digits");
        }

        int checkDigit = calculateGS1(digits);
        return digits + checkDigit;
    }

    /**
     * Calculate GTIN-13 check digit (EAN-13).
     *
     * @param gtin12 First 12 digits of GTIN
     * @return Complete GTIN-13 with check digit
     */
    public static String calculateGTIN13(String gtin12) {
        if (gtin12 == null || gtin12.length() != 12) {
            throw new IllegalArgumentException("GTIN-12 must be exactly 12 digits");
        }

        String digits = gtin12.replaceAll("[^0-9]", "");
        if (digits.length() != 12) {
            throw new IllegalArgumentException("GTIN-12 must contain exactly 12 numeric digits");
        }

        int checkDigit = calculateGS1(digits);
        return digits + checkDigit;
    }

    /**
     * Calculate GTIN-14 check digit.
     *
     * @param gtin13 First 13 digits of GTIN
     * @return Complete GTIN-14 with check digit
     */
    public static String calculateGTIN14(String gtin13) {
        if (gtin13 == null || gtin13.length() != 13) {
            throw new IllegalArgumentException("GTIN-13 must be exactly 13 digits");
        }

        String digits = gtin13.replaceAll("[^0-9]", "");
        if (digits.length() != 13) {
            throw new IllegalArgumentException("GTIN-13 must contain exactly 13 numeric digits");
        }

        int checkDigit = calculateGS1(digits);
        return digits + checkDigit;
    }

    /**
     * Calculate UPC-A check digit.
     *
     * @param upc11 First 11 digits of UPC
     * @return Complete UPC-A with check digit
     */
    public static String calculateUPCA(String upc11) {
        if (upc11 == null || upc11.length() != 11) {
            throw new IllegalArgumentException("UPC-11 must be exactly 11 digits");
        }

        String digits = upc11.replaceAll("[^0-9]", "");
        if (digits.length() != 11) {
            throw new IllegalArgumentException("UPC-11 must contain exactly 11 numeric digits");
        }

        int checkDigit = calculateGS1(digits);
        return digits + checkDigit;
    }

    /**
     * Calculate Mod-11 check digit (for ISBN, etc.).
     *
     * @param input Numeric string without check digit
     * @return Check digit (0-9, X for 10)
     */
    public static String calculateMod11(String input) {
        if (input == null || input.isEmpty()) {
            return "0";
        }

        String digits = input.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return "0";
        }

        int sum = 0;
        int weight = 2;

        // Process digits from right to left
        for (int i = digits.length() - 1; i >= 0; i--) {
            sum += Character.getNumericValue(digits.charAt(i)) * weight;
            weight++;
            if (weight > 7) {
                weight = 2;
            }
        }

        int remainder = 11 - (sum % 11);
        if (remainder == 11) {
            return "0";
        } else if (remainder == 10) {
            return "X";
        } else {
            return String.valueOf(remainder);
        }
    }

    /**
     * Calculate ISBN-10 check digit.
     *
     * @param isbn9 First 9 digits of ISBN
     * @return Check digit (0-9 or X)
     */
    public static String calculateISBN10(String isbn9) {
        if (isbn9 == null || isbn9.length() != 9) {
            throw new IllegalArgumentException("ISBN-9 must be exactly 9 digits");
        }

        String digits = isbn9.replaceAll("[^0-9]", "");
        if (digits.length() != 9) {
            throw new IllegalArgumentException("ISBN-9 must contain exactly 9 numeric digits");
        }

        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * (10 - i);
        }

        int remainder = (11 - (sum % 11)) % 11;
        return remainder == 10 ? "X" : String.valueOf(remainder);
    }

    /**
     * Validate a barcode with its check digit.
     *
     * @param barcode Complete barcode including check digit
     * @param algorithm Algorithm to use (MOD10, GS1, MOD11)
     * @return true if check digit is valid
     */
    public static boolean validate(String barcode, CheckDigitAlgorithm algorithm) {
        if (barcode == null || barcode.length() < 2) {
            return false;
        }

        String digits = barcode.replaceAll("[^0-9X]", "");
        if (digits.length() < 2) {
            return false;
        }

        String payload = digits.substring(0, digits.length() - 1);
        char providedCheck = digits.charAt(digits.length() - 1);

        return switch (algorithm) {
            case MOD10 -> calculateMod10(payload) == Character.getNumericValue(providedCheck);
            case GS1 -> calculateGS1(payload) == Character.getNumericValue(providedCheck);
            case MOD11 -> calculateMod11(payload).equals(String.valueOf(providedCheck));
        };
    }

    /**
     * Validate a GS1 barcode (GTIN, SSCC, etc.).
     *
     * @param barcode Complete barcode
     * @return true if valid
     */
    public static boolean validateGS1(String barcode) {
        return validate(barcode, CheckDigitAlgorithm.GS1);
    }

    /**
     * Validate an SSCC-18.
     *
     * @param sscc18 Complete 18-digit SSCC
     * @return true if valid
     */
    public static boolean validateSSCC18(String sscc18) {
        if (sscc18 == null || sscc18.replaceAll("[^0-9]", "").length() != 18) {
            return false;
        }
        return validateGS1(sscc18);
    }

    /**
     * Validate a GTIN-13 (EAN-13).
     *
     * @param gtin13 Complete 13-digit GTIN
     * @return true if valid
     */
    public static boolean validateGTIN13(String gtin13) {
        if (gtin13 == null || gtin13.replaceAll("[^0-9]", "").length() != 13) {
            return false;
        }
        return validateGS1(gtin13);
    }

    /**
     * Validate a UPC-A (12 digits).
     *
     * @param upc Complete 12-digit UPC
     * @return true if valid
     */
    public static boolean validateUPCA(String upc) {
        if (upc == null || upc.replaceAll("[^0-9]", "").length() != 12) {
            return false;
        }
        return validateGS1(upc);
    }

    /**
     * Supported check digit algorithms.
     */
    public enum CheckDigitAlgorithm {
        MOD10,  // Luhn algorithm
        GS1,    // GS1 standard
        MOD11   // Mod-11 (ISBN, etc.)
    }
}
