package com.wms.po.domain.util;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Barcode generation utilities.
 *
 * Replaces SQL function: fnc_Code128C_Uni (139 LOC)
 *
 * Generates barcode data strings for various formats:
 * - Code 128 (A, B, C subsets)
 * - GS1-128 (with Application Identifiers)
 * - EAN-13/GTIN-13
 * - UCC/SSCC-18
 *
 * Note: This generates the data strings. Actual barcode rendering
 * should be done by a dedicated barcode library (e.g., ZXing, Barcode4j).
 */
public final class BarcodeGenerator {

    // Code 128 special characters
    private static final char START_A = '\u00C8'; // 200
    private static final char START_B = '\u00C9'; // 201
    private static final char START_C = '\u00CA'; // 202
    private static final char CODE_A = '\u00C5';  // 197
    private static final char CODE_B = '\u00C6';  // 198
    private static final char CODE_C = '\u00C7';  // 199
    private static final char FNC1 = '\u00CF';    // 207 (Function 1 for GS1-128)
    private static final char FNC2 = '\u00D0';    // 208
    private static final char FNC3 = '\u00D1';    // 209
    private static final char FNC4 = '\u00D2';    // 210
    private static final char STOP = '\u00CE';    // 206

    // GS1 Application Identifiers
    private static final String AI_SSCC = "00";
    private static final String AI_GTIN = "01";
    private static final String AI_BATCH_LOT = "10";
    private static final String AI_PROD_DATE = "11";
    private static final String AI_EXP_DATE = "17";
    private static final String AI_SERIAL = "21";
    private static final String AI_QTY = "37";
    private static final String AI_COUNT = "30";

    // Code 128C value mappings
    private static final Map<String, Integer> CODE128C_VALUES = new HashMap<>();

    static {
        // Build Code 128C value table (00-99 -> values 0-99)
        for (int i = 0; i <= 99; i++) {
            CODE128C_VALUES.put(String.format("%02d", i), i);
        }
    }

    private BarcodeGenerator() {
        // Utility class - no instantiation
    }

    /**
     * Generate Code 128C barcode string.
     * Equivalent to fnc_Code128C_Uni in SQL.
     *
     * Code 128C is optimized for numeric-only data (pairs of digits).
     *
     * @param data Numeric data to encode (must be even length)
     * @return Code 128C encoded string
     */
    public static String generateCode128C(String data) {
        if (data == null || data.isEmpty()) {
            return "";
        }

        // Remove non-numeric characters
        String digits = data.replaceAll("[^0-9]", "");

        // Pad to even length if necessary
        if (digits.length() % 2 != 0) {
            digits = "0" + digits;
        }

        StringBuilder barcode = new StringBuilder();
        barcode.append(START_C);

        int checksum = 105; // Start C value
        int position = 1;

        // Encode pairs of digits
        for (int i = 0; i < digits.length(); i += 2) {
            String pair = digits.substring(i, i + 2);
            int value = CODE128C_VALUES.get(pair);

            // Add encoded character
            barcode.append((char) (value < 95 ? value + 32 : value + 100));

            // Update checksum
            checksum += value * position;
            position++;
        }

        // Add check character
        int checkValue = checksum % 103;
        barcode.append((char) (checkValue < 95 ? checkValue + 32 : checkValue + 100));

        // Add stop character
        barcode.append(STOP);

        return barcode.toString();
    }

    /**
     * Generate Code 128B barcode string.
     * Code 128B supports full ASCII (space through tilde).
     *
     * @param data Data to encode
     * @return Code 128B encoded string
     */
    public static String generateCode128B(String data) {
        if (data == null || data.isEmpty()) {
            return "";
        }

        StringBuilder barcode = new StringBuilder();
        barcode.append(START_B);

        int checksum = 104; // Start B value
        int position = 1;

        for (char c : data.toCharArray()) {
            int value;
            if (c >= 32 && c <= 126) {
                value = c - 32;
            } else {
                // Non-printable - skip or substitute
                continue;
            }

            barcode.append((char) (value + 32));
            checksum += value * position;
            position++;
        }

        // Add check character
        int checkValue = checksum % 103;
        barcode.append((char) (checkValue + 32));

        // Add stop character
        barcode.append(STOP);

        return barcode.toString();
    }

