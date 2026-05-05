# Error Code Migration Mapping

> **Project:** PO Modernization (Legacy SQL → Java Microservices)
> **Version:** 1.0.0
> **Last Updated:** 2026-05-05
> **Related:** MIGRATION_MASTER_TRACKER.md

This document maps legacy SQL Server RAISERROR codes to Java ErrorCode enums and BusinessException factory methods.

---

## Overview

| Category | Legacy Range | Modern Prefix | Count |
|----------|-------------|---------------|-------|
| Task/Receipt Processing | 68600-68699 | TASK_xxx | 17 |
| Inventory | 68700-68799 | INV_xxx | 25 |
| PO/ASN | 68800-68899 | PO_xxx | 23 |
| Receipt/Finalization | 68900-68999 | RCV_xxx | 19 |
| Integration | 69000-69099 | INT_xxx | 17 |
| Validation | 69100-69199 | VAL_xxx | 16 |
| Putaway | 69200-69299 | PA_xxx | 18 |
| XDock/Allocation | 69300-69399 | XD_xxx | 11 |
| Lottable Rules | 69400-69499 | LOT_xxx | 16 |
| Plugins/Hooks | 69500-69599 | PLG_xxx | 41 |
| Jobs/Scheduler | 69600-69699 | JOB_xxx | 21 |
| Triggers/Events | 69700-69799 | TRG_xxx | 15 |
| Views/Projections | 69800-69899 | VW_xxx | 14 |
| Functions/Utils | 69900-69999 | FN_xxx | 13 |
| Trade Return | 69950-69979 | TR_xxx | 7 |
| Configuration | 69980-69999 | CFG_xxx | 5 |

**Total: 278 mapped error codes**

---

## Section 1: Stored Procedures → Error Codes

### 1.1 WM Wrapper SPs (14 Total)

| SP ID | SP Name | Primary Error Codes | Java Exception |
|-------|---------|---------------------|----------------|
| SP-001 | `WM.lsp_ASN_PopulatePOs_Wrapper` | PO_NOT_FOUND(68800), PO_MAPPING_FAILED(68818) | `BusinessException.poNotFound()` |
| SP-002 | `WM.lsp_ASN_PopulatePODs_Wrapper` | PO_LINE_NOT_FOUND(68803), PO_DUPLICATE_LINE(68815) | `BusinessException.poLineNotFound()` |
| SP-003 | `WM.lsp_FinalizeReceipt_Wrapper` | RECEIPT_ALREADY_FINALIZED(68901), FINALIZE_VALIDATION_FAILED(68920) | `BusinessException.receiptAlreadyFinalized()` |
| SP-004 | `WM.lsp_ASN_PopulateSOs_Wrapper` | TRADE_RETURN_NOT_FOUND(69950), TRADE_RETURN_CREATION_FAILED(69951) | `BusinessException.tradeReturnNotFound()` |
| SP-005 | `WM.lsp_ASN_PopulateSODs_Wrapper` | TRADE_RETURN_SOD_CREATION_FAILED(69956) | `BusinessException.tradeReturnCreationFailed()` |
| SP-006 | `WM.lsp_ASNReleasePATask_Wrapper` | PUTAWAY_TASK_RELEASE_FAILED(69205) | `BusinessException.putawayTaskReleaseFailed()` |
| SP-007 | `WM.lsp_FlowThruAllocate_Wrapper` | FLOWTHRU_ALLOCATION_FAILED(69310) | `BusinessException.flowThruAllocationFailed()` |
| SP-008 | `WM.lsp_XDockAllocation_Wrapper` | XDOCK_ALLOCATION_FAILED(69301) | `BusinessException.xdockAllocationFailed()` |
| SP-009 | `WM.lsp_Validate_Receipt_Std` | VALIDATION_REQUIRED_FIELD(69100), VALIDATION_BUSINESS_RULE(69110) | `BusinessException.validationFailed()` |
| SP-010 | `WM.lsp_Validate_Receiptdetail_Std` | VALIDATION_QUANTITY_INVALID(69106) | `BusinessException.validationFailed()` |
| SP-011 | `WM.lsp_Populate_GetDocFieldsMap` | LOTTABLE_MAPPING_FAILED(69402) | `BusinessException.lottableMappingFailed()` |
| SP-012 | `WM.lsp_Pre_Delete_PO_STD` | PO_NOT_FOUND(68800), PO_ALREADY_CLOSED(68801) | `BusinessException.poNotFound()` |
| SP-013 | `WM.lsp_Pre_Delete_PODetail_STD` | PO_LINE_NOT_FOUND(68803) | `BusinessException.poLineNotFound()` |
| SP-014 | `WM.lsp_RCMConfigSP_PO_Wrapper` | CONFIG_NOT_FOUND(69980) | `BusinessException.configNotFound()` |

