package com.wms.po.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Detail line mapping from PO to Receipt
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetailMapping {

    private String storerKey;
    private String sku;
    private BigDecimal qtyExpected;
    private String uom;
    private String packKey;

    // Source PO info
    private String poKey;
    private int poLineNumber;

    // All lottable values as a map for flexibility
    private Map<String, String> lottables;

    public String getLottable(int index) {
        return lottables != null ? lottables.get("lottable" + String.format("%02d", index)) : null;
    }

    public void setLottable(int index, String value) {
        if (lottables != null) {
            lottables.put("lottable" + String.format("%02d", index), value);
        }
    }
}
