package com.wms.po.integration;

import com.wms.po.activity.*;
import com.wms.po.domain.model.*;
import com.wms.po.workflow.PopulatePOWorkflow;
import com.wms.po.workflow.impl.PopulatePOWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Lottable Mapping functionality in PopulatePO workflow.
 *
 * Tests verify:
 * - Lottable rules application based on variation context
 * - Client-specific lottable configurations
 * - Serial number tracking via lottables
 * - Expiry date handling
 * - Batch/lot number assignment
 *
 * Layer 2: JUnit/Spring Boot Integration Tests
 *
 * NOTE: Temporarily disabled because Temporal SDK doesn't support Mockito proxies
 * for activity implementations. These tests need to be converted to use stub
 * implementations (like TradeReturnWorkflowTest) to work properly.
 * TODO: Convert to stub implementations in Phase 2
 */
@Disabled("Temporal SDK incompatible with Mockito mocks - convert to stub implementations")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LottableMappingIntegrationTest {

    private static final String TASK_QUEUE = "lottable-integration-test-queue";
    private static final String STORER_KEY = "LOTTABLE_CLIENT";
    private static final String FACILITY = "FAC01";
    private static final String USER_ID = "lottable_user";

    private TestWorkflowEnvironment testEnv;
    private WorkflowClient workflowClient;
    private Worker worker;

    // Mock activities
    private ValidationActivity validationActivity;
    private PluginActivity pluginActivity;
    private MappingActivity mappingActivity;
    private PersistenceActivity persistenceActivity;
    private InventoryActivity inventoryActivity;
    private LegacyBridgeActivity legacyBridgeActivity;
    private NotificationActivity notificationActivity;
    private POStatusUpdateActivity poStatusUpdateActivity;

    @BeforeAll
    void setUpEnvironment() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker(TASK_QUEUE);
        workflowClient = testEnv.getWorkflowClient();

        // Create mocks
        validationActivity = mock(ValidationActivity.class);
        pluginActivity = mock(PluginActivity.class);
        mappingActivity = mock(MappingActivity.class);
        persistenceActivity = mock(PersistenceActivity.class);
        inventoryActivity = mock(InventoryActivity.class);
        legacyBridgeActivity = mock(LegacyBridgeActivity.class);
        notificationActivity = mock(NotificationActivity.class);
        poStatusUpdateActivity = mock(POStatusUpdateActivity.class);

        // Register workflow implementation
        worker.registerWorkflowImplementationTypes(PopulatePOWorkflowImpl.class);

        // Register activity implementations
        worker.registerActivitiesImplementations(
            validationActivity,
            pluginActivity,
            mappingActivity,
            persistenceActivity,
            inventoryActivity,
            legacyBridgeActivity,
            notificationActivity,
            poStatusUpdateActivity
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
        reset(validationActivity, pluginActivity, mappingActivity, persistenceActivity,
              inventoryActivity, legacyBridgeActivity, notificationActivity, poStatusUpdateActivity);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Basic Lottable Mapping Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Apply lottable rules for standard SKU")
    void applyLottableRulesForStandardSKU() {
        // Arrange
        PopulateRequest request = createPopulateRequest();
        VariationContext context = createStandardContext();

        setupContextResolution(context);
        setupValidationSuccess(context);
        setupPluginSuccess(context);

        MappingResult mappingResult = createMappingResultWithLottables(Map.of(
            "lottable01", "LOT-2024-001",
            "lottable02", "2024-12-31",
            "lottable03", "BATCH-A"
        ));
        when(mappingActivity.mapPOToASN(any(), any())).thenReturn(mappingResult);

        LottableResult lottableResult = LottableResult.builder()
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
        when(mappingActivity.applyLottables(any(), any())).thenReturn(lottableResult);

        setupPersistenceSuccess();
        setupInventorySuccess();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(mappingActivity).applyLottables(eq(mappingResult), any());
    }

    @Test
    @DisplayName("Apply serial number tracking via lottables")
    void applySerialNumberTrackingViaLottables() {
        // Arrange
        PopulateRequest request = createPopulateRequest();
        VariationContext context = createSerializedContext();

        setupContextResolution(context);
        setupValidationSuccess(context);
        setupPluginSuccess(context);

        MappingResult mappingResult = createMappingResult();
        when(mappingActivity.mapPOToASN(any(), any())).thenReturn(mappingResult);

        // Lottable10 typically holds serial number
        LottableResult lottableResult = LottableResult.builder()
            .success(true)
            .lottablesByDetail(Map.of(
                "DETAIL001", Map.of(
                    "lottable01", "LOT-2024-001",
                    "lottable10", "SN-12345678"
                )
            ))
            .appliedRules(List.of("SERIAL_NUMBER_RULE"))
            .build();
        when(mappingActivity.applyLottables(any(), any())).thenReturn(lottableResult);

        setupPersistenceSuccess();
        setupInventorySuccess();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(mappingActivity).applyLottables(any(), eq(context));
    }

    @Test
    @DisplayName("Apply expiry date lottable for perishable items")
    void applyExpiryDateLottableForPerishableItems() {
        // Arrange
        PopulateRequest request = createPopulateRequest();
        VariationContext context = createPerishableContext();

        setupContextResolution(context);
        setupValidationSuccess(context);
        setupPluginSuccess(context);

        MappingResult mappingResult = createMappingResult();
        when(mappingActivity.mapPOToASN(any(), any())).thenReturn(mappingResult);

        // Lottable02 typically holds expiry date
        LottableResult lottableResult = LottableResult.builder()
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
        when(mappingActivity.applyLottables(any(), any())).thenReturn(lottableResult);

        setupPersistenceSuccess();
        setupInventorySuccess();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(mappingActivity).applyLottables(any(), eq(context));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Client-Specific Lottable Configuration Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Apply client-specific lottable configuration")
    void applyClientSpecificLottableConfiguration() {
        // Arrange
        PopulateRequest request = createPopulateRequestForClient("CLIENT_ABC");
        VariationContext context = createClientSpecificContext("CLIENT_ABC");

        setupContextResolution(context);
        setupValidationSuccess(context);
        setupPluginSuccess(context);

        MappingResult mappingResult = createMappingResult();
        when(mappingActivity.mapPOToASN(any(), any())).thenReturn(mappingResult);

        // Client ABC has custom lottable mapping
        LottableResult lottableResult = LottableResult.builder()
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
        when(mappingActivity.applyLottables(any(), any())).thenReturn(lottableResult);

        setupPersistenceSuccess();
        setupInventorySuccess();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(mappingActivity).applyLottables(any(), eq(context));
    }

    @Test
    @DisplayName("Handle multiple lottable rules for same detail")
    void handleMultipleLottableRulesForSameDetail() {
        // Arrange
        PopulateRequest request = createPopulateRequest();
        VariationContext context = createStandardContext();

        setupContextResolution(context);
        setupValidationSuccess(context);
        setupPluginSuccess(context);

        MappingResult mappingResult = createMappingResult();
        when(mappingActivity.mapPOToASN(any(), any())).thenReturn(mappingResult);

        // Multiple rules apply to same detail
        LottableResult lottableResult = LottableResult.builder()
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
        when(mappingActivity.applyLottables(any(), any())).thenReturn(lottableResult);

        setupPersistenceSuccess();
        setupInventorySuccess();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(mappingActivity).applyLottables(any(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Multi-Detail Lottable Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Apply different lottables per detail line")
    void applyDifferentLottablesPerDetailLine() {
        // Arrange
        PopulateRequest request = createPopulateRequestWithMultiplePOs();
        VariationContext context = createStandardContext();

        setupContextResolution(context);
        setupValidationSuccess(context);
        setupPluginSuccess(context);

        MappingResult mappingResult = createMappingResult();
        when(mappingActivity.mapPOToASN(any(), any())).thenReturn(mappingResult);

        // Different lottables for each detail
        LottableResult lottableResult = LottableResult.builder()
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
        when(mappingActivity.applyLottables(any(), any())).thenReturn(lottableResult);

        setupPersistenceSuccess();
        setupInventorySuccess();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(mappingActivity).applyLottables(eq(mappingResult), any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Lottable Validation Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Handle lottable rule warnings")
    void handleLottableRuleWarnings() {
        // Arrange
        PopulateRequest request = createPopulateRequest();
        VariationContext context = createStandardContext();

        setupContextResolution(context);
        setupValidationSuccess(context);
        setupPluginSuccess(context);

        MappingResult mappingResult = createMappingResult();
        when(mappingActivity.mapPOToASN(any(), any())).thenReturn(mappingResult);

        // Lottable applied with warnings
        LottableResult lottableResult = LottableResult.builder()
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
        when(mappingActivity.applyLottables(any(), any())).thenReturn(lottableResult);

        setupPersistenceSuccess();
        setupInventorySuccess();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        // Warnings should be recorded but not fail the workflow
        verify(mappingActivity).applyLottables(any(), any());
    }

    @Test
    @DisplayName("Handle no lottable rules applicable")
    void handleNoLottableRulesApplicable() {
        // Arrange
        PopulateRequest request = createPopulateRequest();
        VariationContext context = createStandardContext();

        setupContextResolution(context);
        setupValidationSuccess(context);
        setupPluginSuccess(context);

        MappingResult mappingResult = createMappingResult();
        when(mappingActivity.mapPOToASN(any(), any())).thenReturn(mappingResult);

        // No lottable rules match
        LottableResult lottableResult = LottableResult.builder()
            .success(true)
            .lottablesByDetail(Collections.emptyMap())
            .appliedRules(Collections.emptyList())
            .build();
        when(mappingActivity.applyLottables(any(), any())).thenReturn(lottableResult);

        setupPersistenceSuccess();
        setupInventorySuccess();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
        verify(mappingActivity).applyLottables(any(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Batch/Lot Management Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Auto-generate lot number when not provided")
    void autoGenerateLotNumberWhenNotProvided() {
        // Arrange
        PopulateRequest request = createPopulateRequest();
        VariationContext context = createStandardContext();

        setupContextResolution(context);
        setupValidationSuccess(context);
        setupPluginSuccess(context);

        MappingResult mappingResult = createMappingResult();
        when(mappingActivity.mapPOToASN(any(), any())).thenReturn(mappingResult);

        // System generates lot number
        String autoGeneratedLot = "AUTO-LOT-" + System.currentTimeMillis();
        LottableResult lottableResult = LottableResult.builder()
            .success(true)
            .lottablesByDetail(Map.of(
                "DETAIL001", Map.of(
                    "lottable01", autoGeneratedLot
                )
            ))
            .appliedRules(List.of("AUTO_LOT_GENERATION_RULE"))
            .build();
        when(mappingActivity.applyLottables(any(), any())).thenReturn(lottableResult);

        setupPersistenceSuccess();
        setupInventorySuccess();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Preserve existing lot number from PO metadata")
    void preserveExistingLotNumberFromPOMetadata() {
        // Arrange
        String existingLotNumber = "CUSTOMER-LOT-12345";
        PopulateRequest request = PopulateRequest.builder()
            .poKeys(List.of("PO-001"))
            .facility(FACILITY)
            .storerKey(STORER_KEY)
            .userId(USER_ID)
            .metadata(Map.of("lotNumber", existingLotNumber))
            .build();

        VariationContext context = createStandardContext();

        setupContextResolution(context);
        setupValidationSuccess(context);
        setupPluginSuccess(context);

        MappingResult mappingResult = createMappingResult();
        when(mappingActivity.mapPOToASN(any(), any())).thenReturn(mappingResult);

        // Preserve provided lot number
        LottableResult lottableResult = LottableResult.builder()
            .success(true)
            .lottablesByDetail(Map.of(
                "DETAIL001", Map.of(
                    "lottable01", existingLotNumber
                )
            ))
            .appliedRules(List.of("PRESERVE_LOT_RULE"))
            .build();
        when(mappingActivity.applyLottables(any(), any())).thenReturn(lottableResult);

        setupPersistenceSuccess();
        setupInventorySuccess();

        // Act
        PopulatePOWorkflow workflow = startWorkflow();
        PopulateResult result = workflow.populate(request);

        // Assert
        assertThat(result.isSuccess()).isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════

    private PopulatePOWorkflow startWorkflow() {
        return workflowClient.newWorkflowStub(
            PopulatePOWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
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

    private PopulateRequest createPopulateRequestForClient(String clientCode) {
        return PopulateRequest.builder()
            .poKeys(List.of("PO-" + clientCode + "-001"))
            .storerKey(clientCode)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();
    }

    private PopulateRequest createPopulateRequestWithMultiplePOs() {
        return PopulateRequest.builder()
            .poKeys(List.of("PO-001", "PO-002", "PO-003"))
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .userId(USER_ID)
            .build();
    }

    private VariationContext createStandardContext() {
        return VariationContext.builder()
            .version("V2")
            .region("STANDARD")
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .dualWriteEnabled(false)
            .build();
    }

    private VariationContext createSerializedContext() {
        return VariationContext.builder()
            .version("V2")
            .region("SERIALIZED")
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .dualWriteEnabled(false)
            .build();
    }

    private VariationContext createPerishableContext() {
        return VariationContext.builder()
            .version("V2")
            .region("PERISHABLE")
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .dualWriteEnabled(false)
            .build();
    }

    private VariationContext createClientSpecificContext(String clientCode) {
        return VariationContext.builder()
            .version("V2")
            .region("CLIENT_SPECIFIC")
            .storerKey(clientCode)
            .facility(FACILITY)
            .dualWriteEnabled(false)
            .build();
    }

    private MappingResult createMappingResult() {
        return MappingResult.builder()
            .externReceiptKey("RCV-001")
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .build();
    }

    private MappingResult createMappingResultWithLottables(Map<String, String> lottables) {
        return MappingResult.builder()
            .externReceiptKey("RCV-001")
            .storerKey(STORER_KEY)
            .facility(FACILITY)
            .lottablesApplied(true)
            .build();
    }

    // Setup helper methods

    private void setupContextResolution(VariationContext context) {
        when(validationActivity.resolveContext(any())).thenReturn(context);
    }

    private void setupValidationSuccess(VariationContext context) {
        when(validationActivity.validate(any(), eq(context)))
            .thenReturn(ValidationResult.success());
    }

    private void setupPluginSuccess(VariationContext context) {
        when(pluginActivity.runPrePopulate(any(), eq(context)))
            .thenReturn(PluginResult.builder()
                .shouldContinue(true)
                .pluginsExecuted(0)
                .executedPlugins(Collections.emptyList())
                .build());
        when(pluginActivity.runPostPopulate(anyString(), any(), eq(context)))
            .thenReturn(PluginResult.builder()
                .shouldContinue(true)
                .pluginsExecuted(0)
                .executedPlugins(Collections.emptyList())
                .build());
    }

    private void setupPersistenceSuccess() {
        when(persistenceActivity.createReceiptHeader(any()))
            .thenReturn("RCV-001");
        when(persistenceActivity.createReceiptDetails(anyString(), any()))
            .thenReturn(List.of("DET-001"));
    }

    private void setupInventorySuccess() {
        when(inventoryActivity.createReservations(anyString(), any()))
            .thenReturn(List.of("RES-001"));
    }
}
