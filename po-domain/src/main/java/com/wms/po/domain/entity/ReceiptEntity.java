package com.wms.po.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Receipt header entity - maps to RECEIPT table
 */
@Entity
@Table(name = "RECEIPT", schema = "dbo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptEntity {

    @Id
    @Column(name = "RECEIPTKEY", length = 50)
    private String receiptKey;

    @Column(name = "EXTERNRECEIPTKEY", length = 50)
    private String externReceiptKey;

    @Column(name = "STORERKEY", length = 50, nullable = false)
    private String storerKey;

    @Column(name = "FACILITY", length = 20, nullable = false)
    private String facility;

    @Column(name = "RECEIPTTYPE", length = 10)
    private String receiptType;

    @Column(name = "STATUS", length = 1)
    @Builder.Default
    private String status = "0";

    @Column(name = "CARRIERKEY", length = 50)
    private String carrierKey;

    @Column(name = "CARRIERNAME", length = 100)
    private String carrierName;

    @Column(name = "TRAILERNUMBER", length = 50)
    private String trailerNumber;

    @Column(name = "EXPECTEDDATE")
    private LocalDateTime expectedDate;

    @Column(name = "RECEIPTDATE")
    private LocalDateTime receiptDate;

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

    @PrePersist
    protected void onCreate() {
        addDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        editDate = LocalDateTime.now();
    }
}
