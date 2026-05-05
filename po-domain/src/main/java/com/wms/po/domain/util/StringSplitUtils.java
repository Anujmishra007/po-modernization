package com.wms.po.domain.util;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for string splitting and parsing.
 *
 * Replaces SQL function: fnc_DelimSplit
 *
 * The SQL function splits delimited strings into table rows:
 * SELECT * FROM fnc_DelimSplit('A,B,C', ',')
 * Returns: A, B, C as separate rows
 *
 * This Java utility provides equivalent functionality with
 * additional parsing capabilities for WMS data formats.
 */
public final class StringSplitUtils {

    // Common delimiters used in WMS
    public static final String DELIMITER_COMMA = ",";
    public static final String DELIMITER_PIPE = "|";
    public static final String DELIMITER_SEMICOLON = ";";
    public static final String DELIMITER_TAB = "\t";
    public static final String DELIMITER_NEWLINE = "\n";
    public static final String DELIMITER_TILDE = "~";

    private StringSplitUtils() {
        // Utility class - no instantiation
    }

    /**
     * Split a delimited string into a list of strings.
     * Equivalent to fnc_DelimSplit in SQL.
     *
     * @param input The delimited string
     * @param delimiter The delimiter to split on
     * @return List of split strings (empty list if input is null/empty)
     */
    public static List<String> split(String input, String delimiter) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }
        if (delimiter == null || delimiter.isEmpty()) {
            return Collections.singletonList(input);
        }

        return Arrays.stream(input.split(escapeRegex(delimiter)))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * Split a comma-delimited string.
     *
     * @param input The comma-delimited string
     * @return List of split strings
     */
    public static List<String> splitComma(String input) {
        return split(input, DELIMITER_COMMA);
    }

    /**
     * Split a pipe-delimited string.
     *
     * @param input The pipe-delimited string
     * @return List of split strings
     */
    public static List<String> splitPipe(String input) {
        return split(input, DELIMITER_PIPE);
    }

    /**
     * Split and return as Set (removes duplicates).
     *
     * @param input The delimited string
     * @param delimiter The delimiter to split on
     * @return Set of unique split strings
     */
    public static Set<String> splitToSet(String input, String delimiter) {
        return new LinkedHashSet<>(split(input, delimiter));
    }

    /**
     * Split a string into key-value pairs.
     * Format: "key1=value1,key2=value2"
     *
     * @param input The delimited key-value string
     * @param pairDelimiter Delimiter between pairs (e.g., ",")
     * @param keyValueDelimiter Delimiter between key and value (e.g., "=")
     * @return Map of key-value pairs
     */
    public static Map<String, String> splitToMap(String input, String pairDelimiter, String keyValueDelimiter) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();
        List<String> pairs = split(input, pairDelimiter);

        for (String pair : pairs) {
            int idx = pair.indexOf(keyValueDelimiter);
            if (idx > 0) {
                String key = pair.substring(0, idx).trim();
                String value = pair.substring(idx + keyValueDelimiter.length()).trim();
                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * Split a string into key-value pairs with default delimiters (comma and equals).
     *
     * @param input The delimited key-value string
     * @return Map of key-value pairs
     */
    public static Map<String, String> splitToMap(String input) {
        return splitToMap(input, DELIMITER_COMMA, "=");
    }

    /**
     * Split a positional string where each position has a fixed meaning.
     * Format: "value1|value2|value3"
     *
     * @param input The delimited string
     * @param delimiter The delimiter
     * @param expectedCount Expected number of positions
     * @return Array of values (padded with nulls if fewer values exist)
     */
    public static String[] splitPositional(String input, String delimiter, int expectedCount) {
        String[] result = new String[expectedCount];

        if (input == null || input.isEmpty()) {
            return result;
        }

        String[] parts = input.split(escapeRegex(delimiter), -1);
        for (int i = 0; i < Math.min(parts.length, expectedCount); i++) {
            String value = parts[i].trim();
            result[i] = value.isEmpty() ? null : value;
        }

        return result;
    }

    /**
     * Join a collection into a delimited string.
     * Inverse of split.
     *
     * @param values The values to join
     * @param delimiter The delimiter to use
     * @return Joined string
     */
    public static String join(Collection<?> values, String delimiter) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
            .filter(Objects::nonNull)
            .map(Object::toString)
            .collect(Collectors.joining(delimiter));
    }

    /**
     * Join with comma delimiter.
     *
     * @param values The values to join
     * @return Comma-joined string
     */
    public static String joinComma(Collection<?> values) {
        return join(values, DELIMITER_COMMA);
    }

    /**
     * Parse a range string like "1-5" into individual values.
     *
     * @param rangeStr The range string (e.g., "1-5" or "1,3,5-7")
     * @return List of all values in the ranges
     */
    public static List<Integer> parseRanges(String rangeStr) {
        if (rangeStr == null || rangeStr.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> result = new ArrayList<>();
        List<String> parts = splitComma(rangeStr);

        for (String part : parts) {
            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        for (int i = start; i <= end; i++) {
                            result.add(i);
                        }
                    } catch (NumberFormatException e) {
                        // Skip invalid ranges
                    }
                }
            } else {
                try {
                    result.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException e) {
                    // Skip invalid numbers
                }
            }
        }

        return result;
    }

    /**
     * Split WMS-style location string.
     * Format: "ZONE-AISLE-BAY-LEVEL-POSITION" or "A-01-02-03-04"
     *
     * @param location The location string
     * @return LocationParts object with parsed components
     */
    public static LocationParts parseLocation(String location) {
        if (location == null || location.isEmpty()) {
            return new LocationParts(null, null, null, null, null);
        }

        String[] parts = location.split("-");
        return new LocationParts(
            parts.length > 0 ? parts[0] : null,
            parts.length > 1 ? parts[1] : null,
            parts.length > 2 ? parts[2] : null,
            parts.length > 3 ? parts[3] : null,
            parts.length > 4 ? parts[4] : null
        );
    }

    /**
     * Check if a value exists in a delimited list.
     *
     * @param list The delimited list
     * @param value The value to find
     * @param delimiter The delimiter
     * @return true if value exists in list
     */
    public static boolean contains(String list, String value, String delimiter) {
        if (list == null || value == null) {
            return false;
        }
        return split(list, delimiter).contains(value.trim());
    }

    /**
     * Check if value exists in comma-delimited list.
     *
     * @param list The comma-delimited list
     * @param value The value to find
     * @return true if value exists
     */
    public static boolean containsComma(String list, String value) {
        return contains(list, value, DELIMITER_COMMA);
    }

    /**
     * Get value at specific position in delimited string.
     *
     * @param input The delimited string
     * @param delimiter The delimiter
     * @param position The 0-based position
     * @return Value at position, or null if not found
     */
    public static String getAt(String input, String delimiter, int position) {
        List<String> parts = split(input, delimiter);
        if (position >= 0 && position < parts.size()) {
            return parts.get(position);
        }
        return null;
    }

    /**
     * Escape regex special characters in delimiter.
     */
    private static String escapeRegex(String delimiter) {
        return delimiter.replaceAll("([\\\\\\[\\](){}.*+?^$|])", "\\\\$1");
    }

    /**
     * Parsed location components.
     */
    public record LocationParts(
        String zone,
        String aisle,
        String bay,
        String level,
        String position
    ) {
        public boolean isComplete() {
            return zone != null && aisle != null && bay != null;
        }

        public String getZoneAisle() {
            return zone != null && aisle != null ? zone + "-" + aisle : null;
        }
    }
}
