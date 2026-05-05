package com.wms.po.infrastructure.transaction;

import lombok.Data;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Maps legacy stored procedures to saga steps with compensation.
 * This is the bridge between V0/V2 SPs and the modern microservice architecture.
 *
 * Legacy SP Transaction Pattern:
 * ───────────────────────────────
 * BEGIN TRAN
 *   EXEC lsp_ValidatePO @POKEY
 *   EXEC nsp_MapLottables @POKEY, @STORERKEY
 *   EXEC isp_Receipt @STORERKEY, @RECEIPTKEY OUTPUT
 *   EXEC isp_ReceiptDetail @RECEIPTKEY, @SKU, ...
 *   EXEC usp_AllocateInventory @RECEIPTKEY
 *   EXEC usp_UpdatePOStatus @POKEY, @STATUS
 * COMMIT TRAN -- or ROLLBACK on error
 *
 * Modern Saga Pattern:
 * ───────────────────────────────
 * Step 1: validatePO()           [no compensation - read-only]
 * Step 2: mapLottables()         [no compensation - transformation]
 * Step 3: createReceipt()        → compensation: deleteReceipt()
 * Step 4: createReceiptDetails() → compensation: deleteReceiptDetails()
 * Step 5: allocateInventory()    → compensation: releaseInventory()
 * Step 6: updatePOStatus()       → compensation: restorePOStatus()
 */
@Component
@Slf4j
public class SPMigrationMapper {

    // ═══════════════════════════════════════════════════════════════
    // SP TO SAGA STEP MAPPINGS
    // ═══════════════════════════════════════════════════════════════

    private static final Map<String, SPMapping> SP_MAPPINGS = new HashMap<>();

    static {
        // Validation SPs (no compensation needed - read-only)
        SP_MAPPINGS.put("lsp_ValidatePO", SPMapping.builder()
                .spName("lsp_ValidatePO")
                .sagaStep("VALIDATION")
                .activityClass("ValidationActivity")
                .activityMethod("validate")
                .compensatable(false)
                .tables(List.of("ORDERS", "ORDERDETAIL", "STORER", "SKU"))
                .build());

        SP_MAPPINGS.put("nsp_ValidateSKU", SPMapping.builder()
                .spName("nsp_ValidateSKU")
                .sagaStep("VALIDATION")
                .activityClass("ValidationActivity")
                .activityMethod("validateSKU")
                .compensatable(false)
                .tables(List.of("SKU", "PACK"))
                .build());

        // Mapping SPs (no compensation - pure transformation)
        SP_MAPPINGS.put("nsp_MapLottables", SPMapping.builder()
                .spName("nsp_MapLottables")
                .sagaStep("MAPPING")
                .activityClass("MappingActivity")
                .activityMethod("applyLottables")
                .compensatable(false)
                .tables(List.of("LOTTABLECONFIG", "STORERATTRIBUTE"))
                .build());

        SP_MAPPINGS.put("nsp_MapPOToASN", SPMapping.builder()
                .spName("nsp_MapPOToASN")
                .sagaStep("MAPPING")
                .activityClass("MappingActivity")
                .activityMethod("mapPOToASN")
                .compensatable(false)
                .tables(List.of("ORDERS", "ORDERDETAIL"))
                .build());

        // Receipt Creation SPs (WITH compensation)
        SP_MAPPINGS.put("isp_Receipt", SPMapping.builder()
                .spName("isp_Receipt")
                .sagaStep("CREATE_RECEIPT_HEADER")
                .activityClass("PersistenceActivity")
                .activityMethod("createReceiptHeader")
                .compensatable(true)
                .compensationSP("dsp_Receipt")
                .compensationMethod("deleteReceiptHeader")
                .tables(List.of("RECEIPT"))
                .build());

        SP_MAPPINGS.put("isp_ReceiptDetail", SPMapping.builder()
                .spName("isp_ReceiptDetail")
                .sagaStep("CREATE_RECEIPT_DETAILS")
                .activityClass("PersistenceActivity")
                .activityMethod("createReceiptDetails")
                .compensatable(true)
                .compensationSP("dsp_ReceiptDetail")
                .compensationMethod("deleteReceiptDetails")
                .tables(List.of("RECEIPTDETAIL"))
                .build());

        // Inventory SPs (WITH compensation)
        SP_MAPPINGS.put("usp_AllocateInventory", SPMapping.builder()
                .spName("usp_AllocateInventory")
                .sagaStep("ALLOCATE_INVENTORY")
                .activityClass("InventoryActivity")
                .activityMethod("allocateInventory")
                .compensatable(true)
                .compensationSP("usp_ReleaseInventory")
                .compensationMethod("releaseInventory")
                .tables(List.of("LOTxLOCxID", "INVENTORYRESERVATION"))
                .build());

        SP_MAPPINGS.put("isp_LOTxLOCxID", SPMapping.builder()
                .spName("isp_LOTxLOCxID")
                .sagaStep("CREATE_INVENTORY")
                .activityClass("InventoryActivity")
                .activityMethod("createInventory")
                .compensatable(true)
                .compensationSP("dsp_LOTxLOCxID")
                .compensationMethod("deleteInventory")
                .tables(List.of("LOTxLOCxID"))
                .build());

        // Status Update SPs (WITH compensation)
        SP_MAPPINGS.put("usp_UpdatePOStatus", SPMapping.builder()
                .spName("usp_UpdatePOStatus")
                .sagaStep("UPDATE_PO_STATUS")
                .activityClass("POStatusUpdateActivity")
                .activityMethod("updateStatus")
                .compensatable(true)
                .compensationMethod("restoreStatus")
                .tables(List.of("ORDERS"))
                .build());

        SP_MAPPINGS.put("usp_UpdateReceiptStatus", SPMapping.builder()
                .spName("usp_UpdateReceiptStatus")
                .sagaStep("UPDATE_RECEIPT_STATUS")
                .activityClass("PersistenceActivity")
                .activityMethod("updateReceiptStatus")
                .compensatable(true)
                .compensationMethod("restoreReceiptStatus")
                .tables(List.of("RECEIPT"))
                .build());

        // Legacy Sync SPs (WITH compensation)
        SP_MAPPINGS.put("lsp_SyncToLegacy", SPMapping.builder()
                .spName("lsp_SyncToLegacy")
                .sagaStep("LEGACY_SYNC")
                .activityClass("LegacyBridgeActivity")
                .activityMethod("syncToLegacy")
                .compensatable(true)
                .compensationMethod("rollbackLegacy")
                .tables(List.of())
                .build());

        // Notification SPs (no compensation - best effort)
        SP_MAPPINGS.put("nsp_SendNotification", SPMapping.builder()
                .spName("nsp_SendNotification")
                .sagaStep("NOTIFICATION")
                .activityClass("NotificationActivity")
                .activityMethod("sendPopulationComplete")
                .compensatable(false)
                .tables(List.of())
                .build());

        // PO Population Main SP (orchestrator)
        SP_MAPPINGS.put("lsp_PopulatePO", SPMapping.builder()
                .spName("lsp_PopulatePO")
                .sagaStep("POPULATE_PO_WORKFLOW")
                .activityClass("PopulatePOWorkflow")
                .activityMethod("populate")
                .compensatable(true)
                .tables(List.of("ORDERS", "ORDERDETAIL", "RECEIPT", "RECEIPTDETAIL", "LOTxLOCxID"))
                .childSPs(List.of(
                        "lsp_ValidatePO",
                        "nsp_MapLottables",
                        "isp_Receipt",
                        "isp_ReceiptDetail",
                        "usp_AllocateInventory",
                        "usp_UpdatePOStatus"
                ))
                .build());

        // Finalize Receipt SP
        SP_MAPPINGS.put("lsp_FinalizeReceipt_Wrapper", SPMapping.builder()
                .spName("lsp_FinalizeReceipt_Wrapper")
                .sagaStep("FINALIZE_RECEIPT_WORKFLOW")
                .activityClass("FinalizeReceiptWorkflow")
                .activityMethod("finalize")
                .compensatable(true)
                .tables(List.of("RECEIPT", "RECEIPTDETAIL", "LOTxLOCxID"))
                .build());
    }

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get mapping for a stored procedure
     */
    public Optional<SPMapping> getMapping(String spName) {
        return Optional.ofNullable(SP_MAPPINGS.get(spName));
    }

