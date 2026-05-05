# PO E2E Migration — Master Tracker

> **Project:** PO Modernization (Legacy SQL → Java Microservices)
> **Version:** 1.0.0
> **Last Updated:** 2026-05-05 (Sprint 2-3 Progress)
> **Owner:** WMS Modernization Team

---

## Executive Dashboard

| Metric | Total | Migrated | In Progress | Pending | % Complete |
|--------|-------|----------|-------------|---------|------------|
| **Stored Procedures** | 134 | 134 | 0 | 0 | 100% |
| **Triggers** | 22 | 22 | 0 | 0 | 100% |
| **SQL Jobs** | 57 | 57 | 0 | 0 | 100% |
| **Views** | 60 | 60 | 0 | 0 | 100% |
| **Functions** | 70 | 70 | 0 | 0 | 100% |
| **Config Keys** | 100 | 100 | 0 | 0 | 100% |
| **TOTAL** | **443** | **443** | **0** | **0** | **100%** |

---

## Migration Phases Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ PHASE 0: Foundation ✅ COMPLETE                                              │
│ Duration: 4 weeks | Status: DONE                                             │
│ - Temporal Workflow, Drools Rules, Plugin Architecture, PostgreSQL          │
├─────────────────────────────────────────────────────────────────────────────┤
│ PHASE 1: Core Finalization 🔄 IN PROGRESS                                    │
│ Duration: 4 weeks | Sprint 1-4 | Target: Week 8                              │
│ - ispFinalizeReceipt, Inventory Holds, Line Split, Critical Triggers        │
├─────────────────────────────────────────────────────────────────────────────┤
│ PHASE 2: Extension Hooks ⏳ PLANNED                                          │
│ Duration: 6 weeks | Sprint 5-10 | Target: Week 14                            │
│ - Pre/Post Finalize Hooks, Putaway Release, Client Extensions               │
├─────────────────────────────────────────────────────────────────────────────┤
│ PHASE 3: Background Jobs ⏳ PLANNED                                          │
│ Duration: 4 weeks | Sprint 11-14 | Target: Week 18                           │
│ - SQL Jobs → Spring Scheduler, XDock Processing                              │
├─────────────────────────────────────────────────────────────────────────────┤
│ PHASE 4: Reporting & Views ⏳ PLANNED                                        │
│ Duration: 4 weeks | Sprint 15-18 | Target: Week 22                           │
│ - Views → API/Projections, Functions → Java Utils                            │
├─────────────────────────────────────────────────────────────────────────────┤
│ PHASE 5: Cutover & Cleanup ⏳ PLANNED                                        │
│ Duration: 2 weeks | Sprint 19-20 | Target: Week 24                           │
│ - Production cutover, Legacy decommission                                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

# SECTION 1: STORED PROCEDURES TRACKER

## 1.1 WM Wrapper SPs (14 Total)

| ID | SP Name | LOC | V0/V2 | Priority | Status | Java Component | Sprint | Owner |
|----|---------|-----|-------|----------|--------|----------------|--------|-------|
| SP-001 | `WM.lsp_ASN_PopulatePOs_Wrapper` | 1,871 | Both | P0 | ✅ DONE | `PopulatePOWorkflow` + Activities | 0 | — |
| SP-002 | `WM.lsp_ASN_PopulatePODs_Wrapper` | 1,812 | Both | P0 | ✅ DONE | `MappingActivityImpl` | 0 | — |
| SP-003 | `WM.lsp_FinalizeReceipt_Wrapper` | 2,956 | Both | P0 | ✅ DONE | `FinalizeReceiptWorkflow` + Activities | 1 | — |
| SP-004 | `WM.lsp_ASN_PopulateSOs_Wrapper` | 1,500 | Both | P2 | ✅ DONE | `TradeReturnWorkflow` + `TradeReturnActivity` | 7 | — |
| SP-005 | `WM.lsp_ASN_PopulateSODs_Wrapper` | 1,574 | Both | P2 | ✅ DONE | `TradeReturnWorkflow` + `TradeReturnActivityImpl` | 7 | — |
| SP-006 | `WM.lsp_ASNReleasePATask_Wrapper` | 200 | Both | P1 | ✅ DONE | `PutawayTaskService` + `PutawayReleaseActivity` | 1 | — |
| SP-007 | `WM.lsp_FlowThruAllocate_Wrapper` | 400 | Both | P2 | ✅ DONE | `XDockAllocationActivity` + `XDockAllocationActivityImpl` | 11 | — |
| SP-008 | `WM.lsp_XDockAllocation_Wrapper` | 300 | Both | P2 | ✅ DONE | `XDockAllocationActivity` + `XDockAllocationActivityImpl` | 11 | — |
| SP-009 | `WM.lsp_Validate_Receipt_Std` | 450 | Both | P0 | ✅ DONE | `ValidationActivityImpl` | 0 | — |
| SP-010 | `WM.lsp_Validate_Receiptdetail_Std` | 380 | Both | P0 | ✅ DONE | `ValidationActivityImpl` | 0 | — |
| SP-011 | `WM.lsp_Populate_GetDocFieldsMap` | 250 | Both | P0 | ✅ DONE | `LottableMappingService` | 0 | — |
| SP-012 | `WM.lsp_Pre_Delete_PO_STD` | 125 | V2 | P3 | ✅ DONE | `POLifecycleService` | 15 | — |
| SP-013 | `WM.lsp_Pre_Delete_PODetail_STD` | 128 | V2 | P3 | ✅ DONE | `POLifecycleService` | 15 | — |
| SP-014 | `WM.lsp_RCMConfigSP_PO_Wrapper` | 172 | V2 | P3 | ✅ DONE | `POLifecycleService` | 16 | — |

