package com.wms.po.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for Receipt
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptResponse {

    private String receiptKey;
    private String storerKey;
    private String facility;
    private String externReceiptKey;

    private String status;
    private String statusDescription;
    private String type;

    private String carrierKey;
    private String trailerNumber;

    private LocalDateTime receiptDate;

    private BigDecimal totalQtyExpected;
    private BigDecimal totalQtyReceived;

    private int lineCount;
    private List<ReceiptDetailResponse> lines;

    private List<String> linkedPOKeys;

    private LocalDateTime addDate;
    private String addWho;

    public String getStatusDescription() {
        return switch (status) {
            case "0" -> "Open";
            case "1" -> "In Progress";
            case "5" -> "Partially Received";
            case "9" -> "Complete";
            default -> "Unknown";
        };
    }
}
