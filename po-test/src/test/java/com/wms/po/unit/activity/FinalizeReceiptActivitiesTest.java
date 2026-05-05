package com.wms.po.unit.activity;

import com.wms.po.activity.InventoryPostingActivity;
import com.wms.po.activity.impl.*;
import com.wms.po.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FinalizeReceiptActivitiesImpl Tests")
class FinalizeReceiptActivitiesTest {

    @Mock
    private ValidationActivityImpl validationActivity;
    @Mock
    private ReceiptStatusActivityImpl receiptStatusActivity;
    @Mock
    private InventoryPostingActivityImpl inventoryPostingActivity;
    @Mock
    private InventoryHoldActivityImpl inventoryHoldActivity;
    @Mock
    private POQuantityActivityImpl poQuantityActivity;
    @Mock
    private PutawayReleaseActivityImpl putawayReleaseActivity;
    @Mock
    private FinalizePluginActivityImpl finalizePluginActivity;
    @Mock
    private NotificationActivityImpl notificationActivity;

    private FinalizeReceiptActivitiesImpl activities;

    @BeforeEach
    void setUp() {
        activities = new FinalizeReceiptActivitiesImpl(
            validationActivity,
            receiptStatusActivity,
            inventoryPostingActivity,
            inventoryHoldActivity,
            poQuantityActivity,
            putawayReleaseActivity,
            finalizePluginActivity,
            notificationActivity
        );
    }

    @Nested
    @DisplayName("ValidationActivity Delegation Tests")
    class ValidationActivityDelegationTests {

        @Test
        @DisplayName("resolveContext delegates to validationActivity")
        void resolveContextDelegatesToValidationActivity() {
            PopulateRequest request = createPopulateRequest();
            VariationContext expectedContext = createVariationContext();
            when(validationActivity.resolveContext(request)).thenReturn(expectedContext);

            VariationContext result = activities.resolveContext(request);

            assertThat(result).isEqualTo(expectedContext);
            verify(validationActivity).resolveContext(request);
        }

        @Test
        @DisplayName("validate delegates to validationActivity")
        void validateDelegatesToValidationActivity() {
            PopulateRequest request = createPopulateRequest();
            VariationContext context = createVariationContext();
            ValidationResult expectedResult = ValidationResult.success();
            when(validationActivity.validate(request, context)).thenReturn(expectedResult);

            ValidationResult result = activities.validate(request, context);

            assertThat(result).isEqualTo(expectedResult);
            verify(validationActivity).validate(request, context);
        }

        @Test
        @DisplayName("resolveTradeReturnContext delegates to validationActivity")
        void resolveTradeReturnContextDelegatesToValidationActivity() {
            TradeReturnRequest request = createTradeReturnRequest();
            VariationContext expectedContext = createVariationContext();
            when(validationActivity.resolveTradeReturnContext(request)).thenReturn(expectedContext);

            VariationContext result = activities.resolveTradeReturnContext(request);

            assertThat(result).isEqualTo(expectedContext);
            verify(validationActivity).resolveTradeReturnContext(request);
        }
    }

    @Nested
    @DisplayName("ReceiptStatusActivity Delegation Tests")
    class ReceiptStatusActivityDelegationTests {

        @Test
        @DisplayName("validateForFinalization delegates correctly")
        void validateForFinalizationDelegates() {
            when(receiptStatusActivity.validateForFinalization("RCV-001")).thenReturn("0");

            String result = activities.validateForFinalization("RCV-001");

            assertThat(result).isEqualTo("0");
            verify(receiptStatusActivity).validateForFinalization("RCV-001");
        }

        @Test
        @DisplayName("setStatusFinalizing delegates correctly")
        void setStatusFinalizingDelegates() {
            when(receiptStatusActivity.setStatusFinalizing("RCV-001", "user1")).thenReturn("0");

            String result = activities.setStatusFinalizing("RCV-001", "user1");

            assertThat(result).isEqualTo("0");
            verify(receiptStatusActivity).setStatusFinalizing("RCV-001", "user1");
        }

        @Test
        @DisplayName("setStatusFinalized delegates correctly")
        void setStatusFinalizedDelegates() {
            activities.setStatusFinalized("RCV-001", "user1");

            verify(receiptStatusActivity).setStatusFinalized("RCV-001", "user1");
        }

        @Test
        @DisplayName("revertStatus delegates correctly")
        void revertStatusDelegates() {
            activities.revertStatus("RCV-001", "0", "user1");

            verify(receiptStatusActivity).revertStatus("RCV-001", "0", "user1");
        }

        @Test
        @DisplayName("closeReceipt delegates correctly")
        void closeReceiptDelegates() {
            activities.closeReceipt("RCV-001", "user1");

            verify(receiptStatusActivity).closeReceipt("RCV-001", "user1");
        }
    }

