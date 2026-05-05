package com.wms.po.domain.util;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vocollect Voice Picking Utilities.
 *
 * Replaces: FN-032 - Vocollect functions (15 functions)
 * - fnc_Voc_BuildPickAssignment / fnc_Voc_ParsePickResponse
 * - fnc_Voc_BuildCheckDigit / fnc_Voc_ValidateCheckDigit
 * - fnc_Voc_FormatLocation / fnc_Voc_ParseLocation
 * - fnc_Voc_BuildQuantityPrompt / fnc_Voc_ParseQuantityResponse
 * - fnc_Voc_BuildConfirmation / fnc_Voc_ParseConfirmation
 * - fnc_Voc_GetSpeakableText / fnc_Voc_GetSpeakableNumber
 * - fnc_Voc_FormatSKU / fnc_Voc_FormatLPN
 * - fnc_Voc_BuildException
 */
@Slf4j
public final class VocollectUtils {

    private VocollectUtils() {}

    // Check digit patterns
    private static final int[] CHECK_DIGIT_WEIGHTS = {3, 1, 3, 1, 3, 1};
    private static final Pattern LOCATION_PATTERN = Pattern.compile("([A-Z]+)(\\d+)([A-Z]?)(\\d*)([A-Z]?)");

    // ═══════════════════════════════════════════════════════════════════════
    // Pick Assignment
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Build voice pick assignment.
     * Replaces: fnc_Voc_BuildPickAssignment
     */
    public static VoicePickAssignment buildPickAssignment(
            String taskId,
            String location,
            String sku,
            String skuDescription,
            int quantity,
            String uom) {

        return VoicePickAssignment.builder()
            .taskId(taskId)
            .location(location)
            .locationSpoken(formatLocationForSpeech(location))
            .checkDigit(buildCheckDigit(location))
            .sku(sku)
            .skuSpoken(formatSKUForSpeech(sku))
            .skuDescription(skuDescription)
            .quantity(quantity)
            .quantitySpoken(formatNumberForSpeech(quantity))
            .uom(uom)
            .timestamp(LocalDateTime.now())
            .build();
    }

    @Data
    @Builder
    public static class VoicePickAssignment {
        private String taskId;
        private String location;
        private String locationSpoken;
        private String checkDigit;
        private String sku;
        private String skuSpoken;
        private String skuDescription;
        private int quantity;
        private String quantitySpoken;
        private String uom;
        private LocalDateTime timestamp;

        /**
         * Get full spoken prompt for pick.
         */
        public String getSpokenPrompt() {
            return String.format("Go to %s. Pick %s %s of %s.",
                locationSpoken, quantitySpoken, uom, skuSpoken);
        }
    }

    /**
     * Parse voice pick response.
     * Replaces: fnc_Voc_ParsePickResponse
     */
    public static VoicePickResponse parsePickResponse(String response) {
        VoicePickResponse result = new VoicePickResponse();

        if (response == null || response.isEmpty()) {
            result.valid = false;
            return result;
        }

        // Parse response format: "CD:XX QTY:NN [SHORT|EXCEPTION:reason]"
        String[] parts = response.toUpperCase().split("\\s+");

        for (String part : parts) {
            if (part.startsWith("CD:")) {
                result.checkDigit = part.substring(3);
            } else if (part.startsWith("QTY:")) {
                try {
                    result.quantityPicked = Integer.parseInt(part.substring(4));
                } catch (NumberFormatException e) {
                    result.valid = false;
                }
            } else if (part.equals("SHORT")) {
                result.isShort = true;
            } else if (part.startsWith("EXCEPTION:")) {
                result.exceptionCode = part.substring(10);
            }
        }

        result.valid = result.checkDigit != null;
        return result;
    }

