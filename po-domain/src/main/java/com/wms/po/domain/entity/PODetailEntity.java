package com.wms.po.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PO Detail entity - maps to PODETAIL table
 */
@Entity
@Table(name = "PODETAIL", schema = "dbo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PODetailEntity {

    @Id
    @Column(name = "PODETAILKEY", length = 50)
    private String poDetailKey;

    @Column(name = "POKEY", length = 50, nullable = false)
    private String poKey;

    @Column(name = "POLINENUMBER")
    private Integer poLineNumber;

    @Column(name = "SKU", length = 50, nullable = false)
    private String sku;

    @Column(name = "QTYORDERED", precision = 18, scale = 5)
    private BigDecimal qtyOrdered;

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

    @Column(name = "ADDDATE")
    private LocalDateTime addDate;

    @Column(name = "ADDWHO", length = 50)
    private String addWho;

    @Column(name = "EDITDATE")
    private LocalDateTime editDate;

    @Column(name = "EDITWHO", length = 50)
    private String editWho;
}