    @Nested
    @DisplayName("InventoryPostingActivity Delegation Tests")
    class InventoryPostingActivityDelegationTests {

        @Test
        @DisplayName("postInventory delegates correctly")
        void postInventoryDelegates() {
            InventoryPostingActivity.PostingRequest request = mock(InventoryPostingActivity.PostingRequest.class);
            InventoryPostingActivity.PostingResult expectedResult = mock(InventoryPostingActivity.PostingResult.class);
            when(inventoryPostingActivity.postInventory(request)).thenReturn(expectedResult);

            InventoryPostingActivity.PostingResult result = activities.postInventory(request);

            assertThat(result).isEqualTo(expectedResult);
            verify(inventoryPostingActivity).postInventory(request);
        }

        @Test
        @DisplayName("deleteInventory delegates correctly")
        void deleteInventoryDelegates() {
            List<String> inventoryIds = List.of("INV-001", "INV-002");

            activities.deleteInventory(inventoryIds);

            verify(inventoryPostingActivity).deleteInventory(inventoryIds);
        }

        @Test
        @DisplayName("adjustInventory delegates correctly")
        void adjustInventoryDelegates() {
            activities.adjustInventory("INV-001", BigDecimal.TEN, "adjustment", "user1");

            verify(inventoryPostingActivity).adjustInventory("INV-001", BigDecimal.TEN, "adjustment", "user1");
        }
    }

    @Nested
    @DisplayName("NotificationActivity Delegation Tests")
    class NotificationActivityDelegationTests {

        @Test
        @DisplayName("sendPopulationComplete delegates correctly")
        void sendPopulationCompleteDelegates() {
            PopulateRequest request = createPopulateRequest();

            activities.sendPopulationComplete("RCV-001", request);

            verify(notificationActivity).sendPopulationComplete("RCV-001", request);
        }

        @Test
        @DisplayName("sendFinalizeComplete delegates correctly")
        void sendFinalizeCompleteDelegates() {
            FinalizeRequest request = createFinalizeRequest();

            activities.sendFinalizeComplete("RCV-001", request);

            verify(notificationActivity).sendFinalizeComplete("RCV-001", request);
        }

        @Test
        @DisplayName("sendTradeReturnComplete delegates correctly")
        void sendTradeReturnCompleteDelegates() {
            activities.sendTradeReturnComplete("ORD-001", "RCV-001");

            verify(notificationActivity).sendTradeReturnComplete("ORD-001", "RCV-001");
        }

        @Test
        @DisplayName("sendTradeReturnFailed delegates correctly")
        void sendTradeReturnFailedDelegates() {
            activities.sendTradeReturnFailed("RCV-001", "Test error");

            verify(notificationActivity).sendTradeReturnFailed("RCV-001", "Test error");
        }
    }

    @Nested
    @DisplayName("FinalizePluginActivity Delegation Tests")
    class FinalizePluginActivityDelegationTests {

        @Test
        @DisplayName("runPreFinalizePlugins delegates correctly")
        void runPreFinalizePluginsDelegates() {
            FinalizeRequest request = createFinalizeRequest();
            VariationContext context = createVariationContext();
            PluginResult expectedResult = PluginResult.success();
            when(finalizePluginActivity.runPreFinalizePlugins(request, context)).thenReturn(expectedResult);

            PluginResult result = activities.runPreFinalizePlugins(request, context);

            assertThat(result).isEqualTo(expectedResult);
            verify(finalizePluginActivity).runPreFinalizePlugins(request, context);
        }
    }

    // Helper methods
    private PopulateRequest createPopulateRequest() {
        return PopulateRequest.builder()
            .poKeys(List.of("PO-001"))
            .storerKey("TEST_STORER")
            .facility("KR01")
            .userId("test_user")
            .build();
    }

    private FinalizeRequest createFinalizeRequest() {
        return FinalizeRequest.builder()
            .receiptKey("RCV-001")
            .storerKey("TEST_STORER")
            .facility("KR01")
            .userId("test_user")
            .build();
    }

    private TradeReturnRequest createTradeReturnRequest() {
        return TradeReturnRequest.builder()
            .receiptKey("RCV-001")
            .storerKey("TEST_STORER")
            .countryCode("KR")
            .facility("KR01")
            .userId("test_user")
            .build();
    }

    private VariationContext createVariationContext() {
        return VariationContext.builder()
            .version("V2")
            .region("ASIA-KR")
            .client("STANDARD")
            .facility("KR01")
            .storerKey("TEST_STORER")
            .dualWriteEnabled(false)
            .build();
    }
}
