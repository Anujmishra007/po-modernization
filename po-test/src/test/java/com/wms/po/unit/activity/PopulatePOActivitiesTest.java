package com.wms.po.unit.activity;

import com.wms.po.activity.*;
import com.wms.po.activity.impl.*;
import com.wms.po.domain.dto.DetailMapping;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PopulatePOActivitiesImpl Tests")
class PopulatePOActivitiesTest {

    @Mock
    private ValidationActivity validationActivity;
    @Mock
    private MappingActivity mappingActivity;
    @Mock
    private PersistenceActivity persistenceActivity;
    @Mock
    private InventoryActivity inventoryActivity;
    @Mock
    private LegacyBridgeActivity legacyBridgeActivity;
    @Mock
    private NotificationActivity notificationActivity;
    @Mock
    private PluginActivity pluginActivity;

    private PopulatePOActivitiesImpl activities;

    @BeforeEach
    void setUp() {
        activities = new PopulatePOActivitiesImpl(
            validationActivity,
            mappingActivity,
            persistenceActivity,
            inventoryActivity,
            legacyBridgeActivity,
            notificationActivity,
            pluginActivity
        );
    }

    @Nested
    @DisplayName("ValidationActivity Delegation Tests")
    class ValidationDelegationTests {

        @Test
        @DisplayName("resolveContext delegates correctly")
        void resolveContextDelegates() {
            PopulateRequest request = createPopulateRequest();
            VariationContext expectedContext = createVariationContext();
            when(validationActivity.resolveContext(request)).thenReturn(expectedContext);

            VariationContext result = activities.resolveContext(request);

            assertThat(result).isEqualTo(expectedContext);
            verify(validationActivity).resolveContext(request);
        }

        @Test
        @DisplayName("validate delegates correctly")
        void validateDelegates() {
            PopulateRequest request = createPopulateRequest();
            VariationContext context = createVariationContext();
            ValidationResult expectedResult = ValidationResult.success();
            when(validationActivity.validate(request, context)).thenReturn(expectedResult);

            ValidationResult result = activities.validate(request, context);

            assertThat(result).isEqualTo(expectedResult);
            verify(validationActivity).validate(request, context);
        }

        @Test
        @DisplayName("resolveTradeReturnContext delegates correctly")
        void resolveTradeReturnContextDelegates() {
            TradeReturnRequest request = createTradeReturnRequest();
            VariationContext expectedContext = createVariationContext();
            when(validationActivity.resolveTradeReturnContext(request)).thenReturn(expectedContext);

            VariationContext result = activities.resolveTradeReturnContext(request);

            assertThat(result).isEqualTo(expectedContext);
            verify(validationActivity).resolveTradeReturnContext(request);
        }
    }

    @Nested
    @DisplayName("MappingActivity Delegation Tests")
    class MappingDelegationTests {

        @Test
        @DisplayName("mapPOToASN delegates correctly")
        void mapPOToASNDelegates() {
            PopulateRequest request = createPopulateRequest();
            VariationContext context = createVariationContext();
            MappingResult expectedResult = createMappingResult();
            when(mappingActivity.mapPOToASN(request, context)).thenReturn(expectedResult);

            MappingResult result = activities.mapPOToASN(request, context);

            assertThat(result).isEqualTo(expectedResult);
            verify(mappingActivity).mapPOToASN(request, context);
        }

        @Test
        @DisplayName("applyLottables delegates correctly")
        void applyLottablesDelegates() {
            MappingResult mapping = createMappingResult();
            VariationContext context = createVariationContext();
            LottableResult expectedResult = LottableResult.builder().success(true).build();
            when(mappingActivity.applyLottables(mapping, context)).thenReturn(expectedResult);

            LottableResult result = activities.applyLottables(mapping, context);

            assertThat(result).isEqualTo(expectedResult);
            verify(mappingActivity).applyLottables(mapping, context);
        }
    }

    @Nested
    @DisplayName("PersistenceActivity Delegation Tests")
    class PersistenceDelegationTests {

        @Test
        @DisplayName("createReceiptHeader delegates correctly")
        void createReceiptHeaderDelegates() {
            MappingResult mapping = createMappingResult();
            when(persistenceActivity.createReceiptHeader(mapping)).thenReturn("RCV-001");

            String result = activities.createReceiptHeader(mapping);

            assertThat(result).isEqualTo("RCV-001");
            verify(persistenceActivity).createReceiptHeader(mapping);
        }

        @Test
        @DisplayName("deleteReceiptHeader delegates correctly")
        void deleteReceiptHeaderDelegates() {
            activities.deleteReceiptHeader("RCV-001");

            verify(persistenceActivity).deleteReceiptHeader("RCV-001");
        }

        @Test
        @DisplayName("createReceiptDetails delegates correctly")
        void createReceiptDetailsDelegates() {
            List<DetailMapping> details = List.of(createDetailMapping());
            when(persistenceActivity.createReceiptDetails("RCV-001", details))
                .thenReturn(List.of("DTL-001"));

            List<String> result = activities.createReceiptDetails("RCV-001", details);

            assertThat(result).containsExactly("DTL-001");
            verify(persistenceActivity).createReceiptDetails("RCV-001", details);
        }

        @Test
        @DisplayName("deleteReceiptDetails delegates correctly")
        void deleteReceiptDetailsDelegates() {
            List<String> detailKeys = List.of("DTL-001", "DTL-002");

            activities.deleteReceiptDetails(detailKeys);

            verify(persistenceActivity).deleteReceiptDetails(detailKeys);
        }

