package com.wms.po.domain.exception;

import lombok.Getter;

/**
 * Centralized Error Code Registry.
 *
 * Maps legacy SQL Server SP error codes to Java exceptions.
 * Maintains backward compatibility with existing error handling.
 *
 * Error Code Ranges (Legacy SQL Server):
 * - 68600-68699: Task/Receipt Processing Errors
 * - 68700-68799: Inventory Errors
 * - 68800-68899: PO/ASN Errors
 * - 68900-68999: Finalization Errors
 * - 69000-69099: Integration Errors
 * - 69100-69199: Validation Errors
 * - 69200-69299: Putaway Errors
 * - 69300-69399: XDock Errors
 * - 69400-69499: Lottable Rule Errors
 * - 69500-69599: Plugin/Hook Errors
 * - 69600-69699: Job/Scheduler Errors
 * - 69700-69799: Trigger/Event Errors
 * - 69800-69899: View/Projection Errors
 * - 69900-69999: Function/Utility Errors
 *
 * @see MIGRATION_MASTER_TRACKER.md for full SP → ErrorCode mapping
 */
@Getter
public enum ErrorCode {

    // ═══════════════════════════════════════════════════════════════
    // Task/Receipt Processing Errors (68600-68699)
    // Source: Task processing SPs
    // ═══════════════════════════════════════════════════════════════

    INVALID_TASK_DETAIL_KEY(68675, "Invalid TaskDetail Key", "TASK_001"),
    INVALID_FROM_LOCATION(68676, "Invalid From Location", "TASK_002"),
    INVALID_TO_ID(68677, "Invalid To ID", "TASK_003"),
    INVALID_TO_LOCATION(68678, "Invalid To Location", "TASK_004"),
    ITEM_ALREADY_PROCESSED(68679, "Item Already Processed", "TASK_005"),
    INVALID_REASON_CODE_1(68680, "Invalid Reason Code", "TASK_006"),
    INVALID_REASON_CODE_2(68681, "Invalid Reason Code", "TASK_006"),
    INVALID_REASON_CODE_3(68682, "Invalid Reason Code", "TASK_006"),
    TASK_DETAIL_UPDATE_FAILED(68683, "TaskDetail Update Failed", "TASK_007"),
    INSERT_TASK_FAILED(68684, "Insert Task Failed", "TASK_008"),
    LOG_ALERT_FAILED(68685, "Log Alert Failed", "TASK_009"),
    GET_KEY_FAILED_1(68686, "Get Key Failed", "TASK_010"),
    GET_KEY_FAILED_2(68687, "Get Key Failed", "TASK_010"),
    TASK_ALREADY_ASSIGNED(68688, "Task Already Assigned", "TASK_011"),
    TASK_NOT_FOUND(68689, "Task Not Found", "TASK_012"),
    TASK_INVALID_STATUS(68690, "Task Invalid Status", "TASK_013"),
    TASK_COMPLETION_FAILED(68691, "Task Completion Failed", "TASK_014"),

    // ═══════════════════════════════════════════════════════════════
    // Inventory Errors (68700-68799)
    // Source: Inventory processing SPs, nspInventoryHoldWrapper
    // ═══════════════════════════════════════════════════════════════

    INVENTORY_NOT_FOUND(68700, "Inventory Not Found", "INV_001"),
    INVENTORY_INSUFFICIENT(68701, "Insufficient Inventory", "INV_002"),
    INVENTORY_ALREADY_ALLOCATED(68702, "Inventory Already Allocated", "INV_003"),
    INVENTORY_ON_HOLD(68703, "Inventory On Hold", "INV_004"),
    INVENTORY_ADJUSTMENT_FAILED(68704, "Inventory Adjustment Failed", "INV_005"),
    INVENTORY_TRANSFER_FAILED(68705, "Inventory Transfer Failed", "INV_006"),
    INVENTORY_POSTING_FAILED(68706, "Inventory Posting Failed", "INV_007"),
    HOLD_APPLICATION_FAILED(68707, "Hold Application Failed", "INV_008"),
    HOLD_RELEASE_FAILED(68708, "Hold Release Failed", "INV_009"),
    LOCATION_NOT_FOUND(68710, "Location Not Found", "INV_010"),
    LOCATION_FULL(68711, "Location Full", "INV_011"),
    LOCATION_INVALID_TYPE(68712, "Location Invalid Type", "INV_012"),
    LOCATION_NOT_ACTIVE(68713, "Location Not Active", "INV_013"),
    LOCATION_RESTRICTED(68714, "Location Restricted", "INV_014"),
    LICENSE_PLATE_NOT_FOUND(68720, "License Plate Not Found", "INV_020"),
    LICENSE_PLATE_ALREADY_EXISTS(68721, "License Plate Already Exists", "INV_021"),
    LICENSE_PLATE_INVALID(68722, "License Plate Invalid", "INV_022"),
    LICENSE_PLATE_ON_HOLD(68723, "License Plate On Hold", "INV_023"),
    LOT_NOT_FOUND(68730, "Lot Not Found", "INV_030"),
    LOT_EXPIRED(68731, "Lot Expired", "INV_031"),
    LOT_ON_HOLD(68732, "Lot On Hold", "INV_032"),
    LOT_INVALID_STATUS(68733, "Lot Invalid Status", "INV_033"),
    UCC_NOT_FOUND(68740, "UCC Not Found", "INV_040"),
    UCC_ALREADY_EXISTS(68741, "UCC Already Exists", "INV_041"),
    UCC_CREATION_FAILED(68742, "UCC Creation Failed", "INV_042"),