### 1.2 Pre-Finalize Hooks — ispPRREC Series (37 Total)

| SP ID | SP Name | Client | Error Code | Java Exception |
|-------|---------|--------|------------|----------------|
| SP-020 | `ispPRREC01` | H&M (CN) | PRE_FINALIZE_HM_FAILED(69511) | `BusinessException.preFinalizeHookFailed("HM", ...)` |
| SP-021 | `ispPRREC02` | Nike | PRE_FINALIZE_NIKE_FAILED(69512) | `BusinessException.preFinalizeHookFailed("NIKE", ...)` |
| SP-022 | `ispPRREC03` | Adidas | PRE_FINALIZE_ADIDAS_FAILED(69513) | `BusinessException.preFinalizeHookFailed("ADIDAS", ...)` |
| SP-023 | `ispPRREC04` | Columbia | PRE_FINALIZE_COLUMBIA_FAILED(69514) | `BusinessException.preFinalizeHookFailed("COLUMBIA", ...)` |
| SP-024 | `ispPRREC05` | Unilever | PRE_FINALIZE_UNILEVER_FAILED(69515) | `BusinessException.preFinalizeHookFailed("UNILEVER", ...)` |
| SP-025 | `ispPRREC06` | New Look | PRE_FINALIZE_NEWLOOK_FAILED(69516) | `BusinessException.preFinalizeHookFailed("NEWLOOK", ...)` |
| SP-026–31 | `ispPRREC07-12` | Regional | PRE_FINALIZE_REGIONAL_FAILED(69519) | `BusinessException.preFinalizeHookFailed("REGIONAL", ...)` |
| SP-032 | `ispPRREC12` | India | PRE_FINALIZE_INDIA_FAILED(69517) | `BusinessException.preFinalizeHookFailed("INDIA", ...)` |
| SP-033 | `ispPRREC13` | DSG (TH) | PRE_FINALIZE_DSG_TH_FAILED(69518) | `BusinessException.preFinalizeHookFailed("DSG_TH", ...)` |
| SP-034+ | `ispPRREC14–37` | Various | PRE_FINALIZE_HOOK_FAILED(69510) | `BusinessException.preFinalizeHookFailed(name, ...)` |

### 1.3 Post-Finalize Hooks — ispASNFZ Series (30 Total)

| SP ID | SP Name | Purpose | Error Code | Java Exception |
|-------|---------|---------|------------|----------------|
| SP-040 | `ispASNFZ01` | Batch release | POST_FINALIZE_BATCH_RELEASE_FAILED(69521) | `BusinessException.postFinalizeHookFailed("BATCH_RELEASE", ...)` |
| SP-041 | `ispASNFZ02` | UCC stamp | POST_FINALIZE_UCC_STAMP_FAILED(69522) | `BusinessException.postFinalizeHookFailed("UCC_STAMP", ...)` |
| SP-042 | `ispASNFZ03` | Auto PA | POST_FINALIZE_AUTO_PA_FAILED(69523) | `BusinessException.postFinalizeHookFailed("AUTO_PA", ...)` |
| SP-043 | `ispASNFZ04` | Notification | POST_FINALIZE_NOTIFICATION_FAILED(69524) | `BusinessException.postFinalizeHookFailed("NOTIFICATION", ...)` |
| SP-044 | `ispASNFZ05` | Inv sync | POST_FINALIZE_INV_SYNC_FAILED(69525) | `BusinessException.postFinalizeHookFailed("INV_SYNC", ...)` |
| SP-045 | `ispASNFZ06` | Quality | POST_FINALIZE_QUALITY_CHECK_FAILED(69526) | `BusinessException.postFinalizeHookFailed("QUALITY_CHECK", ...)` |
| SP-046 | `ispASNFZ07` | Customs | POST_FINALIZE_CUSTOMS_FAILED(69527) | `BusinessException.postFinalizeHookFailed("CUSTOMS", ...)` |
| SP-047 | `ispASNFZ08` | Auto alloc | POST_FINALIZE_AUTO_ALLOC_FAILED(69528) | `BusinessException.postFinalizeHookFailed("AUTO_ALLOCATE", ...)` |
| SP-048 | `ispASNFZ09` | NewLook adj | POST_FINALIZE_NEWLOOK_ADJ_FAILED(69529) | `BusinessException.postFinalizeHookFailed("NEWLOOK_ADJUST", ...)` |
| SP-050 | `ispASNFZ24` | Columbia UCC | POST_FINALIZE_COLUMBIA_UCC_FAILED(69530) | `BusinessException.postFinalizeHookFailed("COLUMBIA_UCC", ...)` |
| SP-049+ | `ispASNFZ10–30` | Various | POST_FINALIZE_HOOK_FAILED(69520) | `BusinessException.postFinalizeHookFailed(name, ...)` |

