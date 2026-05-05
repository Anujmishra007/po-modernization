package com.wms.po.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of lottable application
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LottableResult {

    private boolean success;
    private Map<String, Map<String, String>> lottablesByDetail; // detailKey -> lottable values
    private List<String> appliedRules;
    private List<String> warnings;
}