    // ═══════════════════════════════════════════════════════════════
    // PO/ASN Errors (68800-68899)
    // Source: WM.lsp_ASN_PopulatePOs_Wrapper, WM.lsp_ASN_PopulatePODs_Wrapper
    // ═══════════════════════════════════════════════════════════════

    PO_NOT_FOUND(68800, "PO Not Found", "PO_001"),
    PO_ALREADY_CLOSED(68801, "PO Already Closed", "PO_002"),
    PO_CANCELLED(68802, "PO Cancelled", "PO_003"),
    PO_LINE_NOT_FOUND(68803, "PO Line Not Found", "PO_004"),
    PO_LINE_FULLY_RECEIVED(68804, "PO Line Fully Received", "PO_005"),
    ASN_NOT_FOUND(68805, "ASN Not Found", "PO_006"),
    ASN_ALREADY_FINALIZED(68806, "ASN Already Finalized", "PO_007"),
    ASN_INVALID_STATUS(68807, "ASN Invalid Status", "PO_008"),
    ASN_CREATION_FAILED(68808, "ASN Creation Failed", "PO_009"),
    ASN_CLOSE_FAILED(68809, "ASN Close Failed", "PO_009b"),
    STORER_MISMATCH(68810, "Storer Key Mismatch", "PO_010"),
    FACILITY_MISMATCH(68811, "Facility Mismatch", "PO_011"),
    SUPPLIER_NOT_FOUND(68812, "Supplier Not Found", "PO_012"),
    SKU_NOT_FOUND(68813, "SKU Not Found", "PO_013"),
    PACK_NOT_FOUND(68814, "Pack Not Found", "PO_014"),
    PO_DUPLICATE_LINE(68815, "Duplicate PO Line", "PO_015"),
    PO_VARIANCE_EXCEEDED(68816, "PO Variance Tolerance Exceeded", "PO_016"),
    PO_INVALID_DOC_TYPE(68817, "Invalid Document Type", "PO_017"),
    PO_MAPPING_FAILED(68818, "PO Field Mapping Failed", "PO_018"),
    PO_VALIDATION_FAILED(68819, "PO Validation Failed", "PO_019"),
    ASN_VARIANCE_EXCEEDED(68820, "ASN Variance Tolerance Exceeded", "PO_020"),
    ASN_DUPLICATE_NOT_ALLOWED(68821, "Only One ASN Per PO Allowed", "PO_021"),
    ASN_SAME_PO_LINE_NOT_ALLOWED(68822, "Same PO Line Already Populated", "PO_022"),

    // ═══════════════════════════════════════════════════════════════
    // Receipt/Finalization Errors (68900-68999)
    // Source: WM.lsp_FinalizeReceipt_Wrapper, ispFinalizeReceipt
    // ═══════════════════════════════════════════════════════════════

    RECEIPT_NOT_FOUND(68900, "Receipt Not Found", "RCV_001"),
    RECEIPT_ALREADY_FINALIZED(68901, "Receipt Already Finalized", "RCV_002"),
    RECEIPT_INVALID_STATUS(68902, "Receipt Invalid Status", "RCV_003"),
    RECEIPT_DETAIL_NOT_FOUND(68903, "Receipt Detail Not Found", "RCV_004"),
    RECEIPT_HEADER_CREATE_FAILED(68904, "Receipt Header Creation Failed", "RCV_005"),
    RECEIPT_DETAIL_CREATE_FAILED(68905, "Receipt Detail Creation Failed", "RCV_006"),
    RECEIPT_CLOSE_FAILED(68906, "Receipt Close Failed", "RCV_007"),
    RECEIPT_UPDATE_FAILED(68907, "Receipt Update Failed", "RCV_008"),
    RECEIPT_LINE_SPLIT_FAILED(68908, "Receipt Line Split Failed", "RCV_009"),
    RECEIPT_QUANTITY_MISMATCH(68910, "Receipt Quantity Mismatch", "RCV_010"),
    RECEIPT_OVERRECEIVE_NOT_ALLOWED(68911, "Over-receive Not Allowed", "RCV_011"),
    RECEIPT_UNDERRECEIVE_NOT_ALLOWED(68912, "Under-receive Not Allowed", "RCV_012"),
    RECEIPT_CARTON_VALIDATION_FAILED(68913, "Carton Type Validation Failed", "RCV_013"),
    FINALIZE_VALIDATION_FAILED(68920, "Finalize Validation Failed", "RCV_020"),
    FINALIZE_INVENTORY_POST_FAILED(68921, "Inventory Posting Failed", "RCV_021"),
    FINALIZE_PO_UPDATE_FAILED(68922, "PO Update Failed", "RCV_022"),
    FINALIZE_HOLD_APPLY_FAILED(68923, "Hold Application Failed", "RCV_023"),
    FINALIZE_PUTAWAY_RELEASE_FAILED(68924, "Putaway Release Failed", "RCV_024"),
    FINALIZE_BATCH_PA_FAILED(68925, "Batch Putaway Failed", "RCV_025"),
    FINALIZE_UCC_GENERATION_FAILED(68926, "UCC Generation Failed", "RCV_026"),
    FINALIZE_NOTIFICATION_FAILED(68927, "Finalize Notification Failed", "RCV_027"),
    FINALIZE_TRANSMITLOG_FAILED(68928, "Transmit Log Failed", "RCV_028"),

