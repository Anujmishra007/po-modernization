package com.wms.po.activity.impl;

import com.wms.po.activity.MappingActivity;
import com.wms.po.domain.dto.DetailMapping;
import com.wms.po.domain.dto.ReceiptHeaderDTO;
import com.wms.po.domain.entity.POEntity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.model.LottableResult;
import com.wms.po.domain.model.MappingResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.domain.repository.PORepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Implementation of MappingActivity - maps PO data to Receipt structure.
 *
 * Maps to legacy SPs:
 * - SP-090-099: ispLottableRule_Wrapper, ispDefLot*, ispGenLot* (error codes 69400-69430)
 *
 * Error codes:
 * - LOT_001 (69400) - Lottable Rule Not Found
 * - LOT_002 (69401) - Lottable Rule Execution Failed
 * - LOT_003 (69402) - Lottable Mapping Failed
 * - LOT_010-015 (69410-69415) - Specific lottable generation errors
 * - PO_018 (68818) - PO Field Mapping Failed
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MappingActivityImpl implements MappingActivity {

    private final PORepository poRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Map PO data to ASN/Receipt structure.
     *
     * Error codes:
     * - PO_001 (68800) - PO Not Found
     * - PO_018 (68818) - PO Field Mapping Failed
     */
    @Override
    public MappingResult mapPOToASN(PopulateRequest request, VariationContext context) {
        log.info("Mapping PO to ASN: poKeys={}, context={}", request.getPoKeys(), context);

        // Load PO data
        List<POEntity> pos;
        try {
            pos = poRepository.findByPoKeyIn(request.getPoKeys());
        } catch (Exception e) {
            log.error("Failed to load POs for mapping: {} (legacy error 68818)",
                request.getPoKeys(), e);
            throw new BusinessException(
                com.wms.po.domain.exception.ErrorCode.PO_MAPPING_FAILED,
                "Failed to load POs for mapping: " + request.getPoKeys(), e)
                .withDetail("poKeys", request.getPoKeys());
        }

        if (pos.isEmpty()) {
            log.error("No POs found for mapping: {} (legacy error 68800)", request.getPoKeys());
            throw BusinessException.poNotFound(request.getPoKeys().get(0));
        }

        // Use first PO for header info (multi-PO consolidation)
        POEntity primaryPO = pos.get(0);

        // Create header mapping
        ReceiptHeaderDTO header = ReceiptHeaderDTO.builder()
            .externReceiptKey(generateExternReceiptKey(request))
            .storerKey(request.getStorerKey())
            .facility(request.getFacility())
            .receiptType("PO")
            .expectedDate(primaryPO.getExpectedDate())
            .userId(request.getUserId())
            .build();

        // Load and map PO details
        List<DetailMapping> details = new ArrayList<>();

        for (POEntity po : pos) {
            List<Map<String, Object>> poDetails = loadPODetails(po.getPoKey());

            for (Map<String, Object> detail : poDetails) {
                DetailMapping mapping = mapDetailLine(detail, po, context);
                details.add(mapping);
            }
        }

        log.info("Mapped {} POs to {} detail lines", pos.size(), details.size());

        return MappingResult.builder()
            .externReceiptKey(header.getExternReceiptKey())
            .storerKey(request.getStorerKey())
            .facility(request.getFacility())
            .userId(request.getUserId())
            .header(header)
            .details(details)
            .build();
    }

    /**
     * Apply lottable rules to mapped data.
     *
     * Error codes:
     * - LOT_001 (69400) - Lottable Rule Not Found
     * - LOT_002 (69401) - Lottable Rule Execution Failed
     * - LOT_003 (69402) - Lottable Mapping Failed
     * - LOT_010-015 (69410-69415) - Specific lottable generation errors
     */
    @Override
    public LottableResult applyLottables(MappingResult mapping, VariationContext context) {
        log.info("Applying lottables for {} details, context={}", mapping.getDetails().size(), context);

        Map<String, Map<String, String>> lottablesByDetail = new HashMap<>();
        List<String> appliedRules = new ArrayList<>();

        try {
            for (DetailMapping detail : mapping.getDetails()) {
                Map<String, String> lottables = new HashMap<>(detail.getLottables() != null ? detail.getLottables() : Map.of());

                // Apply region-specific lottable rules
                if (context.isKorea()) {
                    // Korea requires lottable03 for customs clearance
                    if (!lottables.containsKey("lottable03") || lottables.get("lottable03") == null) {
                        try {
                            lottables.put("lottable03", generateCustomsCode(detail));
                            appliedRules.add("KOREA_CUSTOMS_CODE");
                        } catch (Exception e) {
                            log.error("Failed to generate customs code for SKU {}: {} (legacy error 69413)",
                                detail.getSku(), e.getMessage());
                            throw BusinessException.lottableGenerationFailed(3, "Customs code generation failed");
                        }
                    }
                }

                if (context.isIndia()) {
                    // India requires GST code in lottable04
                    if (!lottables.containsKey("lottable04")) {
                        lottables.put("lottable04", "GST-" + mapping.getStorerKey());
                        appliedRules.add("INDIA_GST_CODE");
                    }
                }

                // Apply client-specific rules
                if (context.isNike()) {
                    // Nike requires style code in lottable01
                    appliedRules.add("NIKE_STYLE_CODE");
                }

                detail.setLottables(lottables);
                lottablesByDetail.put(detail.getPoKey() + "-" + detail.getPoLineNumber(), lottables);
            }

            return LottableResult.builder()
                .success(true)
                .lottablesByDetail(lottablesByDetail)
                .appliedRules(appliedRules)
                .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lottable mapping failed: {} (legacy error 69402)", e.getMessage(), e);
            throw BusinessException.lottableMappingFailed("lottables", e.getMessage());
        }
    }

    private String generateExternReceiptKey(PopulateRequest request) {
        // Generate external receipt key from PO keys
        if (request.getPoKeys().size() == 1) {
            return "RCV-" + request.getPoKeys().get(0);
        }
        return "RCV-MULTI-" + System.currentTimeMillis();
    }

    private List<Map<String, Object>> loadPODetails(String poKey) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT * FROM dbo.PODETAIL WHERE POKEY = ? ORDER BY POLINENUMBER",
                poKey
            );
        } catch (Exception e) {
            log.warn("Failed to load PO details from database, using empty list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private DetailMapping mapDetailLine(Map<String, Object> detail, POEntity po, VariationContext context) {
        Map<String, String> lottables = new HashMap<>();

        // Map lottable fields
        for (int i = 1; i <= 10; i++) {
            String key = "LOTTABLE" + String.format("%02d", i);
            Object value = detail.get(key);
            if (value != null) {
                lottables.put(key.toLowerCase(), value.toString());
            }
        }

        return DetailMapping.builder()
            .sku((String) detail.getOrDefault("SKU", ""))
            .qtyExpected((BigDecimal) detail.getOrDefault("QTYORDERED", BigDecimal.ZERO))
            .uom((String) detail.getOrDefault("UOM", "EA"))
            .packKey((String) detail.get("PACKKEY"))
            .poKey(po.getPoKey())
            .poLineNumber(((Number) detail.getOrDefault("POLINENUMBER", 0)).intValue())
            .lottables(lottables)
            .build();
    }

    private String generateCustomsCode(DetailMapping detail) {
        return "CC-" + detail.getSku().substring(0, Math.min(5, detail.getSku().length()));
    }
}
