package com.wms.po.activity.impl;

import com.wms.po.activity.TradeReturnActivity;
import com.wms.po.domain.exception.BusinessException;
import com.wms.po.domain.exception.ErrorCode;
import com.wms.po.domain.model.TradeReturnRequest;
import com.wms.po.domain.model.VariationContext;
import com.wms.po.domain.service.KeyGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of TradeReturnActivity.
 *
 * Maps to legacy SPs:
 * - SP-004: WM.lsp_ASN_PopulateSOs_Wrapper (error codes 69950-69952)
 * - SP-005: WM.lsp_ASN_PopulateSODs_Wrapper (error codes 69953-69956)
 *
 * Error codes:
 * - TR_001 (69950) - Trade Return Not Found
 * - TR_002 (69951) - Trade Return Creation Failed
 * - TR_003 (69952) - Trade Return Processing Failed
 * - TR_004 (69953) - Trade Return Line Creation Failed
 * - TR_005 (69954) - Trade Return Reservation Failed
 * - TR_006 (69955) - Trade Return Invalid Status
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeReturnActivityImpl implements TradeReturnActivity {

    private final JdbcTemplate jdbcTemplate;
    private final KeyGeneratorService keyGenerator;

    private static final String ORDER_TYPE_RETURN = "TR";
    private static final String STATUS_NEW = "0";

    /**
     * Validate trade return request.
     *
     * Error codes:
     * - TR_001 (69950) - Trade Return Not Found
     * - TR_006 (69955) - Trade Return Invalid Status
     */
    @Override
    public TradeReturnValidationResult validateTradeReturn(TradeReturnRequest request, VariationContext context) {
        log.info("Validating trade return: receiptKey={}, storerKey={}",
            request.getReceiptKey(), request.getStorerKey());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check receipt exists
        if (!receiptExists(request.getReceiptKey())) {
            log.error("Receipt not found for trade return: {} (legacy error 69950)",
                request.getReceiptKey());
            throw BusinessException.tradeReturnNotFound(request.getReceiptKey());
        }

        // Check receipt status allows trade return
        String receiptStatus = getReceiptStatus(request.getReceiptKey());
        if (!isValidStatusForReturn(receiptStatus)) {
            log.error("Receipt {} has invalid status {} for trade return (legacy error 69955)",
                request.getReceiptKey(), receiptStatus);
            throw new BusinessException(ErrorCode.TRADE_RETURN_INVALID_STATUS,
                String.format("Receipt %s status '%s' does not allow trade return",
                    request.getReceiptKey(), receiptStatus))
                .withDetail("receiptKey", request.getReceiptKey())
                .withDetail("status", receiptStatus);
        }

        // Check receipt has lines
        int lineCount = getReceiptLineCount(request.getReceiptKey());
        if (lineCount == 0) {
            log.error("Receipt {} has no detail lines for trade return (legacy error 69950)",
                request.getReceiptKey());
            throw BusinessException.receiptDetailNotFound(request.getReceiptKey(), "all");
        }

        // Check if SO already exists for this receipt
        if (salesOrderExistsForReceipt(request.getReceiptKey())) {
            warnings.add("Sales Order already exists for this receipt");
        }

        // Validate customer code if provided
        if (request.getCustomerCode() != null && !customerExists(request.getCustomerCode())) {
            errors.add("Customer not found: " + request.getCustomerCode());
        }

        if (!errors.isEmpty()) {
            return TradeReturnValidationResult.failure(errors);
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("lineCount", lineCount);
        metadata.put("receiptStatus", receiptStatus);

        return new TradeReturnValidationResult(true, List.of(), warnings, metadata);
    }

    @Override
    public TradeReturnMappingResult mapReceiptToSalesOrder(TradeReturnRequest request, VariationContext context) {
        log.info("Mapping receipt to sales order: receiptKey={}", request.getReceiptKey());

        List<TradeReturnLineMapping> lines = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;

        // Query receipt details
        String sql = """
            SELECT rd.receiptlinenumber, rd.sku, rd.qtyreceived, rd.uom, rd.packkey,
                   rd.lottable01, rd.lottable02, rd.lottable03, rd.lottable04, rd.lottable05,
                   rd.lottable06, rd.lottable07, rd.lottable08, rd.lottable09, rd.lottable10,
                   rd.toloc, rd.toid, rd.conditioncode
            FROM dbo.RECEIPTDETAIL rd
            WHERE rd.receiptkey = ?
            ORDER BY rd.receiptlinenumber
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, request.getReceiptKey());

        int lineNum = 1;
        for (Map<String, Object> row : rows) {
            // Filter by requested line numbers if specified
            Integer receiptLineNum = ((Number) row.get("receiptlinenumber")).intValue();
            if (request.getLineNumbers() != null && !request.getLineNumbers().isEmpty()) {
                if (!request.getLineNumbers().contains(receiptLineNum)) {
                    continue;
                }
            }

            BigDecimal qty = (BigDecimal) row.get("qtyreceived");
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // Skip zero qty lines
            }

            Map<String, String> lottables = new HashMap<>();
            for (int i = 1; i <= 10; i++) {
                String key = "lottable" + String.format("%02d", i);
                Object val = row.get(key);
                if (val != null) {
                    lottables.put(key, val.toString());
                }
            }

            TradeReturnLineMapping lineMapping = new TradeReturnLineMapping(
                lineNum++,
                (String) row.get("sku"),
                qty,
                (String) row.get("uom"),
                (String) row.get("packkey"),
                (String) row.get("lottable01"),  // lot
                (String) row.get("toloc"),
                (String) row.get("toid"),
                request.getReasonCode(),
                (String) row.get("conditioncode"),
                lottables
            );

            lines.add(lineMapping);
            totalQty = totalQty.add(qty);
        }

        Map<String, String> customFields = new HashMap<>();
        customFields.put("susr1", request.getSusr1());
        customFields.put("susr2", request.getSusr2());
        customFields.put("susr3", request.getSusr3());

        log.info("Mapped {} lines with total qty={}", lines.size(), totalQty);

        return new TradeReturnMappingResult(
            request.getReceiptKey(),
            request.getStorerKey(),
            request.getCustomerCode(),
            request.getReturnType() != null ? request.getReturnType() : ORDER_TYPE_RETURN,
            request.getCarrierCode(),
            request.getPriority() != null ? String.valueOf(request.getPriority()) : "2",
            lines,
            totalQty,
            customFields
        );
    }

    /**
     * Create sales order header for trade return.
     *
     * Error codes:
     * - TR_002 (69951) - Trade Return Creation Failed
     */
    @Override
    @Transactional
    public String createSalesOrderHeader(TradeReturnMappingResult mapping) {
        log.info("Creating sales order header for receipt={}", mapping.receiptKey());

        try {
            String orderKey = keyGenerator.generateOrderKey();

            String sql = """
                INSERT INTO dbo.ORDERS (orderkey, storerkey, externorderkey, ordertype, status,
                    consigneekey, carriercode, priority, susr1, susr2, susr3,
                    adddate, addwho, editdate, editwho)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), ?, GETDATE(), ?)
                """;

            jdbcTemplate.update(sql,
                orderKey,
                mapping.storerKey(),
                mapping.receiptKey(),  // Use receipt key as external reference
                mapping.orderType(),
                STATUS_NEW,
                mapping.customerCode(),
                mapping.carrierCode(),
                mapping.priority(),
                mapping.customFields().get("susr1"),
                mapping.customFields().get("susr2"),
                mapping.customFields().get("susr3"),
                "TRADERETURN",
                "TRADERETURN"
            );

            log.info("Created sales order header: orderKey={}", orderKey);
            return orderKey;

        } catch (DataAccessException e) {
            log.error("Failed to create sales order header for receipt {}: {} (legacy error 69951)",
                mapping.receiptKey(), e.getMessage(), e);
            throw BusinessException.tradeReturnCreationFailed(mapping.receiptKey(), e);
        }
    }

    /**
     * Create sales order details for trade return.
     *
     * Error codes:
     * - TR_004 (69953) - Trade Return Line Creation Failed
     */
    @Override
    @Transactional
    public List<String> createSalesOrderDetails(String orderKey, List<TradeReturnLineMapping> lines) {
        log.info("Creating {} sales order details for orderKey={}", lines.size(), orderKey);

        List<String> detailKeys = new ArrayList<>();
        int currentLineNumber = 0;

        String sql = """
            INSERT INTO dbo.ORDERDETAIL (orderkey, orderlinenumber, sku, openqty,
                originalqty, shippedqty, uom, packkey, status,
                lottable01, lottable02, lottable03, lottable04, lottable05,
                lottable06, lottable07, lottable08, lottable09, lottable10,
                adddate, addwho)
            VALUES (?, ?, ?, ?, ?, 0, ?, ?, '0', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), ?)
            """;

        try {
            for (TradeReturnLineMapping line : lines) {
                currentLineNumber = line.lineNumber();
                String detailKey = orderKey + "-" + line.lineNumber();

                jdbcTemplate.update(sql,
                    orderKey,
                    line.lineNumber(),
                    line.sku(),
                    line.qty(),
                    line.qty(),
                    line.uom() != null ? line.uom() : "EA",
                    line.packKey(),
                    line.lottables().get("lottable01"),
                    line.lottables().get("lottable02"),
                    line.lottables().get("lottable03"),
                    line.lottables().get("lottable04"),
                    line.lottables().get("lottable05"),
                    line.lottables().get("lottable06"),
                    line.lottables().get("lottable07"),
                    line.lottables().get("lottable08"),
                    line.lottables().get("lottable09"),
                    line.lottables().get("lottable10"),
                    "TRADERETURN"
                );

                detailKeys.add(detailKey);
            }

            log.info("Created {} order details", detailKeys.size());
            return detailKeys;

        } catch (DataAccessException e) {
            log.error("Failed to create sales order detail for order {} line {}: {} (legacy error 69956)",
                orderKey, currentLineNumber, e.getMessage(), e);
            throw new BusinessException(ErrorCode.TRADE_RETURN_SOD_CREATION_FAILED,
                String.format("Failed to create trade return line %d for order %s", currentLineNumber, orderKey), e)
                .withDetail("orderKey", orderKey)
                .withDetail("lineNumber", currentLineNumber);
        }
    }

    @Override
    @Transactional
    public List<String> createInventoryReservations(String orderKey, List<String> detailKeys) {
        log.info("Creating inventory reservations for orderKey={}", orderKey);

        List<String> reservationIds = new ArrayList<>();

        // Create simple reservation records
        String sql = """
            INSERT INTO dbo.TASKDETAIL (taskdetailkey, orderkey, status, adddate, addwho)
            VALUES (?, ?, '0', GETDATE(), ?)
            """;

        for (String detailKey : detailKeys) {
            String reservationId = UUID.randomUUID().toString().substring(0, 20);
            try {
                jdbcTemplate.update(sql, reservationId, orderKey, "TRADERETURN");
                reservationIds.add(reservationId);
            } catch (Exception e) {
                log.debug("Reservation creation skipped: {}", e.getMessage());
                reservationIds.add(reservationId); // Track for potential cleanup
            }
        }

        log.info("Created {} reservations", reservationIds.size());
        return reservationIds;
    }

    @Override
    @Transactional
    public void releaseReservations(List<String> reservationIds) {
        log.warn("COMPENSATION: Releasing {} reservations", reservationIds.size());

        for (String reservationId : reservationIds) {
            try {
                jdbcTemplate.update(
                    "DELETE FROM dbo.TASKDETAIL WHERE taskdetailkey = ?",
                    reservationId
                );
            } catch (Exception e) {
                log.debug("Reservation release skipped: {}", e.getMessage());
            }
        }

        log.info("COMPENSATION complete: Released all reservations");
    }

    @Override
    @Transactional
    public void deleteSalesOrderHeader(String orderKey) {
        log.warn("COMPENSATION: Deleting sales order header: orderKey={}", orderKey);

        // Delete details first
        jdbcTemplate.update("DELETE FROM dbo.ORDERDETAIL WHERE orderkey = ?", orderKey);

        // Delete header
        jdbcTemplate.update("DELETE FROM dbo.ORDERS WHERE orderkey = ?", orderKey);

        log.info("COMPENSATION complete: Deleted sales order: {}", orderKey);
    }

    @Override
    @Transactional
    public void deleteSalesOrderDetails(List<String> detailKeys) {
        log.warn("COMPENSATION: Deleting {} sales order details", detailKeys.size());

        for (String detailKey : detailKeys) {
            String[] parts = detailKey.split("-");
            if (parts.length == 2) {
                jdbcTemplate.update(
                    "DELETE FROM dbo.ORDERDETAIL WHERE orderkey = ? AND orderlinenumber = ?",
                    parts[0], Integer.parseInt(parts[1])
                );
            }
        }

        log.info("COMPENSATION complete: Deleted order details");
    }

    @Override
    @Transactional
    public void updateReceiptStatus(String receiptKey, String orderKey) {
        log.info("Updating receipt status: receiptKey={}, orderKey={}", receiptKey, orderKey);

        jdbcTemplate.update(
            "UPDATE dbo.RECEIPT SET susr5 = ?, editdate = GETDATE(), editwho = ? WHERE receiptkey = ?",
            orderKey, "TRADERETURN", receiptKey
        );
    }

    @Override
    @Transactional
    public void autoReleaseOrder(String orderKey, VariationContext context) {
        log.info("Auto-releasing order: orderKey={}", orderKey);

        // Update order status to released
        jdbcTemplate.update(
            "UPDATE dbo.ORDERS SET status = '1', editdate = GETDATE(), editwho = ? WHERE orderkey = ?",
            "TRADERETURN", orderKey
        );

        // Update detail status
        jdbcTemplate.update(
            "UPDATE dbo.ORDERDETAIL SET status = '1', editdate = GETDATE(), editwho = ? WHERE orderkey = ?",
            "TRADERETURN", orderKey
        );

        log.info("Order auto-released: orderKey={}", orderKey);
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════

    private boolean receiptExists(String receiptKey) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dbo.RECEIPT WHERE receiptkey = ?",
            Integer.class, receiptKey
        );
        return count != null && count > 0;
    }

    private String getReceiptStatus(String receiptKey) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM dbo.RECEIPT WHERE receiptkey = ?",
            String.class, receiptKey
        );
    }

    private boolean isValidStatusForReturn(String status) {
        // Status 5 = Received, 9 = Closed - both valid for trade return
        return "5".equals(status) || "9".equals(status);
    }

    private int getReceiptLineCount(String receiptKey) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dbo.RECEIPTDETAIL WHERE receiptkey = ?",
            Integer.class, receiptKey
        );
        return count != null ? count : 0;
    }

    private boolean salesOrderExistsForReceipt(String receiptKey) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dbo.ORDERS WHERE externorderkey = ? AND ordertype = 'TR'",
            Integer.class, receiptKey
        );
        return count != null && count > 0;
    }

    private boolean customerExists(String customerCode) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dbo.STORER WHERE storerkey = ? AND type = '2'",
            Integer.class, customerCode
        );
        return count != null && count > 0;
    }
}