    // ═══════════════════════════════════════════════════════════════
    // Integration Errors (69000-69099)
    // Source: Integration/External API calls
    // ═══════════════════════════════════════════════════════════════

    LEGACY_SYNC_FAILED(69000, "Legacy System Sync Failed", "INT_001"),
    KAFKA_PUBLISH_FAILED(69001, "Kafka Publish Failed", "INT_002"),
    TEMPORAL_WORKFLOW_FAILED(69002, "Workflow Execution Failed", "INT_003"),
    TEMPORAL_ACTIVITY_FAILED(69003, "Activity Execution Failed", "INT_004"),
    TEMPORAL_SIGNAL_FAILED(69004, "Workflow Signal Failed", "INT_005"),
    DUAL_WRITE_SYNC_FAILED(69005, "Dual Write Sync Failed", "INT_006"),
    BRIDGE_CALL_FAILED(69006, "Bridge Call Failed", "INT_007"),
    EXTERNAL_API_FAILED(69010, "External API Call Failed", "INT_010"),
    EDI_PARSE_FAILED(69011, "EDI Parse Failed", "INT_011"),
    EDI_TRANSMIT_FAILED(69012, "EDI Transmit Failed", "INT_012"),
    IML_INBOUND_FAILED(69013, "IML Inbound Processing Failed", "INT_013"),
    IML_OUTBOUND_FAILED(69014, "IML Outbound Processing Failed", "INT_014"),
    DATABASE_ERROR(69020, "Database Error", "INT_020"),
    DEADLOCK_DETECTED(69021, "Deadlock Detected - Retry", "INT_021"),
    TIMEOUT_ERROR(69022, "Operation Timeout", "INT_022"),
    CONNECTION_FAILED(69023, "Connection Failed", "INT_023"),
    TRANSACTION_ROLLBACK(69024, "Transaction Rollback", "INT_024"),

    // ═══════════════════════════════════════════════════════════════
    // Validation Errors (69100-69199)
    // Source: WM.lsp_Validate_Receipt_Std, WM.lsp_Validate_Receiptdetail_Std,
    //         isp_ASN_ExtendedValidation
    // ═══════════════════════════════════════════════════════════════

    VALIDATION_REQUIRED_FIELD(69100, "Required Field Missing", "VAL_001"),
    VALIDATION_INVALID_FORMAT(69101, "Invalid Format", "VAL_002"),
    VALIDATION_DUPLICATE(69102, "Duplicate Value", "VAL_003"),
    VALIDATION_RANGE(69103, "Value Out of Range", "VAL_004"),
    VALIDATION_DATE_INVALID(69104, "Invalid Date", "VAL_005"),
    VALIDATION_DATE_RANGE(69105, "Date Out of Range", "VAL_006"),
    VALIDATION_QUANTITY_INVALID(69106, "Invalid Quantity", "VAL_007"),
    VALIDATION_QUANTITY_ZERO(69107, "Quantity Cannot Be Zero", "VAL_008"),
    VALIDATION_QUANTITY_NEGATIVE(69108, "Quantity Cannot Be Negative", "VAL_009"),
    VALIDATION_BUSINESS_RULE(69110, "Business Rule Violation", "VAL_010"),
    VALIDATION_STORER_CONFIG(69111, "Storer Configuration Invalid", "VAL_011"),
    VALIDATION_SKU_CONFIG(69112, "SKU Configuration Invalid", "VAL_012"),
    VALIDATION_PACK_CONFIG(69113, "Pack Configuration Invalid", "VAL_013"),
    VALIDATION_LOCATION_CONFIG(69114, "Location Configuration Invalid", "VAL_014"),
    VALIDATION_EXTENDED_FAILED(69120, "Extended Validation Failed", "VAL_020"),
    VALIDATION_CUSTOM_RULE_FAILED(69121, "Custom Validation Rule Failed", "VAL_021"),

    // ═══════════════════════════════════════════════════════════════
    // Putaway Errors (69200-69299)
    // Source: nspPASTD, WM.lsp_ASNReleasePATask_Wrapper, ispPARL01-08
    // ═══════════════════════════════════════════════════════════════