### 1.4 Core Engine SPs

| SP ID | SP Name | Error Codes | Java Exception |
|-------|---------|-------------|----------------|
| SP-060 | `ispFinalizeReceipt` | FINALIZE_INVENTORY_POST_FAILED(68921), FINALIZE_PO_UPDATE_FAILED(68922) | `BusinessException.finalizeInventoryPostFailed()` |
| SP-061 | `nspPASTD` | PUTAWAY_STRATEGY_NOT_FOUND(69200), PUTAWAY_LOCATION_NOT_FOUND(69201) | `BusinessException.putawayStrategyNotFound()` |
| SP-062 | `nspInventoryHoldWrapper` | HOLD_APPLICATION_FAILED(68707), HOLD_RELEASE_FAILED(68708) | `BusinessException.holdApplicationFailed()` |
| SP-063 | `isp_ItrnUCCAdd` | UCC_CREATION_FAILED(68742), UCC_ALREADY_EXISTS(68741) | Direct `BusinessException(ErrorCode.UCC_CREATION_FAILED)` |
| SP-064 | `nsp_xdockorderprocessing` | XDOCK_PROCESSING_FAILED(69303), XDOCK_SO_CREATION_FAILED(69304) | `BusinessException.xdockProcessingFailed()` |
| SP-065 | `isp_ASN_ExtendedValidation` | VALIDATION_EXTENDED_FAILED(69120) | `BusinessException.validationFailed()` |

### 1.5 Putaway Release SPs

| SP ID | SP Name | Error Code | Java Exception |
|-------|---------|------------|----------------|
| SP-070 | `isp_ASNReleasePATask_Wrapper` | PUTAWAY_TASK_RELEASE_FAILED(69205) | `BusinessException.putawayTaskReleaseFailed()` |
| SP-071 | `ispPARL01` | PUTAWAY_PARL01_FAILED(69220) | `BusinessException.putawayReleaseFailed(..., "PARL01", ...)` |
| SP-072 | `ispPARL02` | PUTAWAY_PARL02_FAILED(69221) | `BusinessException.putawayReleaseFailed(..., "PARL02", ...)` |
| SP-073 | `ispPARL03` | PUTAWAY_PARL03_FAILED(69222) | `BusinessException.putawayReleaseFailed(..., "PARL03", ...)` |
| SP-074 | `ispPARL04` | PUTAWAY_PARL04_FAILED(69223) | `BusinessException.putawayReleaseFailed(..., "PARL04", ...)` |
| SP-075 | `ispPARL05` | PUTAWAY_PARL05_FAILED(69224) | `BusinessException.putawayReleaseFailed(..., "PARL05", ...)` |
| SP-076 | `ispPARL06` | PUTAWAY_PARL06_FAILED(69225) | `BusinessException.putawayReleaseFailed(..., "PARL06", ...)` |
| SP-077 | `ispPARL07` | PUTAWAY_PARL07_FAILED(69226) | `BusinessException.putawayReleaseFailed(..., "PARL07", ...)` |
| SP-078 | `ispPARL08` | PUTAWAY_PARL08_FAILED(69227) | `BusinessException.putawayReleaseFailed(..., "PARL08", ...)` |