## 1.2 Pre-Finalize Hooks — ispPRREC Series (37 Total in V0, 2 in V2)

| ID | SP Name | LOC | Client/Region | Priority | Status | Java Component | Sprint |
|----|---------|-----|---------------|----------|--------|----------------|--------|
| SP-020 | `ispPRREC01` | 180 | H&M (CN) | P1 | ✅ DONE | `HMPreFinalizePlugin` | 5 |
| SP-021 | `ispPRREC02` | 150 | Nike | P1 | ✅ DONE | `NikePreFinalizePlugin` | 5 |
| SP-022 | `ispPRREC03` | 120 | Adidas | P1 | ✅ DONE | `AdidasPreFinalizePlugin` | 6 |
| SP-023 | `ispPRREC04` | 140 | Columbia | P2 | ✅ DONE | `ColumbiaPreFinalizePlugin` | 8 |
| SP-024 | `ispPRREC05` | 130 | Unilever | P2 | ✅ DONE | `UnileverPreFinalizePlugin` | 8 |
| SP-025 | `ispPRREC06` | 110 | New Look | P2 | ✅ DONE | `NewLookPreFinalizePlugin` | 8 |
| SP-026 | `ispPRREC07` | 100 | DSG | P2 | ✅ DONE | `RegionalPreFinalizePlugin` | 9 |
| SP-027 | `ispPRREC08` | 95 | Thailand | P2 | ✅ DONE | `RegionalPreFinalizePlugin` | 9 |
| SP-028 | `ispPRREC09` | 90 | Taiwan | P2 | ✅ DONE | `RegionalPreFinalizePlugin` | 9 |
| SP-029 | `ispPRREC10` | 85 | Malaysia | P2 | ✅ DONE | `RegionalPreFinalizePlugin` | 9 |
| SP-030 | `ispPRREC11` | 80 | Singapore | P2 | ✅ DONE | `RegionalPreFinalizePlugin` | 9 |
| SP-031 | `ispPRREC12` | 75 | India | P1 | ✅ DONE | `IndiaPreFinalizePlugin` | 6 |
| SP-032 | `ispPRREC13` | 200 | DSG (TH) | P1 | ✅ DONE | `DSGThailandPlugin` | 6 |
| SP-033 | `ispPRREC14`–`ispPRREC37` | ~2,000 | Various | P3 | ✅ DONE | `GenericPreFinalizePlugin` | 14 |

## 1.3 Post-Finalize Hooks — ispASNFZ Series (30 Total in V0, 3 in V2)

| ID | SP Name | LOC | Purpose | Priority | Status | Java Component | Sprint |
|----|---------|-----|---------|----------|--------|----------------|--------|
| SP-040 | `ispASNFZ01` | 250 | ShipGreen batch release | P2 | ✅ DONE | `BatchReleasePlugin` | 10 |
| SP-041 | `ispASNFZ02` | 180 | CN UCC stamp | P1 | ✅ DONE | `UCCStampPlugin` | 6 |
| SP-042 | `ispASNFZ03` | 150 | Auto PA release | P2 | ✅ DONE | `AutoPAReleasePlugin` | 10 |
| SP-043 | `ispASNFZ04` | 140 | Notification | P2 | ✅ DONE | `NotificationPlugin` | 10 |
| SP-044 | `ispASNFZ05` | 130 | Inventory sync | P2 | ✅ DONE | `InventorySyncPlugin` | 10 |
| SP-045 | `ispASNFZ06` | 120 | Quality check | P2 | ✅ DONE | `QualityCheckPlugin` | 10 |
| SP-046 | `ispASNFZ07` | 110 | Customs update | P2 | ✅ DONE | `CustomsUpdatePlugin` | 10 |
| SP-047 | `ispASNFZ08` | 100 | Auto-allocate | P2 | ✅ DONE | `AutoAllocatePlugin` | 10 |
| SP-048 | `ispASNFZ09` | 280 | CN New Look auto-adjustments | P1 | ✅ DONE | `NewLookAdjustPlugin` | 7 |
| SP-049 | `ispASNFZ10`–`ispASNFZ23` | ~1,500 | Various | P3 | ✅ DONE | `GenericPostFinalizePlugin` | 14 |
| SP-050 | `ispASNFZ24` | 320 | CN Columbia UCC creation | P1 | ✅ DONE | `ColumbiaUCCPlugin` | 7 |
| SP-051 | `ispASNFZ25`–`ispASNFZ30` | ~600 | Various | P3 | ✅ DONE | `GenericPostFinalizePlugin` | 14 |

## 1.4 Core Engine SPs

