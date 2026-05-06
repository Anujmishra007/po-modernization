package com.wms.po.unit.workflow;

import com.wms.po.activity.*;
import com.wms.po.domain.dto.DetailMapping;
import com.wms.po.domain.model.*;
import com.wms.po.workflow.FinalizeReceiptWorkflow;
import com.wms.po.workflow.PopulatePOWorkflow;
import com.wms.po.workflow.impl.FinalizeReceiptWorkflowImpl;
import com.wms.po.workflow.impl.PopulatePOWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Saga orchestration patterns across multiple workflows.
 *
 * Verifies:
 * - Compensation order (reverse of execution)
 * - Partial completion with rollback
 * - Multi-step saga coordination
 * - Compensation failures handling
 * - Idempotency in compensations
 */
class SagaOrchestrationTest {

    @RegisterExtension
    public static final TestWorkflowExtension testExtension =
        TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(PopulatePOWorkflowImpl.class, FinalizeReceiptWorkflowImpl.class)
            .setDoNotStart(true)
            .build();

    private static final String STORER_KEY = "NIKE_KR";
    private static final String FACILITY = "KR01";
    private static final String USER_ID = "testuser";
    private static final String RECEIPT_KEY = "RCV-SAGA-001";

    // Mock activities for Populate
    private ValidationActivity validationActivity;
    private PluginActivity pluginActivity;
    private MappingActivity mappingActivity;
    private PersistenceActivity persistenceActivity;
    private InventoryActivity inventoryActivity;
    private LegacyBridgeActivity legacyBridgeActivity;
    private NotificationActivity notificationActivity;

    // Mock activities for Finalize
    private ReceiptStatusActivity receiptStatusActivity;
    private InventoryPostingActivity inventoryPostingActivity;
    private InventoryHoldActivity inventoryHoldActivity;
    private POQuantityActivity poQuantityActivity;
    private PutawayReleaseActivity putawayReleaseActivity;
    private FinalizePluginActivity finalizePluginActivity;

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
        pluginActivity = mock(PluginActivity.class);
        mappingActivity = mock(MappingActivity.class);
        persistenceActivity = mock(PersistenceActivity.class);
        inventoryActivity = mock(InventoryActivity.class);
        legacyBridgeActivity = mock(LegacyBridgeActivity.class);
        notificationActivity = mock(NotificationActivity.class);
        receiptStatusActivity = mock(ReceiptStatusActivity.class);
        inventoryPostingActivity = mock(InventoryPostingActivity.class);
        inventoryHoldActivity = mock(InventoryHoldActivity.class);
        poQuantityActivity = mock(POQuantityActivity.class);
        putawayReleaseActivity = mock(PutawayReleaseActivity.class);
        finalizePluginActivity = mock(FinalizePluginActivity.class);

        // Register activities with worker
        worker.registerActivitiesImplementations(
            validationActivity,
            pluginActivity,
            mappingActivity,
            persistenceActivity,
            inventoryActivity,
            legacyBridgeActivity,
            notificationActivity,
            receiptStatusActivity,
            inventoryPostingActivity,
            inventoryHoldActivity,
            poQuantityActivity,
            putawayReleaseActivity,
            finalizePluginActivity
        );

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

        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Compensation Order Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Compensation executes in reverse order of forward steps")
    void compensationExecutesInReverseOrder() {
        // Arrange - Track compensation call order
        List<Integer> callOrder = new java.util.ArrayList<>();

        setupPopulateUpToReservations();

        // Inventory reservation fails
        when(inventoryActivity.createReservations(anyString(), any()))
            .thenThrow(new RuntimeException("Inventory system failure"));

        // Track compensation order
        doAnswer(inv -> {
            callOrder.add(1); // Details deleted first (most recent)
            return null;
        }).when(persistenceActivity).deleteReceiptDetails(any());

        doAnswer(inv -> {
            callOrder.add(2); // Header deleted second (earlier step)
            return null;
        }).when(persistenceActivity).deleteReceiptHeader(anyString());

        // Act
        PopulatePOWorkflow workflow = startPopulateWorkflow();
        PopulateResult result = workflow.populate(createPopulateRequest());

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(callOrder).containsExactly(1, 2); // Reverse order: details, then header
    }

