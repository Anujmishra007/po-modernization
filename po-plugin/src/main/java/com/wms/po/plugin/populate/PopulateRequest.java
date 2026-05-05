package com.wms.po.plugin.populate;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Request object for populate operations.
 *
 * Contains all information needed to create a receipt from a PO.
 */
@Data
@Builder
public class PopulateRequest {

    private String storerKey;
    private String facility;
    private String userId;

    /**
     * PO keys to populate receipt from.
     */
    @Builder.Default
    private List<String> poKeys = new ArrayList<>();

    /**
     * External receipt number (optional).
     */
    private String externalReceiptKey;

    /**
     * Receipt type (ASN, RMA, XDOCK, etc.).
     */
    private String receiptType;

    /**
     * Expected arrival date.
     */
    private LocalDate expectedDate;

    /**
     * Carrier information.
     */
    private String carrierKey;
    private String carrierName;

    /**
     * Transport information.
     */
    private String trailerNumber;
    private String sealNumber;

    /**
     * Override receiving location.
     */
    private String receivingLocation;

    /**
     * Line-level details to override PO values.
     */
    @Builder.Default
    private List<LineOverride> lineOverrides = new ArrayList<>();

    /**
     * Additional metadata/parameters.
     */
    @Builder.Default
    private Map<String, String> parameters = new HashMap<>();

    /**
     * Get parameter with default value.
     */
    public String getParameter(String key, String defaultValue) {
        return parameters.getOrDefault(key, defaultValue);
    }

    /**
     * Line-level override data.
     */
    @Data
    @Builder
    public static class LineOverride {
        private String poKey;
        private Integer poLineNumber;
        private String sku;
        private BigDecimal quantity;
        private String packKey;
        private String uom;
        private String toLocation;
        private String toId;

        // Lottable overrides
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

        // Condition/status overrides
        private String conditionCode;
        private String holdCode;
    }
}