    @Data
    public static class VoicePickResponse {
        private boolean valid;
        private String checkDigit;
        private int quantityPicked;
        private boolean isShort;
        private String exceptionCode;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Check Digit
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Build check digit for location validation.
     * Replaces: fnc_Voc_BuildCheckDigit
     *
     * Uses weighted sum mod 10 algorithm.
     */
    public static String buildCheckDigit(String location) {
        if (location == null || location.isEmpty()) {
            return "00";
        }

        // Extract numbers from location
        String numbers = location.replaceAll("[^0-9]", "");
        if (numbers.isEmpty()) {
            // Use character codes if no numbers
            int sum = 0;
            for (char c : location.toCharArray()) {
                sum += c;
            }
            return String.format("%02d", sum % 100);
        }

        // Calculate weighted sum
        int sum = 0;
        for (int i = 0; i < numbers.length() && i < CHECK_DIGIT_WEIGHTS.length; i++) {
            int digit = Character.getNumericValue(numbers.charAt(i));
            sum += digit * CHECK_DIGIT_WEIGHTS[i];
        }

        // Return 2-digit check digit
        return String.format("%02d", sum % 100);
    }

    /**
     * Validate check digit.
     * Replaces: fnc_Voc_ValidateCheckDigit
     */
    public static boolean validateCheckDigit(String location, String spokenCheckDigit) {
        if (location == null || spokenCheckDigit == null) {
            return false;
        }

        String expected = buildCheckDigit(location);
        return expected.equals(spokenCheckDigit) ||
               expected.equals(spokenCheckDigit.replaceAll("\\D", ""));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Location Formatting
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Format location for speech.
     * Replaces: fnc_Voc_FormatLocation
     *
     * Converts "A01B02C" to "A 01 B 02 C"
     */
    public static String formatLocationForSpeech(String location) {
        if (location == null || location.isEmpty()) {
            return "";
        }

        Matcher matcher = LOCATION_PATTERN.matcher(location.toUpperCase());
        if (matcher.matches()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                if (group != null && !group.isEmpty()) {
                    if (sb.length() > 0) sb.append(" ");
                    // Spell out letters, speak numbers
                    if (group.matches("\\d+")) {
                        sb.append(formatNumberForSpeech(Integer.parseInt(group)));
                    } else {
                        // Spell out each letter
                        for (char c : group.toCharArray()) {
                            if (sb.length() > 0) sb.append(" ");
                            sb.append(c);
                        }
                    }
                }
            }
            return sb.toString();
        }

        // Fallback: insert spaces between letters and numbers
        return location.replaceAll("([A-Za-z])([0-9])", "$1 $2")
                       .replaceAll("([0-9])([A-Za-z])", "$1 $2");
    }

