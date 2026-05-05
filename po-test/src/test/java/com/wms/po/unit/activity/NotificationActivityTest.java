package com.wms.po.unit.activity;

import com.wms.po.activity.impl.NotificationActivityImpl;
import com.wms.po.domain.model.FinalizeRequest;
import com.wms.po.domain.model.PopulateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationActivityImpl Tests")
class NotificationActivityTest {

    private NotificationActivityImpl notificationActivity;

    @BeforeEach
    void setUp() {
        notificationActivity = new NotificationActivityImpl();
    }

    @Nested
    @DisplayName("Population Notification Tests")
    class PopulationNotificationTests {

        @Test
        @DisplayName("sendPopulationComplete does not throw exception")
        void sendPopulationCompleteDoesNotThrow() {
            PopulateRequest request = createPopulateRequest();

            assertThatNoException().isThrownBy(() ->
                notificationActivity.sendPopulationComplete("RCV-001", request));
        }

        @Test
        @DisplayName("sendPopulationFailed does not throw exception")
        void sendPopulationFailedDoesNotThrow() {
            PopulateRequest request = createPopulateRequest();

            assertThatNoException().isThrownBy(() ->
                notificationActivity.sendPopulationFailed("RCV-001", "Test error", request));
        }

        @Test
        @DisplayName("sendPopulationCancelled does not throw exception")
        void sendPopulationCancelledDoesNotThrow() {
            PopulateRequest request = createPopulateRequest();

            assertThatNoException().isThrownBy(() ->
                notificationActivity.sendPopulationCancelled("RCV-001", request));
        }
    }

    @Nested
    @DisplayName("Finalization Notification Tests")
    class FinalizationNotificationTests {

        @Test
        @DisplayName("sendFinalizeComplete does not throw exception")
        void sendFinalizeCompleteDoesNotThrow() {
            FinalizeRequest request = createFinalizeRequest();

            assertThatNoException().isThrownBy(() ->
                notificationActivity.sendFinalizeComplete("RCV-001", request));
        }

        @Test
        @DisplayName("sendFinalizeFailed does not throw exception")
        void sendFinalizeFailedDoesNotThrow() {
            FinalizeRequest request = createFinalizeRequest();

            assertThatNoException().isThrownBy(() ->
                notificationActivity.sendFinalizeFailed("RCV-001", "Test error", request));
        }

        @Test
        @DisplayName("sendFinalizeCancelled does not throw exception")
        void sendFinalizeCancelledDoesNotThrow() {
            FinalizeRequest request = createFinalizeRequest();

            assertThatNoException().isThrownBy(() ->
                notificationActivity.sendFinalizeCancelled("RCV-001", request));
        }
    }

    @Nested
    @DisplayName("Trade Return Notification Tests")
    class TradeReturnNotificationTests {

        @Test
        @DisplayName("sendTradeReturnComplete does not throw exception")
        void sendTradeReturnCompleteDoesNotThrow() {
            assertThatNoException().isThrownBy(() ->
                notificationActivity.sendTradeReturnComplete("ORD-001", "RCV-001"));
        }

        @Test
        @DisplayName("sendTradeReturnFailed does not throw exception")
        void sendTradeReturnFailedDoesNotThrow() {
            assertThatNoException().isThrownBy(() ->
                notificationActivity.sendTradeReturnFailed("RCV-001", "Test error"));
        }

        @Test
        @DisplayName("sendTradeReturnComplete with null orderKey does not throw")
        void sendTradeReturnCompleteWithNullOrderKey() {
            assertThatNoException().isThrownBy(() ->
                notificationActivity.sendTradeReturnComplete(null, "RCV-001"));
        }

        @Test
        @DisplayName("sendTradeReturnFailed with null errorMessage does not throw")
        void sendTradeReturnFailedWithNullErrorMessage() {
            assertThatNoException().isThrownBy(() ->
                notificationActivity.sendTradeReturnFailed("RCV-001", null));
        }
    }

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
}
