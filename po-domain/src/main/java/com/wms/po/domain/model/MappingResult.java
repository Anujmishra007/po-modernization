package com.wms.po.domain.model;

import com.wms.po.domain.dto.DetailMapping;
import com.wms.po.domain.dto.ReceiptHeaderDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of mapping PO to ASN/Receipt
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingResult {

    private String externReceiptKey;
    private String storerKey;
    private String facility;
    private String userId;

    private ReceiptHeaderDTO header;
    private List<DetailMapping> details;

    // Lottable mappings
    private boolean lottablesApplied;
    private List<String> appliedRules;
}
