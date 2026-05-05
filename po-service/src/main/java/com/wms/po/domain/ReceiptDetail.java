package com.wms.po.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Receipt Detail (line item) domain model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptDetail {

    private String receiptKey;
    private String receiptLineNumber;

    private String poKey;
    private String poLineNumber;

    private String sku;
    private String skuDescription;

    private BigDecimal qtyExpected;
    private BigDecimal qtyReceived;

    private String packKey;
    private String uom;

    private String lot;
    private String location;
    private String id; // License plate / container ID

    private String status;

    // Lottable fields
    @Builder.Default
    private Map<String, String> lottables = new HashMap<>();

    public void setLottable(int number, String value) {
        lottables.put("LOTTABLE" + String.format("%02d", number), value);
    }

    public String getLottable(int number) {
        return lottables.get("LOTTABLE" + String.format("%02d", number));
    }

    public BigDecimal getQtyVariance() {
        if (qtyExpected == null || qtyReceived == null) return BigDecimal.ZERO;
        return qtyReceived.subtract(qtyExpected);
    }

    public boolean hasVariance() {
        return getQtyVariance().compareTo(BigDecimal.ZERO) != 0;
    }
}
