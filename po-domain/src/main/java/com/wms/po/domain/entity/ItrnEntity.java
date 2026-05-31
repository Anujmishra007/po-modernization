package com.wms.po.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ITRN entity - Inventory Transaction record.
 * Tracks all inventory movements (receipts, picks, adjustments, etc.)
 */
@Entity
@Table(name = "ITRN", schema = "dbo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItrnEntity {

    @Id
    @Column(name = "ITRNKEY", length = 50)
    private String itrnKey;

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
    private BigDecimal qty;

    @Column(name = "TRANTYPE", length = 20)
    private String tranType;

    @Column(name = "STATUS", length = 10)
    @Builder.Default
    private String status = "OK";

    @Column(name = "RECEIPTKEY", length = 50)
    private String receiptKey;

    @Column(name = "ADDDATE")
    private LocalDateTime addDate;

    @Column(name = "ADDWHO", length = 50)
    private String addWho;
}
