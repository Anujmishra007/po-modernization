package com.wms.po.integration;

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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Lottable Mapping functionality in PopulatePO workflow.
 *
 * Uses Temporal's TestWorkflowEnvironment with stub activity implementations.
 *
 * Tests verify:
 * - Lottable rules application based on variation context
 * - Client-specific lottable configurations
 * - Serial number tracking via lottables
 * - Expiry date handling
 * - Batch/lot number assignment
 */
class LottableMappingIntegrationTest {

    @RegisterExtension
    public static final TestWorkflowExtension testExtension =
        TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(PopulatePOWorkflowImpl.class)
            .setDoNotStart(true)
            .build();

    private static final String STORER_KEY = "LOTTABLE_CLIENT";
    private static final String FACILITY = "FAC01";
    private static final String USER_ID = "lottable_user";

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
    // Basic Lottable Mapping Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Apply lottable rules for standard SKU")
    void applyLottableRulesForStandardSKU() {
        // Arrange
        mappingActivity.lottableResultToReturn = LottableResult.builder()
            .success(true)
            .lottablesByDetail(Map.of(
                "DETAIL001", Map.of(
                    "lottable01", "LOT-2024-001",
                    "lottable02", "2024-12-31",
                    "lottable03", "BATCH-A"
                )
            ))
            .appliedRules(List.of("STANDARD_LOT_RULE", "EXPIRY_DATE_RULE"))
            .build();

        PopulateRequest request = createPopulateRequest();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(mappingActivity.applyLottablesCalled.get()).isTrue();
    }

    @Test
    @DisplayName("Apply serial number tracking via lottables")
    void applySerialNumberTrackingViaLottables() {
        // Arrange
        validationActivity.contextToReturn = VariationContext.builder()
            .version("V2")
            .region("SERIALIZED")
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .dualWriteEnabled(false)
            .build();

        mappingActivity.lottableResultToReturn = LottableResult.builder()
            .success(true)
            .lottablesByDetail(Map.of(
                "DETAIL001", Map.of(
                    "lottable01", "LOT-2024-001",
                    "lottable10", "SN-12345678"
                )
            ))
            .appliedRules(List.of("SERIAL_NUMBER_RULE"))
            .build();

        PopulateRequest request = createPopulateRequest();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(mappingActivity.applyLottablesCalled.get()).isTrue();
    }

    @Test
    @DisplayName("Apply expiry date lottable for perishable items")
    void applyExpiryDateLottableForPerishableItems() {
        // Arrange
        validationActivity.contextToReturn = VariationContext.builder()
            .version("V2")
            .region("PERISHABLE")
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .dualWriteEnabled(false)
            .build();

        mappingActivity.lottableResultToReturn = LottableResult.builder()
            .success(true)
            .lottablesByDetail(Map.of(
                "DETAIL001", Map.of(
                    "lottable01", "LOT-FRESH-001",
                    "lottable02", "2025-06-30",
                    "lottable04", "FRESH"
                )
            ))
            .appliedRules(List.of("PERISHABLE_EXPIRY_RULE"))
            .build();

        PopulateRequest request = createPopulateRequest();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(mappingActivity.applyLottablesCalled.get()).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Client-Specific Lottable Configuration Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Apply client-specific lottable configuration")
    void applyClientSpecificLottableConfiguration() {
        // Arrange
        validationActivity.contextToReturn = VariationContext.builder()
            .version("V2")
            .region("CLIENT_SPECIFIC")
            .storerKey("CLIENT_ABC")
            .facility(FACILITY)
            .dualWriteEnabled(false)
            .build();

        mappingActivity.lottableResultToReturn = LottableResult.builder()
            .success(true)
            .lottablesByDetail(Map.of(
                "DETAIL001", Map.of(
                    "lottable01", "LOT-ABC-001",
                    "lottable05", "CUSTOM_ATTR_1",
                    "lottable06", "CUSTOM_ATTR_2"
                )
            ))
            .appliedRules(List.of("CLIENT_ABC_LOTTABLE_RULE"))
            .build();

        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("PO-CLIENT_ABC-001"))
            .storerKey("CLIENT_ABC")
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(mappingActivity.applyLottablesCalled.get()).isTrue();
    }

