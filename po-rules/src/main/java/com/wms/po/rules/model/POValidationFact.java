package com.wms.po.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PO validation fact for rules engine
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POValidationFact {

    // PO Header
    private String poKey;
    private String storerKey;
    private String facility;
    private String externPoKey;
    private String supplierKey;
    private String poType;
    private String status;
    private LocalDate poDate;
    private LocalDate expectedReceiptDate;

    // Context
    private String region;
    private String client;
    private String dbVersion;
    private String userId;

    // PO Details (for line validation)
    private List<POLineValidationFact> lines;

    // Validation results
    @Builder.Default
    private boolean valid = true;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    // Computed values
    private BigDecimal totalQuantity;
    private BigDecimal totalValue;
    private Integer lineCount;

    public void addError(String error) {
        this.valid = false;
        this.errors.add(error);
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class POLineValidationFact {
        private String poLineKey;
        private String sku;
        private BigDecimal qtyOrdered;
        private BigDecimal qtyReceived;
        private BigDecimal unitPrice;
        private String packKey;
        private String uom;
        private Map<String, String> lottables;

        @Builder.Default
        private boolean valid = true;
        @Builder.Default
        private List<String> errors = new ArrayList<>();

        public void addError(String error) {
            this.valid = false;
            this.errors.add(error);
        }
    }
}
