package com.wms.po.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Search criteria for PO queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POSearchCriteria {

    private String storerKey;
    private String facility;
    private String status;
    private String supplierKey;
    private String externPoKey;
    private LocalDate poDateFrom;
    private LocalDate poDateTo;
    private LocalDate expectedDateFrom;
    private LocalDate expectedDateTo;
    private String poType;

    // Pagination
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 50;

    // Sorting
    @Builder.Default
    private String sortBy = "ADDDATE";
    @Builder.Default
    private String sortDirection = "DESC";
}