    PUTAWAY_STRATEGY_NOT_FOUND(69200, "Putaway Strategy Not Found", "PA_001"),
    PUTAWAY_LOCATION_NOT_FOUND(69201, "Putaway Location Not Found", "PA_002"),
    PUTAWAY_LOCATION_FULL(69202, "Putaway Location Full", "PA_003"),
    PUTAWAY_RESTRICTION_VIOLATED(69203, "Putaway Restriction Violated", "PA_004"),
    PUTAWAY_TASK_CREATE_FAILED(69204, "Putaway Task Creation Failed", "PA_005"),
    PUTAWAY_TASK_RELEASE_FAILED(69205, "Putaway Task Release Failed", "PA_006"),
    PUTAWAY_TASK_ASSIGNMENT_FAILED(69206, "Putaway Task Assignment Failed", "PA_007"),
    PUTAWAY_TASK_COMPLETION_FAILED(69207, "Putaway Task Completion Failed", "PA_008"),
    PUTAWAY_BATCH_FAILED(69210, "Batch Putaway Failed", "PA_010"),
    PUTAWAY_BATCH_PA01_FAILED(69211, "Batch PA Standard Failed", "PA_011"),
    PUTAWAY_BATCH_PA02_FAILED(69212, "Batch PA Nike CRW Failed", "PA_012"),
    PUTAWAY_BATCH_PA03_FAILED(69213, "Batch PA Variant 3 Failed", "PA_013"),
    PUTAWAY_BATCH_PA04_FAILED(69214, "Batch PA Variant 4 Failed", "PA_014"),
    PUTAWAY_BATCH_PA05_FAILED(69215, "Batch PA Nike CallOfModel Failed", "PA_015"),
    PUTAWAY_BATCH_PA06_FAILED(69216, "Batch PA Variant 6 Failed", "PA_016"),
    PUTAWAY_PARL01_FAILED(69220, "PA Release Standard Failed", "PA_020"),
    PUTAWAY_PARL02_FAILED(69221, "PA Release Variant 2 Failed", "PA_021"),
    PUTAWAY_PARL03_FAILED(69222, "PA Release Variant 3 Failed", "PA_022"),
    PUTAWAY_PARL04_FAILED(69223, "PA Release Variant 4 Failed", "PA_023"),
    PUTAWAY_PARL05_FAILED(69224, "PA Release Variant 5 Failed", "PA_024"),
    PUTAWAY_PARL06_FAILED(69225, "PA Release ULM Failed", "PA_025"),
    PUTAWAY_PARL07_FAILED(69226, "PA Release Variant 7 Failed", "PA_026"),
    PUTAWAY_PARL08_FAILED(69227, "PA Release Variant 8 Failed", "PA_027"),
    PUTAWAY_AUTO_RELEASE_FAILED(69230, "Auto PA Release Failed", "PA_030"),

    // ═══════════════════════════════════════════════════════════════
    // XDock/Allocation Errors (69300-69399)
    // Source: WM.lsp_FlowThruAllocate_Wrapper, WM.lsp_XDockAllocation_Wrapper,
    //         nsp_xdockorderprocessing
    // ═══════════════════════════════════════════════════════════════

    XDOCK_ORDER_NOT_FOUND(69300, "XDock Order Not Found", "XD_001"),
    XDOCK_ALLOCATION_FAILED(69301, "XDock Allocation Failed", "XD_002"),
    XDOCK_INSUFFICIENT_INVENTORY(69302, "XDock Insufficient Inventory", "XD_003"),
    XDOCK_PROCESSING_FAILED(69303, "XDock Processing Failed", "XD_004"),
    XDOCK_SO_CREATION_FAILED(69304, "XDock Sales Order Creation Failed", "XD_005"),
    XDOCK_AUTO_ALLOCATE_FAILED(69305, "XDock Auto Allocate Failed", "XD_006"),
    FLOWTHRU_ALLOCATION_FAILED(69310, "Flow-Thru Allocation Failed", "XD_010"),
    FLOWTHRU_ROUTING_FAILED(69311, "Flow-Thru Routing Failed", "XD_011"),
    ALLOCATION_BUILD_FAILED(69320, "Allocation Build Failed", "XD_020"),
    ALLOCATION_RELEASE_FAILED(69321, "Allocation Release Failed", "XD_021"),
    ALLOCATION_WAVE_FAILED(69322, "Wave Allocation Failed", "XD_022"),

    // ═══════════════════════════════════════════════════════════════
    // Lottable Rule Errors (69400-69499)
    // Source: ispLottableRule_Wrapper, ispDefLot1FrRcptDtl, ispDefLot2FrRcptDtl,
    //         ispGenLot2BySuppLot, ispGenLot1_TH01, ispGenLot2_TH02, etc.
    // ═══════════════════════════════════════════════════════════════

    LOTTABLE_RULE_NOT_FOUND(69400, "Lottable Rule Not Found", "LOT_001"),
    LOTTABLE_RULE_EXECUTION_FAILED(69401, "Lottable Rule Execution Failed", "LOT_002"),
    LOTTABLE_MAPPING_FAILED(69402, "Lottable Mapping Failed", "LOT_003"),
    LOTTABLE_VALIDATION_FAILED(69403, "Lottable Validation Failed", "LOT_004"),
    LOTTABLE01_GENERATION_FAILED(69410, "Lot1 Generation Failed", "LOT_010"),
    LOTTABLE02_GENERATION_FAILED(69411, "Lot2 Generation Failed", "LOT_011"),
    LOTTABLE03_GENERATION_FAILED(69412, "Lot3 Generation Failed", "LOT_012"),
    LOTTABLE04_GENERATION_FAILED(69413, "Lot4 Generation Failed", "LOT_013"),
    LOTTABLE05_GENERATION_FAILED(69414, "Lot5 Generation Failed", "LOT_014"),
    LOTTABLE_SUPPLIER_LOT_FAILED(69420, "Supplier Lot Generation Failed", "LOT_020"),
    LOTTABLE_TH_RULE_FAILED(69421, "Thailand Lottable Rule Failed", "LOT_021"),
    LOTTABLE_TW_RULE_FAILED(69422, "Taiwan Lottable Rule Failed", "LOT_022"),
    LOTTABLE_NIKE_CN_FAILED(69423, "Nike CN Lottable Rule Failed", "LOT_023"),
    LOTTABLE_MONDELEZ_FAILED(69424, "Mondelez Lottable Rule Failed", "LOT_024"),
    LOTTABLE_UNILEVER_FAILED(69425, "Unilever Lottable Rule Failed", "LOT_025"),
    LOTTABLE_DROOLS_RULE_FAILED(69430, "Drools Lottable Rule Failed", "LOT_030"),

