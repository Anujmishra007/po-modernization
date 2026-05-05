package com.wms.po.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for PO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POResponse {

    private String poKey;
    private String storerKey;
    private String externPoKey;
    private String facility;
    private String supplierKey;

    private String status;
    private String statusDescription;
    private String type;

    private LocalDate poDate;
    private LocalDate expectedReceiptDate;

    private BigDecimal totalQtyOrdered;
    private BigDecimal totalQtyReceived;
    private BigDecimal totalValue;

    private int lineCount;
    private List<PODetailResponse> lines;

    private LocalDateTime addDate;
    private String addWho;
    private LocalDateTime editDate;
    private String editWho;

    public String getStatusDescription() {
        return switch (status) {
            case "0" -> "Open";
            case "1" -> "In Progress";
            case "5" -> "Partially Received";
            case "9" -> "Closed";
            default -> "Unknown";
        };
    }
}