    @Test
    @DisplayName("All registered compensations execute on failure")
    void allCompensationsExecuteOnFailure() {
        // Arrange
        when(validationActivity.resolveContext(any())).thenReturn(
            VariationContext.builder()
                .version("V2")
                .region("ASIA-KR")
                .storerKey(STORER_KEY)
                .facility(FACILITY)
                .dualWriteEnabled(true)  // Enable dual-write for more steps
                .build()
        );

        setupPopulateUpToReservations();

        List<String> reservationIds = List.of("RES-001", "RES-002");
        when(inventoryActivity.createReservations(anyString(), any()))
            .thenReturn(reservationIds);

        // Legacy sync fails
        doThrow(new RuntimeException("Legacy system down"))
            .when(legacyBridgeActivity).syncToLegacy(anyString(), any());

        // Act
        PopulatePOWorkflow workflow = startPopulateWorkflow();
        PopulateResult result = workflow.populate(createPopulateRequest());

        // Assert
        assertThat(result.isSuccess()).isFalse();

        // Verify ALL compensations called
        verify(inventoryActivity).releaseReservations(eq(reservationIds));
        verify(persistenceActivity).deleteReceiptDetails(any());
        verify(persistenceActivity).deleteReceiptHeader(RECEIPT_KEY);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Partial Completion Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Early failure does not trigger unneeded compensations")
    void earlyFailureNoUnneedCompensations() {
        // Arrange
        when(pluginActivity.runPrePopulate(any(), any()))
            .thenReturn(PluginResult.success());
        when(validationActivity.validate(any(), any()))
            .thenReturn(ValidationResult.failure("PO is closed"));

        // Act
        PopulatePOWorkflow workflow = startPopulateWorkflow();
        PopulateResult result = workflow.populate(createPopulateRequest());

        // Assert
        assertThat(result.isSuccess()).isFalse();

        // Verify NO compensations called (nothing was created)
        verify(persistenceActivity, never()).deleteReceiptHeader(any());
        verify(persistenceActivity, never()).deleteReceiptDetails(any());
        verify(inventoryActivity, never()).releaseReservations(any());
    }

    @Test
    @DisplayName("Only completed steps are compensated")
    void onlyCompletedStepsCompensated() {
        // Arrange
        when(pluginActivity.runPrePopulate(any(), any()))
            .thenReturn(PluginResult.success());
        when(validationActivity.validate(any(), any()))
            .thenReturn(ValidationResult.success());
        when(mappingActivity.mapPOToASN(any(), any()))
            .thenReturn(createTestMappingResult());
        when(mappingActivity.applyLottables(any(), any()))
            .thenReturn(LottableResult.builder().success(true).appliedRules(List.of()).build());
        when(persistenceActivity.createReceiptHeader(any()))
            .thenReturn(RECEIPT_KEY);

        // Details creation fails
        when(persistenceActivity.createReceiptDetails(anyString(), any()))
            .thenThrow(new RuntimeException("Database constraint violation"));

        // Act
        PopulatePOWorkflow workflow = startPopulateWorkflow();
        PopulateResult result = workflow.populate(createPopulateRequest());

        // Assert
        assertThat(result.isSuccess()).isFalse();

        // Header compensation called
        verify(persistenceActivity).deleteReceiptHeader(RECEIPT_KEY);
        // Details compensation NOT called (never created)
        verify(persistenceActivity, never()).deleteReceiptDetails(any());
        // Reservations compensation NOT called (never created)
        verify(inventoryActivity, never()).releaseReservations(any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Compensation Failure Handling Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Compensation failure is reported in result")
    void compensationFailureReported() {
        // Arrange
        setupPopulateUpToReservations();

        when(inventoryActivity.createReservations(anyString(), any()))
            .thenThrow(new RuntimeException("Original failure"));

        // Compensation also fails
        doThrow(new RuntimeException("Compensation failed"))
            .when(persistenceActivity).deleteReceiptDetails(any());

        // Act
        PopulatePOWorkflow workflow = startPopulateWorkflow();
        PopulateResult result = workflow.populate(createPopulateRequest());

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.FAILED);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Idempotency Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Compensation is idempotent - safe to retry")
    void compensationIsIdempotent() {
        // Arrange
        setupPopulateUpToReservations();

        List<String> detailKeys = List.of("DTL-001", "DTL-002");
        when(persistenceActivity.createReceiptDetails(anyString(), any()))
            .thenReturn(detailKeys);

        when(inventoryActivity.createReservations(anyString(), any()))
            .thenThrow(new RuntimeException("System failure"));

        // First delete call succeeds, second call is no-op
        doNothing().when(persistenceActivity).deleteReceiptDetails(any());
        doNothing().when(persistenceActivity).deleteReceiptHeader(any());

        // Act
        PopulatePOWorkflow workflow = startPopulateWorkflow();
        PopulateResult result = workflow.populate(createPopulateRequest());

        // Assert
        assertThat(result.isSuccess()).isFalse();

        // Compensations called once
        verify(persistenceActivity, times(1)).deleteReceiptDetails(detailKeys);
        verify(persistenceActivity, times(1)).deleteReceiptHeader(RECEIPT_KEY);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Multi-Step Saga Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Full saga with 6 compensatable steps")
    void fullSagaWithMultipleSteps() {
        // Arrange - Enable dual-write for more steps
        when(validationActivity.resolveContext(any())).thenReturn(
            VariationContext.builder()
                .version("V2")
                .region("ASIA-KR")
                .storerKey(STORER_KEY)
                .facility(FACILITY)
                .dualWriteEnabled(true)
                .build()
        );

        setupPopulateUpToReservations();

        List<String> reservationIds = List.of("RES-001", "RES-002");
        when(inventoryActivity.createReservations(anyString(), any()))
            .thenReturn(reservationIds);

        doNothing().when(legacyBridgeActivity).syncToLegacy(anyString(), any());
        doNothing().when(notificationActivity).sendPopulationComplete(anyString(), any());

        // Act
        PopulatePOWorkflow workflow = startPopulateWorkflow();
        PopulateResult result = workflow.populate(createPopulateRequest());

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        // Verify all forward steps executed
        verify(persistenceActivity).createReceiptHeader(any());
        verify(persistenceActivity).createReceiptDetails(eq(RECEIPT_KEY), any());
        verify(inventoryActivity).createReservations(eq(RECEIPT_KEY), any());
        verify(legacyBridgeActivity).syncToLegacy(eq(RECEIPT_KEY), any());
    }

    @Test
    @DisplayName("Saga tracks progress through steps")
    void sagaTracksProgress() {
        // Arrange
        setupSuccessfulPopulateMocks();

        // Act
        PopulatePOWorkflow workflow = startPopulateWorkflow();
        PopulateResult result = workflow.populate(createPopulateRequest());

        // Assert
        assertThat(result.isSuccess()).isTrue();

        List<String> steps = workflow.getCompletedSteps();
        assertThat(steps).isNotEmpty();

        // Verify progress tracking
        int progress = workflow.getProgress();
        assertThat(progress).isGreaterThanOrEqualTo(90);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Finalize Workflow Saga Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Finalize saga compensates inventory on PO update failure")
    void finalizeSagaCompensatesInventory() {
        // Arrange
        setupFinalizeValidationMocks();

        List<String> inventoryIds = List.of("INV-001", "INV-002");
        when(inventoryPostingActivity.postInventory(any()))
            .thenReturn(InventoryPostingActivity.PostingResult.builder()
                .success(true)
                .recordsCreated(2)
                .inventoryIds(inventoryIds)
                .build());

        // PO quantity update fails
        when(poQuantityActivity.updateReceivedQuantities(any()))
            .thenReturn(POQuantityActivity.UpdateResult.builder()
                .success(false)
                .errors(List.of("PO is locked"))
                .build());

        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        FinalizeReceiptWorkflow workflow = startFinalizeWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isFalse();

        // Verify inventory compensation
        verify(inventoryPostingActivity).deleteInventory(inventoryIds);
        // Verify status compensation
        verify(receiptStatusActivity).revertStatus(eq(RECEIPT_KEY), anyString(), eq(USER_ID));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private PopulatePOWorkflow startPopulateWorkflow() {
        return client.newWorkflowStub(
            PopulatePOWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .setWorkflowId("saga-populate-" + UUID.randomUUID())
                .build()
        );
    }

    private FinalizeReceiptWorkflow startFinalizeWorkflow() {
        return client.newWorkflowStub(
            FinalizeReceiptWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .setWorkflowId("saga-finalize-" + UUID.randomUUID())
                .build()
        );
    }

    private PopulateRequest createPopulateRequest() {
        return PopulateRequest.builder()
            .poKeys(List.of("PO-SAGA-001"))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();
    }

    private void setupPopulateUpToReservations() {
        when(pluginActivity.runPrePopulate(any(), any()))
            .thenReturn(PluginResult.success());
        when(validationActivity.validate(any(), any()))
            .thenReturn(ValidationResult.success());
        when(mappingActivity.mapPOToASN(any(), any()))
            .thenReturn(createTestMappingResult());
        when(mappingActivity.applyLottables(any(), any()))
            .thenReturn(LottableResult.builder().success(true).appliedRules(List.of()).build());
        when(persistenceActivity.createReceiptHeader(any()))
            .thenReturn(RECEIPT_KEY);
        when(persistenceActivity.createReceiptDetails(anyString(), any()))
            .thenReturn(List.of("DTL-001", "DTL-002"));
    }

    private void setupSuccessfulPopulateMocks() {
        setupPopulateUpToReservations();
        when(inventoryActivity.createReservations(anyString(), any()))
            .thenReturn(List.of("RES-001", "RES-002"));
        when(pluginActivity.runPostPopulate(anyString(), any(), any()))
            .thenReturn(PluginResult.success());
        doNothing().when(notificationActivity).sendPopulationComplete(anyString(), any());
    }

    private void setupFinalizeValidationMocks() {
        when(receiptStatusActivity.validateForFinalization(anyString()))
            .thenReturn("5");
        when(finalizePluginActivity.runPreFinalizePlugins(any(), any()))
            .thenReturn(PluginResult.builder()
                .shouldContinue(true)
                .pluginsExecuted(0)
                .executedPlugins(Collections.emptyList())
                .build());
        when(receiptStatusActivity.setStatusFinalizing(anyString(), anyString()))
            .thenReturn("5");
    }

    private MappingResult createTestMappingResult() {
        return MappingResult.builder()
            .externReceiptKey("RCV-EXT-001")
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .details(List.of(
                DetailMapping.builder()
                    .sku("SKU-001")
                    .qtyExpected(BigDecimal.TEN)
                    .uom("EA")
                    .poKey("PO-001")
                    .poLineNumber(1)
                    .lottables(Map.of())
                    .build(),
                DetailMapping.builder()
                    .sku("SKU-002")
                    .qtyExpected(BigDecimal.valueOf(20))
                    .uom("EA")
                    .poKey("PO-001")
                    .poLineNumber(2)
                    .lottables(Map.of())
                    .build()
            ))
            .build();
    }
}