    // ═══════════════════════════════════════════════════════════════
    // Plugin/Hook Errors (69500-69599)
    // Source: ispPRREC* (Pre-Finalize), ispASNFZ* (Post-Finalize),
    //         ispPRPPLPO* (Pre-Populate), Client plugins
    // ═══════════════════════════════════════════════════════════════

    PLUGIN_NOT_FOUND(69500, "Plugin Not Found", "PLG_001"),
    PLUGIN_EXECUTION_FAILED(69501, "Plugin Execution Failed", "PLG_002"),
    PLUGIN_CONFIGURATION_INVALID(69502, "Plugin Configuration Invalid", "PLG_003"),
    PLUGIN_REGISTRATION_FAILED(69503, "Plugin Registration Failed", "PLG_004"),
    PRE_FINALIZE_HOOK_FAILED(69510, "Pre-Finalize Hook Failed", "PLG_010"),
    PRE_FINALIZE_HM_FAILED(69511, "Pre-Finalize H&M Plugin Failed", "PLG_011"),
    PRE_FINALIZE_NIKE_FAILED(69512, "Pre-Finalize Nike Plugin Failed", "PLG_012"),
    PRE_FINALIZE_ADIDAS_FAILED(69513, "Pre-Finalize Adidas Plugin Failed", "PLG_013"),
    PRE_FINALIZE_COLUMBIA_FAILED(69514, "Pre-Finalize Columbia Plugin Failed", "PLG_014"),
    PRE_FINALIZE_UNILEVER_FAILED(69515, "Pre-Finalize Unilever Plugin Failed", "PLG_015"),
    PRE_FINALIZE_NEWLOOK_FAILED(69516, "Pre-Finalize NewLook Plugin Failed", "PLG_016"),
    PRE_FINALIZE_INDIA_FAILED(69517, "Pre-Finalize India Plugin Failed", "PLG_017"),
    PRE_FINALIZE_DSG_TH_FAILED(69518, "Pre-Finalize DSG Thailand Plugin Failed", "PLG_018"),
    PRE_FINALIZE_REGIONAL_FAILED(69519, "Pre-Finalize Regional Plugin Failed", "PLG_019"),
    POST_FINALIZE_HOOK_FAILED(69520, "Post-Finalize Hook Failed", "PLG_020"),
    POST_FINALIZE_BATCH_RELEASE_FAILED(69521, "Post-Finalize Batch Release Failed", "PLG_021"),
    POST_FINALIZE_UCC_STAMP_FAILED(69522, "Post-Finalize UCC Stamp Failed", "PLG_022"),
    POST_FINALIZE_AUTO_PA_FAILED(69523, "Post-Finalize Auto PA Release Failed", "PLG_023"),
    POST_FINALIZE_NOTIFICATION_FAILED(69524, "Post-Finalize Notification Failed", "PLG_024"),
    POST_FINALIZE_INV_SYNC_FAILED(69525, "Post-Finalize Inventory Sync Failed", "PLG_025"),
    POST_FINALIZE_QUALITY_CHECK_FAILED(69526, "Post-Finalize Quality Check Failed", "PLG_026"),
    POST_FINALIZE_CUSTOMS_FAILED(69527, "Post-Finalize Customs Update Failed", "PLG_027"),
    POST_FINALIZE_AUTO_ALLOC_FAILED(69528, "Post-Finalize Auto Allocate Failed", "PLG_028"),
    POST_FINALIZE_NEWLOOK_ADJ_FAILED(69529, "Post-Finalize NewLook Adjust Failed", "PLG_029"),
    POST_FINALIZE_COLUMBIA_UCC_FAILED(69530, "Post-Finalize Columbia UCC Failed", "PLG_030"),
    PRE_POPULATE_HOOK_FAILED(69540, "Pre-Populate Hook Failed", "PLG_040"),
    PRE_POPULATE_STANDARD_FAILED(69541, "Pre-Populate Standard Plugin Failed", "PLG_041"),
    PRE_POPULATE_DATE_VAL_FAILED(69542, "Pre-Populate Date Validation Failed", "PLG_042"),
    PRE_POPULATE_ADIDAS_FAILED(69543, "Pre-Populate Adidas Plugin Failed", "PLG_043"),
    PRE_POPULATE_QTY_FAILED(69544, "Pre-Populate Quantity Plugin Failed", "PLG_044"),
    PRE_POPULATE_XREF_FAILED(69545, "Pre-Populate Cross-Reference Failed", "PLG_045"),
    PRE_POPULATE_JCB_FAILED(69546, "Pre-Populate JCB Plugin Failed", "PLG_046"),
    CLIENT_AUTO_ASN_FAILED(69550, "Client Auto-ASN Failed", "PLG_050"),
    CLIENT_NIKE_KR_FAILED(69551, "Nike Korea Plugin Failed", "PLG_051"),
    CLIENT_HM_IND_FAILED(69552, "H&M India Plugin Failed", "PLG_052"),
    CLIENT_FLEX_FAILED(69553, "Flextronics Plugin Failed", "PLG_053"),
    CLIENT_ULM_FAILED(69554, "ULM Plugin Failed", "PLG_054"),
    POST_ALLOCATION_HOOK_FAILED(69560, "Post-Allocation Hook Failed", "PLG_060"),