    /**
     * Generate GS1-128 barcode string.
     * Used for logistics and supply chain (SSCC, GTIN, lot, dates).
     *
     * @param data Data to encode (already formatted with AIs)
     * @return GS1-128 encoded string
     */
    public static String generateGS1128(String data) {
        if (data == null || data.isEmpty()) {
            return "";
        }

        StringBuilder barcode = new StringBuilder();
        barcode.append(START_C);
        barcode.append(FNC1); // GS1-128 requires FNC1 after start

        // Check if data is purely numeric
        String cleanData = data.replaceAll("[^0-9]", "");
        if (cleanData.equals(data) && cleanData.length() % 2 == 0) {
            // Pure numeric - use Code C
            int checksum = 105 + 102; // Start C + FNC1 value
            int position = 2;

            for (int i = 0; i < cleanData.length(); i += 2) {
                String pair = cleanData.substring(i, i + 2);
                int value = Integer.parseInt(pair);
                barcode.append((char) (value < 95 ? value + 32 : value + 100));
                checksum += value * position;
                position++;
            }

            int checkValue = checksum % 103;
            barcode.append((char) (checkValue < 95 ? checkValue + 32 : checkValue + 100));
        } else {
            // Mixed content - use Code B
            barcode.setCharAt(0, START_B);
            int checksum = 104 + 102; // Start B + FNC1 value
            int position = 2;

            for (char c : data.toCharArray()) {
                int value;
                if (c >= 32 && c <= 126) {
                    value = c - 32;
                } else {
                    continue;
                }
                barcode.append((char) (value + 32));
                checksum += value * position;
                position++;
            }

            int checkValue = checksum % 103;
            barcode.append((char) (checkValue + 32));
        }

        barcode.append(STOP);
        return barcode.toString();
    }

    /**
     * Generate SSCC-18 barcode string (GS1-128 format).
     *
     * @param extensionDigit Extension digit (0-9)
     * @param companyPrefix GS1 company prefix (7-9 digits)
     * @param serialRef Serial reference (remaining digits to make 17 total)
     * @return GS1-128 formatted SSCC barcode
     */
    public static String generateSSCC18Barcode(int extensionDigit, String companyPrefix, String serialRef) {
        // Build SSCC-17 (without check digit)
        String sscc17 = extensionDigit + companyPrefix + serialRef;

        // Pad/truncate to 17 digits
        if (sscc17.length() < 17) {
            sscc17 = String.format("%-17s", sscc17).replace(' ', '0');
        } else if (sscc17.length() > 17) {
            sscc17 = sscc17.substring(0, 17);
        }

        // Calculate check digit
        String sscc18 = CheckDigitCalculator.calculateSSCC18(sscc17);

        // Format with AI
        return AI_SSCC + sscc18;
    }

    /**
     * Generate GTIN barcode string (GS1-128 format).
     *
     * @param gtin GTIN-13 or GTIN-14
     * @return GS1-128 formatted GTIN barcode
     */
    public static String generateGTINBarcode(String gtin) {
        String cleanGtin = gtin.replaceAll("[^0-9]", "");

        // Pad to GTIN-14 if necessary
        if (cleanGtin.length() == 13) {
            cleanGtin = "0" + cleanGtin;
        } else if (cleanGtin.length() < 14) {
            cleanGtin = String.format("%14s", cleanGtin).replace(' ', '0');
        } else if (cleanGtin.length() > 14) {
            cleanGtin = cleanGtin.substring(0, 14);
        }

        return AI_GTIN + cleanGtin;
    }

    /**
     * Build a GS1-128 string with multiple AIs.
     */
    public static class GS1128Builder {
        private final StringBuilder data = new StringBuilder();
        private static final char GS = '\u001D'; // Group separator

        public GS1128Builder sscc(String sscc18) {
            data.append(AI_SSCC).append(sscc18.replaceAll("[^0-9]", ""));
            return this;
        }

        public GS1128Builder gtin(String gtin14) {
            data.append(AI_GTIN).append(gtin14.replaceAll("[^0-9]", ""));
            return this;
        }

        public GS1128Builder batchLot(String lot) {
            // Variable length - needs GS terminator
            data.append(AI_BATCH_LOT).append(lot).append(GS);
            return this;
        }

        public GS1128Builder productionDate(String dateYYMMDD) {
            data.append(AI_PROD_DATE).append(dateYYMMDD.replaceAll("[^0-9]", ""));
            return this;
        }