### 1.6 Batch Putaway SPs

| SP ID | SP Name | Error Code | Java Exception |
|-------|---------|------------|----------------|
| SP-080 | `ispBatPA01` | PUTAWAY_BATCH_PA01_FAILED(69211) | `BusinessException.putawayBatchFailed(..., "PA01", ...)` |
| SP-081 | `ispBatPA02` | PUTAWAY_BATCH_PA02_FAILED(69212) | `BusinessException.putawayBatchFailed(..., "PA02", ...)` |
| SP-082 | `ispBatPA03` | PUTAWAY_BATCH_PA03_FAILED(69213) | `BusinessException.putawayBatchFailed(..., "PA03", ...)` |
| SP-083 | `ispBatPA04` | PUTAWAY_BATCH_PA04_FAILED(69214) | `BusinessException.putawayBatchFailed(..., "PA04", ...)` |
| SP-084 | `ispBatPA05` | PUTAWAY_BATCH_PA05_FAILED(69215) | `BusinessException.putawayBatchFailed(..., "PA05", ...)` |
| SP-085 | `ispBatPA06` | PUTAWAY_BATCH_PA06_FAILED(69216) | `BusinessException.putawayBatchFailed(..., "PA06", ...)` |

### 1.7 Lottable Rule SPs

| SP ID | SP Name | Error Code | Java Exception |
|-------|---------|------------|----------------|
| SP-090 | `ispLottableRule_Wrapper` | LOTTABLE_RULE_NOT_FOUND(69400) | `BusinessException.lottableRuleNotFound()` |
| SP-091 | `ispDefLot1FrRcptDtl` | LOTTABLE01_GENERATION_FAILED(69410) | `BusinessException.lottableGenerationFailed(1, ...)` |
| SP-092 | `ispDefLot2FrRcptDtl` | LOTTABLE02_GENERATION_FAILED(69411) | `BusinessException.lottableGenerationFailed(2, ...)` |
| SP-093 | `ispGenLot2BySuppLot` | LOTTABLE_SUPPLIER_LOT_FAILED(69420) | Direct `BusinessException(ErrorCode.LOTTABLE_SUPPLIER_LOT_FAILED)` |
| SP-094 | `ispGenLot1_TH01` | LOTTABLE_TH_RULE_FAILED(69421) | Direct `BusinessException(ErrorCode.LOTTABLE_TH_RULE_FAILED)` |
| SP-095 | `ispGenLot2_TH02` | LOTTABLE_TH_RULE_FAILED(69421) | Direct `BusinessException(ErrorCode.LOTTABLE_TH_RULE_FAILED)` |
| SP-096 | `ispGenLot12_TW01` | LOTTABLE_TW_RULE_FAILED(69422) | Direct `BusinessException(ErrorCode.LOTTABLE_TW_RULE_FAILED)` |
| SP-097 | `ispGenLotMondelez` | LOTTABLE_MONDELEZ_FAILED(69424) | Direct `BusinessException(ErrorCode.LOTTABLE_MONDELEZ_FAILED)` |
| SP-098 | `ispGenLotUNILEVER` | LOTTABLE_UNILEVER_FAILED(69425) | Direct `BusinessException(ErrorCode.LOTTABLE_UNILEVER_FAILED)` |
| SP-099 | `ispDefLot1FrRcptDtl_NIKECN` | LOTTABLE_NIKE_CN_FAILED(69423) | Direct `BusinessException(ErrorCode.LOTTABLE_NIKE_CN_FAILED)` |
| SP-100+ | Drools rules | LOTTABLE_DROOLS_RULE_FAILED(69430) | `BusinessException.droolsRuleFailed()` |

### 1.8 Pre-Populate PO SPs

