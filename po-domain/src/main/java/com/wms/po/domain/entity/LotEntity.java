package com.wms.po.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LOT entity - tracks lot attributes for inventory.
 * Part of the LOTxLOCxID inventory model.
 */
@Entity
@Table(name = "LOT", schema = "dbo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LotEntity {

    @Id
    @Column(name = "LOT", length = 50)
    private String lot;

    @Column(name = "STORERKEY", length = 50, nullable = false)
    private String storerKey;

    @Column(name = "SKU", length = 50, nullable = false)
    private String sku;

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
}