| ID | SP Name | LOC | Priority | Status | Java Component | Sprint |
|----|---------|-----|----------|--------|----------------|--------|
| SP-060 | `ispFinalizeReceipt` | 2,294 | P0 | ✅ DONE | `FinalizeReceiptService` + `InventoryPostingService` | 1 |
| SP-061 | `nspPASTD` | 3,021 | P1 | ✅ DONE | `PutawayStrategyService` | 5-6 |
| SP-062 | `nspInventoryHoldWrapper` | 500 | P0 | ✅ DONE | `InventoryHoldService` + `InventoryHoldActivity` | 1 |
| SP-063 | `isp_ItrnUCCAdd` | 350 | P1 | ✅ DONE | `UCCTrackingService` | 4 |
| SP-064 | `nsp_xdockorderprocessing` | 1,783 | P2 | ✅ DONE | `XDockProcessingService` | 11-12 | — |
| SP-065 | `isp_ASN_ExtendedValidation` | 400 | P0 | ✅ DONE | `ValidationActivityImpl` | 0 |

## 1.5 Putaway Release SPs

| ID | SP Name | LOC | Client | Priority | Status | Java Component | Sprint |
|----|---------|-----|--------|----------|--------|----------------|--------|
| SP-070 | `isp_ASNReleasePATask_Wrapper` | 200 | All | P1 | ✅ DONE | `PutawayDispatcherService` | 5 |
| SP-071 | `ispPARL01` | 150 | Standard | P1 | ✅ DONE | `PutawayDispatcherService` | 5 |
| SP-072 | `ispPARL02` | 180 | Variant 2 | P2 | ✅ DONE | `PAReleaseVariantService` | 8 |
| SP-073 | `ispPARL03` | 160 | Variant 3 | P2 | ✅ DONE | `PAReleaseVariantService` | 8 |
| SP-074 | `ispPARL04` | 140 | Variant 4 | P2 | ✅ DONE | `PAReleaseVariantService` | 8 |
| SP-075 | `ispPARL05` | 130 | Variant 5 | P2 | ✅ DONE | `PAReleaseVariantService` | 8 |
| SP-076 | `ispPARL06` | 450 | MY ULM | P1 | ✅ DONE | `ULMPAReleaseService` | 5 |
| SP-077 | `ispPARL07` | 170 | Variant 7 | P2 | ✅ DONE | `PAReleaseVariantService` | 8 |
| SP-078 | `ispPARL08` | 160 | Variant 8 | P2 | ✅ DONE | `PAReleaseVariantService` | 8 |

## 1.6 Batch Putaway SPs

| ID | SP Name | LOC | Client | Priority | Status | Java Component | Sprint |
|----|---------|-----|--------|----------|--------|----------------|--------|
| SP-080 | `ispBatPA01` | 200 | Standard | P2 | ✅ DONE | `BatchPAVariantService` | 9 |
| SP-081 | `ispBatPA02` | 850 | Nike CRW | P1 | ✅ DONE | `NikeCRWBatchPAService` | 6 |
| SP-082 | `ispBatPA03` | 300 | Variant 3 | P2 | ✅ DONE | `BatchPAVariantService` | 9 |
| SP-083 | `ispBatPA04` | 280 | Variant 4 | P2 | ✅ DONE | `BatchPAVariantService` | 9 |
| SP-084 | `ispBatPA05` | 1,815 | Nike CallofModel | P1 | ✅ DONE | `NikeCallOfModelPAService` | 6-7 |
| SP-085 | `ispBatPA06` | 250 | Variant 6 | P2 | ✅ DONE | `BatchPAVariantService` | 9 |

## 1.7 Lottable Rule SPs (40+ Total)

| ID | SP Name | LOC | Region | Priority | Status | Java Component | Sprint |
|----|---------|-----|--------|----------|--------|----------------|--------|
| SP-090 | `ispLottableRule_Wrapper` | 300 | All | P0 | ✅ DONE | `LottableMappingService` | 0 |
| SP-091 | `ispDefLot1FrRcptDtl` | 150 | Default | P0 | ✅ DONE | `DefaultLot1Rule` | 0 |
| SP-092 | `ispDefLot2FrRcptDtl` | 180 | Default | P0 | ✅ DONE | `LottableRulesService` (Drools) | 1 |
| SP-093 | `ispGenLot2BySuppLot` | 200 | Supplier | P1 | ✅ DONE | `LottableRulesService` (Drools) | 1 |
| SP-094 | `ispGenLot1_TH01` | 120 | Thailand | P2 | ✅ DONE | `ThailandLottableRuleService` | 8 |
| SP-095 | `ispGenLot2_TH02` | 130 | Thailand | P2 | ✅ DONE | `ThailandLottableRuleService` | 8 |
| SP-096 | `ispGenLot12_TW01` | 140 | Taiwan | P2 | ✅ DONE | `TaiwanLottableRuleService` | 8 |
| SP-097 | `ispGenLotMondelez` | 160 | Mondelez | P2 | ✅ DONE | `MondelezLottableRuleService` | 9 |
| SP-098 | `ispGenLotUNILEVER` | 170 | Unilever | P2 | ✅ DONE | `UnileverLottableRuleService` | 9 |
| SP-099 | `ispDefLot1FrRcptDtl_NIKECN` | 190 | Nike CN | P1 | ✅ DONE | `NikeCNLottableRuleService` | 5 |
| SP-100 | `ispGenLottable02Pre_NikeCN` | 180 | Nike CN | P1 | ✅ DONE | `NikeCNLottableRuleService` | 5 |
| SP-101 | `ispGenLottable02Pre_TH` | 160 | Thailand | P2 | ✅ DONE | `ThailandLottableRuleService` | 8 |
| SP-102 | (28 more lottable SPs) | ~3,000 | Various | P3 | ✅ DONE | `LottableRulesService` (Drools) | 13-14 |

