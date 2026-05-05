package com.wms.po.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Receipt detail entity - maps to RECEIPTDETAIL table
 */
@Entity
@Table(name = "RECEIPTDETAIL", schema = "dbo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptDetailEntity {

    @Id
    @Column(name = "RECEIPTDETAILKEY", length = 50)
    private String receiptDetailKey;

    @Column(name = "RECEIPTKEY", length = 50, nullable = false)
    private String receiptKey;

    @Column(name = "RECEIPTLINENUMBER")
    private Integer lineNumber;

    @Column(name = "SKU", length = 50, nullable = false)
    private String sku;

    @Column(name = "QTYEXPECTED", precision = 18, scale = 5)
    private BigDecimal qtyExpected;

    @Column(name = "QTYRECEIVED", precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal qtyReceived = BigDecimal.ZERO;

    @Column(name = "UOM", length = 10)
    private String uom;

    @Column(name = "PACKKEY", length = 50)
    private String packKey;

    @Column(name = "STATUS", length = 1)
    @Builder.Default
    private String status = "0";

    @Column(name = "POKEY", length = 50)
    private String poKey;

    @Column(name = "POLINENUMBER")
    private Integer poLineNumber;

    // Lottable fields
    @Column(name = "LOTTABLE01", length = 50)
    private String lottable01;

    @Column(name = "LOTTABLE02", length = 50)
    private String lottable02;

    @Column(name = "LOTTABLE03", length = 50)
    private String lottable03;

    @Column(name = "LOTTABLE04", length = 50)
    private String lottable04;

    @Column(name = "LOTTABLE05", length = 50)
    private String lottable05;

    @Column(name = "LOTTABLE06", length = 50)
    private String lottable06;

    @Column(name = "LOTTABLE07", length = 50)
    private String lottable07;

    @Column(name = "LOTTABLE08", length = 50)
    private String lottable08;

    @Column(name = "LOTTABLE09", length = 50)
    private String lottable09;

    @Column(name = "LOTTABLE10", length = 50)
    private String lottable10;

    @Column(name = "ADDDATE")
    private LocalDateTime addDate;

    @Column(name = "ADDWHO", length = 50)
    private String addWho;

    @Column(name = "EDITDATE")
    private LocalDateTime editDate;

    @Column(name = "EDITWHO", length = 50)
    private String editWho;

    @PrePersist
    protected void onCreate() {
        addDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        editDate = LocalDateTime.now();
    }
}
