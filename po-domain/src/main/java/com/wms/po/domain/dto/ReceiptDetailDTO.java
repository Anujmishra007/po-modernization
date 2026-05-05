package com.wms.po.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for receipt detail creation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptDetailDTO {

    private String sku;
    private BigDecimal qtyExpected;
    private String uom;
    private String packKey;

    // Lottables
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

    // Source PO info
    private String poKey;
    private String poLineNumber;
}