    // ═══════════════════════════════════════════════════════════════
    // Job/Scheduler Errors (69600-69699)
    // Source: SQL Agent Jobs → Spring @Scheduled
    // ═══════════════════════════════════════════════════════════════

    JOB_EXECUTION_FAILED(69600, "Job Execution Failed", "JOB_001"),
    JOB_NOT_FOUND(69601, "Job Not Found", "JOB_002"),
    JOB_ALREADY_RUNNING(69602, "Job Already Running", "JOB_003"),
    JOB_SCHEDULING_FAILED(69603, "Job Scheduling Failed", "JOB_004"),
    JOB_AUTO_POPULATE_FAILED(69610, "Auto Populate PO→ASN Job Failed", "JOB_010"),
    JOB_AUTO_FINALIZE_FAILED(69611, "Auto Finalize ASN Job Failed", "JOB_011"),
    JOB_AUTO_PA_RELEASE_FAILED(69612, "Auto PA Release Job Failed", "JOB_012"),
    JOB_BUILD_ALLOC_FAILED(69613, "Build Auto Allocation Job Failed", "JOB_013"),
    JOB_XDOCK_ALLOC_FAILED(69614, "XDock Auto Allocate Job Failed", "JOB_014"),
    JOB_XDOCK_SO_FAILED(69615, "XDock Create SO Job Failed", "JOB_015"),
    JOB_INBOUND_MASTER_FAILED(69620, "Inbound Master Job Failed", "JOB_020"),
    JOB_GENERIC_INBOUND_PO_FAILED(69621, "Generic Inbound PO Job Failed", "JOB_021"),
    JOB_GENERIC_INBOUND_ASN_FAILED(69622, "Generic Inbound ASN Job Failed", "JOB_022"),
    JOB_GENERIC_OUTBOUND_FAILED(69623, "Generic Outbound Job Failed", "JOB_023"),
    JOB_ARCHIVE_FAILED(69630, "Archive Job Failed", "JOB_030"),
    JOB_PURGE_INTERFACE_FAILED(69631, "Purge Interface Job Failed", "JOB_031"),
    JOB_PURGE_INVENTORY_FAILED(69632, "Purge Zero Inventory Job Failed", "JOB_032"),
    JOB_ALERT_FAILED(69640, "Alert Job Failed", "JOB_040"),
    JOB_BI_REFRESH_FAILED(69641, "BI Refresh Job Failed", "JOB_041"),
    JOB_HOUSEKEEPING_FAILED(69642, "Housekeeping Job Failed", "JOB_042"),
    JOB_CLIENT_SPECIFIC_FAILED(69650, "Client-Specific Job Failed", "JOB_050"),

    // ═══════════════════════════════════════════════════════════════
    // Trigger/Event Errors (69700-69799)
    // Source: SQL Triggers → JPA EntityListeners / Domain Events
    // ═══════════════════════════════════════════════════════════════

    TRIGGER_EXECUTION_FAILED(69700, "Trigger Execution Failed", "TRG_001"),
    EVENT_PUBLISH_FAILED(69701, "Event Publish Failed", "TRG_002"),
    EVENT_HANDLER_FAILED(69702, "Event Handler Failed", "TRG_003"),
    PO_HEADER_ADD_TRIGGER_FAILED(69710, "PO Header Add Trigger Failed", "TRG_010"),
    PO_HEADER_UPDATE_TRIGGER_FAILED(69711, "PO Header Update Trigger Failed", "TRG_011"),
    PO_HEADER_DELETE_TRIGGER_FAILED(69712, "PO Header Delete Trigger Failed", "TRG_012"),
    PO_DETAIL_ADD_TRIGGER_FAILED(69713, "PO Detail Add Trigger Failed", "TRG_013"),
    PO_DETAIL_UPDATE_TRIGGER_FAILED(69714, "PO Detail Update Trigger Failed", "TRG_014"),
    PO_DETAIL_DELETE_TRIGGER_FAILED(69715, "PO Detail Delete Trigger Failed", "TRG_015"),
    RECEIPT_HEADER_ADD_TRIGGER_FAILED(69720, "Receipt Header Add Trigger Failed", "TRG_020"),
    RECEIPT_HEADER_UPDATE_TRIGGER_FAILED(69721, "Receipt Header Update Trigger Failed", "TRG_021"),
    RECEIPT_DETAIL_ADD_TRIGGER_FAILED(69722, "Receipt Detail Add Trigger Failed", "TRG_022"),
    RECEIPT_DETAIL_UPDATE_TRIGGER_FAILED(69723, "Receipt Detail Update Trigger Failed", "TRG_023"),
    RECEIPT_DETAIL_DELETE_TRIGGER_FAILED(69724, "Receipt Detail Delete Trigger Failed", "TRG_024"),
    TRANSMITLOG_UPDATE_TRIGGER_FAILED(69730, "TransmitLog Update Trigger Failed", "TRG_030"),