    /**
     * Get all mappings
     */
    public Map<String, SPMapping> getAllMappings() {
        return Collections.unmodifiableMap(SP_MAPPINGS);
    }

    /**
     * Get saga steps for a workflow SP (like lsp_PopulatePO)
     */
    public List<SPMapping> getSagaStepsForWorkflow(String workflowSP) {
        SPMapping workflow = SP_MAPPINGS.get(workflowSP);
        if (workflow == null || workflow.getChildSPs() == null) {
            return List.of();
        }

        List<SPMapping> steps = new ArrayList<>();
        for (String childSP : workflow.getChildSPs()) {
            SPMapping step = SP_MAPPINGS.get(childSP);
            if (step != null) {
                steps.add(step);
            }
        }
        return steps;
    }

    /**
     * Get compensatable steps for a workflow
     */
    public List<SPMapping> getCompensatableSteps(String workflowSP) {
        return getSagaStepsForWorkflow(workflowSP).stream()
                .filter(SPMapping::isCompensatable)
                .toList();
    }

    /**
     * Get tables affected by a workflow
     */
    public Set<String> getAffectedTables(String workflowSP) {
        Set<String> tables = new HashSet<>();
        for (SPMapping step : getSagaStepsForWorkflow(workflowSP)) {
            tables.addAll(step.getTables());
        }
        return tables;
    }

    // ═══════════════════════════════════════════════════════════════
    // SP MAPPING DATA CLASS
    // ═══════════════════════════════════════════════════════════════

    @Data
    @Builder
    public static class SPMapping {
        private String spName;              // Legacy SP name (e.g., isp_Receipt)
        private String sagaStep;            // Saga step name
        private String activityClass;       // Java activity class
        private String activityMethod;      // Method to call
        private boolean compensatable;      // Does this step need compensation?
        private String compensationSP;      // Legacy compensation SP (if any)
        private String compensationMethod;  // Java compensation method
        private List<String> tables;        // Tables affected
        private List<String> childSPs;      // For workflow SPs that call other SPs
    }
}
