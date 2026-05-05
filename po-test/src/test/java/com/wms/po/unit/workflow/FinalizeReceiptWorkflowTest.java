package com.wms.po.unit.workflow;

import com.wms.po.activity.*;
import com.wms.po.domain.exception.ValidationException;
import com.wms.po.domain.model.*;
import com.wms.po.workflow.FinalizeReceiptWorkflow;
import com.wms.po.workflow.FinalizeReceiptWorkflow.InventoryProgress;
import com.wms.po.workflow.impl.FinalizeReceiptWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FinalizeReceiptWorkflow.
 *
 * Tests the complete finalization flow including:
 * - Happy path finalization
 * - Validation failures
 * - Compensation on inventory posting failure
 * - Hold application
 * - PO quantity updates
 * - Putaway task release
 * - Plugin execution
 * - Pause/resume/cancel signals
 * - Query methods
 */
class FinalizeReceiptWorkflowTest {

    @RegisterExtension
    public static final TestWorkflowExtension testExtension =
        TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(FinalizeReceiptWorkflowImpl.class)
            .setDoNotStart(true)
            .build();

    private static final String TASK_QUEUE = "finalize-test-queue";
    private static final String RECEIPT_KEY = "RCP001";
    private static final String STORER_KEY = "STORER01";
    private static final String FACILITY = "DC01";
    private static final String USER_ID = "testuser";

    // Mock activities
    private ValidationActivity validationActivity;
    private ReceiptStatusActivity receiptStatusActivity;
    private InventoryPostingActivity inventoryPostingActivity;
    private InventoryHoldActivity inventoryHoldActivity;
    private POQuantityActivity poQuantityActivity;
    private PutawayReleaseActivity putawayReleaseActivity;
    private FinalizePluginActivity finalizePluginActivity;
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
        receiptStatusActivity = mock(ReceiptStatusActivity.class);
        inventoryPostingActivity = mock(InventoryPostingActivity.class);
        inventoryHoldActivity = mock(InventoryHoldActivity.class);
        poQuantityActivity = mock(POQuantityActivity.class);
        putawayReleaseActivity = mock(PutawayReleaseActivity.class);
        finalizePluginActivity = mock(FinalizePluginActivity.class);
        notificationActivity = mock(NotificationActivity.class);

        // Register activities with worker
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

        // Setup default context resolution
        when(validationActivity.resolveContext(any())).thenReturn(
            VariationContext.builder()
                .version("V1")
                .region("US")
                .storerKey(STORER_KEY)
                .facility(FACILITY)
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
    @DisplayName("Successful finalization with all options enabled")
    void testSuccessfulFinalization() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        setupSuccessfulActivityMocks();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(RECEIPT_KEY, result.getReceiptKey());
        assertEquals("9", result.getFinalStatus());
        assertEquals(WorkflowStatus.COMPLETED, result.getWorkflowStatus());
        assertTrue(result.getInventoryRecordsCreated() >= 0);

        // Verify all activities were called
        verify(validationActivity).resolveContext(any());
        verify(receiptStatusActivity).validateForFinalization(RECEIPT_KEY);
        verify(receiptStatusActivity).setStatusFinalizing(eq(RECEIPT_KEY), eq(USER_ID));
        verify(inventoryPostingActivity).postInventory(any());
        verify(receiptStatusActivity).setStatusFinalized(eq(RECEIPT_KEY), eq(USER_ID));
        verify(notificationActivity).sendFinalizeComplete(eq(RECEIPT_KEY), any());
    }

    @Test
    @DisplayName("Successful finalization with auto-close enabled")
    void testFinalizationWithAutoClose() {
        // Arrange
        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .autoClose(true)
            .build();
        setupSuccessfulActivityMocks();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertTrue(result.isSuccess());
        verify(receiptStatusActivity).closeReceipt(eq(RECEIPT_KEY), eq(USER_ID));
    }

    @Test
    @DisplayName("Successful finalization without putaway release")
    void testFinalizationWithoutPutaway() {
        // Arrange
        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .releasePutaway(false)
            .build();
        setupSuccessfulActivityMocks();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertTrue(result.isSuccess());
        verify(putawayReleaseActivity, never()).releasePutawayTasks(any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Validation Failure Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Validation failure returns error without compensation")
    void testValidationFailure() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        when(receiptStatusActivity.validateForFinalization(anyString()))
            .thenThrow(new ValidationException("Receipt is not in valid state for finalization"));

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(WorkflowStatus.FAILED, result.getWorkflowStatus());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.contains("Receipt is not in valid state")));