| SP ID | SP Name | Error Code | Java Exception |
|-------|---------|------------|----------------|
| SP-110 | `isp_PrePopulatePO_Wrapper` | PRE_POPULATE_HOOK_FAILED(69540) | `BusinessException.prePopulateHookFailed()` |
| SP-111 | `ispPRPPLPO01` | PRE_POPULATE_STANDARD_FAILED(69541) | `BusinessException.prePopulateHookFailed("STANDARD", ...)` |
| SP-112 | `ispPRPPLPO02` | PRE_POPULATE_DATE_VAL_FAILED(69542) | `BusinessException.prePopulateHookFailed("DATE_VALIDATION", ...)` |
| SP-113 | `ispPRPPLPO03` | PRE_POPULATE_ADIDAS_FAILED(69543) | `BusinessException.prePopulateHookFailed("ADIDAS", ...)` |
| SP-114 | `ispPRPPLPO04` | PRE_POPULATE_QTY_FAILED(69544) | `BusinessException.prePopulateHookFailed("QUANTITY", ...)` |
| SP-115 | `ispPRPPLPO05` | PRE_POPULATE_XREF_FAILED(69545) | `BusinessException.prePopulateHookFailed("CROSS_REFERENCE", ...)` |
| SP-116 | `ispPRPPLPO_GBR_JCB` | PRE_POPULATE_JCB_FAILED(69546) | `BusinessException.prePopulateHookFailed("JCB", ...)` |

### 1.9 Client Auto-ASN SPs

| SP ID | SP Name | Error Code | Java Exception |
|-------|---------|------------|----------------|
| SP-120 | `isp_NIKEKR_PopulatePOTOASN` | CLIENT_NIKE_KR_FAILED(69551) | `BusinessException.clientAutoAsnFailed("NIKE_KR", ...)` |
| SP-121 | `isp_HMIND_AutoCreateAsnByPO` | CLIENT_HM_IND_FAILED(69552) | `BusinessException.clientAutoAsnFailed("HM_IND", ...)` |
| SP-122 | `ispPopulateTOPO_FLEX` | CLIENT_FLEX_FAILED(69553) | `BusinessException.clientAutoAsnFailed("FLEXTRONICS", ...)` |
| SP-123 | `ispPopulateTOPO_ULM` | CLIENT_ULM_FAILED(69554) | `BusinessException.clientAutoAsnFailed("ULM", ...)` |

### 1.10 Utility SPs

| SP ID | SP Name | Error Code | Java Exception |
|-------|---------|------------|----------------|
| SP-130 | `nspg_GetKey` | GET_KEY_FAILED_1(68686) | Direct `BusinessException(ErrorCode.GET_KEY_FAILED_1)` |
| SP-131 | `nspGetRight` | GET_RIGHT_FAILED(69911) | Direct `BusinessException(ErrorCode.GET_RIGHT_FAILED)` |
| SP-132 | `WM.lsp_SetUser` | DATABASE_ERROR(69020) | Direct `BusinessException(ErrorCode.DATABASE_ERROR)` |
| SP-133 | `WM.lsp_ResetUser` | DATABASE_ERROR(69020) | Direct `BusinessException(ErrorCode.DATABASE_ERROR)` |
| SP-134 | `ispGenTransmitLog3` | FINALIZE_TRANSMITLOG_FAILED(68928) | Direct `BusinessException(ErrorCode.FINALIZE_TRANSMITLOG_FAILED)` |

---

## Section 2: Triggers → Error Codes