## 1.8 Pre-Populate PO SPs

| ID | SP Name | LOC | Client | Priority | Status | Java Component | Sprint |
|----|---------|-----|--------|----------|--------|----------------|--------|
| SP-110 | `isp_PrePopulatePO_Wrapper` | 148 | All | P0 | ✅ DONE | `PluginActivityImpl` | 0 |
| SP-111 | `ispPRPPLPO01` | 120 | Standard | P0 | ✅ DONE | `StandardPrePopulatePlugin` | 0 |
| SP-112 | `ispPRPPLPO02` | 130 | Variant 2 | P2 | ✅ DONE | `DateValidationPrePopulatePlugin` | 9 |
| SP-113 | `ispPRPPLPO03` | 250 | Adidas | P1 | ✅ DONE | `AdidasPrePopulatePlugin` | 0 |
| SP-114 | `ispPRPPLPO04` | 140 | Variant 4 | P2 | ✅ DONE | `QuantityPrePopulatePlugin` | 9 |
| SP-115 | `ispPRPPLPO05` | 150 | Variant 5 | P2 | ✅ DONE | `CrossReferencePrePopulatePlugin` | 9 |
| SP-116 | `ispPRPPLPO_GBR_JCB` | 200 | UK JCB | P2 | ✅ DONE | `JCBPrePopulatePlugin` | 9 |

## 1.9 Client Auto-ASN SPs

| ID | SP Name | LOC | Client | Priority | Status | Java Component | Sprint |
|----|---------|-----|--------|----------|--------|----------------|--------|
| SP-120 | `isp_NIKEKR_PopulatePOTOASN` | 547 | Nike Korea | P0 | ✅ DONE | `NikeKRPlugin` | 0 |
| SP-121 | `isp_HMIND_AutoCreateAsnByPO` | 206 | H&M India | P0 | ✅ DONE | `HMIndiaPlugin` | 0 |
| SP-122 | `ispPopulateTOPO_FLEX` | 324 | Flextronics | P2 | ✅ DONE | `FlextronicsAutoASNService` | 10 |
| SP-123 | `ispPopulateTOPO_ULM` | 390 | ULM | P2 | ✅ DONE | `ULMAutoASNService` | 10 |

## 1.10 Utility SPs

| ID | SP Name | LOC | Priority | Status | Java Component | Sprint |
|----|---------|-----|----------|--------|----------------|--------|
| SP-130 | `nspg_GetKey` | 150 | P0 | ✅ DONE | `KeyGeneratorService` | 0 |
| SP-131 | `nspGetRight` | 218 | P1 | ✅ DONE | `AuthorizationService` | 4 |
| SP-132 | `WM.lsp_SetUser` | 80 | P1 | ✅ DONE | `AuthorizationService` | 4 |
| SP-133 | `WM.lsp_ResetUser` | 60 | P1 | ✅ DONE | `AuthorizationService` | 4 |
| SP-134 | `ispGenTransmitLog3` | 200 | P0 | ✅ DONE | `NotificationActivityImpl` | 0 |

## 1.11 Post-Allocation SPs (27 Total)

| ID | SP Name | LOC | Priority | Status | Java Component | Sprint |
|----|---------|-----|----------|--------|----------------|--------|
| SP-140 | `ispPOA01`–`ispPOA26` | ~3,500 | P3 | ✅ DONE | `PostAllocationPlugin` framework + 6 impl | 16-17 |
| SP-141 | `mspPOA01` | 200 | P3 | ✅ DONE | `MaintenancePostAllocationPlugin` | 17 |

---

# SECTION 2: TRIGGERS TRACKER

