package com.wms.po.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Response DTO for PO line item
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PODetailResponse {

    private String poKey;
    private String poLineNumber;
    private String sku;
    private String skuDescription;

    private BigDecimal qtyOrdered;
    private BigDecimal qtyReceived;
    private BigDecimal qtyOpen;

    private String packKey;
    private String uom;

    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    private String status;
    private String statusDescription;

    private Map<String, String> lottables;

    public String getStatusDescription() {
        return switch (status) {
            case "0" -> "Open";
            case "5" -> "Partially Received";
            case "9" -> "Fully Received";
            default -> "Unknown";
        };
    }
}