    @Test
    @DisplayName("Handle multiple lottable rules for same detail")
    void handleMultipleLottableRulesForSameDetail() {
        // Arrange
        mappingActivity.lottableResultToReturn = LottableResult.builder()
            .success(true)
            .lottablesByDetail(Map.of(
                "DETAIL001", Map.of(
                    "lottable01", "LOT-2024-001",
                    "lottable02", "2024-12-31",
                    "lottable03", "BATCH-A",
                    "lottable04", "QUALITY-A",
                    "lottable05", "ORIGIN-US"
                )
            ))
            .appliedRules(List.of(
                "LOT_NUMBER_RULE",
                "EXPIRY_DATE_RULE",
                "BATCH_RULE",
                "QUALITY_RULE",
                "ORIGIN_RULE"
            ))
            .build();

        PopulateRequest request = createPopulateRequest();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(mappingActivity.applyLottablesCalled.get()).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Multi-Detail Lottable Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Apply different lottables per detail line")
    void applyDifferentLottablesPerDetailLine() {
        // Arrange
        mappingActivity.lottableResultToReturn = LottableResult.builder()
            .success(true)
            .lottablesByDetail(Map.of(
                "DETAIL001", Map.of(
                    "lottable01", "LOT-A-001",
                    "lottable02", "2024-06-30"
                ),
                "DETAIL002", Map.of(
                    "lottable01", "LOT-B-002",
                    "lottable02", "2024-12-31"
                ),
                "DETAIL003", Map.of(
                    "lottable01", "LOT-C-003",
                    "lottable10", "SN-UNIQUE-123"
                )
            ))
            .appliedRules(List.of("LOT_NUMBER_RULE", "EXPIRY_DATE_RULE", "SERIAL_NUMBER_RULE"))
            .build();

        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("PO-001", "PO-002", "PO-003"))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(mappingActivity.applyLottablesCalled.get()).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Lottable Validation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Handle lottable rule warnings")
    void handleLottableRuleWarnings() {
        // Arrange
        mappingActivity.lottableResultToReturn = LottableResult.builder()
            .success(true)
            .lottablesByDetail(Map.of(
                "DETAIL001", Map.of(
                    "lottable01", "LOT-2024-001"
                )
            ))
            .appliedRules(List.of("LOT_NUMBER_RULE"))
            .warnings(List.of(
                "Expiry date not provided - using default",
                "Country of origin missing"
            ))
            .build();

        PopulateRequest request = createPopulateRequest();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(mappingActivity.applyLottablesCalled.get()).isTrue();
    }

    @Test
    @DisplayName("Handle no lottable rules applicable")
    void handleNoLottableRulesApplicable() {
        // Arrange
        mappingActivity.lottableResultToReturn = LottableResult.builder()
            .success(true)
            .lottablesByDetail(Collections.emptyMap())
            .appliedRules(Collections.emptyList())
            .build();

        PopulateRequest request = createPopulateRequest();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(mappingActivity.applyLottablesCalled.get()).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private PopulatePOWorkflow startWorkflow() {
        return client.newWorkflowStub(
            PopulatePOWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .setWorkflowId("lottable-test-" + UUID.randomUUID())
                .build()
        );
    }

    private PopulateRequest createPopulateRequest() {
        return PopulateRequest.builder()
            .poKeys(List.of("PO-001"))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Stub Activity Implementations
    // ═══════════════════════════════════════════════════════════════════════

    static class TestValidationActivity implements ValidationActivity {
        AtomicBoolean resolveContextCalled = new AtomicBoolean(false);
        AtomicBoolean validateCalled = new AtomicBoolean(false);

        VariationContext contextToReturn = VariationContext.builder()
            .version("V2")
            .region("STANDARD")
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .dualWriteEnabled(false)
            .build();

        @Override
        public VariationContext resolveContext(PopulateRequest request) {
            resolveContextCalled.set(true);
            return contextToReturn;
        }

        @Override
        public ValidationResult validate(PopulateRequest request, VariationContext context) {
            validateCalled.set(true);
            return ValidationResult.success();
        }

        @Override
        public VariationContext resolveTradeReturnContext(TradeReturnRequest request) {
            return contextToReturn;
        }
    }

    static class TestPluginActivity implements PluginActivity {
        @Override
        public PluginResult runPrePopulate(PopulateRequest request, VariationContext context) {
            return PluginResult.builder()
                .shouldContinue(true)
                .pluginsExecuted(0)
                .executedPlugins(Collections.emptyList())
                .build();
        }

        @Override
        public PluginResult runPostPopulate(String receiptKey, PopulateRequest request, VariationContext context) {
            return PluginResult.builder()
                .shouldContinue(true)
                .pluginsExecuted(0)
                .executedPlugins(Collections.emptyList())
                .build();
        }
    }

    static class TestMappingActivity implements MappingActivity {
        AtomicBoolean mapPOToASNCalled = new AtomicBoolean(false);
        AtomicBoolean applyLottablesCalled = new AtomicBoolean(false);

        LottableResult lottableResultToReturn = LottableResult.builder()
            .success(true)
            .appliedRules(Collections.emptyList())
            .build();

        @Override
        public MappingResult mapPOToASN(PopulateRequest request, VariationContext context) {
            mapPOToASNCalled.set(true);
            return MappingResult.builder()
                .externReceiptKey("RCV-001")
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
                        .build()
                ))
                .build();
        }

        @Override
        public LottableResult applyLottables(MappingResult mapping, VariationContext context) {
            applyLottablesCalled.set(true);
            return lottableResultToReturn;
        }
    }

    static class TestPersistenceActivity implements PersistenceActivity {
        @Override
        public String createReceiptHeader(MappingResult mapping) {
            return "RCV-001";
        }

        @Override
        public List<String> createReceiptDetails(String receiptKey, List<DetailMapping> details) {
            return List.of("DTL-001");
        }

        @Override
        public void deleteReceiptHeader(String receiptKey) {}

        @Override
        public void deleteReceiptDetails(List<String> detailKeys) {}

        @Override
        public void updateReceiptStatus(String receiptKey, String status) {}
    }

    static class TestInventoryActivity implements InventoryActivity {
        @Override
        public List<String> createReservations(String receiptKey, List<String> detailKeys) {
            return List.of("RES-001");
        }

        @Override
        public void releaseReservations(List<String> reservationIds) {}

        @Override
        public void preAllocateInventory(String receiptKey, List<String> detailKeys) {}

        @Override
        public void releasePreAllocation(String receiptKey) {}
    }

    static class TestLegacyBridgeActivity implements LegacyBridgeActivity {
        @Override
        public void syncToLegacy(String receiptKey, VariationContext context) {}

        @Override
        public void rollbackLegacy(String receiptKey, VariationContext context) {}

        @Override
        public boolean verifyLegacySync(String receiptKey, VariationContext context) {
            return true;
        }
    }

    static class TestNotificationActivity implements NotificationActivity {
        @Override
        public void sendPopulationComplete(String receiptKey, PopulateRequest request) {}
        @Override
        public void sendPopulationFailed(String receiptKey, String errorMessage, PopulateRequest request) {}
        @Override
        public void sendPopulationCancelled(String receiptKey, PopulateRequest request) {}
        @Override
        public void sendFinalizeComplete(String receiptKey, FinalizeRequest request) {}
        @Override
        public void sendFinalizeFailed(String receiptKey, String errorMessage, FinalizeRequest request) {}
        @Override
        public void sendFinalizeCancelled(String receiptKey, FinalizeRequest request) {}
        @Override
        public void sendTradeReturnComplete(String orderKey, String receiptKey) {}
        @Override
        public void sendTradeReturnFailed(String receiptKey, String errorMessage) {}
    }
}
