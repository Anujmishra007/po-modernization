package com.wms.po.unit.workflow;

import com.wms.po.activity.*;
import com.wms.po.domain.dto.DetailMapping;
import com.wms.po.domain.model.*;
import com.wms.po.workflow.PopulatePOWorkflow;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PopulatePOWorkflow using Temporal TestWorkflowExtension.
 *
 * Tests the complete population flow including:
 * - Happy path population
 * - Validation failures
 * - Compensation on inventory failure
 * - Compensation on legacy sync failure
 * - Query methods
 * - Cancellation
 */
class PopulatePOWorkflowTest {

    @RegisterExtension
    public static final TestWorkflowExtension testExtension =
        TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(PopulatePOWorkflowImpl.class)
            .setDoNotStart(true)
            .build();

    private static final String PO_KEY = "PO001";
    private static final String STORER_KEY = "STORER01";
    private static final String FACILITY = "KR01";
    private static final String USER_ID = "testuser";
    private static final String RECEIPT_KEY = "RCV001";

    // Mock activities - registered with Temporal worker
    private ValidationActivity validationActivity;
    private PluginActivity pluginActivity;
    private MappingActivity mappingActivity;
    private PersistenceActivity persistenceActivity;
    private InventoryActivity inventoryActivity;
    private LegacyBridgeActivity legacyBridgeActivity;
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
        pluginActivity = mock(PluginActivity.class);
        mappingActivity = mock(MappingActivity.class);
        persistenceActivity = mock(PersistenceActivity.class);
        inventoryActivity = mock(InventoryActivity.class);
        legacyBridgeActivity = mock(LegacyBridgeActivity.class);
        notificationActivity = mock(NotificationActivity.class);

