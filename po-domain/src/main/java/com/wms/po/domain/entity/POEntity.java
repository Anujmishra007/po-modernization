package com.wms.po.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Purchase Order entity - maps to PO table
 */
@Entity
@Table(name = "PO", schema = "dbo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POEntity {

    @Id
    @Column(name = "POKEY", length = 50)
    private String poKey;

    @Column(name = "EXTERNPOKEY", length = 50)
    private String externPoKey;

    @Column(name = "STORERKEY", length = 50, nullable = false)
    private String storerKey;

    @Column(name = "FACILITY", length = 20)
    private String facility;

    @Column(name = "POTYPE", length = 10)
    private String poType;

    @Column(name = "STATUS", length = 1)
    @Builder.Default
    private String status = "0";

    @Column(name = "BUYERKEY", length = 50)
    private String buyerKey;

    @Column(name = "BUYERNAME", length = 100)
    private String buyerName;

    @Column(name = "SUPPLIERKEY", length = 50)
    private String supplierKey;

    @Column(name = "SUPPLIERNAME", length = 100)
    private String supplierName;

    @Column(name = "ORDERDATE")
    private LocalDateTime orderDate;

    @Column(name = "EXPECTEDDATE")
    private LocalDateTime expectedDate;

    @Column(name = "CLOSEDATE")
    private LocalDateTime closeDate;

    @Column(name = "NOTES", length = 500)
    private String notes;

    @Column(name = "ADDDATE")
    private LocalDateTime addDate;

    @Column(name = "ADDWHO", length = 50)
    private String addWho;

    @Column(name = "EDITDATE")
    private LocalDateTime editDate;

    @Column(name = "EDITWHO", length = 50)
    private String editWho;

    @Version
    @Column(name = "VERSION")
    @Builder.Default
    private Integer version = 0;
}
