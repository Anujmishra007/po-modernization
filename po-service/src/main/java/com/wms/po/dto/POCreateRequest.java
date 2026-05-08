package com.wms.po.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for creating a PO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POCreateRequest {

    @NotBlank(message = "Storer key is required")
    private String storerKey;

    @NotBlank(message = "Facility is required")
    private String facility;

    private String externPoKey;

    private String supplierKey;

    private String poType;

    private LocalDate poDate;
    private LocalDate expectedReceiptDate;

    private String buyerRef;
    private String notes;

    // Lines are optional for simple PO creation - can be added later
    private List<PODetailRequest> lines;
}