        @Test
        @DisplayName("updateReceiptStatus delegates correctly")
        void updateReceiptStatusDelegates() {
            activities.updateReceiptStatus("RCV-001", "5");

            verify(persistenceActivity).updateReceiptStatus("RCV-001", "5");
        }
    }

    @Nested
    @DisplayName("InventoryActivity Delegation Tests")
    class InventoryDelegationTests {

        @Test
        @DisplayName("createReservations delegates correctly")
        void createReservationsDelegates() {
            List<String> detailKeys = List.of("DTL-001");
            when(inventoryActivity.createReservations("RCV-001", detailKeys))
                .thenReturn(List.of("RES-001"));

            List<String> result = activities.createReservations("RCV-001", detailKeys);

            assertThat(result).containsExactly("RES-001");
            verify(inventoryActivity).createReservations("RCV-001", detailKeys);
        }

        @Test
        @DisplayName("releaseReservations delegates correctly")
        void releaseReservationsDelegates() {
            List<String> reservationIds = List.of("RES-001");

            activities.releaseReservations(reservationIds);

            verify(inventoryActivity).releaseReservations(reservationIds);
        }

        @Test
        @DisplayName("preAllocateInventory delegates correctly")
        void preAllocateInventoryDelegates() {
            List<String> detailKeys = List.of("DTL-001");

            activities.preAllocateInventory("RCV-001", detailKeys);

            verify(inventoryActivity).preAllocateInventory("RCV-001", detailKeys);
        }

        @Test
        @DisplayName("releasePreAllocation delegates correctly")
        void releasePreAllocationDelegates() {
            activities.releasePreAllocation("RCV-001");

            verify(inventoryActivity).releasePreAllocation("RCV-001");
        }
    }

    @Nested
    @DisplayName("LegacyBridgeActivity Delegation Tests")
    class LegacyBridgeDelegationTests {

        @Test
        @DisplayName("syncToLegacy delegates correctly")
        void syncToLegacyDelegates() {
            VariationContext context = createVariationContext();

            activities.syncToLegacy("RCV-001", context);

            verify(legacyBridgeActivity).syncToLegacy("RCV-001", context);
        }

        @Test
        @DisplayName("rollbackLegacy delegates correctly")
        void rollbackLegacyDelegates() {
            VariationContext context = createVariationContext();

            activities.rollbackLegacy("RCV-001", context);

            verify(legacyBridgeActivity).rollbackLegacy("RCV-001", context);
        }

        @Test
        @DisplayName("verifyLegacySync delegates correctly")
        void verifyLegacySyncDelegates() {
            VariationContext context = createVariationContext();
            when(legacyBridgeActivity.verifyLegacySync("RCV-001", context)).thenReturn(true);

            boolean result = activities.verifyLegacySync("RCV-001", context);

            assertThat(result).isTrue();
            verify(legacyBridgeActivity).verifyLegacySync("RCV-001", context);
        }
    }

    @Nested
    @DisplayName("NotificationActivity Delegation Tests")
    class NotificationDelegationTests {

        @Test
        @DisplayName("sendPopulationComplete delegates correctly")
        void sendPopulationCompleteDelegates() {
            PopulateRequest request = createPopulateRequest();

            activities.sendPopulationComplete("RCV-001", request);

            verify(notificationActivity).sendPopulationComplete("RCV-001", request);
        }

        @Test
        @DisplayName("sendPopulationFailed delegates correctly")
        void sendPopulationFailedDelegates() {
            PopulateRequest request = createPopulateRequest();

            activities.sendPopulationFailed("RCV-001", "Error", request);

            verify(notificationActivity).sendPopulationFailed("RCV-001", "Error", request);
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
            activities.sendTradeReturnFailed("RCV-001", "Error");

            verify(notificationActivity).sendTradeReturnFailed("RCV-001", "Error");
        }
    }

    @Nested
    @DisplayName("PluginActivity Delegation Tests")
    class PluginDelegationTests {

        @Test
        @DisplayName("runPrePopulate delegates correctly")
        void runPrePopulateDelegates() {
            PopulateRequest request = createPopulateRequest();
            VariationContext context = createVariationContext();
            PluginResult expectedResult = PluginResult.success();
            when(pluginActivity.runPrePopulate(request, context)).thenReturn(expectedResult);

            PluginResult result = activities.runPrePopulate(request, context);

            assertThat(result).isEqualTo(expectedResult);
            verify(pluginActivity).runPrePopulate(request, context);
        }

        @Test
        @DisplayName("runPostPopulate delegates correctly")
        void runPostPopulateDelegates() {
            PopulateRequest request = createPopulateRequest();
            VariationContext context = createVariationContext();
            PluginResult expectedResult = PluginResult.success();
            when(pluginActivity.runPostPopulate("RCV-001", request, context)).thenReturn(expectedResult);

            PluginResult result = activities.runPostPopulate("RCV-001", request, context);

            assertThat(result).isEqualTo(expectedResult);
            verify(pluginActivity).runPostPopulate("RCV-001", request, context);
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

    private MappingResult createMappingResult() {
        return MappingResult.builder()
            .externReceiptKey("RCV-EXT-001")
            .storerKey("TEST_STORER")
            .facility("KR01")
            .userId("test_user")
            .details(List.of(createDetailMapping()))
            .build();
    }

    private DetailMapping createDetailMapping() {
        return DetailMapping.builder()
            .sku("SKU-001")
            .qtyExpected(BigDecimal.TEN)
            .uom("EA")
            .poKey("PO-001")
            .poLineNumber(1)
            .lottables(Map.of())
            .build();
    }
}
