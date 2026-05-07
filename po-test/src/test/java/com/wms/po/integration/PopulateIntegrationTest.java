package com.wms.po.integration;

import com.wms.po.activity.*;
import com.wms.po.domain.dto.DetailMapping;
import com.wms.po.domain.model.*;
import com.wms.po.workflow.PopulatePOWorkflow;
import com.wms.po.workflow.impl.PopulatePOWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for PopulatePO workflow.
 *
 * Uses Temporal's TestWorkflowEnvironment which provides an in-memory
 * workflow service - no external Temporal server required.
 *
 * Tests verify:
 * - Full workflow execution with mock activities
 * - Compensation behavior on failures
 * - Query method functionality
 * - Multiple PO consolidation
 *
 * NOTE: Temporarily disabled because Temporal SDK doesn't support Mockito proxies
 * for activity implementations. These tests need to be converted to use stub
 * implementations (like TradeReturnWorkflowTest) to work properly.
 * TODO: Convert to stub implementations in Phase 2
 */
@Disabled("Temporal SDK incompatible with Mockito mocks - convert to stub implementations")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PopulateIntegrationTest {

    private static final String TASK_QUEUE = "populate-integration-test-queue";
    private static final String STORER_KEY = "NIKE_KR";
    private static final String FACILITY = "KR01";
    private static final String USER_ID = "integration_user";

    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    private WorkflowClient workflowClient;

    // Mock activities
    private ValidationActivity validationActivity;
    private PluginActivity pluginActivity;
    private MappingActivity mappingActivity;
    private PersistenceActivity persistenceActivity;
    private InventoryActivity inventoryActivity;
    private LegacyBridgeActivity legacyBridgeActivity;
    private NotificationActivity notificationActivity;

    @BeforeAll
    void setUpEnvironment() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker(TASK_QUEUE);
        workflowClient = testEnv.getWorkflowClient();

        // Create mock activities
        validationActivity = mock(ValidationActivity.class);
        pluginActivity = mock(PluginActivity.class);
        mappingActivity = mock(MappingActivity.class);
        persistenceActivity = mock(PersistenceActivity.class);
        inventoryActivity = mock(InventoryActivity.class);
        legacyBridgeActivity = mock(LegacyBridgeActivity.class);
        notificationActivity = mock(NotificationActivity.class);

        // Register workflows and activities
        worker.registerWorkflowImplementationTypes(PopulatePOWorkflowImpl.class);
        worker.registerActivitiesImplementations(
            validationActivity,
            pluginActivity,
            mappingActivity,
            persistenceActivity,
            inventoryActivity,
            legacyBridgeActivity,
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
        reset(validationActivity, pluginActivity, mappingActivity,
              persistenceActivity, inventoryActivity, legacyBridgeActivity,
              notificationActivity);

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
    @DisplayName("Successfully populate single PO")
    void populateSinglePO() {
        // Arrange
        String poKey = "INT-PO-001";
        String receiptKey = "INT-RCV-001";

        setupSuccessfulMocks(receiptKey);

        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of(poKey))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReceiptKey()).isEqualTo(receiptKey);
        assertThat(result.getDetailCount()).isGreaterThan(0);
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        // Verify activities called
        verify(validationActivity).resolveContext(any());
        verify(validationActivity).validate(any(), any());
        verify(mappingActivity).mapPOToASN(any(), any());
        verify(persistenceActivity).createReceiptHeader(any());
        verify(persistenceActivity).createReceiptDetails(eq(receiptKey), any());
        verify(inventoryActivity).createReservations(eq(receiptKey), any());
    }

    @Test
    @DisplayName("Successfully populate multiple POs into single receipt")
    void populateMultiplePOs() {
        // Arrange
        String receiptKey = "INT-RCV-MULTI-001";
        setupSuccessfulMocks(receiptKey);

        // Create mapping with details from multiple POs
        MappingResult multiPoMapping = MappingResult.builder()
            .externReceiptKey("EXT-" + receiptKey)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .details(List.of(
                createDetailMapping("SKU-001", "INT-PO-001", 1, BigDecimal.valueOf(100)),
                createDetailMapping("SKU-002", "INT-PO-001", 2, BigDecimal.valueOf(50)),
                createDetailMapping("SKU-003", "INT-PO-002", 1, BigDecimal.valueOf(75)),
                createDetailMapping("SKU-004", "INT-PO-003", 1, BigDecimal.valueOf(200))
            ))
            .build();

        when(mappingActivity.mapPOToASN(any(), any())).thenReturn(multiPoMapping);
        when(persistenceActivity.createReceiptDetails(anyString(), any()))
            .thenReturn(List.of("DTL-001", "DTL-002", "DTL-003", "DTL-004"));

        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("INT-PO-001", "INT-PO-002", "INT-PO-003"))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDetailCount()).isEqualTo(4);

        verify(validationActivity).validate(argThat(req ->
            req.getPoKeys().size() == 3), any());
    }

    @Test
    @DisplayName("Successfully populate with dual-write enabled")
    void populateWithDualWrite() {
        // Arrange
        String receiptKey = "INT-RCV-DW-001";

        when(validationActivity.resolveContext(any())).thenReturn(
            VariationContext.builder()
                .version("V2")
                .region("ASIA-KR")
                .storerKey(STORER_KEY)
                .facility(FACILITY)
                .dualWriteEnabled(true)  // Enable dual-write
                .build()
        );

        setupSuccessfulMocks(receiptKey);
        doNothing().when(legacyBridgeActivity).syncToLegacy(anyString(), any());

        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("INT-PO-DW-001"))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(legacyBridgeActivity).syncToLegacy(eq(receiptKey), any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Validation Failure Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Validation failure returns descriptive error")
    void validationFailureReturnsError() {
        // Arrange
        when(pluginActivity.runPrePopulate(any(), any()))
            .thenReturn(PluginResult.success());
        when(validationActivity.validate(any(), any()))
            .thenReturn(ValidationResult.failure(List.of(
                "PO-001 is already received",
                "PO-002 has invalid status"
            )));

        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("PO-001", "PO-002"))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).hasSize(2);
        assertThat(result.getErrors()).contains("PO-001 is already received");
        assertThat(result.getErrors()).contains("PO-002 has invalid status");

        // Verify no persistence occurred
        verify(persistenceActivity, never()).createReceiptHeader(any());
    }

    @Test
    @DisplayName("Non-existent PO returns not found error")
    void nonExistentPOReturnsError() {
        // Arrange
        when(pluginActivity.runPrePopulate(any(), any()))
            .thenReturn(PluginResult.success());
        when(validationActivity.validate(any(), any()))
            .thenReturn(ValidationResult.failure("PO NON_EXISTENT_PO not found"));

        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("NON_EXISTENT_PO"))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("not found"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Compensation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Inventory failure triggers full compensation")
    void inventoryFailureTriggersCompensation() {
        // Arrange
        String receiptKey = "INT-RCV-COMP-001";
        List<String> detailKeys = List.of("DTL-001", "DTL-002");

        when(pluginActivity.runPrePopulate(any(), any()))
            .thenReturn(PluginResult.success());
        when(validationActivity.validate(any(), any()))
            .thenReturn(ValidationResult.success());
        when(mappingActivity.mapPOToASN(any(), any()))
            .thenReturn(createMappingResult(receiptKey));
        when(mappingActivity.applyLottables(any(), any()))
            .thenReturn(LottableResult.builder().success(true).appliedRules(List.of()).build());
        when(persistenceActivity.createReceiptHeader(any()))
            .thenReturn(receiptKey);
        when(persistenceActivity.createReceiptDetails(anyString(), any()))
            .thenReturn(detailKeys);

        // Inventory creation fails
        when(inventoryActivity.createReservations(anyString(), any()))
            .thenThrow(new RuntimeException("Inventory system unavailable"));

        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("INT-PO-COMP-001"))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.FAILED);

        // Verify compensations called in reverse order
        verify(persistenceActivity).deleteReceiptDetails(detailKeys);
        verify(persistenceActivity).deleteReceiptHeader(receiptKey);
    }

    @Test
    @DisplayName("Legacy sync failure triggers compensation with reservations release")
    void legacySyncFailureTriggersFullCompensation() {
        // Arrange
        String receiptKey = "INT-RCV-LEGACY-001";
        List<String> detailKeys = List.of("DTL-001", "DTL-002");
        List<String> reservationIds = List.of("RES-001", "RES-002");

        when(validationActivity.resolveContext(any())).thenReturn(
            VariationContext.builder()
                .version("V2")
                .region("ASIA-KR")
                .storerKey(STORER_KEY)
                .facility(FACILITY)
                .dualWriteEnabled(true)
                .build()
        );

        when(pluginActivity.runPrePopulate(any(), any()))
            .thenReturn(PluginResult.success());
        when(validationActivity.validate(any(), any()))
            .thenReturn(ValidationResult.success());
        when(mappingActivity.mapPOToASN(any(), any()))
            .thenReturn(createMappingResult(receiptKey));
        when(mappingActivity.applyLottables(any(), any()))
            .thenReturn(LottableResult.builder().success(true).appliedRules(List.of()).build());
        when(persistenceActivity.createReceiptHeader(any()))
            .thenReturn(receiptKey);
        when(persistenceActivity.createReceiptDetails(anyString(), any()))
            .thenReturn(detailKeys);
        when(inventoryActivity.createReservations(anyString(), any()))
            .thenReturn(reservationIds);

        // Legacy sync fails
        doThrow(new RuntimeException("Legacy system timeout"))
            .when(legacyBridgeActivity).syncToLegacy(anyString(), any());

        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("INT-PO-LEGACY-001"))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isFalse();

        // Verify all compensations in reverse order
        verify(inventoryActivity).releaseReservations(reservationIds);
        verify(persistenceActivity).deleteReceiptDetails(detailKeys);
        verify(persistenceActivity).deleteReceiptHeader(receiptKey);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Query Method Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Query methods return correct values during execution")
    void queryMethodsReturnCorrectValues() {
        // Arrange
        String receiptKey = "INT-RCV-QUERY-001";
        setupSuccessfulMocks(receiptKey);

        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("INT-PO-QUERY-001"))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(workflow.getCurrentStep()).isEqualTo("COMPLETED");
        assertThat(workflow.getProgress()).isGreaterThanOrEqualTo(90);

        List<String> completedSteps = workflow.getCompletedSteps();
        assertThat(completedSteps).isNotEmpty();
        assertThat(completedSteps).anyMatch(s -> s.contains("RESOLVE_CONTEXT"));
        assertThat(completedSteps).anyMatch(s -> s.contains("VALIDATION"));
        assertThat(completedSteps).anyMatch(s -> s.contains("CREATE_HEADER"));
        assertThat(completedSteps).anyMatch(s -> s.contains("CREATE_DETAILS"));
        assertThat(completedSteps).anyMatch(s -> s.contains("CREATE_RESERVATIONS"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Edge Case Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Large PO with many lines handled correctly")
    void largePOHandledCorrectly() {
        // Arrange
        String receiptKey = "INT-RCV-LARGE-001";
        int lineCount = 100;

        // Create mapping with many lines
        List<DetailMapping> manyLines = new java.util.ArrayList<>();
        List<String> manyDetailKeys = new java.util.ArrayList<>();
        List<String> manyReservationIds = new java.util.ArrayList<>();

        for (int i = 1; i <= lineCount; i++) {
            manyLines.add(createDetailMapping("SKU-" + i, "INT-PO-LARGE-001", i, BigDecimal.TEN));
            manyDetailKeys.add("DTL-" + String.format("%03d", i));
            manyReservationIds.add("RES-" + String.format("%03d", i));
        }

        MappingResult largeMapping = MappingResult.builder()
            .externReceiptKey("EXT-" + receiptKey)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .details(manyLines)
            .build();

        when(pluginActivity.runPrePopulate(any(), any()))
            .thenReturn(PluginResult.success());
        when(validationActivity.validate(any(), any()))
            .thenReturn(ValidationResult.success());
        when(mappingActivity.mapPOToASN(any(), any()))
            .thenReturn(largeMapping);
        when(mappingActivity.applyLottables(any(), any()))
            .thenReturn(LottableResult.builder().success(true).appliedRules(List.of()).build());
        when(persistenceActivity.createReceiptHeader(any()))
            .thenReturn(receiptKey);
        when(persistenceActivity.createReceiptDetails(anyString(), any()))
            .thenReturn(manyDetailKeys);
        when(inventoryActivity.createReservations(anyString(), any()))
            .thenReturn(manyReservationIds);
        doNothing().when(notificationActivity).sendPopulationComplete(anyString(), any());
        when(pluginActivity.runPostPopulate(anyString(), any(), any()))
            .thenReturn(PluginResult.success());

        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("INT-PO-LARGE-001"))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDetailCount()).isEqualTo(lineCount);
    }

    @Test
    @DisplayName("Notification failure does not fail workflow")
    void notificationFailureDoesNotFailWorkflow() {
        // Arrange
        String receiptKey = "INT-RCV-NOTIFY-001";
        setupSuccessfulMocks(receiptKey);

        // Notification fails
        doThrow(new RuntimeException("Kafka unavailable"))
            .when(notificationActivity).sendPopulationComplete(anyString(), any());

        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("INT-PO-NOTIFY-001"))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert - workflow should still succeed
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getReceiptKey()).isEqualTo(receiptKey);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private PopulatePOWorkflow startWorkflow() {
        return workflowClient.newWorkflowStub(
            PopulatePOWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowId("populate-int-" + UUID.randomUUID())
                .build()
        );
    }

    private void setupSuccessfulMocks(String receiptKey) {
        when(pluginActivity.runPrePopulate(any(), any()))
            .thenReturn(PluginResult.success());
        when(pluginActivity.runPostPopulate(anyString(), any(), any()))
            .thenReturn(PluginResult.success());
        when(validationActivity.validate(any(), any()))
            .thenReturn(ValidationResult.success());
        when(mappingActivity.mapPOToASN(any(), any()))
            .thenReturn(createMappingResult(receiptKey));
        when(mappingActivity.applyLottables(any(), any()))
            .thenReturn(LottableResult.builder().success(true).appliedRules(List.of()).build());
        when(persistenceActivity.createReceiptHeader(any()))
            .thenReturn(receiptKey);
        when(persistenceActivity.createReceiptDetails(anyString(), any()))
            .thenReturn(List.of("DTL-001", "DTL-002"));
        when(inventoryActivity.createReservations(anyString(), any()))
            .thenReturn(List.of("RES-001", "RES-002"));
        doNothing().when(notificationActivity).sendPopulationComplete(anyString(), any());
    }

    private MappingResult createMappingResult(String receiptKey) {
        return MappingResult.builder()
            .externReceiptKey("EXT-" + receiptKey)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .details(List.of(
                createDetailMapping("SKU-001", "PO-001", 1, BigDecimal.valueOf(100)),
                createDetailMapping("SKU-002", "PO-001", 2, BigDecimal.valueOf(50))
            ))
            .build();
    }

    private DetailMapping createDetailMapping(String sku, String poKey, int lineNum, BigDecimal qty) {
        return DetailMapping.builder()
            .sku(sku)
            .qtyExpected(qty)
            .uom("EA")
            .poKey(poKey)
            .poLineNumber(lineNum)
            .lottables(Map.of())
            .build();
    }
}
