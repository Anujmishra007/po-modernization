package com.wms.po.unit.workflow;

import com.wms.po.activity.NotificationActivity;
import com.wms.po.activity.TradeReturnActivity;
import com.wms.po.activity.TradeReturnActivity.*;
import com.wms.po.activity.ValidationActivity;
import com.wms.po.domain.model.*;
import com.wms.po.workflow.TradeReturnWorkflow;
import com.wms.po.workflow.impl.TradeReturnWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TradeReturnWorkflow using Temporal TestWorkflowExtension.
 *
 * Tests the complete trade return (ASN to Sales Order) flow including:
 * - Happy path SO creation
 * - Validation failures
 * - Compensation on failures
 * - Auto-release functionality
 * - Query methods
 */
class TradeReturnWorkflowTest {

    @RegisterExtension
    public static final TestWorkflowExtension testExtension =
        TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(TradeReturnWorkflowImpl.class)
            .setDoNotStart(true)
            .build();

    private static final String STORER_KEY = "NIKE_KR";
    private static final String FACILITY = "KR01";
    private static final String USER_ID = "testuser";
    private static final String RECEIPT_KEY = "RCV-TR-001";
    private static final String ORDER_KEY = "SO-TR-001";

    // Mock activities
    private ValidationActivity validationActivity;
    private TradeReturnActivity tradeReturnActivity;
    private NotificationActivity notificationActivity;

    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    private WorkflowClient client;