        public GS1128Builder expirationDate(String dateYYMMDD) {
            data.append(AI_EXP_DATE).append(dateYYMMDD.replaceAll("[^0-9]", ""));
            return this;
        }

        public GS1128Builder serial(String serial) {
            // Variable length - needs GS terminator
            data.append(AI_SERIAL).append(serial).append(GS);
            return this;
        }

        public GS1128Builder quantity(int qty) {
            // Variable length - needs GS terminator
            data.append(AI_QTY).append(qty).append(GS);
            return this;
        }

        public GS1128Builder count(int count) {
            data.append(AI_COUNT).append(String.format("%06d", count));
            return this;
        }

        public String build() {
            return data.toString();
        }

        public String toBarcode() {
            return generateGS1128(data.toString());
        }
    }

    /**
     * Create a GS1-128 builder for complex barcodes.
     *
     * @return New GS1128Builder instance
     */
    public static GS1128Builder gs1128() {
        return new GS1128Builder();
    }

    /**
     * Generate human-readable interpretation (HRI) for GS1-128.
     * Adds parentheses around Application Identifiers.
     *
     * @param gs1Data Raw GS1-128 data string
     * @return Human-readable format
     */
    public static String formatHRI(String gs1Data) {
        if (gs1Data == null || gs1Data.isEmpty()) {
            return "";
        }

        StringBuilder hri = new StringBuilder();
        int i = 0;

        while (i < gs1Data.length()) {
            // Try to match known AIs (2, 3, or 4 digits)
            String ai = null;
            int aiLen = 0;

            // Check 4-digit AIs first
            if (i + 4 <= gs1Data.length()) {
                String test = gs1Data.substring(i, i + 4);
                if (isKnownAI(test)) {
                    ai = test;
                    aiLen = 4;
                }
            }
            // Then 3-digit
            if (ai == null && i + 3 <= gs1Data.length()) {
                String test = gs1Data.substring(i, i + 3);
                if (isKnownAI(test)) {
                    ai = test;
                    aiLen = 3;
                }
            }
            // Then 2-digit
            if (ai == null && i + 2 <= gs1Data.length()) {
                String test = gs1Data.substring(i, i + 2);
                if (isKnownAI(test)) {
                    ai = test;
                    aiLen = 2;
                }
            }

            if (ai != null) {
                hri.append("(").append(ai).append(")");
                i += aiLen;

                // Find data until next AI or GS character
                int dataEnd = i;
                while (dataEnd < gs1Data.length() &&
                       gs1Data.charAt(dataEnd) != '\u001D') {
                    dataEnd++;
                }

                // Try to detect next AI
                for (int j = i + 1; j < dataEnd; j++) {
                    if (j + 2 <= gs1Data.length() && isKnownAI(gs1Data.substring(j, j + 2))) {
                        dataEnd = j;
                        break;
                    }
                }

                hri.append(gs1Data, i, dataEnd);
                i = dataEnd;

                // Skip GS if present
                if (i < gs1Data.length() && gs1Data.charAt(i) == '\u001D') {
                    i++;
                }
            } else {
                hri.append(gs1Data.charAt(i));
                i++;
            }
        }

        return hri.toString();
    }

    private static boolean isKnownAI(String ai) {
        return switch (ai) {
            case "00", "01", "02", "10", "11", "12", "13", "15", "17",
                 "20", "21", "22", "30", "37", "90", "91", "92", "93", "94", "95", "96", "97", "98", "99",
                 "240", "241", "242", "250", "251", "253", "254", "255",
                 "310", "311", "312", "313", "314", "315", "316",
                 "320", "321", "322", "323", "324", "325", "326",
                 "330", "331", "332", "333", "334", "335", "336",
                 "340", "341", "342", "343", "344", "345", "346",
                 "400", "401", "402", "403", "410", "411", "412", "413", "414", "415", "420", "421", "422", "423", "424", "425", "426" -> true;
            default -> ai.startsWith("31") || ai.startsWith("32") || ai.startsWith("33") || ai.startsWith("34") ||
                       ai.startsWith("35") || ai.startsWith("36") || ai.startsWith("39") || ai.startsWith("70") ||
                       ai.startsWith("71") || ai.startsWith("80") || ai.startsWith("81") || ai.startsWith("82");
        };
    }
}