    // ═══════════════════════════════════════════════════════════════
    // View/Projection Errors (69800-69899)
    // Source: SQL Views → JPA Projections / DTOs
    // ═══════════════════════════════════════════════════════════════

    VIEW_NOT_FOUND(69800, "View Not Found", "VW_001"),
    PROJECTION_FAILED(69801, "Projection Failed", "VW_002"),
    VIEW_PO_FAILED(69810, "PO View Failed", "VW_010"),
    VIEW_PO_DETAIL_FAILED(69811, "PO Detail View Failed", "VW_011"),
    VIEW_ASN_FAILED(69812, "ASN View Failed", "VW_012"),
    VIEW_ASN_EXTENDED_FAILED(69813, "ASN Extended Validation View Failed", "VW_013"),
    VIEW_RECEIPT_FAILED(69814, "Receipt View Failed", "VW_014"),
    VIEW_RECEIPT_DETAIL_FAILED(69815, "Receipt Detail View Failed", "VW_015"),
    VIEW_BI_ASN_CONFIRM_FAILED(69820, "BI ASN Confirmation View Failed", "VW_020"),
    VIEW_BI_PO_DETAIL_FAILED(69821, "BI PO Detail View Failed", "VW_021"),
    VIEW_BI_RECEIPT_CONFIRM_FAILED(69822, "BI Receipt Confirmation View Failed", "VW_022"),
    VIEW_BI_INV_STOCK_FAILED(69823, "BI Inventory Stock View Failed", "VW_023"),

    // ═══════════════════════════════════════════════════════════════
    // Function/Utility Errors (69900-69999)
    // Source: SQL Functions → Java Utils/Services
    // ═══════════════════════════════════════════════════════════════

    FUNCTION_EXECUTION_FAILED(69900, "Function Execution Failed", "FN_001"),
    FUNCTION_NOT_FOUND(69901, "Function Not Found", "FN_002"),
    DELIM_SPLIT_FAILED(69910, "String Split Failed", "FN_010"),
    GET_RIGHT_FAILED(69911, "Get Right/Permission Failed", "FN_011"),
    GET_RIGHT2_FAILED(69912, "Get Right2/Permission Failed", "FN_012"),
    UOM_CONVERSION_FAILED(69913, "UOM Conversion Failed", "FN_013"),
    CUBE_CALCULATION_FAILED(69914, "Cube Calculation Failed", "FN_014"),
    BARCODE_GENERATION_FAILED(69920, "Barcode Generation Failed", "FN_020"),
    CHECK_DIGIT_FAILED(69921, "Check Digit Calculation Failed", "FN_021"),
    GS1_LABEL_FAILED(69922, "GS1 Label Generation Failed", "FN_022"),
    PUTAWAY_RESTRICTION_BUILD_FAILED(69930, "Putaway Restriction Build Failed", "FN_030"),
    DATE_TIME_UTIL_FAILED(69940, "Date/Time Utility Failed", "FN_040"),
    TCP_WCS_UTIL_FAILED(69941, "TCP/WCS Utility Failed", "FN_041"),
    VOCOLLECT_UTIL_FAILED(69942, "Vocollect Utility Failed", "FN_042"),

    // ═══════════════════════════════════════════════════════════════
    // Trade Return Errors (69950-69979)
    // Source: WM.lsp_ASN_PopulateSOs_Wrapper, WM.lsp_ASN_PopulateSODs_Wrapper
    // ═══════════════════════════════════════════════════════════════

    TRADE_RETURN_NOT_FOUND(69950, "Trade Return Not Found", "TR_001"),
    TRADE_RETURN_CREATION_FAILED(69951, "Trade Return Creation Failed", "TR_002"),
    TRADE_RETURN_INVALID_STATUS(69952, "Trade Return Invalid Status", "TR_003"),
    TRADE_RETURN_ORDER_NOT_FOUND(69953, "Trade Return Order Not Found", "TR_004"),
    TRADE_RETURN_PROCESSING_FAILED(69954, "Trade Return Processing Failed", "TR_005"),
    TRADE_RETURN_SO_CREATION_FAILED(69955, "Trade Return SO Creation Failed", "TR_006"),
    TRADE_RETURN_SOD_CREATION_FAILED(69956, "Trade Return SO Detail Creation Failed", "TR_007"),

    // ═══════════════════════════════════════════════════════════════
    // Configuration Errors (69980-69999)
    // Source: CODELKUP, StorerConfig, etc.
    // ═══════════════════════════════════════════════════════════════

    CONFIG_NOT_FOUND(69980, "Configuration Not Found", "CFG_001"),
    CONFIG_INVALID(69981, "Configuration Invalid", "CFG_002"),
    CONFIG_CODELKUP_NOT_FOUND(69982, "CodeLkup Entry Not Found", "CFG_003"),
    CONFIG_STORER_NOT_FOUND(69983, "Storer Config Not Found", "CFG_004"),
    CONFIG_MAPPING_FAILED(69984, "Configuration Mapping Failed", "CFG_005"),

