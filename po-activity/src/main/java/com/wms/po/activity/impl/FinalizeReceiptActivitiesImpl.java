package com.wms.po.activity.impl;

import com.wms.po.activity.*;
import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.TradeReturnRequest;
import com.wms.po.domain.model.ValidationResult;
import com.wms.po.domain.model.VariationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Combined implementation of all finalization activities.
 * Delegates to individual activity implementations.
 *
 * This class is registered with the Temporal worker to provide
 * all finalization-related activity implementations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FinalizeReceiptActivitiesImpl implements FinalizeReceiptActivities {

    private final ValidationActivityImpl validationActivity;
    private final ReceiptStatusActivityImpl receiptStatusActivity;
    private final InventoryPostingActivityImpl inventoryPostingActivity;
    private final InventoryHoldActivityImpl inventoryHoldActivity;
    private final POQuantityActivityImpl poQuantityActivity;
    private final PutawayReleaseActivityImpl putawayReleaseActivity;
    private final FinalizePluginActivityImpl finalizePluginActivity;
    private final NotificationActivityImpl notificationActivity;

    // ═══════════════════════════════════════════════════════════════
    // ValidationActivity delegations
    // ═══════════════════════════════════════════════════════════════

    @Override
    public VariationContext resolveContext(PopulateRequest request) {
        return validationActivity.resolveContext(request);
    }

    @Override
    public ValidationResult validate(PopulateRequest request, VariationContext context) {
        return validationActivity.validate(request, context);
    }

    // ═══════════════════════════════════════════════════════════════
    // ReceiptStatusActivity delegations
    // ═══════════════════════════════════════════════════════════════

    @Override
    public String validateForFinalization(String receiptKey) {
        return receiptStatusActivity.validateForFinalization(receiptKey);
    }

    @Override
    public String setStatusFinalizing(String receiptKey, String userId) {
        return receiptStatusActivity.setStatusFinalizing(receiptKey, userId);
    }

    @Override
    public void setStatusFinalized(String receiptKey, String userId) {
        receiptStatusActivity.setStatusFinalized(receiptKey, userId);
    }

    @Override
    public void revertStatus(String receiptKey, String originalStatus, String userId) {
        receiptStatusActivity.revertStatus(receiptKey, originalStatus, userId);
    }

    @Override
    public void closeReceipt(String receiptKey, String userId) {
        receiptStatusActivity.closeReceipt(receiptKey, userId);
    }

    // ═══════════════════════════════════════════════════════════════
    // InventoryPostingActivity delegations
    // ═══════════════════════════════════════════════════════════════

    @Override
    public InventoryPostingActivity.PostingResult postInventory(InventoryPostingActivity.PostingRequest request) {
        return inventoryPostingActivity.postInventory(request);
    }

    @Override
    public void deleteInventory(List<String> inventoryIds) {
        inventoryPostingActivity.deleteInventory(inventoryIds);
    }

    @Override
    public void adjustInventory(String inventoryId, BigDecimal adjustment, String reason, String userId) {
        inventoryPostingActivity.adjustInventory(inventoryId, adjustment, reason, userId);
    }

    // ═══════════════════════════════════════════════════════════════
    // InventoryHoldActivity delegations
    // ═══════════════════════════════════════════════════════════════

    @Override
    public List<InventoryHoldActivity.HoldToApply> evaluateHolds(InventoryHoldActivity.HoldEvaluationRequest request) {
        return inventoryHoldActivity.evaluateHolds(request);
    }

    @Override
    public InventoryHoldActivity.HoldResult applyHolds(InventoryHoldActivity.HoldApplicationRequest request) {
        return inventoryHoldActivity.applyHolds(request);
    }

    @Override
    public void removeHolds(List<String> holdIds, String reason) {
        inventoryHoldActivity.removeHolds(holdIds, reason);
    }

    // ═══════════════════════════════════════════════════════════════
    // POQuantityActivity delegations
    // ═══════════════════════════════════════════════════════════════

    @Override
    public POQuantityActivity.UpdateResult updateReceivedQuantities(POQuantityActivity.UpdateRequest request) {
        return poQuantityActivity.updateReceivedQuantities(request);
    }

    @Override
    public void revertReceivedQuantities(List<POQuantityActivity.LineUpdate> updates) {
        poQuantityActivity.revertReceivedQuantities(updates);
    }

    @Override
    public boolean isFullyReceived(String poKey) {
        return poQuantityActivity.isFullyReceived(poKey);
    }

    @Override
    public void closePO(String poKey, String userId) {
        poQuantityActivity.closePO(poKey, userId);
    }

    // ═══════════════════════════════════════════════════════════════
    // PutawayReleaseActivity delegations
    // ═══════════════════════════════════════════════════════════════

    @Override
    public PutawayReleaseActivity.ReleaseResult releasePutawayTasks(PutawayReleaseActivity.ReleaseRequest request) {
        return putawayReleaseActivity.releasePutawayTasks(request);
    }

    @Override
    public void cancelPutawayTasks(List<String> taskIds, String reason) {
        putawayReleaseActivity.cancelPutawayTasks(taskIds, reason);
    }

    // ═══════════════════════════════════════════════════════════════
    // FinalizePluginActivity delegations
    // ═══════════════════════════════════════════════════════════════

    @Override
    public PluginResult runPreFinalizePlugins(FinalizeRequest request, VariationContext context) {
        return finalizePluginActivity.runPreFinalizePlugins(request, context);
    }

    @Override
    public FinalizePluginActivity.PluginSummary runPostFinalizePlugins(
            String receiptKey,
            FinalizeRequest request,
            VariationContext context,
            Map<String, Object> finalizationData) {
        return finalizePluginActivity.runPostFinalizePlugins(receiptKey, request, context, finalizationData);
    }

    @Override
    public void rollbackPreFinalizePlugins(String receiptKey, List<String> pluginResults) {
        finalizePluginActivity.rollbackPreFinalizePlugins(receiptKey, pluginResults);
    }

    // ═══════════════════════════════════════════════════════════════
    // NotificationActivity delegations
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void sendPopulationComplete(String receiptKey, PopulateRequest request) {
        notificationActivity.sendPopulationComplete(receiptKey, request);
    }

    @Override
    public void sendPopulationFailed(String receiptKey, String errorMessage, PopulateRequest request) {
        notificationActivity.sendPopulationFailed(receiptKey, errorMessage, request);
    }

    @Override
    public void sendPopulationCancelled(String receiptKey, PopulateRequest request) {
        notificationActivity.sendPopulationCancelled(receiptKey, request);
    }

    @Override
    public void sendFinalizeComplete(String receiptKey, FinalizeRequest request) {
        notificationActivity.sendFinalizeComplete(receiptKey, request);
    }

    @Override
    public void sendFinalizeFailed(String receiptKey, String errorMessage, FinalizeRequest request) {
        notificationActivity.sendFinalizeFailed(receiptKey, errorMessage, request);
    }

    @Override
    public void sendFinalizeCancelled(String receiptKey, FinalizeRequest request) {
        notificationActivity.sendFinalizeCancelled(receiptKey, request);
    }

    @Override
    public void sendTradeReturnComplete(String orderKey, String receiptKey) {
        notificationActivity.sendTradeReturnComplete(orderKey, receiptKey);
    }

    @Override
    public void sendTradeReturnFailed(String receiptKey, String errorMessage) {
        notificationActivity.sendTradeReturnFailed(receiptKey, errorMessage);
    }

    // ═══════════════════════════════════════════════════════════════
    // ValidationActivity - Trade Return delegation
    // ═══════════════════════════════════════════════════════════════

    @Override
    public VariationContext resolveTradeReturnContext(TradeReturnRequest request) {
        return validationActivity.resolveTradeReturnContext(request);
    }
}
