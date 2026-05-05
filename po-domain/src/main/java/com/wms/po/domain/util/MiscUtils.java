package com.wms.po.domain.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Miscellaneous Utility Functions.
 *
 * Replaces: FN-033 - Misc Utils (20 functions)
 * - fnc_PadLeft / fnc_PadRight
 * - fnc_FormatNumber / fnc_FormatCurrency
 * - fnc_NullOrEmpty / fnc_Coalesce
 * - fnc_HashMD5 / fnc_HashSHA256
 * - fnc_GenerateGUID
 * - fnc_ExtractNumbers / fnc_ExtractLetters
 * - fnc_Capitalize / fnc_TitleCase
 * - fnc_TrimAll / fnc_RemoveSpecialChars
 * - fnc_CountOccurrences
 * - fnc_ReverseString
 * - fnc_IsNumeric / fnc_IsAlpha
 * - fnc_Base64Encode / fnc_Base64Decode
 */
public final class MiscUtils {

    private MiscUtils() {}

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern ALPHA_PATTERN = Pattern.compile("[a-zA-Z]+");
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("[a-zA-Z0-9]+");

    // ═══════════════════════════════════════════════════════════════════════
    // String Padding
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Pad string on the left.
     * Replaces: fnc_PadLeft
     */
    public static String padLeft(String str, int length, char padChar) {
        if (str == null) str = "";
        if (str.length() >= length) return str;
        StringBuilder sb = new StringBuilder();
        for (int i = str.length(); i < length; i++) {
            sb.append(padChar);
        }
        sb.append(str);
        return sb.toString();
    }

    public static String padLeft(String str, int length) {
        return padLeft(str, length, ' ');
    }

    /**
     * Pad string on the right.
     * Replaces: fnc_PadRight
     */
    public static String padRight(String str, int length, char padChar) {
        if (str == null) str = "";
        if (str.length() >= length) return str;
        StringBuilder sb = new StringBuilder(str);
        for (int i = str.length(); i < length; i++) {
            sb.append(padChar);
        }
        return sb.toString();
    }

    public static String padRight(String str, int length) {
        return padRight(str, length, ' ');
    }

    /**
     * Pad number with leading zeros.
     */
    public static String padZero(long number, int length) {
        return padLeft(String.valueOf(number), length, '0');
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Number Formatting
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Format number with grouping.
     * Replaces: fnc_FormatNumber
     */
    public static String formatNumber(BigDecimal number, int decimals) {
        if (number == null) return "";
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) {
            pattern.append(".");
            for (int i = 0; i < decimals; i++) {
                pattern.append("0");
            }
        }
        DecimalFormat df = new DecimalFormat(pattern.toString());
        return df.format(number);
    }

    public static String formatNumber(double number, int decimals) {
        return formatNumber(BigDecimal.valueOf(number), decimals);
    }

    /**
     * Format as currency.
     * Replaces: fnc_FormatCurrency
     */
    public static String formatCurrency(BigDecimal amount, String currencySymbol) {
        if (amount == null) return "";
        String symbol = currencySymbol != null ? currencySymbol : "$";
        return symbol + formatNumber(amount, 2);
    }

    public static String formatCurrency(BigDecimal amount) {
        return formatCurrency(amount, "$");
    }