| TR ID | Trigger Name | Table | Error Code | Java Event/Exception |
|-------|--------------|-------|------------|----------------------|
| TR-001 | `ntrPOHeaderAdd` | PO | PO_HEADER_ADD_TRIGGER_FAILED(69710) | `BusinessException.poTriggerFailed("ADD", ...)` |
| TR-002 | `ntrPOHeaderUpdate` | PO | PO_HEADER_UPDATE_TRIGGER_FAILED(69711) | `BusinessException.poTriggerFailed("UPDATE", ...)` |
| TR-003 | `ntrPOHeaderDelete` | PO | PO_HEADER_DELETE_TRIGGER_FAILED(69712) | `BusinessException.poTriggerFailed("DELETE", ...)` |
| TR-004 | `ntrPODetailAdd` | PODETAIL | PO_DETAIL_ADD_TRIGGER_FAILED(69713) | `BusinessException.poDetailTriggerFailed("ADD", ...)` |
| TR-005 | `ntrPODetailUpdate` | PODETAIL | PO_DETAIL_UPDATE_TRIGGER_FAILED(69714) | `BusinessException.poDetailTriggerFailed("UPDATE", ...)` |
| TR-006 | `ntrPODetailDelete` | PODETAIL | PO_DETAIL_DELETE_TRIGGER_FAILED(69715) | `BusinessException.poDetailTriggerFailed("DELETE", ...)` |
| TR-007 | `ntrReceiptHeaderAdd` | RECEIPT | RECEIPT_HEADER_ADD_TRIGGER_FAILED(69720) | `BusinessException.receiptTriggerFailed("ADD", ...)` |
| TR-008 | `ntrReceiptHeaderUpdate` | RECEIPT | RECEIPT_HEADER_UPDATE_TRIGGER_FAILED(69721) | `BusinessException.receiptTriggerFailed("UPDATE", ...)` |
| TR-009 | `ntrReceiptDetailAdd` | RECEIPTDETAIL | RECEIPT_DETAIL_ADD_TRIGGER_FAILED(69722) | Direct throw |
| TR-010 | `ntrReceiptDetailUpdate` | RECEIPTDETAIL | RECEIPT_DETAIL_UPDATE_TRIGGER_FAILED(69723) | Direct throw |
| TR-011 | `ntrReceiptDetailDelete` | RECEIPTDETAIL | RECEIPT_DETAIL_DELETE_TRIGGER_FAILED(69724) | Direct throw |
| TR-012 | `ntrTransmitlog3Update` | TRANSMITLOG3 | TRANSMITLOG_UPDATE_TRIGGER_FAILED(69730) | Direct throw |

---

## Section 3: SQL Jobs → Error Codes

### 3.1 Auto-Processing Jobs

| JOB ID | Job Name | Error Code | Java Exception |
|--------|----------|------------|----------------|
| JOB-001 | `AutoPopulatePOToASN` | JOB_AUTO_POPULATE_FAILED(69610) | `BusinessException.autoPopulateJobFailed()` |
| JOB-002 | `AutoFinalizeASN` | JOB_AUTO_FINALIZE_FAILED(69611) | `BusinessException.autoFinalizeJobFailed()` |
| JOB-003 | `AutoReleasePATask` | JOB_AUTO_PA_RELEASE_FAILED(69612) | `BusinessException.autoPaReleaseJobFailed()` |
| JOB-004 | `BuildAutoAllocation` | JOB_BUILD_ALLOC_FAILED(69613) | `BusinessException.jobExecutionFailed("BuildAutoAllocation", ...)` |
| JOB-005 | `XDockAutoAL` | JOB_XDOCK_ALLOC_FAILED(69614) | `BusinessException.jobExecutionFailed("XDockAutoAL", ...)` |
| JOB-006 | `XDockCreateSO` | JOB_XDOCK_SO_FAILED(69615) | `BusinessException.jobExecutionFailed("XDockCreateSO", ...)` |

### 3.2 Interface Jobs

| JOB ID | Job Name | Error Code | Java Exception |
|--------|----------|------------|----------------|
| JOB-010 | `InboundMaster` | JOB_INBOUND_MASTER_FAILED(69620) | `BusinessException.inboundMasterJobFailed()` |
| JOB-011 | `GenericInbound_PO` | JOB_GENERIC_INBOUND_PO_FAILED(69621) | `BusinessException.jobExecutionFailed("GenericInbound_PO", ...)` |
| JOB-012 | `GenericInbound_ASN` | JOB_GENERIC_INBOUND_ASN_FAILED(69622) | `BusinessException.jobExecutionFailed("GenericInbound_ASN", ...)` |
| JOB-013 | `GenericOutbound` | JOB_GENERIC_OUTBOUND_FAILED(69623) | `BusinessException.jobExecutionFailed("GenericOutbound", ...)` |

### 3.3 Archive/Cleanup Jobs

| JOB ID | Job Name | Error Code | Java Exception |
|--------|----------|------------|----------------|
| JOB-020 | `Archive_WMS` | JOB_ARCHIVE_FAILED(69630) | `BusinessException.archiveJobFailed()` |
| JOB-021 | `Purge_Interface` | JOB_PURGE_INTERFACE_FAILED(69631) | `BusinessException.jobExecutionFailed("Purge_Interface", ...)` |
| JOB-022 | `Purge_LOTxLOCxID` | JOB_PURGE_INVENTORY_FAILED(69632) | `BusinessException.jobExecutionFailed("Purge_LOTxLOCxID", ...)` |