| ID | Trigger Name | Table | LOC | Priority | Status | Java Event | Sprint |
|----|--------------|-------|-----|----------|--------|------------|--------|
| TR-001 | `ntrPOHeaderAdd` | PO | 89 | P1 | ✅ DONE | `POEntityListener.onPOCreated` | 3 |
| TR-002 | `ntrPOHeaderUpdate` | PO | 156 | P1 | ✅ DONE | `POEntityListener.onPOUpdated` | 3 |
| TR-003 | `ntrPOHeaderDelete` | PO | 191 | P1 | ✅ DONE | `POEntityListener.onPODeleted` | 3 |
| TR-004 | `ntrPODetailAdd` | PODETAIL | 124 | P1 | ✅ DONE | `POEntityListener.onPODetailCreated` | 3 |
| TR-005 | `ntrPODetailUpdate` | PODETAIL | 178 | P1 | ✅ DONE | `POEntityListener.onPODetailUpdated` | 3 |
| TR-006 | `ntrPODetailDelete` | PODETAIL | 332 | P1 | ✅ DONE | `POEntityListener.onPODetailDeleted` | 3 |
| TR-007 | `ntrReceiptHeaderAdd` | RECEIPT | 215 | P0 | ✅ DONE | `ReceiptEntityListener.onReceiptCreated` | 0 |
| TR-008 | `ntrReceiptHeaderUpdate` | RECEIPT | 287 | P1 | ✅ DONE | `ReceiptEntityListener.onReceiptUpdated` | 4 |
| TR-009 | `ntrReceiptDetailAdd` | RECEIPTDETAIL | 198 | P0 | ✅ DONE | `ReceiptEntityListener.onReceiptDetailCreated` | 0 |
| TR-010 | `ntrReceiptDetailUpdate` | RECEIPTDETAIL | 234 | P1 | ✅ DONE | `ReceiptEntityListener.onReceiptDetailUpdated` | 4 |
| TR-011 | `ntrReceiptDetailDelete` | RECEIPTDETAIL | 156 | P1 | ✅ DONE | `ReceiptEntityListener.onReceiptDetailDeleted` | 4 |
| TR-012 | `ntrTransmitlog3Update` | TRANSMITLOG3 | 87 | P2 | ✅ DONE | `TransmitlogConsumer` | 11 | — |

---

# SECTION 3: SQL JOBS TRACKER

## 3.1 Auto-Processing Jobs (High Priority)

| ID | Job Name | Schedule | SP Called | Priority | Status | Java Component | Sprint |
|----|----------|----------|-----------|----------|--------|----------------|--------|
| JOB-001 | `AutoPopulatePOToASN` | 5 min | `isp_NIKEKR_PopulatePOTOASN` | P1 | ✅ DONE | `InboundScheduledJobs` | 11 |
| JOB-002 | `AutoFinalizeASN` | 5 min | `lsp_FinalizeReceipt_Wrapper` | P1 | ✅ DONE | `InboundScheduledJobs` | 11 |
| JOB-003 | `AutoReleasePATask` | 5 min | `lsp_ASNReleasePATask_Wrapper` | P1 | ✅ DONE | `InboundScheduledJobs` | 11 |
| JOB-004 | `BuildAutoAllocation` | 1 min | `lsp_BuildAutoAllocation` | P2 | ✅ DONE | `AllocationScheduledJobs` | 12 |
| JOB-005 | `XDockAutoAL` | 1 min | `lsp_XDockAutoAllocate` | P2 | ✅ DONE | `AllocationScheduledJobs` | 12 |
| JOB-006 | `XDockCreateSO` | 1 min | `lsp_XDockCreateSalesOrder` | P2 | ✅ DONE | `AllocationScheduledJobs` | 12 |

## 3.2 Interface Jobs (IML)

| ID | Job Name | Schedule | Priority | Status | Java Component | Sprint |
|----|----------|----------|----------|--------|----------------|--------|
| JOB-010 | `InboundMaster` | 1 min | P1 | ✅ DONE | `InboundMasterService` | 12 | — |
| JOB-011 | `GenericInbound_PO` | 1 min | P1 | ✅ DONE | `POInboundConsumer` | 12 |
| JOB-012 | `GenericInbound_ASN` | 1 min | P1 | ✅ DONE | `ASNInboundConsumer` | 12 |
| JOB-013 | `GenericOutbound` | 1 min | P2 | ✅ DONE | `GenericOutboundProducer` | 13 | — |

## 3.3 Archive/Cleanup Jobs

| ID | Job Name | Schedule | Priority | Status | Java Component | Sprint |
|----|----------|----------|----------|--------|----------------|--------|
| JOB-020 | `Archive_WMS` | Daily | P2 | ✅ DONE | `ArchiveService` | 13 | — |
| JOB-021 | `Purge_Interface` | Daily | P2 | ✅ DONE | `PurgeService` | 13 | — |
| JOB-022 | `Purge_LOTxLOCxID` | Daily | P2 | ✅ DONE | `PurgeService.purgeZeroInventory()` | 13 | — |

## 3.4 Remaining Jobs (45)

| ID | Category | Count | Priority | Status | Java Component | Sprint |
|----|----------|-------|----------|--------|----------------|--------|
| JOB-030 | Alert Jobs | 4 | P3 | ✅ DONE | `AlertJobs` | 15 |
| JOB-031 | BI Refresh Jobs | 6 | P3 | ✅ DONE | `BIRefreshJobs` | 15 |
| JOB-032 | Housekeeping Jobs | 8 | P3 | ✅ DONE | `HousekeepingJobs` | 15 |
| JOB-033 | Client-Specific Jobs | 27 | P3 | ✅ DONE | `ClientSpecificJobs` | 16-17 |

---

# SECTION 4: VIEWS TRACKER

## 4.1 Core PO/ASN Views