    /**
     * Parse spoken location.
     * Replaces: fnc_Voc_ParseLocation
     */
    public static String parseSpokenLocation(String spoken) {
        if (spoken == null) return null;
        // Remove spaces and normalize
        return spoken.replaceAll("\\s+", "").toUpperCase();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Quantity Handling
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Build quantity prompt.
     * Replaces: fnc_Voc_BuildQuantityPrompt
     */
    public static String buildQuantityPrompt(int expectedQty, String uom) {
        return String.format("Pick %s %s. Say quantity picked.",
            formatNumberForSpeech(expectedQty), uom != null ? uom : "each");
    }

    /**
     * Parse quantity response.
     * Replaces: fnc_Voc_ParseQuantityResponse
     */
    public static Integer parseQuantityResponse(String spoken) {
        if (spoken == null || spoken.isEmpty()) {
            return null;
        }

        // Handle word numbers
        String normalized = spoken.toLowerCase().trim();
        Integer wordNumber = parseWordNumber(normalized);
        if (wordNumber != null) {
            return wordNumber;
        }

        // Try parsing as digit
        try {
            return Integer.parseInt(spoken.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Speakable Text
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Format number for speech.
     * Replaces: fnc_Voc_GetSpeakableNumber
     */
    public static String formatNumberForSpeech(int number) {
        if (number >= 0 && number <= 20) {
            return NUMBER_WORDS[number];
        } else if (number < 100) {
            int tens = number / 10;
            int ones = number % 10;
            if (ones == 0) {
                return TENS_WORDS[tens];
            } else {
                return TENS_WORDS[tens] + " " + NUMBER_WORDS[ones];
            }
        } else {
            return String.valueOf(number);
        }
    }

    public static String formatNumberForSpeech(BigDecimal number) {
        if (number == null) return "zero";
        return formatNumberForSpeech(number.intValue());
    }

    private static final String[] NUMBER_WORDS = {
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen", "twenty"
    };

    private static final String[] TENS_WORDS = {
        "", "ten", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    };

    private static Integer parseWordNumber(String word) {
        for (int i = 0; i < NUMBER_WORDS.length; i++) {
            if (NUMBER_WORDS[i].equals(word)) {
                return i;
            }
        }
        for (int i = 0; i < TENS_WORDS.length; i++) {
            if (TENS_WORDS[i].equals(word)) {
                return i * 10;
            }
        }
        return null;
    }

    /**
     * Format SKU for speech.
     * Replaces: fnc_Voc_FormatSKU
     */
    public static String formatSKUForSpeech(String sku) {
        if (sku == null || sku.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (char c : sku.toCharArray()) {
            if (sb.length() > 0) sb.append(" ");
            if (Character.isDigit(c)) {
                sb.append(NUMBER_WORDS[Character.getNumericValue(c)]);
            } else if (Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * Format LPN for speech.
     * Replaces: fnc_Voc_FormatLPN
     */
    public static String formatLPNForSpeech(String lpn) {
        if (lpn == null || lpn.isEmpty()) return "";

        // For LPNs, just speak the last 4-6 characters
        String suffix = lpn.length() > 6 ? lpn.substring(lpn.length() - 6) : lpn;
        return formatSKUForSpeech(suffix);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Confirmation
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Build confirmation prompt.
     * Replaces: fnc_Voc_BuildConfirmation
     */
    public static String buildConfirmationPrompt(String action, String detail) {
        return String.format("%s %s. Say ready to confirm or cancel.", action, detail);
    }

    /**
     * Parse confirmation response.
     * Replaces: fnc_Voc_ParseConfirmation
     */
    public static ConfirmationResult parseConfirmation(String spoken) {
        if (spoken == null) {
            return ConfirmationResult.UNKNOWN;
        }

        String normalized = spoken.toLowerCase().trim();
        if (normalized.contains("ready") || normalized.contains("confirm") ||
            normalized.contains("yes") || normalized.contains("correct")) {
            return ConfirmationResult.CONFIRMED;
        } else if (normalized.contains("cancel") || normalized.contains("no") ||
                   normalized.contains("wrong") || normalized.contains("back")) {
            return ConfirmationResult.CANCELLED;
        }
        return ConfirmationResult.UNKNOWN;
    }

    public enum ConfirmationResult {
        CONFIRMED,
        CANCELLED,
        UNKNOWN
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Exception Handling
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Build exception record.
     * Replaces: fnc_Voc_BuildException
     */
    public static VoiceException buildException(String taskId, String exceptionType,
                                                 String location, String reason) {
        return VoiceException.builder()
            .taskId(taskId)
            .exceptionType(exceptionType)
            .location(location)
            .reason(reason)
            .timestamp(LocalDateTime.now())
            .build();
    }

    @Data
    @Builder
    public static class VoiceException {
        private String taskId;
        private String exceptionType;
        private String location;
        private String reason;
        private LocalDateTime timestamp;

        // Exception types
        public static final String SHORT_PICK = "SHORT";
        public static final String DAMAGED = "DAMAGED";
        public static final String WRONG_ITEM = "WRONG_ITEM";
        public static final String LOCATION_EMPTY = "LOC_EMPTY";
        public static final String CANT_ACCESS = "NO_ACCESS";
    }

    /**
     * Get available exception codes.
     */
    public static List<String> getExceptionCodes() {
        return List.of(
            "SHORT - Short pick",
            "DAMAGED - Product damaged",
            "WRONG - Wrong item at location",
            "EMPTY - Location empty",
            "ACCESS - Cannot access location"
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Session Management
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Build session start message.
     */
    public static String buildSessionStart(String userId, String deviceId) {
        return String.format("Welcome %s. Device %s. Say ready to begin.",
            userId, deviceId);
    }

    /**
     * Build session end message.
     */
    public static String buildSessionEnd(int tasksCompleted, int totalUnits) {
        return String.format("Session complete. %s tasks, %s units picked. Goodbye.",
            formatNumberForSpeech(tasksCompleted),
            formatNumberForSpeech(totalUnits));
    }
}
