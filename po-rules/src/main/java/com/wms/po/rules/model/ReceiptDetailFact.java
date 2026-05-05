package com.wms.po.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fact object for receipt detail lottable rules.
 * Used by Drools to compute lottable values based on client/region rules.
 *
 * Maps to ispDefLot1FrRcptDtl, ispDefLot2FrRcptDtl, ispGenLot2BySuppLot, etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptDetailFact {

    // Keys
    private String receiptKey;
    private int lineNumber;
    private String storerKey;
    private String facility;
    private String region;

    // SKU info
    private String sku;
    private String skuDescription;
    private String packKey;
    private String uom;

    // PO info (for lottable derivation)
    private String poKey;
    private int poLineNumber;

    // Quantities
    private BigDecimal qtyExpected;
    private BigDecimal qtyReceived;

    // Input lottables (from PO or ASN)
    private String inputLottable01;
    private String inputLottable02;
    private String inputLottable03;
    private String inputLottable04;
    private String inputLottable05;
    private String inputLottable06;
    private String inputLottable07;
    private String inputLottable08;
    private String inputLottable09;
    private String inputLottable10;

    // Computed/Output lottables
    private String lottable01;
    private String lottable02;
    private String lottable03;
    private String lottable04;
    private String lottable05;
    private String lottable06;
    private String lottable07;
    private String lottable08;
    private String lottable09;
    private String lottable10;

    // Supplier info (for supplier lot derivation)
    private String supplierKey;
    private String supplierLot;
    private String supplierBatch;

    // Date fields
    private LocalDate manufactureDate;
    private LocalDate expiryDate;
    private Integer shelfLifeDays;

    // Additional attributes
    private String countryOfOrigin;
    private String customsReference;
    private String qualityGrade;
    private String color;
    private String size;
    private String style;

    // Validation state
    @Builder.Default
    private boolean valid = true;
    @Builder.Default
    private List<String> validationErrors = new ArrayList<>();
    @Builder.Default
    private List<String> appliedRules = new ArrayList<>();

    // Flag indicating lottables have been computed
    @Builder.Default
    private boolean lottablesComputed = false;

    public void markInvalid(String error) {
        this.valid = false;
        this.validationErrors.add(error);
    }

    public void addAppliedRule(String ruleName) {
        this.appliedRules.add(ruleName);
    }

    /**
     * Copy input lottables to output lottables (default behavior)
     */
    public void copyInputToOutput() {
        if (this.lottable01 == null) this.lottable01 = inputLottable01;
        if (this.lottable02 == null) this.lottable02 = inputLottable02;
        if (this.lottable03 == null) this.lottable03 = inputLottable03;
        if (this.lottable04 == null) this.lottable04 = inputLottable04;
        if (this.lottable05 == null) this.lottable05 = inputLottable05;
        if (this.lottable06 == null) this.lottable06 = inputLottable06;
        if (this.lottable07 == null) this.lottable07 = inputLottable07;
        if (this.lottable08 == null) this.lottable08 = inputLottable08;
        if (this.lottable09 == null) this.lottable09 = inputLottable09;
        if (this.lottable10 == null) this.lottable10 = inputLottable10;
    }

    /**
     * Generate lottable02 from supplier lot if not already set
     */
    public void deriveFromSupplierLot() {
        if (this.lottable02 == null && this.supplierLot != null) {
            this.lottable02 = this.supplierLot;
        }
    }

    /**
     * Generate lot number in format: RCV-{receiptKey}-{lineNumber}
     */
    public String generateReceiptBasedLot() {
        return String.format("RCV-%s-%03d", receiptKey, lineNumber);
    }

    /**
     * Generate lot number in format: {date}-{sequence}
     */
    public String generateDateBasedLot(String dateStr, int sequence) {
        return String.format("%s-%04d", dateStr, sequence);
    }
}
