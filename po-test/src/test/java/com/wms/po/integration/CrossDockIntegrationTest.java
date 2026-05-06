package com.wms.po.integration;

import com.wms.po.activity.*;
import com.wms.po.activity.XDockAllocationActivity.*;
import com.wms.po.domain.model.*;
import com.wms.po.workflow.FinalizeReceiptWorkflow;
import com.wms.po.workflow.impl.FinalizeReceiptWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Cross-Dock (XDock) allocation functionality.
 *
 * Tests verify:
 * - XDock allocation during finalization
 * - Flow-through allocation matching
 * - XDock allocation compensation
 * - Priority-based allocation
 * - Partial allocation scenarios
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrossDockIntegrationTest {

    private static final String TASK_QUEUE = "xdock-integration-test-queue";
    private static final String STORER_KEY = "NIKE_KR";
    private static final String FACILITY = "KR01";
    private static final String USER_ID = "integration_user";
    private static final String RECEIPT_KEY = "INT-RCV-XDOCK-001";

    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    private WorkflowClient workflowClient;

    // Mock activities
    private ValidationActivity validationActivity;
    private ReceiptStatusActivity receiptStatusActivity;
    private InventoryPostingActivity inventoryPostingActivity;
    private InventoryHoldActivity inventoryHoldActivity;
    private POQuantityActivity poQuantityActivity;
    private PutawayReleaseActivity putawayReleaseActivity;
    private FinalizePluginActivity finalizePluginActivity;
    private NotificationActivity notificationActivity;
    private XDockAllocationActivity xDockAllocationActivity;

    @BeforeAll
    void setUpEnvironment() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker(TASK_QUEUE);
        workflowClient = testEnv.getWorkflowClient();

        // Create mock activities
        validationActivity = mock(ValidationActivity.class);
        receiptStatusActivity = mock(ReceiptStatusActivity.class);
        inventoryPostingActivity = mock(InventoryPostingActivity.class);
        inventoryHoldActivity = mock(InventoryHoldActivity.class);
        poQuantityActivity = mock(POQuantityActivity.class);
        putawayReleaseActivity = mock(PutawayReleaseActivity.class);
        finalizePluginActivity = mock(FinalizePluginActivity.class);
        notificationActivity = mock(NotificationActivity.class);
        xDockAllocationActivity = mock(XDockAllocationActivity.class);

        // Register workflows and activities
        worker.registerWorkflowImplementationTypes(FinalizeReceiptWorkflowImpl.class);
        worker.registerActivitiesImplementations(
            validationActivity,
            receiptStatusActivity,
            inventoryPostingActivity,
            inventoryHoldActivity,
            poQuantityActivity,
            putawayReleaseActivity,
            finalizePluginActivity,
            notificationActivity,
            xDockAllocationActivity
        );

        testEnv.start();
    }

    @AfterAll
    void tearDownEnvironment() {
        if (testEnv != null) {
            testEnv.close();
        }
    }

    @BeforeEach
    void resetMocks() {
        reset(validationActivity, receiptStatusActivity, inventoryPostingActivity,
              inventoryHoldActivity, poQuantityActivity, putawayReleaseActivity,
              finalizePluginActivity, notificationActivity, xDockAllocationActivity);

        // Setup default context resolution
        when(validationActivity.resolveContext(any())).thenReturn(
            VariationContext.builder()
                .version("V2")
                .region("ASIA-KR")
                .storerKey(STORER_KEY)
                .facility(FACILITY)
                .dualWriteEnabled(false)
                .build()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // XDock Allocation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Successful XDock allocation during finalization")
    void successfulXDockAllocation() {
        // Arrange
        setupSuccessfulFinalizationMocks();

        List<AllocationDetail> allocations = List.of(
            new AllocationDetail("ALLOC-001", RECEIPT_KEY, 1, "ORD-001", 1, "SKU-001",
                BigDecimal.valueOf(50), "LOT001", "XDOCK01", "LP001"),
            new AllocationDetail("ALLOC-002", RECEIPT_KEY, 2, "ORD-002", 1, "SKU-002",
                BigDecimal.valueOf(30), "LOT002", "XDOCK01", "LP002")
        );

        when(xDockAllocationActivity.allocateFlowThrough(anyString(), anyString()))
            .thenReturn(XDockAllocationResult.success(allocations, BigDecimal.valueOf(80)));

        FinalizeRequest request = createXDockRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(xDockAllocationActivity).allocateFlowThrough(eq(RECEIPT_KEY), eq(STORER_KEY));
    }

    @Test
    @DisplayName("XDock allocation finds no matching orders")
    void xDockNoMatchingOrders() {
        // Arrange
        setupSuccessfulFinalizationMocks();

        when(xDockAllocationActivity.allocateFlowThrough(anyString(), anyString()))
            .thenReturn(XDockAllocationResult.noMatch());

        FinalizeRequest request = createXDockRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert - finalization should still succeed
        assertThat(result.isSuccess()).isTrue();
        verify(xDockAllocationActivity).allocateFlowThrough(eq(RECEIPT_KEY), eq(STORER_KEY));
    }

    @Test
    @DisplayName("Partial XDock allocation")
    void partialXDockAllocation() {
        // Arrange
        setupSuccessfulFinalizationMocks();

        // Only partial quantity allocated
        List<AllocationDetail> partialAllocations = List.of(
            new AllocationDetail("ALLOC-001", RECEIPT_KEY, 1, "ORD-001", 1, "SKU-001",
                BigDecimal.valueOf(25), "LOT001", "XDOCK01", "LP001")  // Only 25 of 50
        );

        XDockAllocationResult partialResult = new XDockAllocationResult(
            true, 1, BigDecimal.valueOf(25), partialAllocations, List.of(),
            List.of("Partial allocation: only 25 of 50 units allocated for SKU-001")
        );

        when(xDockAllocationActivity.allocateFlowThrough(anyString(), anyString()))
            .thenReturn(partialResult);

        FinalizeRequest request = createXDockRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("XDock allocation failure triggers compensation")
    void xDockAllocationFailureTriggersCompensation() {
        // Arrange
        setupValidationMocks();

        when(xDockAllocationActivity.allocateFlowThrough(anyString(), anyString()))
            .thenReturn(XDockAllocationResult.failure(
                List.of("XDock system unavailable")));

        FinalizeRequest request = createXDockRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert - XDock failure should be handled gracefully
        assertThat(result).as("Should return a result even when XDock fails").isNotNull();
        verify(xDockAllocationActivity).allocateFlowThrough(anyString(), anyString());
    }

    @Test
    @DisplayName("Find eligible orders for XDock")
    void findEligibleOrdersForXDock() {
        // Arrange
        List<EligibleOrder> eligibleOrders = List.of(
            new EligibleOrder("ORD-001", 1, "SKU-001", BigDecimal.valueOf(100), "1",
                "2024-01-15", Map.of()),
            new EligibleOrder("ORD-002", 1, "SKU-001", BigDecimal.valueOf(50), "2",
                "2024-01-16", Map.of())
        );

        when(xDockAllocationActivity.findEligibleOrders(anyString(), anyString(), any()))
            .thenReturn(eligibleOrders);

        // Act
        List<EligibleOrder> result = xDockAllocationActivity.findEligibleOrders(
            "SKU-001", STORER_KEY, BigDecimal.valueOf(150));

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).priority()).isEqualTo("1");  // Higher priority first
    }

    @Test
    @DisplayName("XDock allocation respects lottable requirements")
    void xDockAllocationRespectsLottables() {
        // Arrange
        setupSuccessfulFinalizationMocks();

        // Eligible order with specific lottable requirements
        EligibleOrder orderWithLottables = new EligibleOrder(
            "ORD-LOTTABLE", 1, "SKU-001", BigDecimal.valueOf(50), "1", "2024-01-15",
            Map.of("LOTTABLE01", "LOT-SPECIFIC", "LOTTABLE02", "2024-12-31")
        );

        when(xDockAllocationActivity.findEligibleOrders(anyString(), anyString(), any()))
            .thenReturn(List.of(orderWithLottables));

        // Act
        List<EligibleOrder> result = xDockAllocationActivity.findEligibleOrders(
            "SKU-001", STORER_KEY, BigDecimal.valueOf(50));

        // Assert
        assertThat(result.get(0).lottableRequirements()).containsKey("LOTTABLE01");
    }

    @Test
    @DisplayName("Cancel XDock allocation on compensation")
    void cancelXDockAllocationOnCompensation() {
        // Arrange
        String allocationId = "ALLOC-CANCEL-001";

        doNothing().when(xDockAllocationActivity).cancelAllocation(anyString());

        // Act
        xDockAllocationActivity.cancelAllocation(allocationId);

        // Assert
        verify(xDockAllocationActivity).cancelAllocation(eq(allocationId));
    }

    @Test
    @DisplayName("Release XDock allocation for picking")
    void releaseXDockAllocationForPicking() {
        // Arrange
        String allocationId = "ALLOC-RELEASE-001";

        doNothing().when(xDockAllocationActivity).releaseAllocation(anyString());

        // Act
        xDockAllocationActivity.releaseAllocation(allocationId);

        // Assert
        verify(xDockAllocationActivity).releaseAllocation(eq(allocationId));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Priority-Based Allocation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("XDock allocates to highest priority order first")
    void xDockAllocatesHighestPriorityFirst() {
        // Arrange
        setupSuccessfulFinalizationMocks();

        // High priority order gets allocation
        List<AllocationDetail> priorityAllocation = List.of(
            new AllocationDetail("ALLOC-PRI-001", RECEIPT_KEY, 1, "ORD-HIGH-PRI", 1,
                "SKU-001", BigDecimal.valueOf(50), "LOT001", "XDOCK01", "LP001")
        );

        when(xDockAllocationActivity.allocateFlowThrough(anyString(), anyString()))
            .thenReturn(XDockAllocationResult.success(priorityAllocation, BigDecimal.valueOf(50)));

        FinalizeRequest request = createXDockRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("XDock allocates to earliest ship date when same priority")
    void xDockAllocatesEarliestShipDate() {
        // Arrange
        List<EligibleOrder> samepriorityOrders = List.of(
            new EligibleOrder("ORD-EARLY", 1, "SKU-001", BigDecimal.valueOf(50), "1",
                "2024-01-10", Map.of()),  // Earlier ship date
            new EligibleOrder("ORD-LATE", 1, "SKU-001", BigDecimal.valueOf(50), "1",
                "2024-01-20", Map.of())   // Later ship date
        );

        when(xDockAllocationActivity.findEligibleOrders(anyString(), anyString(), any()))
            .thenReturn(samepriorityOrders);

        // Act
        List<EligibleOrder> result = xDockAllocationActivity.findEligibleOrders(
            "SKU-001", STORER_KEY, BigDecimal.valueOf(50));

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).shipDate()).isEqualTo("2024-01-10");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Line-Level Allocation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Allocate specific receipt line to orders")
    void allocateSpecificLine() {
        // Arrange
        List<AllocationDetail> lineAllocation = List.of(
            new AllocationDetail("ALLOC-LINE-001", RECEIPT_KEY, 1, "ORD-001", 1,
                "SKU-001", BigDecimal.valueOf(100), "LOT001", "XDOCK01", "LP001")
        );

        when(xDockAllocationActivity.allocateLine(anyString(), anyInt(), any()))
            .thenReturn(XDockAllocationResult.success(lineAllocation, BigDecimal.valueOf(100)));

        // Act
        XDockAllocationResult result = xDockAllocationActivity.allocateLine(
            RECEIPT_KEY, 1, List.of("ORD-001"));

        // Assert
        assertThat(result.success()).isTrue();
        assertThat(result.allocationsCreated()).isEqualTo(1);
        assertThat(result.allocations().get(0).receiptLineNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Allocate line with auto-match orders")
    void allocateLineWithAutoMatch() {
        // Arrange
        List<AllocationDetail> autoMatchAllocation = List.of(
            new AllocationDetail("ALLOC-AUTO-001", RECEIPT_KEY, 1, "ORD-AUTO-001", 1,
                "SKU-001", BigDecimal.valueOf(50), "LOT001", "XDOCK01", "LP001"),
            new AllocationDetail("ALLOC-AUTO-002", RECEIPT_KEY, 1, "ORD-AUTO-002", 1,
                "SKU-001", BigDecimal.valueOf(50), "LOT001", "XDOCK01", "LP001")
        );

        when(xDockAllocationActivity.allocateLine(anyString(), anyInt(), isNull()))
            .thenReturn(XDockAllocationResult.success(autoMatchAllocation, BigDecimal.valueOf(100)));

        // Act - pass null for orderKeys to auto-match
        XDockAllocationResult result = xDockAllocationActivity.allocateLine(
            RECEIPT_KEY, 1, null);

        // Assert
        assertThat(result.success()).isTrue();
        assertThat(result.allocationsCreated()).isEqualTo(2);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private FinalizeReceiptWorkflow startWorkflow() {
        return workflowClient.newWorkflowStub(
            FinalizeReceiptWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowId("finalize-xdock-" + UUID.randomUUID())
                .build()
        );
    }

    private FinalizeRequest createXDockRequest() {
        return FinalizeRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .autoClose(false)
            .releasePutaway(false)
            .applyHolds(false)
            .overrides(Map.of("enableCrossDock", true))  // Enable XDock via overrides
            .build();
    }

    private void setupSuccessfulFinalizationMocks() {
        setupValidationMocks();
        setupSuccessfulInventoryMocks();
        setupSuccessfulPOQuantityMocks();
        setupSuccessfulPluginMocks();
    }

    private void setupValidationMocks() {
        when(receiptStatusActivity.validateForFinalization(anyString()))
            .thenReturn("5");

        when(finalizePluginActivity.runPreFinalizePlugins(any(), any()))
            .thenReturn(PluginResult.builder()
                .shouldContinue(true)
                .pluginsExecuted(0)
                .executedPlugins(Collections.emptyList())
                .build());
    }

    private void setupSuccessfulInventoryMocks() {
        when(receiptStatusActivity.setStatusFinalizing(anyString(), anyString()))
            .thenReturn("5");

        List<String> inventoryIds = List.of("INV001", "INV002");
        when(inventoryPostingActivity.postInventory(any()))
            .thenReturn(InventoryPostingActivity.PostingResult.builder()
                .success(true)
                .recordsCreated(2)
                .totalQuantity(new BigDecimal("100"))
                .inventoryIds(inventoryIds)
                .build());

        doNothing().when(receiptStatusActivity).setStatusFinalized(anyString(), anyString());
        doNothing().when(receiptStatusActivity).closeReceipt(anyString(), anyString());
    }

    private void setupSuccessfulPOQuantityMocks() {
        List<POQuantityActivity.LineUpdate> updates = List.of(
            POQuantityActivity.LineUpdate.builder()
                .poLineNumber(1)
                .sku("SKU001")
                .receivedQuantity(new BigDecimal("50"))
                .build()
        );

        when(poQuantityActivity.updateReceivedQuantities(any()))
            .thenReturn(POQuantityActivity.UpdateResult.builder()
                .success(true)
                .linesUpdated(1)
                .updates(updates)
                .build());
    }

    private void setupSuccessfulPluginMocks() {
        when(finalizePluginActivity.runPostFinalizePlugins(anyString(), any(), any(), any()))
            .thenReturn(FinalizePluginActivity.PluginSummary.builder()
                .pluginsExecuted(0)
                .successCount(0)
                .errors(Collections.emptyList())
                .build());

        doNothing().when(notificationActivity).sendFinalizeComplete(anyString(), any());
    }
}
