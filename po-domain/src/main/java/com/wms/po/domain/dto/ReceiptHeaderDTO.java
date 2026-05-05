package com.wms.po.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for receipt header creation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptHeaderDTO {

    private String externReceiptKey;
    private String storerKey;
    private String facility;
    private String receiptType;
    private String carrierKey;
    private String carrierName;
    private String trailerNumber;
    private LocalDateTime expectedDate;
    private String userId;
    private String notes;
}
