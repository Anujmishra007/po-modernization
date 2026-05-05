package com.wms.po.activity.impl;

import com.wms.po.activity.*;
import com.wms.po.domain.dto.DetailMapping;
import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.LottableResult;
import com.wms.po.domain.model.MappingResult;
import com.wms.po.domain.model.PluginResult;
import com.wms.po.domain.model.PopulateRequest;
import com.wms.po.domain.model.TradeReturnRequest;
import com.wms.po.domain.model.ValidationResult;
import com.wms.po.domain.model.VariationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Combined activities implementation that delegates to individual activity implementations.
 * This class implements all activity interfaces to provide a single bean for Temporal registration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PopulatePOActivitiesImpl implements PopulatePOActivities {

    private final ValidationActivity validationActivity;
    private final MappingActivity mappingActivity;
    private final PersistenceActivity persistenceActivity;
    private final InventoryActivity inventoryActivity;
    private final LegacyBridgeActivity legacyBridgeActivity;
    private final NotificationActivity notificationActivity;
    private final PluginActivity pluginActivity;

    // ValidationActivity methods
    @Override
    public VariationContext resolveContext(PopulateRequest request) {
        return validationActivity.resolveContext(request);
    }

    @Override
    public ValidationResult validate(PopulateRequest request, VariationContext context) {
        return validationActivity.validate(request, context);
    }

    // MappingActivity methods
    @Override
    public MappingResult mapPOToASN(PopulateRequest request, VariationContext context) {
        return mappingActivity.mapPOToASN(request, context);
    }

    @Override
    public LottableResult applyLottables(MappingResult mapping, VariationContext context) {
        return mappingActivity.applyLottables(mapping, context);
    }

    // PersistenceActivity methods
    @Override
    public String createReceiptHeader(MappingResult mapping) {
        return persistenceActivity.createReceiptHeader(mapping);
    }

    @Override
    public void deleteReceiptHeader(String receiptKey) {
        persistenceActivity.deleteReceiptHeader(receiptKey);
    }

    @Override
    public List<String> createReceiptDetails(String receiptKey, List<DetailMapping> details) {
        return persistenceActivity.createReceiptDetails(receiptKey, details);
    }

    @Override
    public void deleteReceiptDetails(List<String> detailKeys) {
        persistenceActivity.deleteReceiptDetails(detailKeys);
    }

    @Override
    public void updateReceiptStatus(String receiptKey, String status) {
        persistenceActivity.updateReceiptStatus(receiptKey, status);
    }

    // InventoryActivity methods
    @Override
    public List<String> createReservations(String receiptKey, List<String> detailKeys) {
        return inventoryActivity.createReservations(receiptKey, detailKeys);
    }

    @Override
    public void releaseReservations(List<String> reservationIds) {
        inventoryActivity.releaseReservations(reservationIds);
    }

    @Override
    public void preAllocateInventory(String receiptKey, List<String> detailKeys) {
        inventoryActivity.preAllocateInventory(receiptKey, detailKeys);
    }

    @Override
    public void releasePreAllocation(String receiptKey) {
        inventoryActivity.releasePreAllocation(receiptKey);
    }

    // LegacyBridgeActivity methods
    @Override
    public void syncToLegacy(String receiptKey, VariationContext context) {
        legacyBridgeActivity.syncToLegacy(receiptKey, context);
    }

    @Override
    public void rollbackLegacy(String receiptKey, VariationContext context) {
        legacyBridgeActivity.rollbackLegacy(receiptKey, context);
    }

    @Override
    public boolean verifyLegacySync(String receiptKey, VariationContext context) {
        return legacyBridgeActivity.verifyLegacySync(receiptKey, context);
    }

    // NotificationActivity methods - Population
    @Override
    public void sendPopulationComplete(String receiptKey, PopulateRequest request) {
        notificationActivity.sendPopulationComplete(receiptKey, request);
    }

    @Override
    public void sendPopulationFailed(String receiptKey, String reason, PopulateRequest request) {
        notificationActivity.sendPopulationFailed(receiptKey, reason, request);
    }

    @Override
    public void sendPopulationCancelled(String receiptKey, PopulateRequest request) {
        notificationActivity.sendPopulationCancelled(receiptKey, request);
    }

    // NotificationActivity methods - Finalization
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

    // PluginActivity methods
    @Override
    public PluginResult runPrePopulate(PopulateRequest request, VariationContext context) {
        return pluginActivity.runPrePopulate(request, context);
    }

    @Override
    public PluginResult runPostPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
        return pluginActivity.runPostPopulate(receiptKey, request, context);
    }

    // Trade Return methods
    @Override
    public void sendTradeReturnComplete(String orderKey, String receiptKey) {
        notificationActivity.sendTradeReturnComplete(orderKey, receiptKey);
    }

    @Override
    public void sendTradeReturnFailed(String receiptKey, String errorMessage) {
        notificationActivity.sendTradeReturnFailed(receiptKey, errorMessage);
    }

    @Override
    public VariationContext resolveTradeReturnContext(TradeReturnRequest request) {
        return validationActivity.resolveTradeReturnContext(request);
    }
}