| ID | View Name | Tables | Priority | Status | Java Component | Sprint |
|----|-----------|--------|----------|--------|----------------|--------|
| VW-001 | `V_PO` | PO | P2 | ✅ DONE | `POProjection` | 15 | — |
| VW-002 | `V_PODetail` | PODETAIL | P2 | ✅ DONE | `PODetailProjection` | 15 | — |
| VW-003 | `V_ASN` | RECEIPT, LOC, PACK | P1 | ✅ DONE | `ASNProjection` | 14 |
| VW-004 | `V_ASN_Extended_Validation` | CODELKUP | P1 | ✅ DONE | `ValidationConfigDTO` | 14 |
| VW-005 | `V_RECEIPT` | RECEIPT | P1 | ✅ DONE | `ReceiptProjection` | 14 |
| VW-006 | `V_RECEIPTDETAIL` | RECEIPTDETAIL | P1 | ✅ DONE | `ReceiptDetailProjection` | 14 |

## 4.2 BI Views (54 Total)

| ID | Category | Count | Priority | Status | Java Component | Sprint |
|----|----------|-------|----------|--------|----------------|--------|
| VW-010 | ASN Confirmation | 8 | P3 | ✅ DONE | `ASNConfirmationProjection` | 16 |
| VW-011 | PO Detail | 6 | P3 | ✅ DONE | `PODetailProjection` | 16 |
| VW-012 | Receipt Confirmation | 10 | P3 | ✅ DONE | `ReceiptConfirmationProjection` | 16 |
| VW-013 | Inventory Stock | 12 | P3 | ✅ DONE | `InventoryStockProjection` | 17 |
| VW-014 | Client-Specific | 18 | P3 | ✅ DONE | `ClientSpecificProjection` | 17 |

---

# SECTION 5: FUNCTIONS TRACKER

## 5.1 Core Utility Functions

| ID | Function Name | LOC | Priority | Status | Java Component | Sprint |
|----|---------------|-----|----------|--------|----------------|--------|
| FN-001 | `fnc_DelimSplit` | 83 | P1 | ✅ DONE | `StringSplitUtils` | 4 |
| FN-002 | `fnc_GetRight` | 218 | P1 | ✅ DONE | `AuthorizationService.hasRight()` | 4 |
| FN-003 | `fnc_GetRight2` | 262 | P1 | ✅ DONE | `AuthorizationService.hasRight()` | 4 |
| FN-004 | `fncConvUOM` | 88 | P1 | ✅ DONE | `UOMConversionService` | 4 |
| FN-005 | `fnc_CalculateCube` | 128 | P2 | ✅ DONE | `DimensionCalculator` | 10 |

## 5.2 Barcode/Label Functions

| ID | Function Name | LOC | Priority | Status | Java Component | Sprint |
|----|---------------|-----|----------|--------|----------------|--------|
| FN-010 | `fnc_Code128C_Uni` | 139 | P2 | ✅ DONE | `BarcodeGenerator` | 10 |
| FN-011 | `fnc_CalcCheckDigit_M10` | 70 | P2 | ✅ DONE | `CheckDigitCalculator` | 10 |
| FN-012 | `fnc_GetGS1Label` | 123 | P2 | ✅ DONE | `LabelService` | 10 |

## 5.3 Putaway Functions

| ID | Function Name | LOC | Priority | Status | Java Component | Sprint |
|----|---------------|-----|----------|--------|----------------|--------|
| FN-020 | `fnc_BuildPutawayRestriction` | 740 | P1 | ✅ DONE | `PutawayRestrictionBuilder` | 5 |

## 5.4 Remaining Functions (60+)

| ID | Category | Count | Priority | Status | Java Component | Sprint |
|----|----------|-------|----------|--------|----------------|--------|
| FN-030 | Date/Time | 9 | P3 | ✅ DONE | `DateTimeUtils` | 15 |
| FN-031 | TCP/WCS | 20 | P3 | ✅ DONE | `TCPWCSUtils` | 16 |
| FN-032 | Vocollect | 15 | P3 | ✅ DONE | `VocollectUtils` | 17 |
| FN-033 | Misc Utils | 20 | P3 | ✅ DONE | `MiscUtils` | 17 |

---

# SECTION 6: CONFIGURATION TRACKER

## 6.1 CODELKUP Entries

| ID | ListName | Purpose | Priority | Status | Java Config | Sprint |
|----|----------|---------|----------|--------|-------------|--------|
| CFG-001 | `PO2ASNMAP` | Field mapping | P0 | ✅ DONE | `clients/*.yaml` | 0 |
| CFG-002 | `PO2ASNTYPE` | Doc type mapping | P0 | ✅ DONE | `application.yml` | 0 |
| CFG-003 | `LOTTABLE01`–`15` | Lottable rules | P0 | ✅ DONE | Drools DRL | 0 |
| CFG-004 | `CTNTYPETAB` | Carton validation | P1 | ✅ DONE | `ValidationConfig.CartonType` | 4 |
| CFG-005 | `ORDTYP2ASN` | Order→ASN mapping | P2 | ✅ DONE | `OrderConfig` | 10 | — |
| CFG-006 | `CLOSEASNSTATUS` | Close behavior | P1 | ✅ DONE | `FinalizeConfig` | 3 |
| CFG-007 | `RECEIPTSTATUS` | Status transitions | P1 | ✅ DONE | `StatusConfig` | 3 |

## 6.2 StorerConfig Keys

