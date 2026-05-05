package com.wms.po.domain.projection;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Validation Configuration DTO.
 *
 * Replaces: V_ASN_Extended_Validation view
 * Source tables: CODELKUP, STORERCONFIG
 *
 * Provides validation configuration for ASN/Receipt processing
 * based on storer settings and code lookups.
 */
@Data
@Builder
public class ValidationConfigDTO {

    // Identity
    private String storerKey;
    private String facility;

    // Quantity Validation
    private boolean allowOverReceipt;
    private boolean allowUnderReceipt;
    private BigDecimal overReceiptTolerance;
    private BigDecimal underReceiptTolerance;
    private String varianceHandling;  // ACCEPT, REJECT, WARN

    // Status Validation
    @Builder.Default
    private List<String> allowedReceiptStatuses = List.of("0", "1", "2", "3");
    @Builder.Default
    private List<String> allowedPOStatuses = List.of("0", "1", "2", "3");

    // SKU Validation
    private boolean requireSkuExists;
    private boolean allowNewSku;
    private boolean validateSkuStatus;
    @Builder.Default
    private List<String> allowedSkuStatuses = List.of("A", "0");

    // Lot Validation
    private boolean requireLotNumber;
    private boolean validateLotExpiry;
    private Integer minDaysToExpiry;
    private boolean allowExpiredLots;

    // Location Validation
    private boolean validateLocation;
    private boolean requireReceivingDock;
    @Builder.Default
    private List<String> allowedLocationTypes = List.of("RECV", "STAGE", "DOCK");

    // Carton Validation
    private boolean validateCartonType;
    @Builder.Default
    private List<String> allowedCartonTypes = List.of();
    private boolean requireSSCC;
    private boolean validateSSCCFormat;

    // UOM Validation
    private boolean validateUOM;
    @Builder.Default
    private List<String> allowedUOMs = List.of("EA", "CS", "PL");

    // Code Lookups (from CODELKUP)
    @Builder.Default
    private Map<String, List<String>> codeLookups = Map.of();

    // Business Rules
    private boolean requirePOMatch;
    private boolean allowBlindReceipt;
    private boolean requireQCInspection;
    private boolean autoGenerateLPN;
    private boolean autoReleasePutaway;

    // Custom Validations
    @Builder.Default
    private List<CustomValidation> customValidations = List.of();

    /**
     * Check if over-receipt is within tolerance.
     */
    public boolean isOverReceiptWithinTolerance(BigDecimal expected, BigDecimal received) {
        if (!allowOverReceipt) {
            return received.compareTo(expected) <= 0;
        }
        if (overReceiptTolerance == null) {
            return true;
        }
        BigDecimal variance = received.subtract(expected);
        BigDecimal maxVariance = expected.multiply(overReceiptTolerance.divide(BigDecimal.valueOf(100)));
        return variance.compareTo(maxVariance) <= 0;
    }

    /**
     * Check if under-receipt is within tolerance.
     */
    public boolean isUnderReceiptWithinTolerance(BigDecimal expected, BigDecimal received) {
        if (!allowUnderReceipt && received.compareTo(expected) < 0) {
            return false;
        }
        if (underReceiptTolerance == null) {
            return true;
        }
        BigDecimal variance = expected.subtract(received);
        BigDecimal maxVariance = expected.multiply(underReceiptTolerance.divide(BigDecimal.valueOf(100)));
        return variance.compareTo(maxVariance) <= 0;
    }

    /**
     * Check if status is allowed.
     */
    public boolean isReceiptStatusAllowed(String status) {
        return allowedReceiptStatuses.contains(status);
    }

    /**
     * Check if carton type is allowed.
     */
    public boolean isCartonTypeAllowed(String cartonType) {
        if (allowedCartonTypes.isEmpty()) {
            return true; // No restriction
        }
        return allowedCartonTypes.contains(cartonType);
    }

    @Data
    @Builder
    public static class CustomValidation {
        private String name;
        private String field;
        private String operator;  // EQ, NE, GT, LT, IN, REGEX
        private String value;
        private String errorMessage;
        private boolean blocking;  // If true, fails validation; if false, just warns
    }
}