    @BeforeEach
    void setUp(TestWorkflowEnvironment testEnv, Worker worker, WorkflowClient client) {
        this.testEnv = testEnv;
        this.worker = worker;
        this.client = client;

        // Create mock activities
        validationActivity = mock(ValidationActivity.class);
        tradeReturnActivity = mock(TradeReturnActivity.class);
        notificationActivity = mock(NotificationActivity.class);

        // Register activities with worker
        worker.registerActivitiesImplementations(
            validationActivity,
            tradeReturnActivity,
            notificationActivity
        );

        // Setup default context resolution
        when(validationActivity.resolveTradeReturnContext(any())).thenReturn(
            VariationContext.builder()
                .version("V2")
                .region("ASIA-KR")
                .storerKey(STORER_KEY)
                .facility(FACILITY)
                .dualWriteEnabled(false)
                .build()
        );

        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Happy Path Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Happy path - successful trade return SO creation")
    void testTradeReturnSuccess() {
        // Arrange
        setupSuccessfulActivityMocks();

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        TradeReturnResult result = workflow.populateSalesOrder(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrderKey()).isEqualTo(ORDER_KEY);
        assertThat(result.getReceiptKey()).isEqualTo(RECEIPT_KEY);
        assertThat(result.getDetailCount()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        // Verify all activities were called
        verify(validationActivity).resolveTradeReturnContext(any());
        verify(tradeReturnActivity).validateTradeReturn(any(), any());
        verify(tradeReturnActivity).mapReceiptToSalesOrder(any(), any());
        verify(tradeReturnActivity).createSalesOrderHeader(any());
        verify(tradeReturnActivity).createSalesOrderDetails(eq(ORDER_KEY), any());
        verify(tradeReturnActivity).createInventoryReservations(eq(ORDER_KEY), any());
        verify(notificationActivity).sendTradeReturnComplete(eq(ORDER_KEY), eq(RECEIPT_KEY));
    }

    @Test
    @DisplayName("Successful trade return with auto-release")
    void testTradeReturnWithAutoRelease() {
        // Arrange
        setupSuccessfulActivityMocks();
        doNothing().when(tradeReturnActivity).autoReleaseOrder(anyString(), any());

        TradeReturnRequest request = TradeReturnRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .countryCode("KR")
            .facility(FACILITY)
            .userId(USER_ID)
            .autoRelease(true)  // Enable auto-release
            .build();

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        TradeReturnResult result = workflow.populateSalesOrder(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(tradeReturnActivity).autoReleaseOrder(eq(ORDER_KEY), any());
    }

    @Test
    @DisplayName("Successful trade return without auto-release")
    void testTradeReturnWithoutAutoRelease() {
        // Arrange
        setupSuccessfulActivityMocks();

        TradeReturnRequest request = TradeReturnRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .countryCode("KR")
            .facility(FACILITY)
            .userId(USER_ID)
            .autoRelease(false)  // Disable auto-release
            .build();

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        TradeReturnResult result = workflow.populateSalesOrder(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(tradeReturnActivity, never()).autoReleaseOrder(anyString(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Validation Failure Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Validation failure returns error without SO creation")
    void testValidationFailure() {
        // Arrange
        when(tradeReturnActivity.validateTradeReturn(any(), any()))
            .thenReturn(TradeReturnValidationResult.failure(
                List.of("Receipt not eligible for trade return", "Receipt already processed")));

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        TradeReturnResult result = workflow.populateSalesOrder(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(result.getErrors()).contains("Receipt not eligible for trade return");

        // Verify no SO creation occurred
        verify(tradeReturnActivity, never()).createSalesOrderHeader(any());
    }

    @Test
    @DisplayName("Invalid receipt key returns error")
    void testInvalidReceiptKey() {
        // Arrange
        when(tradeReturnActivity.validateTradeReturn(any(), any()))
            .thenReturn(TradeReturnValidationResult.failure(
                List.of("Receipt RCV-INVALID not found")));

        TradeReturnRequest request = TradeReturnRequest.builder()
            .receiptKey("RCV-INVALID")
            .storerKey(STORER_KEY)
            .countryCode("KR")
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        TradeReturnResult result = workflow.populateSalesOrder(request);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("not found"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Compensation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Reservation failure triggers compensation")
    void testReservationFailureTriggersCompensation() {
        // Arrange
        setupMocksUpToDetails();

        // Reservation creation fails
        when(tradeReturnActivity.createInventoryReservations(anyString(), any()))
            .thenThrow(new RuntimeException("Inventory system unavailable"));

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        TradeReturnResult result = workflow.populateSalesOrder(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.FAILED);

        // Verify compensation called in reverse order
        verify(tradeReturnActivity).deleteSalesOrderDetails(any());
        verify(tradeReturnActivity).deleteSalesOrderHeader(ORDER_KEY);
    }

    @Test
    @DisplayName("Detail creation failure triggers header compensation")
    void testDetailFailureTriggersHeaderCompensation() {
        // Arrange
        when(tradeReturnActivity.validateTradeReturn(any(), any()))
            .thenReturn(TradeReturnValidationResult.success());
        when(tradeReturnActivity.mapReceiptToSalesOrder(any(), any()))
            .thenReturn(createTestMappingResult());
        when(tradeReturnActivity.createSalesOrderHeader(any()))
            .thenReturn(ORDER_KEY);

        // Detail creation fails
        when(tradeReturnActivity.createSalesOrderDetails(anyString(), any()))
            .thenThrow(new RuntimeException("Database error"));

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        TradeReturnResult result = workflow.populateSalesOrder(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isFalse();

        // Verify header compensation called
        verify(tradeReturnActivity).deleteSalesOrderHeader(ORDER_KEY);
        // Details compensation not called (details never created)
        verify(tradeReturnActivity, never()).deleteSalesOrderDetails(any());
    }

    @Test
    @DisplayName("Full compensation on late failure")
    void testFullCompensationOnLateFailure() {
        // Arrange
        setupMocksUpToDetails();

        List<String> reservationIds = List.of("RES-001", "RES-002");
        List<String> detailKeys = List.of("DTL-001", "DTL-002");

        when(tradeReturnActivity.createSalesOrderDetails(anyString(), any()))
            .thenReturn(detailKeys);
        when(tradeReturnActivity.createInventoryReservations(anyString(), any()))
            .thenReturn(reservationIds);

        // Update receipt status fails
        doThrow(new RuntimeException("Receipt locked"))
            .when(tradeReturnActivity).updateReceiptStatus(anyString(), anyString());

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        TradeReturnResult result = workflow.populateSalesOrder(createTestRequest());

        // Assert - workflow should still succeed (receipt status update is best-effort)
        assertThat(result.isSuccess()).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Query Method Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getStatus returns correct workflow status")
    void testGetStatus() {
        // Arrange
        setupSuccessfulActivityMocks();

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        TradeReturnResult result = workflow.populateSalesOrder(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    @DisplayName("getProgress returns completion percentage")
    void testGetProgress() {
        // Arrange
        setupSuccessfulActivityMocks();

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        workflow.populateSalesOrder(createTestRequest());

        // Assert - after completion, progress should be near 100%
        int progress = workflow.getProgress();
        assertThat(progress).isGreaterThanOrEqualTo(90);
    }

    @Test
    @DisplayName("getCurrentStep returns current step name")
    void testGetCurrentStep() {
        // Arrange
        setupSuccessfulActivityMocks();

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        workflow.populateSalesOrder(createTestRequest());

        // Assert - after completion
        assertThat(workflow.getCurrentStep()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("getCompletedSteps returns list of completed steps")
    void testGetCompletedSteps() {
        // Arrange
        setupSuccessfulActivityMocks();

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        workflow.populateSalesOrder(createTestRequest());

        // Assert
        List<String> completedSteps = workflow.getCompletedSteps();
        assertThat(completedSteps).isNotEmpty();
        assertThat(completedSteps).anyMatch(s -> s.contains("RESOLVE_CONTEXT"));
        assertThat(completedSteps).anyMatch(s -> s.contains("VALIDATION"));
        assertThat(completedSteps).anyMatch(s -> s.contains("CREATE_HEADER"));
        assertThat(completedSteps).anyMatch(s -> s.contains("CREATE_DETAILS"));
        assertThat(completedSteps).anyMatch(s -> s.contains("CREATE_RESERVATIONS"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Notification Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Success notification sent on completion")
    void testSuccessNotification() {
        // Arrange
        setupSuccessfulActivityMocks();

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        workflow.populateSalesOrder(createTestRequest());

        // Assert
        verify(notificationActivity).sendTradeReturnComplete(eq(ORDER_KEY), eq(RECEIPT_KEY));
    }

    @Test
    @DisplayName("Notification failure does not fail workflow")
    void testNotificationFailureDoesNotFailWorkflow() {
        // Arrange
        setupSuccessfulActivityMocks();
        doThrow(new RuntimeException("Notification service down"))
            .when(notificationActivity).sendTradeReturnComplete(anyString(), anyString());

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        TradeReturnResult result = workflow.populateSalesOrder(createTestRequest());

        // Assert - workflow should still succeed
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOrderKey()).isEqualTo(ORDER_KEY);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Edge Case Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Single line trade return")
    void testSingleLineTradeReturn() {
        // Arrange
        when(tradeReturnActivity.validateTradeReturn(any(), any()))
            .thenReturn(TradeReturnValidationResult.success());

        TradeReturnMappingResult singleLineMapping = new TradeReturnMappingResult(
            RECEIPT_KEY, STORER_KEY, "CUST001", "RT", "CARR01", "5",
            List.of(createLineMapping(1, "SKU-001", BigDecimal.TEN)),
            BigDecimal.TEN, Map.of()
        );

        when(tradeReturnActivity.mapReceiptToSalesOrder(any(), any()))
            .thenReturn(singleLineMapping);
        when(tradeReturnActivity.createSalesOrderHeader(any()))
            .thenReturn(ORDER_KEY);
        when(tradeReturnActivity.createSalesOrderDetails(anyString(), any()))
            .thenReturn(List.of("DTL-001"));
        when(tradeReturnActivity.createInventoryReservations(anyString(), any()))
            .thenReturn(List.of("RES-001"));
        doNothing().when(notificationActivity).sendTradeReturnComplete(anyString(), anyString());

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        TradeReturnResult result = workflow.populateSalesOrder(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDetailCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Large trade return with many lines")
    void testLargeTradeReturn() {
        // Arrange
        when(tradeReturnActivity.validateTradeReturn(any(), any()))
            .thenReturn(TradeReturnValidationResult.success());

        // Create mapping with many lines
        List<TradeReturnLineMapping> manyLines = new java.util.ArrayList<>();
        List<String> manyDetailKeys = new java.util.ArrayList<>();
        List<String> manyReservationIds = new java.util.ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;

        for (int i = 1; i <= 100; i++) {
            manyLines.add(createLineMapping(i, "SKU-" + i, BigDecimal.TEN));
            manyDetailKeys.add("DTL-" + String.format("%03d", i));
            manyReservationIds.add("RES-" + String.format("%03d", i));
            totalQty = totalQty.add(BigDecimal.TEN);
        }

        TradeReturnMappingResult largeMapping = new TradeReturnMappingResult(
            RECEIPT_KEY, STORER_KEY, "CUST001", "RT", "CARR01", "5",
            manyLines, totalQty, Map.of()
        );

        when(tradeReturnActivity.mapReceiptToSalesOrder(any(), any()))
            .thenReturn(largeMapping);
        when(tradeReturnActivity.createSalesOrderHeader(any()))
            .thenReturn(ORDER_KEY);
        when(tradeReturnActivity.createSalesOrderDetails(anyString(), any()))
            .thenReturn(manyDetailKeys);
        when(tradeReturnActivity.createInventoryReservations(anyString(), any()))
            .thenReturn(manyReservationIds);
        doNothing().when(notificationActivity).sendTradeReturnComplete(anyString(), anyString());

        // Act
        TradeReturnWorkflow workflow = startWorkflow();
        TradeReturnResult result = workflow.populateSalesOrder(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDetailCount()).isEqualTo(100);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private TradeReturnWorkflow startWorkflow() {
        return client.newWorkflowStub(
            TradeReturnWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .setWorkflowId("trade-return-" + UUID.randomUUID())
                .build()
        );
    }

    private TradeReturnRequest createTestRequest() {
        return TradeReturnRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .countryCode("KR")
            .facility(FACILITY)
            .userId(USER_ID)
            .autoRelease(false)
            .build();
    }

    private void setupSuccessfulActivityMocks() {
        when(tradeReturnActivity.validateTradeReturn(any(), any()))
            .thenReturn(TradeReturnValidationResult.success());
        when(tradeReturnActivity.mapReceiptToSalesOrder(any(), any()))
            .thenReturn(createTestMappingResult());
        when(tradeReturnActivity.createSalesOrderHeader(any()))
            .thenReturn(ORDER_KEY);
        when(tradeReturnActivity.createSalesOrderDetails(anyString(), any()))
            .thenReturn(List.of("DTL-001", "DTL-002"));
        when(tradeReturnActivity.createInventoryReservations(anyString(), any()))
            .thenReturn(List.of("RES-001", "RES-002"));
        doNothing().when(tradeReturnActivity).updateReceiptStatus(anyString(), anyString());
        doNothing().when(notificationActivity).sendTradeReturnComplete(anyString(), anyString());
    }

    private void setupMocksUpToDetails() {
        when(tradeReturnActivity.validateTradeReturn(any(), any()))
            .thenReturn(TradeReturnValidationResult.success());
        when(tradeReturnActivity.mapReceiptToSalesOrder(any(), any()))
            .thenReturn(createTestMappingResult());
        when(tradeReturnActivity.createSalesOrderHeader(any()))
            .thenReturn(ORDER_KEY);
        when(tradeReturnActivity.createSalesOrderDetails(anyString(), any()))
            .thenReturn(List.of("DTL-001", "DTL-002"));
    }

    private TradeReturnMappingResult createTestMappingResult() {
        return new TradeReturnMappingResult(
            RECEIPT_KEY,
            STORER_KEY,
            "CUST001",
            "RT",
            "CARR01",
            "5",
            List.of(
                createLineMapping(1, "SKU-001", BigDecimal.valueOf(50)),
                createLineMapping(2, "SKU-002", BigDecimal.valueOf(30))
            ),
            BigDecimal.valueOf(80),
            Map.of()
        );
    }

    private TradeReturnLineMapping createLineMapping(int lineNum, String sku, BigDecimal qty) {
        return new TradeReturnLineMapping(
            lineNum, sku, qty, "EA", "STD", "LOT001", "RECV01", "LP001",
            "RET", "GOOD", Map.of()
        );
    }
}
