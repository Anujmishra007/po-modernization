package com.wms.po.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Purchase Order Detail (line item) domain model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderDetail {

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

    // Lottable fields
    @Builder.Default
    private Map<String, String> lottables = new HashMap<>();

    public void setLottable(int number, String value) {
        lottables.put("LOTTABLE" + String.format("%02d", number), value);
    }

    public String getLottable(int number) {
        return lottables.get("LOTTABLE" + String.format("%02d", number));
    }

    public BigDecimal getQtyOpen() {
        if (qtyOrdered == null) return BigDecimal.ZERO;
        if (qtyReceived == null) return qtyOrdered;
        return qtyOrdered.subtract(qtyReceived);
    }

    public boolean isFullyReceived() {
        return getQtyOpen().compareTo(BigDecimal.ZERO) <= 0;
    }
}