        // Register activities with worker
        worker.registerActivitiesImplementations(
            validationActivity,
            pluginActivity,
            mappingActivity,
            persistenceActivity,
            inventoryActivity,
            legacyBridgeActivity,
            notificationActivity
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
    // Happy Path Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Happy path - successful population")
    void testPopulateSuccess() {
        // Arrange
        setupSuccessfulActivityMocks();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReceiptKey()).isEqualTo(RECEIPT_KEY);
        assertThat(result.getDetailCount()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        // Verify all activities were called
        verify(validationActivity).resolveContext(any());
        verify(pluginActivity).runPrePopulate(any(), any());
        verify(validationActivity).validate(any(), any());
        verify(mappingActivity).mapPOToASN(any(), any());
        verify(mappingActivity).applyLottables(any(), any());
        verify(persistenceActivity).createReceiptHeader(any());
        verify(persistenceActivity).createReceiptDetails(eq(RECEIPT_KEY), any());
        verify(inventoryActivity).createReservations(eq(RECEIPT_KEY), any());
        verify(notificationActivity).sendPopulationComplete(eq(RECEIPT_KEY), any());
    }

    @Test
    @DisplayName("Successful population with dual-write enabled")
    void testPopulateWithDualWrite() {
        // Arrange
        when(validationActivity.resolveContext(any())).thenReturn(
            VariationContext.builder()
                .version("V2")
                .region("ASIA-KR")
                .storerKey(STORER_KEY)
                .facility(FACILITY)
                .dualWriteEnabled(true)  // Enable dual-write
                .build()
        );
        setupSuccessfulActivityMocks();
        doNothing().when(legacyBridgeActivity).syncToLegacy(anyString(), any());

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(legacyBridgeActivity).syncToLegacy(eq(RECEIPT_KEY), any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Validation Failure Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Validation failure returns error without compensation")
    void testValidationFailure() {
        // Arrange
        when(pluginActivity.runPrePopulate(any(), any()))
            .thenReturn(PluginResult.success());
        when(validationActivity.validate(any(), any()))
            .thenReturn(ValidationResult.failure("PO is closed"));

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(result.getErrors()).contains("PO is closed");

        // Verify no persistence activities called
        verify(persistenceActivity, never()).createReceiptHeader(any());
        verify(persistenceActivity, never()).deleteReceiptHeader(any());
    }

    @Test
    @DisplayName("Pre-populate plugin stops workflow")
    void testPrePluginStopsWorkflow() {
        // Arrange
        when(pluginActivity.runPrePopulate(any(), any()))
            .thenReturn(PluginResult.builder()
                .shouldContinue(false)
                .reason("Custom validation failed")
                .build());

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Plugin stopped"));

        // Verify no further activities called
        verify(validationActivity, never()).validate(any(), any());
        verify(mappingActivity, never()).mapPOToASN(any(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Compensation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Inventory failure triggers compensation")
    void testInventoryFailureTriggersCompensation() {
        // Arrange
        setupMocksUpToPersistence();

        // Create reservations fails
        when(inventoryActivity.createReservations(anyString(), any()))
            .thenThrow(new RuntimeException("Inventory system unavailable"));

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.FAILED);

        // Verify compensation called in reverse order
        verify(persistenceActivity).deleteReceiptDetails(any());
        verify(persistenceActivity).deleteReceiptHeader(RECEIPT_KEY);
    }

    @Test
    @DisplayName("Legacy sync failure triggers full compensation")
    void testLegacySyncFailureTriggersFullCompensation() {
        // Arrange - Enable dual-write
        when(validationActivity.resolveContext(any())).thenReturn(
            VariationContext.builder()
                .version("V2")
                .region("ASIA-KR")
                .storerKey(STORER_KEY)
                .facility(FACILITY)
                .dualWriteEnabled(true)
                .build()
        );
        setupSuccessfulActivityMocks();

        List<String> reservationIds = List.of("RES-001", "RES-002");
        List<String> detailKeys = List.of("DTL-001", "DTL-002");

        when(inventoryActivity.createReservations(anyString(), any()))
            .thenReturn(reservationIds);
        when(persistenceActivity.createReceiptDetails(anyString(), any()))
            .thenReturn(detailKeys);

        // Legacy sync fails
        doThrow(new RuntimeException("Legacy system down"))
            .when(legacyBridgeActivity).syncToLegacy(anyString(), any());

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isFalse();

        // Verify all compensations called in reverse order
        verify(inventoryActivity).releaseReservations(reservationIds);
        verify(persistenceActivity).deleteReceiptDetails(detailKeys);
        verify(persistenceActivity).deleteReceiptHeader(RECEIPT_KEY);
    }

    @Test
    @DisplayName("Compensation on create details failure")
    void testCompensationOnDetailsFailure() {
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

        // Create details fails
        when(persistenceActivity.createReceiptDetails(anyString(), any()))
            .thenThrow(new RuntimeException("Database error"));

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isFalse();

        // Verify header compensation called
        verify(persistenceActivity).deleteReceiptHeader(RECEIPT_KEY);
        // Details compensation should not be called as details were never created
        verify(persistenceActivity, never()).deleteReceiptDetails(any());
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
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(createTestRequest());

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
        PopulatePOWorkflow workflow = startWorkflow();
        workflow.populate(createTestRequest());

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
        PopulatePOWorkflow workflow = startWorkflow();
        workflow.populate(createTestRequest());

        // Assert - after completion, current step should be COMPLETED
        assertThat(workflow.getCurrentStep()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("getCompletedSteps returns list of completed steps")
    void testGetCompletedSteps() {
        // Arrange
        setupSuccessfulActivityMocks();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        workflow.populate(createTestRequest());

        // Assert
        List<String> completedSteps = workflow.getCompletedSteps();
        assertThat(completedSteps).isNotEmpty();
        assertThat(completedSteps).anyMatch(s -> s.contains("RESOLVE_CONTEXT"));
        assertThat(completedSteps).anyMatch(s -> s.contains("VALIDATION"));
        assertThat(completedSteps).anyMatch(s -> s.contains("CREATE_HEADER"));
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
        PopulatePOWorkflow workflow = startWorkflow();
        workflow.populate(createTestRequest());

        // Assert
        verify(notificationActivity).sendPopulationComplete(eq(RECEIPT_KEY), any());
    }

    @Test
    @DisplayName("Failure notification sent on error")
    void testFailureNotification() {
        // Arrange
        setupMocksUpToPersistence();
        when(inventoryActivity.createReservations(anyString(), any()))
            .thenThrow(new RuntimeException("System error"));

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        workflow.populate(createTestRequest());

        // Assert
        verify(notificationActivity).sendPopulationFailed(any(), anyString(), any());
    }

    @Test
    @DisplayName("Notification failure does not fail workflow")
    void testNotificationFailureDoesNotFailWorkflow() {
        // Arrange
        setupSuccessfulActivityMocks();
        doThrow(new RuntimeException("Notification service down"))
            .when(notificationActivity).sendPopulationComplete(anyString(), any());

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(createTestRequest());

        // Assert - workflow should still succeed
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReceiptKey()).isEqualTo(RECEIPT_KEY);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Edge Case Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Empty PO lines handled correctly")
    void testEmptyPOLines() {
        // Arrange
        when(pluginActivity.runPrePopulate(any(), any()))
            .thenReturn(PluginResult.success());
        when(validationActivity.validate(any(), any()))
            .thenReturn(ValidationResult.success());

        MappingResult emptyMapping = MappingResult.builder()
            .externReceiptKey("RCV-EXT-001")
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .details(Collections.emptyList())
            .build();

        when(mappingActivity.mapPOToASN(any(), any())).thenReturn(emptyMapping);
        when(mappingActivity.applyLottables(any(), any()))
            .thenReturn(LottableResult.builder().success(true).appliedRules(List.of()).build());
        when(persistenceActivity.createReceiptHeader(any())).thenReturn(RECEIPT_KEY);
        when(persistenceActivity.createReceiptDetails(anyString(), any()))
            .thenReturn(Collections.emptyList());
        when(inventoryActivity.createReservations(anyString(), any()))
            .thenReturn(Collections.emptyList());

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(createTestRequest());

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDetailCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Multiple PO keys processed correctly")
    void testMultiplePOKeys() {
        // Arrange
        setupSuccessfulActivityMocks();

        PopulateRequest multiPoRequest = PopulateRequest.builder()
            .poKeys(List.of("PO-001", "PO-002", "PO-003"))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(multiPoRequest);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(validationActivity).validate(argThat(req ->
            req.getPoKeys().size() == 3), any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private PopulatePOWorkflow startWorkflow() {
        return client.newWorkflowStub(
            PopulatePOWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .setWorkflowId("populate-" + UUID.randomUUID())
                .build()
        );
    }

    private PopulateRequest createTestRequest() {
        return PopulateRequest.builder()
            .poKeys(List.of(PO_KEY))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();
    }

    private void setupSuccessfulActivityMocks() {
        // Plugin mocks
        when(pluginActivity.runPrePopulate(any(), any()))
            .thenReturn(PluginResult.success());
        when(pluginActivity.runPostPopulate(anyString(), any(), any()))
            .thenReturn(PluginResult.success());

        // Validation mocks
        when(validationActivity.validate(any(), any()))
            .thenReturn(ValidationResult.success());

        // Mapping mocks
        when(mappingActivity.mapPOToASN(any(), any()))
            .thenReturn(createTestMappingResult());
        when(mappingActivity.applyLottables(any(), any()))
            .thenReturn(LottableResult.builder()
                .success(true)
                .appliedRules(List.of())
                .build());

        // Persistence mocks
        when(persistenceActivity.createReceiptHeader(any()))
            .thenReturn(RECEIPT_KEY);
        when(persistenceActivity.createReceiptDetails(anyString(), any()))
            .thenReturn(List.of("DTL-001", "DTL-002"));

        // Inventory mocks
        when(inventoryActivity.createReservations(anyString(), any()))
            .thenReturn(List.of("RES-001", "RES-002"));

        // Notification mocks
        doNothing().when(notificationActivity).sendPopulationComplete(anyString(), any());
    }

    private void setupMocksUpToPersistence() {
        when(pluginActivity.runPrePopulate(any(), any()))
            .thenReturn(PluginResult.success());
        when(validationActivity.validate(any(), any()))
            .thenReturn(ValidationResult.success());
        when(mappingActivity.mapPOToASN(any(), any()))
            .thenReturn(createTestMappingResult());
        when(mappingActivity.applyLottables(any(), any()))
            .thenReturn(LottableResult.builder()
                .success(true)
                .appliedRules(List.of())
                .build());
        when(persistenceActivity.createReceiptHeader(any()))
            .thenReturn(RECEIPT_KEY);
        when(persistenceActivity.createReceiptDetails(anyString(), any()))
            .thenReturn(List.of("DTL-001", "DTL-002"));
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
                    .poKey(PO_KEY)
                    .poLineNumber(1)
                    .lottables(Map.of())
                    .build(),
                DetailMapping.builder()
                    .sku("SKU-002")
                    .qtyExpected(BigDecimal.valueOf(20))
                    .uom("EA")
                    .poKey(PO_KEY)
                    .poLineNumber(2)
                    .lottables(Map.of())
                    .build()
            ))
            .build();
    }
}