### 3.4 Other Jobs

| JOB ID | Category | Error Code | Java Exception |
|--------|----------|------------|----------------|
| JOB-030 | Alert | JOB_ALERT_FAILED(69640) | `BusinessException.jobExecutionFailed("AlertJob", ...)` |
| JOB-031 | BI Refresh | JOB_BI_REFRESH_FAILED(69641) | `BusinessException.jobExecutionFailed("BIRefresh", ...)` |
| JOB-032 | Housekeeping | JOB_HOUSEKEEPING_FAILED(69642) | `BusinessException.jobExecutionFailed("Housekeeping", ...)` |
| JOB-033 | Client-Specific | JOB_CLIENT_SPECIFIC_FAILED(69650) | `BusinessException.jobExecutionFailed(jobName, ...)` |

---

## Section 4: Functions → Error Codes

| FN ID | Function Name | Error Code | Java Exception |
|-------|---------------|------------|----------------|
| FN-001 | `fnc_DelimSplit` | DELIM_SPLIT_FAILED(69910) | Direct throw |
| FN-002 | `fnc_GetRight` | GET_RIGHT_FAILED(69911) | Direct throw |
| FN-003 | `fnc_GetRight2` | GET_RIGHT2_FAILED(69912) | Direct throw |
| FN-004 | `fncConvUOM` | UOM_CONVERSION_FAILED(69913) | `BusinessException.uomConversionFailed()` |
| FN-005 | `fnc_CalculateCube` | CUBE_CALCULATION_FAILED(69914) | Direct throw |
| FN-010 | `fnc_Code128C_Uni` | BARCODE_GENERATION_FAILED(69920) | `BusinessException.barcodeGenerationFailed()` |
| FN-011 | `fnc_CalcCheckDigit_M10` | CHECK_DIGIT_FAILED(69921) | Direct throw |
| FN-012 | `fnc_GetGS1Label` | GS1_LABEL_FAILED(69922) | Direct throw |
| FN-020 | `fnc_BuildPutawayRestriction` | PUTAWAY_RESTRICTION_BUILD_FAILED(69930) | `BusinessException.putawayRestrictionBuildFailed()` |
| FN-030 | Date/Time Functions | DATE_TIME_UTIL_FAILED(69940) | Direct throw |
| FN-031 | TCP/WCS Functions | TCP_WCS_UTIL_FAILED(69941) | Direct throw |
| FN-032 | Vocollect Functions | VOCOLLECT_UTIL_FAILED(69942) | Direct throw |

---

## Section 5: Views → Error Codes

| VW ID | View Name | Error Code | Java Projection/Exception |
|-------|-----------|------------|---------------------------|
| VW-001 | `V_PO` | VIEW_PO_FAILED(69810) | `POProjection` |
| VW-002 | `V_PODetail` | VIEW_PO_DETAIL_FAILED(69811) | `PODetailProjection` |
| VW-003 | `V_ASN` | VIEW_ASN_FAILED(69812) | `ASNProjection` |
| VW-004 | `V_ASN_Extended_Validation` | VIEW_ASN_EXTENDED_FAILED(69813) | `ValidationConfigDTO` |
| VW-005 | `V_RECEIPT` | VIEW_RECEIPT_FAILED(69814) | `ReceiptProjection` |
| VW-006 | `V_RECEIPTDETAIL` | VIEW_RECEIPT_DETAIL_FAILED(69815) | `ReceiptDetailProjection` |
| VW-010 | BI ASN Confirmation | VIEW_BI_ASN_CONFIRM_FAILED(69820) | `ASNConfirmationProjection` |
| VW-011 | BI PO Detail | VIEW_BI_PO_DETAIL_FAILED(69821) | `PODetailProjection` |
| VW-012 | BI Receipt Confirm | VIEW_BI_RECEIPT_CONFIRM_FAILED(69822) | `ReceiptConfirmationProjection` |
| VW-013 | BI Inventory Stock | VIEW_BI_INV_STOCK_FAILED(69823) | `InventoryStockProjection` |

---

## Usage Examples

### Throwing Exceptions in Service Layer