    // ═══════════════════════════════════════════════════════════════
    // Generic/Unknown
    // ═══════════════════════════════════════════════════════════════

    UNKNOWN_ERROR(99999, "Unknown Error", "GEN_999");

    private final int legacyCode;
    private final String description;
    private final String modernCode;

    ErrorCode(int legacyCode, String description, String modernCode) {
        this.legacyCode = legacyCode;
        this.description = description;
        this.modernCode = modernCode;
    }

    /**
     * Find ErrorCode by legacy numeric code
     */
    public static ErrorCode fromLegacyCode(int code) {
        for (ErrorCode ec : values()) {
            if (ec.legacyCode == code) {
                return ec;
            }
        }
        return UNKNOWN_ERROR;
    }

    /**
     * Find ErrorCode by modern code
     */
    public static ErrorCode fromModernCode(String code) {
        if (code == null) {
            return UNKNOWN_ERROR;
        }
        for (ErrorCode ec : values()) {
            if (ec.modernCode.equals(code)) {
                return ec;
            }
        }
        return UNKNOWN_ERROR;
    }

    /**
     * Find ErrorCode by name (case-insensitive)
     */
    public static ErrorCode fromName(String name) {
        if (name == null) {
            return UNKNOWN_ERROR;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN_ERROR;
        }
    }

    /**
     * Check if this error is retryable
     */
    public boolean isRetryable() {
        return this == DEADLOCK_DETECTED ||
               this == TIMEOUT_ERROR ||
               this == CONNECTION_FAILED ||
               this == LEGACY_SYNC_FAILED ||
               this == KAFKA_PUBLISH_FAILED ||
               this == TEMPORAL_WORKFLOW_FAILED ||
               this == TEMPORAL_ACTIVITY_FAILED ||
               this == DUAL_WRITE_SYNC_FAILED ||
               this == JOB_ALREADY_RUNNING;
    }

    /**
     * Get HTTP status code for this error
     */
    public int getHttpStatus() {
        String prefix = this.modernCode.length() >= 3 ? this.modernCode.substring(0, 3) : this.modernCode;

        return switch (prefix) {
            case "VAL" -> 400; // Bad Request - Validation errors
            case "CFG" -> 400; // Bad Request - Configuration errors
            case "PO_", "RCV", "INV", "PA_", "XD_", "LOT", "TR_" -> {
                if (this.description.contains("Not Found")) {
                    yield 404; // Not Found
                } else if (this.description.contains("Already") ||
                           this.description.contains("Duplicate") ||
                           this.description.contains("Mismatch")) {
                    yield 409; // Conflict
                }
                yield 400; // Bad Request
            }
            case "TAS" -> { // TASK errors
                if (this.description.contains("Not Found")) {
                    yield 404;
                }
                yield 400;
            }
            case "PLG" -> 422; // Unprocessable Entity - Plugin errors
            case "JOB", "TRG" -> 500; // Internal Server Error - Job/Trigger errors
            case "VW_", "FN_" -> 500; // Internal Server Error - View/Function errors
            case "INT" -> {
                if (this == TIMEOUT_ERROR) {
                    yield 504; // Gateway Timeout
                }
                yield 503; // Service Unavailable
            }
            default -> 500; // Internal Server Error
        };
    }

    /**
     * Get error category for grouping
     */
    public String getCategory() {
        String prefix = this.modernCode.length() >= 3 ? this.modernCode.substring(0, 3) : this.modernCode;

        return switch (prefix) {
            case "TAS" -> "TASK";
            case "INV" -> "INVENTORY";
            case "PO_" -> "PO_ASN";
            case "RCV" -> "RECEIPT";
            case "INT" -> "INTEGRATION";
            case "VAL" -> "VALIDATION";
            case "PA_" -> "PUTAWAY";
            case "XD_" -> "XDOCK";
            case "LOT" -> "LOTTABLE";
            case "PLG" -> "PLUGIN";
            case "JOB" -> "JOB";
            case "TRG" -> "TRIGGER";
            case "VW_" -> "VIEW";
            case "FN_" -> "FUNCTION";
            case "TR_" -> "TRADE_RETURN";
            case "CFG" -> "CONFIG";
            default -> "GENERAL";
        };
    }

    /**
     * Check if this is a client-specific error
     */
    public boolean isClientSpecific() {
        return this.name().contains("NIKE") ||
               this.name().contains("HM") ||
               this.name().contains("ADIDAS") ||
               this.name().contains("COLUMBIA") ||
               this.name().contains("UNILEVER") ||
               this.name().contains("NEWLOOK") ||
               this.name().contains("DSG") ||
               this.name().contains("MONDELEZ") ||
               this.name().contains("JCB") ||
               this.name().contains("FLEX") ||
               this.name().contains("ULM");
    }

    /**
     * Check if this is a regional error
     */
    public boolean isRegional() {
        return this.name().contains("_TH") ||
               this.name().contains("_TW") ||
               this.name().contains("_CN") ||
               this.name().contains("_KR") ||
               this.name().contains("_IND") ||
               this.name().contains("INDIA") ||
               this.name().contains("THAILAND") ||
               this.name().contains("TAIWAN") ||
               this.name().contains("REGIONAL");
    }
}
