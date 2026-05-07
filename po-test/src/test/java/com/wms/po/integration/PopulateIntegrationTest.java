package com.wms.po.integration;

import com.wms.po.activity.*;
import com.wms.po.domain.dto.DetailMapping;
import com.wms.po.domain.model.*;
import com.wms.po.workflow.PopulatePOWorkflow;
import com.wms.po.workflow.impl.PopulatePOWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PopulatePO workflow.
 *
 * Uses Temporal's TestWorkflowEnvironment which provides an in-memory
 * workflow service - no external Temporal server required.
 *
 * Tests verify:
 * - Full workflow execution with stub activities
 * - Compensation behavior on failures
 * - Query method functionality
 * - Multiple PO consolidation
 *
 * Note: Uses stub activity implementations instead of Mockito mocks
 * because Temporal SDK doesn't support Mockito proxies for activities.
 */
class PopulateIntegrationTest {

    @RegisterExtension
    public static final TestWorkflowExtension testExtension =
        TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(PopulatePOWorkflowImpl.class)
            .setDoNotStart(true)
            .build();

    private static final String STORER_KEY = "NIKE_KR";
    private static final String FACILITY = "KR01";
    private static final String USER_ID = "integration_user";

    // Stub activities
    private TestValidationActivity validationActivity;
    private TestPluginActivity pluginActivity;
    private TestMappingActivity mappingActivity;
    private TestPersistenceActivity persistenceActivity;
    private TestInventoryActivity inventoryActivity;
    private TestLegacyBridgeActivity legacyBridgeActivity;
    private TestNotificationActivity notificationActivity;

    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    private WorkflowClient client;

    @BeforeEach
    void setUp(TestWorkflowEnvironment testEnv, Worker worker, WorkflowClient client) {
        this.testEnv = testEnv;
        this.worker = worker;
        this.client = client;

        // Create stub activity implementations
        validationActivity = new TestValidationActivity();
        pluginActivity = new TestPluginActivity();
        mappingActivity = new TestMappingActivity();
        persistenceActivity = new TestPersistenceActivity();
        inventoryActivity = new TestInventoryActivity();
        legacyBridgeActivity = new TestLegacyBridgeActivity();
        notificationActivity = new TestNotificationActivity();

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
    @DisplayName("Successfully populate single PO")
    void populateSinglePO() {
        // Arrange
        String receiptKey = "INT-RCV-001";
        persistenceActivity.receiptKeyToReturn = receiptKey;

        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("INT-PO-001"))
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
        assertThat(validationActivity.resolveContextCalled.get()).isTrue();
        assertThat(validationActivity.validateCalled.get()).isTrue();
        assertThat(mappingActivity.mapPOToASNCalled.get()).isTrue();
        assertThat(persistenceActivity.createHeaderCalled.get()).isTrue();
        assertThat(persistenceActivity.createDetailsCalled.get()).isTrue();
        assertThat(inventoryActivity.createReservationsCalled.get()).isTrue();
    }

    @Test
    @DisplayName("Successfully populate multiple POs into single receipt")
    void populateMultiplePOs() {
        // Arrange
        String receiptKey = "INT-RCV-MULTI-001";
        persistenceActivity.receiptKeyToReturn = receiptKey;
        persistenceActivity.detailKeysToReturn = List.of("DTL-001", "DTL-002", "DTL-003", "DTL-004");

        mappingActivity.mappingResultToReturn = MappingResult.builder()
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
        assertThat(validationActivity.lastValidatedRequest.get().getPoKeys()).hasSize(3);
    }