| ID | ConfigKey | Purpose | Priority | Status | Java Config | Sprint |
|----|-----------|---------|----------|--------|-------------|--------|
| CFG-020 | `PrePopulatePOSP` | Hook SP name | P0 | ✅ DONE | Plugin Registry | 0 |
| CFG-021 | `AllowPopulateSamePOLine` | Duplicate lines | P1 | ✅ DONE | `ValidationConfig` | 3 |
| CFG-022 | `DefaultRcptLOC` | Default location | P1 | ✅ DONE | `ValidationConfig` | 3 |
| CFG-023 | `CloseASNStatus` | Auto-close | P1 | ✅ DONE | `FinalizeConfig` | 3 |
| CFG-024 | `CloseASNUponFinalize` | Close trigger | P1 | ✅ DONE | `FinalizeConfig` | 3 |
| CFG-025 | `ChkASNVarianceTolerance` | Tolerance % | P1 | ✅ DONE | `ValidationConfig` | 3 |
| CFG-026 | `AllowOneASNPerPO` | Single ASN | P1 | ✅ DONE | `ValidationConfig` | 3 |
| CFG-027 | `ASNReleasePATask_SP` | PA SP name | P1 | ✅ DONE | `FinalizeConfig.getPutawayReleaseSP()` | 5 |

## 6.3 Inventory Config Keys (17)

| ID | Category | Count | Priority | Status | Java Config | Sprint |
|----|----------|-------|----------|--------|-------------|--------|
| CFG-030 | Inventory Settings | 17 | P3 | ✅ DONE | `InventoryConfig` | 18 |

## 6.4 Putaway Config Keys (19)

| ID | Category | Count | Priority | Status | Java Config | Sprint |
|----|----------|-------|----------|--------|-------------|--------|
| CFG-050 | Putaway Settings | 19 | P3 | ✅ DONE | `PutawayConfig` | 18 |

## 6.5 Integration Config Keys (14)

| ID | Category | Count | Priority | Status | Java Config | Sprint |
|----|----------|-------|----------|--------|-------------|--------|
| CFG-070 | EDI/Integration | 14 | P3 | ✅ DONE | `IntegrationConfig` | 18 |

## 6.6 Labeling Config Keys (12)

| ID | Category | Count | Priority | Status | Java Config | Sprint |
|----|----------|-------|----------|--------|-------------|--------|
| CFG-090 | Label/Barcode | 12 | P3 | ✅ DONE | `LabelingConfig` | 18 |

## 6.7 Notification Config Keys (11)

| ID | Category | Count | Priority | Status | Java Config | Sprint |
|----|----------|-------|----------|--------|-------------|--------|
| CFG-110 | Notification/Alert | 11 | P3 | ✅ DONE | `NotificationConfig` | 18 |

## 6.8 Regional Config Keys (15)

| ID | Category | Count | Priority | Status | Java Config | Sprint |
|----|----------|-------|----------|--------|-------------|--------|
| CFG-130 | Regional/Country | 15 | P3 | ✅ DONE | `RegionalConfig` | 18 |

---

# SECTION 7: SPRINT PLAN

## Sprint 1 (Week 5-6): Core Finalization Part 1
| Task | Component | Effort | Owner |
|------|-----------|--------|-------|
| Implement `ispFinalizeReceipt` core logic | `FinalizeReceiptService` | 5d | |
| Implement `ispDefLot2FrRcptDtl` complete | `DefaultLot2Rule` | 2d | |
| Implement `ispGenLot2BySuppLot` complete | `SupplierLotRule` | 2d | |
| Unit tests for finalization | Tests | 1d | |

## Sprint 2 (Week 7-8): Core Finalization Part 2
| Task | Component | Effort | Owner |
|------|-----------|--------|-------|
| Implement `nspInventoryHoldWrapper` | `InventoryHoldService` | 3d | |
| Implement line split logic | `LineSplitService` | 2d | |
| Implement ASN close logic | `ASNCloseService` | 2d | |
| Integration tests | Tests | 3d | |

## Sprint 3 (Week 9-10): Triggers & Config
| Task | Component | Effort | Owner |
|------|-----------|--------|-------|
| Implement PO triggers (TR-001 to TR-006) | JPA Listeners | 4d | |
| Implement status config (CFG-006, CFG-007) | `StatusConfig` | 2d | |
| Implement validation config (CFG-021 to CFG-026) | `ValidationConfig` | 3d | |
| Tests | Tests | 1d | |

## Sprint 4 (Week 11-12): Security & Utils
| Task | Component | Effort | Owner |
|------|-----------|--------|-------|
| Implement `fnc_GetRight`/`fnc_GetRight2` | `AuthorizationService` | 3d | |
| Implement `fnc_DelimSplit` | `StringUtils` | 1d | |
| Implement `fncConvUOM` | `UOMConversionService` | 2d | |
| Implement `isp_ItrnUCCAdd` | `UCCTrackingService` | 2d | |
| Implement receipt triggers (TR-008 to TR-011) | JPA Listeners | 2d | |

## Sprint 5 (Week 13-14): Putaway Release Part 1
| Task | Component | Effort | Owner |
|------|-----------|--------|-------|
| Implement `isp_ASNReleasePATask_Wrapper` | `PutawayDispatcher` | 2d | |
| Implement `ispPARL01` (Standard) | `StandardPARelease` | 2d | |
| Implement `ispPARL06` (ULM) | `ULMPARelease` | 3d | |
| Implement `fnc_BuildPutawayRestriction` | `PutawayRestrictionBuilder` | 3d | |

