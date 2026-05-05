package com.wms.po.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Receipt domain model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Receipt {

    private String receiptKey;
    private String storerKey;
    private String facility;
    private String externReceiptKey;

    private String status;
    private String type;

    private String carrierKey;
    private String carrierName;
    private String trailerNumber;

    private LocalDateTime receiptDate;
    private LocalDateTime expectedDate;

    private BigDecimal totalQtyExpected;
    private BigDecimal totalQtyReceived;

    @Builder.Default
    private List<ReceiptDetail> details = new ArrayList<>();

    @Builder.Default
    private List<String> linkedPOKeys = new ArrayList<>();

    // Audit fields
    private LocalDateTime addDate;
    private String addWho;
    private LocalDateTime editDate;
    private String editWho;

    public boolean isComplete() {
        return "9".equals(status);
    }

    public boolean isInProgress() {
        return "1".equals(status) || "5".equals(status);
    }

    public boolean isOpen() {
        return "0".equals(status);
    }
}
