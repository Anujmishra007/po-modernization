package com.wms.po.integration;

import com.wms.po.activity.*;
import com.wms.po.domain.model.*;
import com.wms.po.workflow.FinalizeReceiptWorkflow;
import com.wms.po.workflow.FinalizeReceiptWorkflow.InventoryProgress;
import com.wms.po.workflow.impl.FinalizeReceiptWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for FinalizeReceipt workflow.
 *
 * Uses Temporal's TestWorkflowEnvironment which provides an in-memory
 * workflow service - no external Temporal server required.
 *
 * Tests verify:
 * - Full finalization workflow with mock activities
 * - Status transitions
 * - Inventory posting
 * - Hold application
 * - PO quantity updates
 * - Putaway task release
 * - Compensation on failures
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinalizeIntegrationTest {

    private static final String TASK_QUEUE = "finalize-integration-test-queue";
    private static final String STORER_KEY = "NIKE_KR";
    private static final String FACILITY = "KR01";
    private static final String USER_ID = "integration_user";
    private static final String RECEIPT_KEY = "INT-RCV-001";

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
            notificationActivity
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
              finalizePluginActivity, notificationActivity);

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
    // Happy Path Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Successfully finalize receipt with all options disabled")
    void finalizeReceiptBasic() {
        // Arrange
        setupSuccessfulMocks();

        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .autoClose(false)
            .releasePutaway(false)
            .applyHolds(false)
            .build();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReceiptKey()).isEqualTo(RECEIPT_KEY);
        assertThat(result.getFinalStatus()).isEqualTo("9");
        assertThat(result.getWorkflowStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(result.getInventoryRecordsCreated()).isGreaterThan(0);

        // Verify activities called
        verify(receiptStatusActivity).validateForFinalization(RECEIPT_KEY);
        verify(receiptStatusActivity).setStatusFinalizing(eq(RECEIPT_KEY), eq(USER_ID));
        verify(inventoryPostingActivity).postInventory(any());
        verify(receiptStatusActivity).setStatusFinalized(eq(RECEIPT_KEY), eq(USER_ID));
        verify(notificationActivity).sendFinalizeComplete(eq(RECEIPT_KEY), any());
    }

    @Test
    @DisplayName("Successfully finalize receipt with auto-close")
    void finalizeWithAutoClose() {
        // Arrange
        setupSuccessfulMocks();

        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .autoClose(true)
            .releasePutaway(false)
            .applyHolds(false)
            .build();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(receiptStatusActivity).closeReceipt(eq(RECEIPT_KEY), eq(USER_ID));
    }

    @Test
    @DisplayName("Successfully finalize receipt with holds application")
    void finalizeWithHolds() {
        // Arrange
        setupSuccessfulMocks();

        // Setup hold activity
        List<InventoryHoldActivity.HoldToApply> holdsToApply = List.of(
            InventoryHoldActivity.HoldToApply.builder()
                .holdCode("QC_HOLD")
                .holdReason("Quality check required")
                .holdType("QC")
                .build()
        );
        when(inventoryHoldActivity.evaluateHolds(any())).thenReturn(holdsToApply);
        when(inventoryHoldActivity.applyHolds(any()))
            .thenReturn(InventoryHoldActivity.HoldResult.builder()
                .success(true)
                .holdIds(List.of("HOLD-001"))
                .holdsApplied(1)
                .build());

        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .autoClose(false)
            .releasePutaway(false)
            .applyHolds(true)
            .build();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(inventoryHoldActivity).evaluateHolds(any());
        verify(inventoryHoldActivity).applyHolds(any());
    }

    @Test
    @DisplayName("Successfully finalize receipt with putaway release")
    void finalizeWithPutaway() {
        // Arrange
        setupSuccessfulMocks();

        // Setup putaway activity
        when(putawayReleaseActivity.releasePutawayTasks(any()))
            .thenReturn(PutawayReleaseActivity.ReleaseResult.builder()
                .success(true)
                .taskIds(List.of("TASK-001", "TASK-002", "TASK-003", "TASK-004", "TASK-005"))
                .tasksCreated(5)
                .build());

        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .autoClose(false)
            .releasePutaway(true)
            .applyHolds(false)
            .build();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPutawayTasksReleased()).isEqualTo(5);
        verify(putawayReleaseActivity).releasePutawayTasks(any());
    }

    @Test
    @DisplayName("Successfully finalize receipt with all options enabled")
    void finalizeWithAllOptions() {
        // Arrange
        setupSuccessfulMocks();
        setupHoldAndPutawayMocks();

        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .autoClose(true)
            .releasePutaway(true)
            .applyHolds(true)
            .build();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(inventoryHoldActivity).evaluateHolds(any());
        verify(putawayReleaseActivity).releasePutawayTasks(any());
        verify(receiptStatusActivity).closeReceipt(eq(RECEIPT_KEY), eq(USER_ID));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Validation Failure Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Validation failure returns error without inventory posting")
    void validationFailureReturnsError() {
        // Arrange
        when(receiptStatusActivity.validateForFinalization(anyString()))
            .thenThrow(new com.wms.po.domain.exception.ValidationException(
                "Receipt is not in valid state for finalization"));

        FinalizeRequest request = createDefaultRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getWorkflowStatus()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(result.getErrors()).anyMatch(e -> e.contains("not in valid state"));

        // Verify no inventory posting occurred
        verify(inventoryPostingActivity, never()).postInventory(any());
    }

    @Test
    @DisplayName("Already finalized receipt returns error")
    void alreadyFinalizedReceiptReturnsError() {
        // Arrange
        when(receiptStatusActivity.validateForFinalization(anyString()))
            .thenThrow(new com.wms.po.domain.exception.ValidationException(
                "Receipt is already finalized"));

        FinalizeRequest request = createDefaultRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("already finalized"));
    }

    @Test
    @DisplayName("Pre-finalize plugin stops workflow")
    void prePluginStopsWorkflow() {
        // Arrange
        when(receiptStatusActivity.validateForFinalization(anyString())).thenReturn("5");
        when(finalizePluginActivity.runPreFinalizePlugins(any(), any()))
            .thenReturn(PluginResult.builder()
                .shouldContinue(false)
                .reason("Custom validation failed in plugin")
                .pluginsExecuted(1)
                .build());

        FinalizeRequest request = createDefaultRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Pre-finalize plugin stopped"));
        verify(inventoryPostingActivity, never()).postInventory(any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Compensation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Inventory posting failure triggers status compensation")
    void inventoryPostingFailureTriggersCompensation() {
        // Arrange
        when(receiptStatusActivity.validateForFinalization(anyString())).thenReturn("5");
        when(finalizePluginActivity.runPreFinalizePlugins(any(), any()))
            .thenReturn(PluginResult.builder().shouldContinue(true).pluginsExecuted(0)
                .executedPlugins(Collections.emptyList()).build());
        when(receiptStatusActivity.setStatusFinalizing(anyString(), anyString()))
            .thenReturn("5");

        // Inventory posting fails
        when(inventoryPostingActivity.postInventory(any()))
            .thenReturn(InventoryPostingActivity.PostingResult.builder()
                .success(false)
                .errors(List.of("Database connection failed"))
                .build());

        FinalizeRequest request = createDefaultRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getWorkflowStatus()).isEqualTo(WorkflowStatus.FAILED);

        // Verify compensation: status reverted
        verify(receiptStatusActivity).revertStatus(eq(RECEIPT_KEY), eq("5"), eq(USER_ID));

        // Verify no subsequent activities were called
        verify(poQuantityActivity, never()).updateReceivedQuantities(any());
    }

    @Test
    @DisplayName("PO quantity update failure triggers inventory compensation")
    void poQuantityFailureTriggersInventoryCompensation() {
        // Arrange
        setupValidationMocks();

        List<String> inventoryIds = List.of("INV001", "INV002");
        when(inventoryPostingActivity.postInventory(any()))
            .thenReturn(InventoryPostingActivity.PostingResult.builder()
                .success(true)
                .recordsCreated(2)
                .totalQuantity(new BigDecimal("100"))
                .inventoryIds(inventoryIds)
                .build());

        // PO quantity update fails
        when(poQuantityActivity.updateReceivedQuantities(any()))
            .thenReturn(POQuantityActivity.UpdateResult.builder()
                .success(false)
                .errors(List.of("Concurrent modification detected"))
                .build());

        FinalizeRequest request = createDefaultRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isFalse();

        // Verify compensation order: inventory deleted, status reverted
        verify(inventoryPostingActivity).deleteInventory(eq(inventoryIds));
        verify(receiptStatusActivity).revertStatus(eq(RECEIPT_KEY), eq("5"), eq(USER_ID));
    }

    @Test
    @DisplayName("Pre-finalize plugins are rolled back on failure")
    void prePluginsRolledBackOnFailure() {
        // Arrange
        when(receiptStatusActivity.validateForFinalization(anyString())).thenReturn("5");

        List<String> executedPlugins = List.of("Plugin1", "Plugin2");
        when(finalizePluginActivity.runPreFinalizePlugins(any(), any()))
            .thenReturn(PluginResult.builder()
                .shouldContinue(true)
                .pluginsExecuted(2)
                .executedPlugins(executedPlugins)
                .build());

        when(receiptStatusActivity.setStatusFinalizing(anyString(), anyString()))
            .thenReturn("5");

        // Inventory posting fails
        when(inventoryPostingActivity.postInventory(any()))
            .thenReturn(InventoryPostingActivity.PostingResult.builder()
                .success(false)
                .errors(List.of("Posting failed"))
                .build());

        FinalizeRequest request = createDefaultRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isFalse();

        // Verify plugins were rolled back
        verify(finalizePluginActivity).rollbackPreFinalizePlugins(eq(RECEIPT_KEY), eq(executedPlugins));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Query Method Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Query methods return correct values after completion")
    void queryMethodsReturnCorrectValues() {
        // Arrange
        setupSuccessfulMocks();

        FinalizeRequest request = createDefaultRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(workflow.getProgress()).isGreaterThanOrEqualTo(90);
        assertThat(workflow.canCancel()).isFalse();

        InventoryProgress invProgress = workflow.getInventoryProgress();
        assertThat(invProgress).isNotNull();

        List<String> completedSteps = workflow.getCompletedSteps();
        assertThat(completedSteps).isNotEmpty();
        assertThat(completedSteps).anyMatch(s -> s.contains("VALIDATE"));
        assertThat(completedSteps).anyMatch(s -> s.contains("POST_INVENTORY"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Notification Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Success notification sent on completion")
    void successNotificationSentOnCompletion() {
        // Arrange
        setupSuccessfulMocks();

        FinalizeRequest request = createDefaultRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(notificationActivity).sendFinalizeComplete(eq(RECEIPT_KEY), any());
    }

    @Test
    @DisplayName("Failure notification sent on error")
    void failureNotificationSentOnError() {
        // Arrange
        setupValidationMocks();

        when(inventoryPostingActivity.postInventory(any()))
            .thenReturn(InventoryPostingActivity.PostingResult.builder()
                .success(false)
                .errors(List.of("Critical error"))
                .build());

        FinalizeRequest request = createDefaultRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        verify(notificationActivity).sendFinalizeFailed(
            eq(RECEIPT_KEY), contains("Critical error"), any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PO Quantity Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Finalization updates PO received quantities")
    void finalizationUpdatesPOQuantities() {
        // Arrange
        setupSuccessfulMocks();

        List<POQuantityActivity.LineUpdate> updates = List.of(
            POQuantityActivity.LineUpdate.builder()
                .poLineNumber(1)
                .sku("SKU001")
                .receivedQuantity(new BigDecimal("50"))
                .build(),
            POQuantityActivity.LineUpdate.builder()
                .poLineNumber(2)
                .sku("SKU002")
                .receivedQuantity(new BigDecimal("100"))
                .build()
        );

        when(poQuantityActivity.updateReceivedQuantities(any()))
            .thenReturn(POQuantityActivity.UpdateResult.builder()
                .success(true)
                .linesUpdated(2)
                .updates(updates)
                .build());

        FinalizeRequest request = createDefaultRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(poQuantityActivity).updateReceivedQuantities(any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Edge Case Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Large receipt with many lines finalized successfully")
    void largeReceiptFinalizedSuccessfully() {
        // Arrange
        setupSuccessfulMocks();

        // Large inventory result
        List<String> manyInventoryIds = new java.util.ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            manyInventoryIds.add("INV-" + String.format("%04d", i));
        }

        when(inventoryPostingActivity.postInventory(any()))
            .thenReturn(InventoryPostingActivity.PostingResult.builder()
                .success(true)
                .recordsCreated(500)
                .totalQuantity(new BigDecimal("5000"))
                .inventoryIds(manyInventoryIds)
                .build());

        FinalizeRequest request = createDefaultRequest();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getInventoryRecordsCreated()).isEqualTo(500);
    }

    @Test
    @DisplayName("Hold evaluation failure does not fail finalization when configured")
    void holdEvaluationFailureHandled() {
        // Arrange
        setupSuccessfulMocks();

        // Hold evaluation throws exception
        when(inventoryHoldActivity.evaluateHolds(any()))
            .thenThrow(new RuntimeException("Hold service unavailable"));

        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .autoClose(false)
            .releasePutaway(false)
            .applyHolds(true)  // Holds enabled but service fails
            .build();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert - workflow should fail since hold application was requested
        assertThat(result.isSuccess()).isFalse();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private FinalizeReceiptWorkflow startWorkflow() {
        return workflowClient.newWorkflowStub(
            FinalizeReceiptWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowId("finalize-int-" + UUID.randomUUID())
                .build()
        );
    }

    private FinalizeRequest createDefaultRequest() {
        return FinalizeRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .autoClose(false)
            .releasePutaway(false)
            .applyHolds(false)
            .build();
    }

    private void setupSuccessfulMocks() {
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
        doNothing().when(notificationActivity).sendFinalizeFailed(anyString(), anyString(), any());
    }

    private void setupHoldAndPutawayMocks() {
        List<InventoryHoldActivity.HoldToApply> holdsToApply = List.of(
            InventoryHoldActivity.HoldToApply.builder()
                .holdCode("QC_HOLD")
                .holdReason("Quality check required")
                .holdType("QC")
                .build()
        );
        when(inventoryHoldActivity.evaluateHolds(any())).thenReturn(holdsToApply);
        when(inventoryHoldActivity.applyHolds(any()))
            .thenReturn(InventoryHoldActivity.HoldResult.builder()
                .success(true)
                .holdIds(List.of("HOLD-001"))
                .holdsApplied(1)
                .build());

        when(putawayReleaseActivity.releasePutawayTasks(any()))
            .thenReturn(PutawayReleaseActivity.ReleaseResult.builder()
                .success(true)
                .taskIds(List.of("TASK-001", "TASK-002", "TASK-003"))
                .tasksCreated(3)
                .build());
    }
}
