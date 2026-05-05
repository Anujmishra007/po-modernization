package com.wms.po.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Response DTO for Receipt line item
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptDetailResponse {

    private String receiptKey;
    private String receiptLineNumber;

    private String poKey;
    private String poLineNumber;

    private String sku;
    private String skuDescription;

    private BigDecimal qtyExpected;
    private BigDecimal qtyReceived;
    private BigDecimal qtyVariance;

    private String packKey;
    private String uom;

    private String lot;
    private String location;
    private String id;

    private String status;

    private Map<String, String> lottables;

    public BigDecimal getQtyVariance() {
        if (qtyExpected == null || qtyReceived == null) return BigDecimal.ZERO;
        return qtyReceived.subtract(qtyExpected);
    }
}