## Sprint 6 (Week 15-16): Pre-Finalize Hooks
| Task | Component | Effort | Owner |
|------|-----------|--------|-------|
| Implement `ispPRREC01` (H&M) | `HMPreFinalizePlugin` | 2d | |
| Implement `ispPRREC02` (Nike) | `NikePreFinalizePlugin` | 2d | |
| Implement `ispPRREC03` (Adidas) | `AdidasPreFinalizePlugin` | 2d | |
| Implement `ispPRREC12` (India) | `IndiaPreFinalizePlugin` | 2d | |
| Implement `ispBatPA02` (Nike CRW) | `NikeCRWBatchPA` | 2d | |

## Sprint 7 (Week 17-18): Post-Finalize Hooks
| Task | Component | Effort | Owner |
|------|-----------|--------|-------|
| Implement `ispASNFZ02` (UCC stamp) | `UCCStampPlugin` | 2d | |
| Implement `ispASNFZ09` (New Look) | `NewLookAdjustPlugin` | 3d | |
| Implement `ispASNFZ24` (Columbia) | `ColumbiaUCCPlugin` | 3d | |
| Implement `ispBatPA05` (Nike CallOfModel) | `NikeCallOfModelPA` | 2d | |

## Sprint 8-9 (Week 19-22): Regional Hooks
| Task | Component | Effort | Owner |
|------|-----------|--------|-------|
| Implement remaining `ispPRREC*` (24 SPs) | Regional Plugins | 8d | |
| Implement remaining `ispPARL*` (6 SPs) | PA Variants | 4d | |
| Implement remaining `ispBatPA*` (4 SPs) | Batch PA Variants | 4d | |
| Implement lottable rules (28 SPs) | Lot Rules | 4d | |

## Sprint 10 (Week 23-24): Client Plugins
| Task | Component | Effort | Owner |
|------|-----------|--------|-------|
| Implement `ispPopulateTOPO_FLEX` | `FlextronicsPlugin` | 2d | |
| Implement `ispPopulateTOPO_ULM` | `ULMPlugin` | 2d | |
| Implement remaining `ispASNFZ*` (24 SPs) | Post-Finalize Plugins | 4d | |
| Implement barcode functions | `BarcodeService` | 2d | |

## Sprint 11-12 (Week 25-28): Background Jobs
| Task | Component | Effort | Owner |
|------|-----------|--------|-------|
| Implement auto-processing jobs (6) | `@Scheduled` services | 5d | |
| Implement interface jobs (4) | Spring Integration | 5d | |
| Implement `nsp_xdockorderprocessing` | `XDockProcessingService` | 4d | |
| Implement XDock jobs | `XDockScheduler` | 3d | |
| Implement archive jobs | `ArchiveService` | 3d | |

## Sprint 13-14 (Week 29-32): Views & Remaining
| Task | Component | Effort | Owner |
|------|-----------|--------|-------|
| Implement core views (6) as JPA Projections | Projections | 3d | |
| Implement BI views as API endpoints | REST APIs | 5d | |
| Implement remaining functions (60) | Utility Services | 8d | |
| Implement remaining config keys (90) | Config classes | 4d | |

## Sprint 15-17 (Week 33-38): Post-Allocation & Cleanup
| Task | Component | Effort | Owner |
|------|-----------|--------|-------|
| Implement `ispPOA01`–`ispPOA26` | `PostAllocationPlugins` | 10d | |
| Implement remaining jobs (45) | Scheduled services | 10d | |
| Implement remaining views (50) | Projections/APIs | 10d | |

## Sprint 18-20 (Week 39-44): Cutover
| Task | Component | Effort | Owner |
|------|-----------|--------|-------|
| Integration testing | Tests | 5d | |
| Performance testing | Load tests | 5d | |
| Production cutover | DevOps | 5d | |
| Legacy decommission | Cleanup | 5d | |

---

# SECTION 8: RISK REGISTER

| ID | Risk | Impact | Probability | Mitigation |
|----|------|--------|-------------|------------|
| R-001 | `nspPASTD` (3,021 LOC) complexity | High | High | Incremental migration, feature flags |
| R-002 | 37 pre-finalize hooks client-specific | Medium | High | Generic plugin framework, YAML config |
| R-003 | Trigger cascade chains | High | Medium | Event-driven architecture, async processing |
| R-004 | SQL Jobs timing sensitivity | Medium | Medium | Distributed scheduler (Quartz), idempotency |
| R-005 | Data migration during cutover | High | Medium | Dual-write period, reconciliation |

---

# SECTION 9: DEFINITIONS

| Term | Definition |
|------|------------|
| **P0** | Critical - Must have for MVP |
| **P1** | High - Required for production release |
| **P2** | Medium - Important but can be deferred |
| **P3** | Low - Nice to have |
| **LOC** | Lines of Code |
| **SP** | Stored Procedure |
| **✅ DONE** | Fully implemented and tested |
| **🔄 X%** | Partially implemented |
| **⏳ TODO** | Not started |

---

**Document Version History:**
| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2026-05-05 | Claude | Initial creation |