    /**
     * Format as percentage.
     */
    public static String formatPercent(BigDecimal value, int decimals) {
        if (value == null) return "";
        BigDecimal percentage = value.multiply(BigDecimal.valueOf(100));
        return formatNumber(percentage, decimals) + "%";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Null/Empty Checks
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if string is null or empty.
     * Replaces: fnc_NullOrEmpty
     */
    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * Check if string is null, empty, or whitespace only.
     */
    public static boolean isNullOrBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Return first non-null, non-empty value.
     * Replaces: fnc_Coalesce
     */
    @SafeVarargs
    public static <T> T coalesce(T... values) {
        for (T value : values) {
            if (value != null) {
                if (value instanceof String) {
                    if (!((String) value).isEmpty()) {
                        return value;
                    }
                } else {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * Return value or default if null/empty.
     */
    public static String nvl(String value, String defaultValue) {
        return isNullOrEmpty(value) ? defaultValue : value;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Hashing
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Generate MD5 hash.
     * Replaces: fnc_HashMD5
     */
    public static String hashMD5(String input) {
        return hash(input, "MD5");
    }

    /**
     * Generate SHA-256 hash.
     * Replaces: fnc_HashSHA256
     */
    public static String hashSHA256(String input) {
        return hash(input, "SHA-256");
    }

    private static String hash(String input, String algorithm) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Generate GUID/UUID.
     * Replaces: fnc_GenerateGUID
     */
    public static String generateGUID() {
        return UUID.randomUUID().toString().toUpperCase();
    }

    public static String generateGUIDNoDash() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // String Extraction
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Extract only numbers from string.
     * Replaces: fnc_ExtractNumbers
     */
    public static String extractNumbers(String input) {
        if (input == null) return "";
        return input.replaceAll("[^0-9]", "");
    }

    /**
     * Extract only letters from string.
     * Replaces: fnc_ExtractLetters
     */
    public static String extractLetters(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z]", "");
    }

    /**
     * Extract alphanumeric characters only.
     */
    public static String extractAlphanumeric(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9]", "");
    }

    /**
     * Remove special characters.
     * Replaces: fnc_RemoveSpecialChars
     */
    public static String removeSpecialChars(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9\\s]", "");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Case Conversion
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Capitalize first letter.
     * Replaces: fnc_Capitalize
     */
    public static String capitalize(String input) {
        if (isNullOrEmpty(input)) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }

    /**
     * Convert to title case.
     * Replaces: fnc_TitleCase
     */
    public static String titleCase(String input) {
        if (isNullOrEmpty(input)) return input;
        String[] words = input.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(capitalize(word));
            }
        }
        return sb.toString();
    }

    /**
     * Trim all whitespace (including internal).
     * Replaces: fnc_TrimAll
     */
    public static String trimAll(String input) {
        if (input == null) return null;
        return input.replaceAll("\\s+", "");
    }

    /**
     * Normalize whitespace (collapse multiple spaces).
     */
    public static String normalizeWhitespace(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("\\s+", " ");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // String Analysis
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Count occurrences of substring.
     * Replaces: fnc_CountOccurrences
     */
    public static int countOccurrences(String text, String substring) {
        if (text == null || substring == null || substring.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }

    /**
     * Reverse string.
     * Replaces: fnc_ReverseString
     */
    public static String reverse(String input) {
        if (input == null) return null;
        return new StringBuilder(input).reverse().toString();
    }

    /**
     * Check if string is numeric.
     * Replaces: fnc_IsNumeric
     */
    public static boolean isNumeric(String str) {
        if (isNullOrEmpty(str)) return false;
        return NUMERIC_PATTERN.matcher(str).matches();
    }

    /**
     * Check if string is alphabetic.
     * Replaces: fnc_IsAlpha
     */
    public static boolean isAlpha(String str) {
        if (isNullOrEmpty(str)) return false;
        return ALPHA_PATTERN.matcher(str).matches();
    }

    /**
     * Check if string is alphanumeric.
     */
    public static boolean isAlphanumeric(String str) {
        if (isNullOrEmpty(str)) return false;
        return ALPHANUMERIC_PATTERN.matcher(str).matches();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Base64 Encoding
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Encode to Base64.
     * Replaces: fnc_Base64Encode
     */
    public static String base64Encode(String input) {
        if (input == null) return null;
        return Base64.getEncoder().encodeToString(input.getBytes());
    }

    /**
     * Decode from Base64.
     * Replaces: fnc_Base64Decode
     */
    public static String base64Decode(String input) {
        if (input == null) return null;
        try {
            return new String(Base64.getDecoder().decode(input));
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Collection Utilities
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Safe get from list.
     */
    public static <T> T safeGet(List<T> list, int index, T defaultValue) {
        if (list == null || index < 0 || index >= list.size()) {
            return defaultValue;
        }
        return list.get(index);
    }

    /**
     * Safe get from map.
     */
    public static <K, V> V safeGet(Map<K, V> map, K key, V defaultValue) {
        if (map == null) return defaultValue;
        V value = map.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Join collection to string.
     */
    public static String join(Collection<?> collection, String delimiter) {
        if (collection == null || collection.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Object item : collection) {
            if (sb.length() > 0) sb.append(delimiter);
            sb.append(item);
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Number Utilities
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Safe parse integer.
     */
    public static Integer parseIntSafe(String str, Integer defaultValue) {
        if (isNullOrEmpty(str)) return defaultValue;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Safe parse BigDecimal.
     */
    public static BigDecimal parseBigDecimalSafe(String str, BigDecimal defaultValue) {
        if (isNullOrEmpty(str)) return defaultValue;
        try {
            return new BigDecimal(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Round to specified decimal places.
     */
    public static BigDecimal round(BigDecimal value, int scale) {
        if (value == null) return null;
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    /**
     * Check if value is within range.
     */
    public static boolean isInRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null) return false;
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }
}
