package com.wms.po.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Request to finalize a receipt (ASN).
 * Maps to ispFinalizeReceipt stored procedure input.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FinalizeRequest {

    /**
     * Primary key of the receipt to finalize
     */
    private String receiptKey;

    /**
     * Facility code where receipt is being finalized
     */
    private String facility;

    /**
     * Storer/client key
     */
    private String storerKey;

    /**
     * User performing the finalization
     */
    private String userId;

    /**
     * Optional: Specific line numbers to finalize (null = all lines)
     */
    private List<Integer> lineNumbers;

    /**
     * Whether to auto-close the receipt after finalization
     */
    @Builder.Default
    private Boolean autoClose = true;

    /**
     * Whether to auto-release putaway tasks
     */
    @Builder.Default
    private Boolean releasePutaway = true;

    /**
     * Variance tolerance percentage (e.g., 0.05 = 5%)
     */
    @Builder.Default
    private BigDecimal varianceTolerance = BigDecimal.ZERO;

    /**
     * Whether to apply inventory holds based on rules
     */
    @Builder.Default
    private Boolean applyHolds = true;

    /**
     * Override flags for specific behaviors
     */
    private Map<String, Object> overrides;

    /**
     * Skip validation checks (admin override only)
     */
    @Builder.Default
    private Boolean skipValidation = false;

    /**
     * Run in async mode (return immediately, process in background)
     */
    @Builder.Default
    private Boolean async = false;

    public boolean shouldAutoClose() {
        return Boolean.TRUE.equals(autoClose);
    }

    public boolean shouldReleasePutaway() {
        return Boolean.TRUE.equals(releasePutaway);
    }

    public boolean shouldApplyHolds() {
        return Boolean.TRUE.equals(applyHolds);
    }

    public boolean shouldSkipValidation() {
        return Boolean.TRUE.equals(skipValidation);
    }

    public boolean isAsync() {
        return Boolean.TRUE.equals(async);
    }
}