```java
// PO not found
throw BusinessException.poNotFound("PO-001");

// Receipt already finalized
throw BusinessException.receiptAlreadyFinalized("RCV-123");

// Plugin failed with cause
try {
    plugin.execute(context);
} catch (Exception e) {
    throw BusinessException.preFinalizeHookFailed("NIKE", receiptKey, e);
}

// Lottable rule failed
throw BusinessException.lottableGenerationFailed(2, "Invalid date format");

// Job already running
throw BusinessException.jobAlreadyRunning("AutoFinalizeASN");
```

### Handling in Global Exception Handler

```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException ex) {
    Map<String, Object> error = ex.toErrorResponse();
    // Response includes both legacyCode (68xxx) and modernCode (XXX_nnn)
    return ResponseEntity.status(ex.getHttpStatus()).body(error);
}
```

### API Response Format

```json
{
  "errorCode": "PO_001",
  "legacyCode": 68800,
  "message": "PO not found: PO-001",
  "description": "PO Not Found",
  "category": "PO_ASN",
  "details": {
    "poKey": "PO-001"
  },
  "retryable": false,
  "timestamp": "2026-05-05T10:30:00",
  "status": 404
}
```

---

## Error Code Reference by Category

### Task Processing (68675-68691)
| Code | Name | HTTP |
|------|------|------|
| 68675 | INVALID_TASK_DETAIL_KEY | 400 |
| 68676 | INVALID_FROM_LOCATION | 400 |
| 68677 | INVALID_TO_ID | 400 |
| 68678 | INVALID_TO_LOCATION | 400 |
| 68679 | ITEM_ALREADY_PROCESSED | 409 |
| 68680-82 | INVALID_REASON_CODE | 400 |
| 68683 | TASK_DETAIL_UPDATE_FAILED | 500 |
| 68684 | INSERT_TASK_FAILED | 500 |
| 68689 | TASK_NOT_FOUND | 404 |

### Inventory (68700-68742)
| Code | Name | HTTP |
|------|------|------|
| 68700 | INVENTORY_NOT_FOUND | 404 |
| 68701 | INVENTORY_INSUFFICIENT | 400 |
| 68702 | INVENTORY_ALREADY_ALLOCATED | 409 |
| 68703 | INVENTORY_ON_HOLD | 400 |
| 68707 | HOLD_APPLICATION_FAILED | 500 |
| 68710 | LOCATION_NOT_FOUND | 404 |
| 68711 | LOCATION_FULL | 400 |
| 68720 | LICENSE_PLATE_NOT_FOUND | 404 |
| 68730 | LOT_NOT_FOUND | 404 |
| 68731 | LOT_EXPIRED | 400 |

### PO/ASN (68800-68822)
| Code | Name | HTTP |
|------|------|------|
| 68800 | PO_NOT_FOUND | 404 |
| 68801 | PO_ALREADY_CLOSED | 409 |
| 68802 | PO_CANCELLED | 409 |
| 68803 | PO_LINE_NOT_FOUND | 404 |
| 68805 | ASN_NOT_FOUND | 404 |
| 68806 | ASN_ALREADY_FINALIZED | 409 |
| 68807 | ASN_INVALID_STATUS | 400 |
| 68816 | PO_VARIANCE_EXCEEDED | 400 |
| 68821 | ASN_DUPLICATE_NOT_ALLOWED | 409 |

### Receipt/Finalization (68900-68928)
| Code | Name | HTTP |
|------|------|------|
| 68900 | RECEIPT_NOT_FOUND | 404 |
| 68901 | RECEIPT_ALREADY_FINALIZED | 409 |
| 68902 | RECEIPT_INVALID_STATUS | 400 |
| 68910 | RECEIPT_QUANTITY_MISMATCH | 400 |
| 68911 | RECEIPT_OVERRECEIVE_NOT_ALLOWED | 400 |
| 68920 | FINALIZE_VALIDATION_FAILED | 400 |
| 68921 | FINALIZE_INVENTORY_POST_FAILED | 500 |
| 68924 | FINALIZE_PUTAWAY_RELEASE_FAILED | 500 |

---

**Document Version History:**
| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2026-05-05 | Claude | Initial creation with full SP/Job/Function/Trigger mapping |
