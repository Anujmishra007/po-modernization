package com.wms.po.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lottable field mapping for region/client variations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LottableMapping {

    private String storerKey;
    private String region;
    private String client;

    // Lottable field assignments
    private String lottable01Field;  // e.g., "LOT_NUMBER"
    private String lottable02Field;  // e.g., "BATCH_NUMBER"
    private String lottable03Field;  // e.g., "SERIAL_NUMBER"
    private String lottable04Field;  // e.g., "EXPIRY_DATE"
    private String lottable05Field;  // e.g., "MFG_DATE"
    private String lottable06Field;  // e.g., "COUNTRY_OF_ORIGIN"
    private String lottable07Field;  // e.g., "SUPPLIER_LOT"
    private String lottable08Field;  // e.g., "CUSTOMS_REF"
    private String lottable09Field;
    private String lottable10Field;

    // Validation rules
    private boolean lottable01Required;
    private boolean lottable02Required;
    private boolean lottable03Required;
    private boolean lottable04Required;
    private boolean lottable05Required;

    // Format patterns
    private String lottable01Pattern;
    private String lottable02Pattern;
    private String lottable04Pattern;  // Date format for expiry

    public String getFieldMapping(int lottableNumber) {
        return switch (lottableNumber) {
            case 1 -> lottable01Field;
            case 2 -> lottable02Field;
            case 3 -> lottable03Field;
            case 4 -> lottable04Field;
            case 5 -> lottable05Field;
            case 6 -> lottable06Field;
            case 7 -> lottable07Field;
            case 8 -> lottable08Field;
            case 9 -> lottable09Field;
            case 10 -> lottable10Field;
            default -> null;
        };
    }
}
