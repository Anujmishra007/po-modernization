package com.wms.po.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Purchase Order domain model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder {

    private String poKey;
    private String storerKey;
    private String externPoKey;
    private String facility;
    private String supplierKey;
    private String supplierName;

    private String status;
    private String type;

    private LocalDate poDate;
    private LocalDate expectedReceiptDate;

    private BigDecimal totalQtyOrdered;
    private BigDecimal totalQtyReceived;
    private BigDecimal totalValue;

    private String buyerRef;
    private String notes;

    @Builder.Default
    private List<PurchaseOrderDetail> details = new ArrayList<>();

    // Audit fields
    private LocalDateTime addDate;
    private String addWho;
    private LocalDateTime editDate;
    private String editWho;

    public boolean isFullyReceived() {
        return totalQtyOrdered != null && totalQtyReceived != null
            && totalQtyReceived.compareTo(totalQtyOrdered) >= 0;
    }

    public boolean isPartiallyReceived() {
        return totalQtyReceived != null
            && totalQtyReceived.compareTo(BigDecimal.ZERO) > 0
            && !isFullyReceived();
    }

    public boolean isOpen() {
        return "0".equals(status);
    }

    public boolean isClosed() {
        return "9".equals(status);
    }
}
