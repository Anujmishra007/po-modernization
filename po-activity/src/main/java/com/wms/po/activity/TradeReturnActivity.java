package com.wms.po.activity;

import com.wms.po.domain.model.TradeReturnRequest;
import com.wms.po.domain.model.VariationContext;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Activity interface for Trade Return operations.
 *
 * Replaces:
 * - SP-004: WM.lsp_ASN_PopulateSOs_Wrapper (Sales Order header creation)
 * - SP-005: WM.lsp_ASN_PopulateSODs_Wrapper (Sales Order detail creation)
 */
@ActivityInterface
public interface TradeReturnActivity {

    /**
     * Validate the trade return request.
     * Checks receipt exists, is valid for return, lines match, etc.
     *
     * @param request Trade return request
     * @param context Variation context
     * @return Validation result with errors/warnings
     */
    @ActivityMethod
    TradeReturnValidationResult validateTradeReturn(TradeReturnRequest request, VariationContext context);

    /**
     * Map receipt data to sales order format.
     * Transforms ASN/receipt lines to SO lines.
     *
     * @param request Trade return request
     * @param context Variation context
     * @return Mapped data for SO creation
     */
    @ActivityMethod
    TradeReturnMappingResult mapReceiptToSalesOrder(TradeReturnRequest request, VariationContext context);

    /**
     * Create the Sales Order header.
     *
     * @param mapping Mapped SO data
     * @return Created order key
     */
    @ActivityMethod
    String createSalesOrderHeader(TradeReturnMappingResult mapping);

    /**
     * Create Sales Order detail lines.
     *
     * @param orderKey Order key to attach details to
     * @param lines Detail lines to create
     * @return List of created detail keys
     */
    @ActivityMethod
    List<String> createSalesOrderDetails(String orderKey, List<TradeReturnLineMapping> lines);

    /**
     * Reserve inventory for the return.
     *
     * @param orderKey Order key
     * @param detailKeys Detail keys
     * @return Reservation IDs
     */
    @ActivityMethod
    List<String> createInventoryReservations(String orderKey, List<String> detailKeys);

    /**
     * Release reservations on compensation.
     *
     * @param reservationIds Reservation IDs to release
     */
    @ActivityMethod
    void releaseReservations(List<String> reservationIds);

    /**
     * Delete SO header on compensation.
     *
     * @param orderKey Order key to delete
     */
    @ActivityMethod
    void deleteSalesOrderHeader(String orderKey);

    /**
     * Delete SO details on compensation.
     *
     * @param detailKeys Detail keys to delete
     */
    @ActivityMethod
    void deleteSalesOrderDetails(List<String> detailKeys);

    /**
     * Update receipt status to indicate SO created.
     *
     * @param receiptKey Receipt key
     * @param orderKey Created order key
     */
    @ActivityMethod
    void updateReceiptStatus(String receiptKey, String orderKey);

    /**
     * Auto-release the order for outbound processing.
     *
     * @param orderKey Order key to release
     * @param context Variation context
     */
    @ActivityMethod
    void autoReleaseOrder(String orderKey, VariationContext context);

    /**
     * Validation result for trade return.
     */
    record TradeReturnValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        Map<String, Object> metadata
    ) {
        public static TradeReturnValidationResult success() {
            return new TradeReturnValidationResult(true, List.of(), List.of(), Map.of());
        }

        public static TradeReturnValidationResult failure(List<String> errors) {
            return new TradeReturnValidationResult(false, errors, List.of(), Map.of());
        }
    }

    /**
     * Mapping result containing transformed SO data.
     */
    record TradeReturnMappingResult(
        String receiptKey,
        String storerKey,
        String customerCode,
        String orderType,
        String carrierCode,
        String priority,
        List<TradeReturnLineMapping> lines,
        BigDecimal totalQty,
        Map<String, String> customFields
    ) {}

    /**
     * Individual line mapping for SO detail.
     */
    record TradeReturnLineMapping(
        int lineNumber,
        String sku,
        BigDecimal qty,
        String uom,
        String packKey,
        String lot,
        String loc,
        String id,
        String reasonCode,
        String conditionCode,
        Map<String, String> lottables
    ) {}
}