    @Test
    @DisplayName("Successfully populate with dual-write enabled")
    void populateWithDualWrite() {
        // Arrange
        String receiptKey = "INT-RCV-DW-001";
        persistenceActivity.receiptKeyToReturn = receiptKey;

        validationActivity.contextToReturn = VariationContext.builder()
            .version("V2")
            .region("ASIA-KR")
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .dualWriteEnabled(true)
            .build();

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
        assertThat(legacyBridgeActivity.syncToLegacyCalled.get()).isTrue();
        assertThat(legacyBridgeActivity.lastSyncedReceiptKey.get()).isEqualTo(receiptKey);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Validation Failure Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Validation failure returns descriptive error")
    void validationFailureReturnsError() {
        // Arrange
        validationActivity.validationResult = ValidationResult.failure(List.of(
            "PO-001 is already received",
            "PO-002 has invalid status"
        ));

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
        assertThat(persistenceActivity.createHeaderCalled.get()).isFalse();
    }

    @Test
    @DisplayName("Non-existent PO returns not found error")
    void nonExistentPOReturnsError() {
        // Arrange
        validationActivity.validationResult = ValidationResult.failure("PO NON_EXISTENT_PO not found");

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

        persistenceActivity.receiptKeyToReturn = receiptKey;
        persistenceActivity.detailKeysToReturn = detailKeys;
        inventoryActivity.shouldFail = true;

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

        // Verify compensations called
        assertThat(persistenceActivity.deleteDetailsCalled.get()).isTrue();
        assertThat(persistenceActivity.deleteHeaderCalled.get()).isTrue();
    }

    @Test
    @DisplayName("Legacy sync failure triggers compensation with reservations release")
    void legacySyncFailureTriggersFullCompensation() {
        // Arrange
        String receiptKey = "INT-RCV-LEGACY-001";
        List<String> detailKeys = List.of("DTL-001", "DTL-002");
        List<String> reservationIds = List.of("RES-001", "RES-002");

        validationActivity.contextToReturn = VariationContext.builder()
            .version("V2")
            .region("ASIA-KR")
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .dualWriteEnabled(true)
            .build();

        persistenceActivity.receiptKeyToReturn = receiptKey;
        persistenceActivity.detailKeysToReturn = detailKeys;
        inventoryActivity.reservationIdsToReturn = reservationIds;
        legacyBridgeActivity.shouldFail = true;

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

        // Verify all compensations
        assertThat(inventoryActivity.releaseReservationsCalled.get()).isTrue();
        assertThat(persistenceActivity.deleteDetailsCalled.get()).isTrue();
        assertThat(persistenceActivity.deleteHeaderCalled.get()).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Query Method Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Query methods return correct values during execution")
    void queryMethodsReturnCorrectValues() {
        // Arrange
        String receiptKey = "INT-RCV-QUERY-001";
        persistenceActivity.receiptKeyToReturn = receiptKey;

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

        List<DetailMapping> manyLines = new ArrayList<>();
        List<String> manyDetailKeys = new ArrayList<>();
        List<String> manyReservationIds = new ArrayList<>();

        for (int i = 1; i <= lineCount; i++) {
            manyLines.add(createDetailMapping("SKU-" + i, "INT-PO-LARGE-001", i, BigDecimal.TEN));
            manyDetailKeys.add("DTL-" + String.format("%03d", i));
            manyReservationIds.add("RES-" + String.format("%03d", i));
        }

        mappingActivity.mappingResultToReturn = MappingResult.builder()
            .externReceiptKey("EXT-" + receiptKey)
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .details(manyLines)
            .build();

        persistenceActivity.receiptKeyToReturn = receiptKey;
        persistenceActivity.detailKeysToReturn = manyDetailKeys;
        inventoryActivity.reservationIdsToReturn = manyReservationIds;

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
        persistenceActivity.receiptKeyToReturn = receiptKey;
        notificationActivity.shouldFail = true;

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
        return client.newWorkflowStub(
            PopulatePOWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .setWorkflowId("populate-int-" + UUID.randomUUID())
                .build()
        );
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

    // ═══════════════════════════════════════════════════════════════════════
    // Stub Activity Implementations
    // ═══════════════════════════════════════════════════════════════════════

    static class TestValidationActivity implements ValidationActivity {
        AtomicBoolean resolveContextCalled = new AtomicBoolean(false);
        AtomicBoolean validateCalled = new AtomicBoolean(false);
        AtomicReference<PopulateRequest> lastValidatedRequest = new AtomicReference<>();

        VariationContext contextToReturn = VariationContext.builder()
            .version("V2")
            .region("ASIA-KR")
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .dualWriteEnabled(false)
            .build();

        ValidationResult validationResult = ValidationResult.success();

        @Override
        public VariationContext resolveContext(PopulateRequest request) {
            resolveContextCalled.set(true);
            return contextToReturn;
        }

        @Override
        public ValidationResult validate(PopulateRequest request, VariationContext context) {
            validateCalled.set(true);
            lastValidatedRequest.set(request);
            return validationResult;
        }

        @Override
        public VariationContext resolveTradeReturnContext(TradeReturnRequest request) {
            return contextToReturn;
        }
    }

    static class TestPluginActivity implements PluginActivity {
        AtomicBoolean prePopulateCalled = new AtomicBoolean(false);
        AtomicBoolean postPopulateCalled = new AtomicBoolean(false);

        PluginResult prePopulateResult = PluginResult.success();
        PluginResult postPopulateResult = PluginResult.success();

        @Override
        public PluginResult runPrePopulate(PopulateRequest request, VariationContext context) {
            prePopulateCalled.set(true);
            return prePopulateResult;
        }

        @Override
        public PluginResult runPostPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
            postPopulateCalled.set(true);
            return postPopulateResult;
        }
    }

    static class TestMappingActivity implements MappingActivity {
        AtomicBoolean mapPOToASNCalled = new AtomicBoolean(false);
        AtomicBoolean applyLottablesCalled = new AtomicBoolean(false);

        MappingResult mappingResultToReturn = null;

        @Override
        public MappingResult mapPOToASN(PopulateRequest request, VariationContext context) {
            mapPOToASNCalled.set(true);
            if (mappingResultToReturn != null) {
                return mappingResultToReturn;
            }
            return MappingResult.builder()
                .externReceiptKey("EXT-RCV-001")
                .storerKey(STORER_KEY)
                .facility(FACILITY)
                .userId(USER_ID)
                .details(List.of(
                    DetailMapping.builder()
                        .sku("SKU-001")
                        .qtyExpected(BigDecimal.valueOf(100))
                        .uom("EA")
                        .poKey("PO-001")
                        .poLineNumber(1)
                        .lottables(Map.of())
                        .build(),
                    DetailMapping.builder()
                        .sku("SKU-002")
                        .qtyExpected(BigDecimal.valueOf(50))
                        .uom("EA")
                        .poKey("PO-001")
                        .poLineNumber(2)
                        .lottables(Map.of())
                        .build()
                ))
                .build();
        }

        @Override
        public LottableResult applyLottables(MappingResult mapping, VariationContext context) {
            applyLottablesCalled.set(true);
            return LottableResult.builder()
                .success(true)
                .appliedRules(List.of())
                .build();
        }
    }

    static class TestPersistenceActivity implements PersistenceActivity {
        AtomicBoolean createHeaderCalled = new AtomicBoolean(false);
        AtomicBoolean createDetailsCalled = new AtomicBoolean(false);
        AtomicBoolean deleteHeaderCalled = new AtomicBoolean(false);
        AtomicBoolean deleteDetailsCalled = new AtomicBoolean(false);
        AtomicBoolean updateStatusCalled = new AtomicBoolean(false);

        String receiptKeyToReturn = "RCV-001";
        List<String> detailKeysToReturn = List.of("DTL-001", "DTL-002");

        @Override
        public String createReceiptHeader(MappingResult mapping) {
            createHeaderCalled.set(true);
            return receiptKeyToReturn;
        }

        @Override
        public List<String> createReceiptDetails(String receiptKey, List<DetailMapping> details) {
            createDetailsCalled.set(true);
            return detailKeysToReturn;
        }

        @Override
        public void deleteReceiptHeader(String receiptKey) {
            deleteHeaderCalled.set(true);
        }

        @Override
        public void deleteReceiptDetails(List<String> detailKeys) {
            deleteDetailsCalled.set(true);
        }

        @Override
        public void updateReceiptStatus(String receiptKey, String status) {
            updateStatusCalled.set(true);
        }
    }

    static class TestInventoryActivity implements InventoryActivity {
        AtomicBoolean createReservationsCalled = new AtomicBoolean(false);
        AtomicBoolean releaseReservationsCalled = new AtomicBoolean(false);
        AtomicBoolean preAllocateCalled = new AtomicBoolean(false);
        AtomicBoolean releasePreAllocationCalled = new AtomicBoolean(false);

        List<String> reservationIdsToReturn = List.of("RES-001", "RES-002");
        boolean shouldFail = false;

        @Override
        public List<String> createReservations(String receiptKey, List<String> detailKeys) {
            if (shouldFail) {
                throw ApplicationFailure.newNonRetryableFailure(
                    "Inventory system unavailable", "INVENTORY_UNAVAILABLE");
            }
            createReservationsCalled.set(true);
            return reservationIdsToReturn;
        }

        @Override
        public void releaseReservations(List<String> reservationIds) {
            releaseReservationsCalled.set(true);
        }

        @Override
        public void preAllocateInventory(String receiptKey, List<String> detailKeys) {
            preAllocateCalled.set(true);
        }

        @Override
        public void releasePreAllocation(String receiptKey) {
            releasePreAllocationCalled.set(true);
        }
    }

    static class TestLegacyBridgeActivity implements LegacyBridgeActivity {
        AtomicBoolean syncToLegacyCalled = new AtomicBoolean(false);
        AtomicBoolean rollbackLegacyCalled = new AtomicBoolean(false);
        AtomicBoolean verifyLegacySyncCalled = new AtomicBoolean(false);
        AtomicReference<String> lastSyncedReceiptKey = new AtomicReference<>();

        boolean shouldFail = false;

        @Override
        public void syncToLegacy(String receiptKey, VariationContext context) {
            if (shouldFail) {
                throw ApplicationFailure.newNonRetryableFailure(
                    "Legacy system timeout", "LEGACY_SYNC_FAILED");
            }
            syncToLegacyCalled.set(true);
            lastSyncedReceiptKey.set(receiptKey);
        }

        @Override
        public void rollbackLegacy(String receiptKey, VariationContext context) {
            rollbackLegacyCalled.set(true);
        }

        @Override
        public boolean verifyLegacySync(String receiptKey, VariationContext context) {
            verifyLegacySyncCalled.set(true);
            return true;
        }
    }

    static class TestNotificationActivity implements NotificationActivity {
        AtomicBoolean sendPopulationCompleteCalled = new AtomicBoolean(false);
        AtomicBoolean sendPopulationFailedCalled = new AtomicBoolean(false);

        boolean shouldFail = false;

        @Override
        public void sendPopulationComplete(String receiptKey, PopulateRequest request) {
            if (shouldFail) {
                throw ApplicationFailure.newNonRetryableFailure(
                    "Kafka unavailable", "NOTIFICATION_FAILED");
            }
            sendPopulationCompleteCalled.set(true);
        }

        @Override
        public void sendPopulationFailed(String receiptKey, String errorMessage, PopulateRequest request) {
            sendPopulationFailedCalled.set(true);
        }

        @Override
        public void sendPopulationCancelled(String receiptKey, PopulateRequest request) {
        }

        @Override
        public void sendFinalizeComplete(String receiptKey, FinalizeRequest request) {
        }

        @Override
        public void sendFinalizeFailed(String receiptKey, String errorMessage, FinalizeRequest request) {
        }

        @Override
        public void sendFinalizeCancelled(String receiptKey, FinalizeRequest request) {
        }

        @Override
        public void sendTradeReturnComplete(String orderKey, String receiptKey) {
        }

        @Override
        public void sendTradeReturnFailed(String receiptKey, String errorMessage) {
        }
    }
}
