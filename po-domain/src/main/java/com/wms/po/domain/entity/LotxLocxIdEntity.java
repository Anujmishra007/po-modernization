package com.wms.po.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LOTxLOCxID entity - central inventory repository in WMS.
 * LOT = Lot attributes, LOC = Location, ID = License Plate
 */
@Entity
@Table(name = "LOTXLOCXID", schema = "dbo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LotxLocxIdEntity {

    @Id
    @Column(name = "LOTXLOCXIDKEY", length = 50)
    private String lotxlocxidKey;

    @Column(name = "STORERKEY", length = 50, nullable = false)
    private String storerKey;

    @Column(name = "SKU", length = 50, nullable = false)
    private String sku;

    @Column(name = "LOT", length = 50)
    private String lot;

    @Column(name = "LOC", length = 50)
    private String loc;

    @Column(name = "ID", length = 50)
    private String id;

    @Column(name = "QTY", precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal qty = BigDecimal.ZERO;

    @Column(name = "QTYALLOCATED", precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal qtyAllocated = BigDecimal.ZERO;

    @Column(name = "QTYPICKED", precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal qtyPicked = BigDecimal.ZERO;

    @Column(name = "STATUS", length = 10)
    @Builder.Default
    private String status = "OK";

    @Column(name = "HOLD", length = 1)
    @Builder.Default
    private String hold = "0";

    @Column(name = "PACKKEY", length = 50)
    private String packKey;

    @Column(name = "UOM", length = 10)
    private String uom;

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

    @Column(name = "CREATEDATE")
    private LocalDateTime createDate;

    @Column(name = "ADDDATE")
    private LocalDateTime addDate;

    @Column(name = "ADDWHO", length = 50)
    private String addWho;

    @Column(name = "EDITDATE")
    private LocalDateTime editDate;

    @Column(name = "EDITWHO", length = 50)
    private String editWho;
}