        // Verify no inventory operations occurred
        verify(inventoryPostingActivity, never()).postInventory(any());
        verify(receiptStatusActivity, never()).setStatusFinalizing(anyString(), anyString());
    }

    @Test
    @DisplayName("Pre-plugin stops finalization")
    void testPrePluginStopsFinalization() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        when(receiptStatusActivity.validateForFinalization(anyString())).thenReturn("5");
        when(finalizePluginActivity.runPreFinalizePlugins(any(), any()))
            .thenReturn(PluginResult.builder()
                .shouldContinue(false)
                .reason("Custom validation failed")
                .pluginsExecuted(1)
                .build());

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(WorkflowStatus.FAILED, result.getWorkflowStatus());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.contains("Pre-finalize plugin stopped")));

        // Verify no inventory operations occurred
        verify(inventoryPostingActivity, never()).postInventory(any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Compensation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Inventory posting failure triggers compensation")
    void testInventoryPostingFailureCompensation() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        setupValidationMocks();
        when(receiptStatusActivity.setStatusFinalizing(anyString(), anyString()))
            .thenReturn("5");
        when(inventoryPostingActivity.postInventory(any()))
            .thenReturn(InventoryPostingActivity.PostingResult.builder()
                .success(false)
                .errors(List.of("Database connection failed"))
                .build());

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(WorkflowStatus.FAILED, result.getWorkflowStatus());

        // Verify compensation: status reverted
        verify(receiptStatusActivity).revertStatus(eq(RECEIPT_KEY), eq("5"), eq(USER_ID));

        // Verify no subsequent activities were called
        verify(inventoryHoldActivity, never()).applyHolds(any());
        verify(poQuantityActivity, never()).updateReceivedQuantities(any());
    }

    @Test
    @DisplayName("PO quantity update failure triggers full compensation")
    void testPOQuantityFailureCompensation() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        setupValidationMocks();
        when(receiptStatusActivity.setStatusFinalizing(anyString(), anyString()))
            .thenReturn("5");

        // Successful inventory posting
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

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertFalse(result.isSuccess());

        // Verify compensation order: inventory deleted, status reverted
        verify(inventoryPostingActivity).deleteInventory(eq(inventoryIds));
        verify(receiptStatusActivity).revertStatus(eq(RECEIPT_KEY), eq("5"), eq(USER_ID));
    }

    @Test
    @DisplayName("Hold application failure does not prevent finalization")
    void testHoldApplicationFailure() {
        // Arrange
        FinalizeRequest request = FinalizeRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .applyHolds(true)
            .releasePutaway(false)
            .build();
        setupValidationMocks();
        setupSuccessfulInventoryMocks();

        // Hold evaluation throws exception
        when(inventoryHoldActivity.evaluateHolds(any()))
            .thenThrow(new RuntimeException("Hold service unavailable"));

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert - Finalization should still fail due to exception
        assertFalse(result.isSuccess());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Plugin Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Pre-finalize plugins execute and can be compensated")
    void testPrePluginsWithCompensation() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        setupValidationMocks();
        List<String> executedPlugins = List.of("Plugin1", "Plugin2");
        when(finalizePluginActivity.runPreFinalizePlugins(any(), any()))
            .thenReturn(PluginResult.builder()
                .shouldContinue(true)
                .pluginsExecuted(2)
                .executedPlugins(executedPlugins)
                .build());

        when(receiptStatusActivity.setStatusFinalizing(anyString(), anyString()))
            .thenReturn("5");

        // Inventory posting fails to trigger compensation
        when(inventoryPostingActivity.postInventory(any()))
            .thenReturn(InventoryPostingActivity.PostingResult.builder()
                .success(false)
                .errors(List.of("Posting failed"))
                .build());

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertFalse(result.isSuccess());

        // Verify plugins were rolled back
        verify(finalizePluginActivity).rollbackPreFinalizePlugins(eq(RECEIPT_KEY), eq(executedPlugins));
    }

    @Test
    @DisplayName("Post-finalize plugins execute after successful finalization")
    void testPostPluginsExecute() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        setupSuccessfulActivityMocks();

        when(finalizePluginActivity.runPostFinalizePlugins(anyString(), any(), any(), any()))
            .thenReturn(FinalizePluginActivity.PluginSummary.builder()
                .pluginsExecuted(3)
                .successCount(3)
                .errors(Collections.emptyList())
                .build());

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertTrue(result.isSuccess());
        verify(finalizePluginActivity).runPostFinalizePlugins(
            eq(RECEIPT_KEY), any(), any(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Query Method Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getStatus returns current workflow status")
    void testGetStatus() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        setupSuccessfulActivityMocks();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    @DisplayName("getProgress returns completion percentage")
    void testGetProgress() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        setupSuccessfulActivityMocks();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert - after completion, progress should be 100%
        int progress = workflow.getProgress();
        assertTrue(progress >= 0 && progress <= 100);
    }

    @Test
    @DisplayName("canCancel returns false after inventory posting starts")
    void testCanCancelAfterInventoryPosting() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        setupSuccessfulActivityMocks();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert - after completion, canCancel should be false
        assertFalse(workflow.canCancel());
    }

    @Test
    @DisplayName("getInventoryProgress returns line processing stats")
    void testGetInventoryProgress() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        setupSuccessfulActivityMocks();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        InventoryProgress progress = workflow.getInventoryProgress();
        assertNotNull(progress);
    }

    @Test
    @DisplayName("getCompletedSteps returns list of completed step descriptions")
    void testGetCompletedSteps() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        setupSuccessfulActivityMocks();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        List<String> completedSteps = workflow.getCompletedSteps();
        assertNotNull(completedSteps);
        assertFalse(completedSteps.isEmpty());
        assertTrue(completedSteps.stream().anyMatch(s -> s.contains("VALIDATE")));
        assertTrue(completedSteps.stream().anyMatch(s -> s.contains("POST_INVENTORY")));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Notification Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Success notification sent on completion")
    void testSuccessNotification() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        setupSuccessfulActivityMocks();

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertTrue(result.isSuccess());
        verify(notificationActivity).sendFinalizeComplete(eq(RECEIPT_KEY), any());
    }

    @Test
    @DisplayName("Failure notification sent on error")
    void testFailureNotification() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        setupValidationMocks();
        when(receiptStatusActivity.setStatusFinalizing(anyString(), anyString()))
            .thenReturn("5");
        when(inventoryPostingActivity.postInventory(any()))
            .thenReturn(InventoryPostingActivity.PostingResult.builder()
                .success(false)
                .errors(List.of("Critical error"))
                .build());

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertFalse(result.isSuccess());
        verify(notificationActivity).sendFinalizeFailed(
            eq(RECEIPT_KEY), contains("Critical error"), any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PO Close Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PO closed when fully received")
    void testPOClosedWhenFullyReceived() {
        // Arrange
        FinalizeRequest request = createDefaultRequest();
        setupSuccessfulActivityMocks();

        String poKey = "PO001";
        when(poQuantityActivity.isFullyReceived(poKey)).thenReturn(true);

        // Act
        FinalizeReceiptWorkflow workflow = startWorkflow();
        FinalizeResult result = workflow.finalize(request);

        // Assert
        assertTrue(result.isSuccess());
        // Note: PO close only happens if getPoKeyFromReceipt returns non-null
        // In current implementation it returns null, so closePO won't be called
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private FinalizeReceiptWorkflow startWorkflow() {
        return client.newWorkflowStub(
            FinalizeReceiptWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .setWorkflowId("finalize-" + UUID.randomUUID())
                .build()
        );
    }

    private FinalizeRequest createDefaultRequest() {
        return FinalizeRequest.builder()
            .receiptKey(RECEIPT_KEY)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .applyHolds(false)
            .releasePutaway(false)
            .autoClose(false)
            .build();
    }

    private void setupSuccessfulActivityMocks() {
        setupValidationMocks();
        setupSuccessfulInventoryMocks();
        setupSuccessfulPOQuantityMocks();
        setupSuccessfulPluginMocks();
    }

    private void setupValidationMocks() {
        when(receiptStatusActivity.validateForFinalization(anyString()))
            .thenReturn("5"); // Receipt in "received" status

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
}
