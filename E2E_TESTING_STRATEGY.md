# PO Modernization - E2E Testing Strategy & Migration Validation

## Table of Contents
1. [Executive Summary](#1-executive-summary)
2. [Entry Points & Flow Coverage](#2-entry-points--flow-coverage)
3. [Test Case Matrix by Module](#3-test-case-matrix-by-module)
4. [SP → Microservice Migration Mapping](#4-sp--microservice-migration-mapping)
5. [Compensation Flow Testing](#5-compensation-flow-testing)
6. [Error & Edge Case Coverage](#6-error--edge-case-coverage)
7. [Test Data Preparation](#7-test-data-preparation)
8. [Database Setup](#8-database-setup)
9. [Automation Tool Recommendations](#9-automation-tool-recommendations)
10. [Test Execution Tracker](#10-test-execution-tracker)
11. [Validation Checklist](#11-validation-checklist)

---

## 1. Executive Summary

### Objective
Ensure 100% migration coverage from legacy SQL Server stored procedures to modern microservice-based architecture using Temporal orchestration and saga pattern.

### Scope
- **5 Entry Points**: API, EDI, Trigger, Job, RDT
- **150+ Error Codes**: Mapped from legacy SP RAISERROR codes (68600-69999)
- **39+ Stored Procedures**: Core inbound/finalization flow
- **458+ Triggers**: Event-driven logic
- **61+ Jobs**: Scheduled batch processing
- **21 Plugins**: Client-specific customizations

### Testing Layers
```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: Unit Tests (JUnit/Mockito)                        │
│  - Individual service methods                                │
│  - Activity implementations                                  │
│  - Domain services                                           │
├─────────────────────────────────────────────────────────────┤
│  Layer 2: Integration Tests (Spring Test)                   │
│  - Service-to-database interactions                          │
│  - Workflow orchestration                                    │
│  - Kafka event handling                                      │
├─────────────────────────────────────────────────────────────┤
│  Layer 3: E2E Tests (Karate/REST Assured)                   │
│  - Full API flows                                            │
│  - Entry point validation                                    │
│  - Compensation scenarios                                    │
├─────────────────────────────────────────────────────────────┤
│  Layer 4: Regression Tests (Parallel Legacy vs Modern)      │
│  - Response comparison                                       │
│  - Data integrity validation                                 │
│  - Performance benchmarking                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Entry Points & Flow Coverage

### 2.1 API Entry Point
| Flow ID | Flow Name | Legacy SP | Modern Service | Test Priority |
|---------|-----------|-----------|----------------|---------------|
| API-001 | Create PO | `nspg_AddPO` | `POService.createPO()` | P1 |
| API-002 | Update PO | `nspg_UpdatePO` | `POService.updatePO()` | P1 |
| API-003 | Delete PO | `nspg_DeletePO` | `POService.deletePO()` | P2 |
| API-004 | Get PO | `nspg_GetPO` | `POService.getPO()` | P1 |
| API-005 | Populate PO→ASN | `WM.lsp_ASN_PopulatePOs_Wrapper` | `PopulationService.populatePO()` | P1 |
| API-006 | Finalize Receipt | `WM.lsp_FinalizeReceipt_Wrapper` | `ReceiptFinalizationService.finalize()` | P1 |
| API-007 | Get Receipt | `nspg_GetReceipt` | `ReceiptService.getReceipt()` | P1 |
| API-008 | Update Receipt | `nspg_UpdateReceipt` | `ReceiptService.updateReceipt()` | P2 |
| API-009 | Trade Return | `WM.lsp_ASN_PopulateSOs_Wrapper` | `TradeReturnService.process()` | P2 |
| API-010 | XDock Allocation | `WM.lsp_XDockAllocation_Wrapper` | `XDockService.allocate()` | P2 |

### 2.2 EDI Entry Point
| Flow ID | Flow Name | Legacy SP | Modern Service | Test Priority |
|---------|-----------|-----------|----------------|---------------|
| EDI-001 | Inbound PO (850) | `nsp_GenericInbound_PO` | `EDIInboundService.processPO()` | P1 |
| EDI-002 | Inbound ASN (856) | `nsp_GenericInbound_ASN` | `EDIInboundService.processASN()` | P1 |
| EDI-003 | Outbound Receipt Confirm | `nsp_GenericOutbound_ReceiptConfirm` | `EDIOutboundService.sendReceipt()` | P2 |
| EDI-004 | Outbound Inventory | `nsp_GenericOutbound_Inventory` | `EDIOutboundService.sendInventory()` | P2 |

### 2.3 Trigger Entry Point (Event-Driven)
| Flow ID | Flow Name | Legacy Trigger | Modern Listener | Test Priority |
|---------|-----------|----------------|-----------------|---------------|
| TRG-001 | PO Header Insert | `tr_orders_insert` | `POHeaderEventListener` | P1 |
| TRG-002 | PO Header Update | `tr_orders_update` | `POHeaderEventListener` | P1 |
| TRG-003 | PO Detail Insert | `tr_orderdetail_insert` | `PODetailEventListener` | P1 |
| TRG-004 | PO Detail Update | `tr_orderdetail_update` | `PODetailEventListener` | P1 |
| TRG-005 | Receipt Insert | `tr_receipt_insert` | `ReceiptEventListener` | P1 |
| TRG-006 | Receipt Update | `tr_receipt_update` | `ReceiptEventListener` | P1 |
| TRG-007 | Receipt Detail Insert | `tr_receiptdetail_insert` | `ReceiptDetailEventListener` | P2 |
| TRG-008 | Receipt Detail Update | `tr_receiptdetail_update` | `ReceiptDetailEventListener` | P2 |
| TRG-009 | LOTxLOCxID Insert | `tr_lotxlocxid_insert` | `InventoryEventListener` | P1 |
| TRG-010 | LOTxLOCxID Update | `tr_lotxlocxid_update` | `InventoryEventListener` | P1 |

### 2.4 Job Entry Point (Scheduled)
| Flow ID | Flow Name | Legacy Job | Modern Scheduler | Test Priority |
|---------|-----------|------------|------------------|---------------|
| JOB-001 | Auto Populate PO→ASN | `WMS Auto Populate PO` | `AutoPopulateJob` | P1 |
| JOB-002 | Auto Finalize ASN | `WMS Auto Finalize ASN` | `AutoFinalizeJob` | P1 |
| JOB-003 | Auto PA Release | `WMS Auto PA Release` | `AutoPutawayReleaseJob` | P2 |
| JOB-004 | XDock Auto Allocate | `WMS XDock Auto Allocate` | `XDockAutoAllocateJob` | P2 |
| JOB-005 | Build Auto Allocation | `WMS Build Auto Allocation` | `BuildAutoAllocationJob` | P2 |
| JOB-006 | Inbound Master | `WMS Inbound Master` | `InboundMasterJob` | P2 |
| JOB-007 | Generic Inbound PO | `WMS Generic Inbound PO` | `GenericInboundPOJob` | P1 |
| JOB-008 | Generic Inbound ASN | `WMS Generic Inbound ASN` | `GenericInboundASNJob` | P1 |
| JOB-009 | Generic Outbound | `WMS Generic Outbound` | `GenericOutboundJob` | P2 |
| JOB-010 | Archive/Purge | `WMS Archive Tables` | `ArchiveJob` | P3 |

### 2.5 RDT Entry Point (Handheld Device)
| Flow ID | Flow Name | Legacy SP | Modern Service | Test Priority |
|---------|-----------|-----------|----------------|---------------|
| RDT-001 | Receive PO | `rdtfnc_ReceivePO` | `RDTReceiveService.receive()` | P1 |
| RDT-002 | Finalize Receipt | `rdtfnc_FinalizeReceipt` | `RDTFinalizeService.finalize()` | P1 |
| RDT-003 | Verify Receipt | `rdtfnc_VerifyReceipt` | `RDTVerifyService.verify()` | P2 |
| RDT-004 | Putaway Task | `rdtfnc_PutawayTask` | `RDTPutawayService.execute()` | P2 |
| RDT-005 | Receive LPN | `rdtfnc_ReceiveLPN` | `RDTReceiveLPNService.receive()` | P2 |

---

## 3. Test Case Matrix by Module

### 3.1 PO Management Module

| TC ID | Test Case | Type | Entry Point | Expected Result | Error Code |
|-------|-----------|------|-------------|-----------------|------------|
| PO-TC-001 | Create PO with valid data | Happy | API | PO created, status=0 | - |
| PO-TC-002 | Create PO missing required fields | Error | API | 400 Bad Request | VAL_001 |
| PO-TC-003 | Create PO duplicate PO key | Error | API | 409 Conflict | VAL_003 |
| PO-TC-004 | Update PO valid status transition | Happy | API | PO updated | - |
| PO-TC-005 | Update PO invalid status transition | Error | API | 400 Bad Request | PO_008 |
| PO-TC-006 | Update PO that doesn't exist | Error | API | 404 Not Found | PO_001 |
| PO-TC-007 | Delete PO with no receipts | Happy | API | PO deleted | - |
| PO-TC-008 | Delete PO with active receipts | Error | API | 409 Conflict | PO_002 |
| PO-TC-009 | Get PO by valid key | Happy | API | PO returned | - |
| PO-TC-010 | Get PO by invalid key | Error | API | 404 Not Found | PO_001 |
| PO-TC-011 | Create PO via EDI 850 | Happy | EDI | PO created | - |
| PO-TC-012 | Create PO via EDI with invalid SKU | Error | EDI | Parse error | INT_011 |
| PO-TC-013 | PO trigger on insert | Happy | Trigger | Event published | - |
| PO-TC-014 | PO trigger on update | Happy | Trigger | Event published | - |
| PO-TC-015 | Bulk PO import via job | Happy | Job | All POs created | - |

### 3.2 Population Module (PO → ASN/Receipt)

| TC ID | Test Case | Type | Entry Point | Expected Result | Error Code |
|-------|-----------|------|-------------|-----------------|------------|
| POP-TC-001 | Populate single PO | Happy | API | Receipt created | - |
| POP-TC-002 | Populate multiple POs | Happy | API | Single receipt created | - |
| POP-TC-003 | Populate PO already populated | Error | API | 409 Conflict | PO_022 |
| POP-TC-004 | Populate PO not found | Error | API | 404 Not Found | PO_001 |
| POP-TC-005 | Populate closed PO | Error | API | 400 Bad Request | PO_002 |
| POP-TC-006 | Populate PO with line splits | Happy | API | Multiple details created | - |
| POP-TC-007 | Populate with lottable mapping | Happy | API | Lottables populated | - |
| POP-TC-008 | Populate with validation failure | Error | API | 400 Bad Request | VAL_020 |
| POP-TC-009 | Populate PO storer mismatch | Error | API | 400 Bad Request | PO_010 |
| POP-TC-010 | Populate PO facility mismatch | Error | API | 400 Bad Request | PO_011 |
| POP-TC-011 | Populate with pre-populate plugin | Happy | API | Plugin executed | - |
| POP-TC-012 | Populate with plugin failure | Error | API | 422 Unprocessable | PLG_040 |
| POP-TC-013 | Auto-populate via job | Happy | Job | Receipts created | - |
| POP-TC-014 | Populate via EDI 856 | Happy | EDI | Receipt created | - |
| POP-TC-015 | Populate via RDT | Happy | RDT | Receipt created | - |
| POP-TC-016 | Populate with XDock allocation | Happy | API | Allocation created | - |
| POP-TC-017 | Populate async workflow | Happy | API | Workflow started | - |
| POP-TC-018 | Populate workflow timeout | Error | API | 504 Timeout | INT_022 |
| POP-TC-019 | Populate workflow cancelled | Edge | API | Compensation executed | - |
| POP-TC-020 | Populate saga compensation | Edge | API | All steps rolled back | - |

### 3.3 Finalization Module

| TC ID | Test Case | Type | Entry Point | Expected Result | Error Code |
|-------|-----------|------|-------------|-----------------|------------|
| FIN-TC-001 | Finalize single receipt | Happy | API | Receipt finalized, status=9 | - |
| FIN-TC-002 | Finalize receipt batch | Happy | API | All receipts finalized | - |
| FIN-TC-003 | Finalize already finalized | Error | API | 409 Conflict | RCV_002 |
| FIN-TC-004 | Finalize receipt not found | Error | API | 404 Not Found | RCV_001 |
| FIN-TC-005 | Finalize invalid status | Error | API | 400 Bad Request | RCV_003 |
| FIN-TC-006 | Finalize with inventory posting | Happy | API | LOTxLOCxID updated | - |
| FIN-TC-007 | Finalize inventory post failure | Error | API | 422 Unprocessable | RCV_021 |
| FIN-TC-008 | Finalize with hold application | Happy | API | Holds applied | - |
| FIN-TC-009 | Finalize hold failure | Error | API | 422 Unprocessable | RCV_023 |
| FIN-TC-010 | Finalize with putaway release | Happy | API | Tasks created | - |
| FIN-TC-011 | Finalize putaway failure | Error | API | 422 Unprocessable | RCV_024 |
| FIN-TC-012 | Finalize with pre-finalize plugin | Happy | API | Plugin executed | - |
| FIN-TC-013 | Finalize pre-plugin failure | Error | API | 422 Unprocessable | PLG_010 |
| FIN-TC-014 | Finalize with post-finalize plugin | Happy | API | Plugin executed | - |
| FIN-TC-015 | Finalize post-plugin failure | Edge | API | Logged (best effort) | PLG_020 |
| FIN-TC-016 | Finalize Nike client workflow | Happy | API | Nike rules applied | - |
| FIN-TC-017 | Finalize H&M client workflow | Happy | API | H&M rules applied | - |
| FIN-TC-018 | Finalize Adidas client workflow | Happy | API | Adidas rules applied | - |
| FIN-TC-019 | Finalize auto via job | Happy | Job | Auto-finalized | - |
| FIN-TC-020 | Finalize via RDT | Happy | RDT | Receipt finalized | - |
| FIN-TC-021 | Finalize with PO quantity update | Happy | API | PO qty received updated | - |
| FIN-TC-022 | Finalize PO auto-close | Happy | API | PO closed if fully received | - |
| FIN-TC-023 | Finalize saga compensation | Edge | API | All steps rolled back | - |
| FIN-TC-024 | Finalize workflow pause/resume | Edge | API | Workflow resumed | - |
| FIN-TC-025 | Finalize partial (by line) | Happy | API | Selected lines finalized | - |

### 3.4 Inventory Module

| TC ID | Test Case | Type | Entry Point | Expected Result | Error Code |
|-------|-----------|------|-------------|-----------------|------------|
| INV-TC-001 | Post inventory to LOTxLOCxID | Happy | Internal | Inventory posted | - |
| INV-TC-002 | Post inventory duplicate LP | Error | Internal | 409 Conflict | INV_021 |
| INV-TC-003 | Apply hold to inventory | Happy | Internal | Hold applied | - |
| INV-TC-004 | Release hold from inventory | Happy | Internal | Hold released | - |
| INV-TC-005 | Create reservation | Happy | Internal | Reservation created | - |
| INV-TC-006 | Release reservation | Happy | Internal | Reservation released | - |
| INV-TC-007 | Insufficient inventory | Error | Internal | 400 Bad Request | INV_002 |
| INV-TC-008 | Inventory on hold | Error | Internal | 400 Bad Request | INV_004 |
| INV-TC-009 | Invalid location | Error | Internal | 404 Not Found | INV_010 |
| INV-TC-010 | Location full | Error | Internal | 400 Bad Request | INV_011 |

### 3.5 Putaway Module

| TC ID | Test Case | Type | Entry Point | Expected Result | Error Code |
|-------|-----------|------|-------------|-----------------|------------|
| PA-TC-001 | Create putaway task | Happy | Internal | Task created | - |
| PA-TC-002 | Release putaway task | Happy | Internal | Task released | - |
| PA-TC-003 | Complete putaway task | Happy | RDT | Task completed | - |
| PA-TC-004 | Strategy not found | Error | Internal | 404 Not Found | PA_001 |
| PA-TC-005 | Location not found | Error | Internal | 404 Not Found | PA_002 |
| PA-TC-006 | Location full | Error | Internal | 400 Bad Request | PA_003 |
| PA-TC-007 | Batch putaway release | Happy | Job | Batch released | - |
| PA-TC-008 | Auto putaway release | Happy | Plugin | Auto released | - |

### 3.6 XDock Module

| TC ID | Test Case | Type | Entry Point | Expected Result | Error Code |
|-------|-----------|------|-------------|-----------------|------------|
| XD-TC-001 | XDock allocation | Happy | API | Allocated | - |
| XD-TC-002 | XDock insufficient inventory | Error | API | 400 Bad Request | XD_003 |
| XD-TC-003 | XDock SO creation | Happy | API | SO created | - |
| XD-TC-004 | Flow-thru allocation | Happy | API | Flow-thru executed | - |
| XD-TC-005 | Auto XDock allocate job | Happy | Job | Auto allocated | - |

### 3.7 Lottable Rules Module

| TC ID | Test Case | Type | Entry Point | Expected Result | Error Code |
|-------|-----------|------|-------------|-----------------|------------|
| LOT-TC-001 | Apply standard lottable rule | Happy | Internal | Lottables mapped | - |
| LOT-TC-002 | Generate Lot1 from receipt date | Happy | Internal | Lot1 generated | - |
| LOT-TC-003 | Generate Lot2 from supplier lot | Happy | Internal | Lot2 generated | - |
| LOT-TC-004 | Drools rule execution | Happy | Internal | Rule applied | - |
| LOT-TC-005 | Thailand lottable rule | Happy | Internal | TH rule applied | - |
| LOT-TC-006 | Taiwan lottable rule | Happy | Internal | TW rule applied | - |
| LOT-TC-007 | Rule execution failure | Error | Internal | 422 Unprocessable | LOT_002 |

### 3.8 Plugin Module

| TC ID | Test Case | Type | Entry Point | Expected Result | Error Code |
|-------|-----------|------|-------------|-----------------|------------|
| PLG-TC-001 | Execute pre-populate plugin | Happy | Internal | Plugin executed | - |
| PLG-TC-002 | Execute post-populate plugin | Happy | Internal | Plugin executed | - |
| PLG-TC-003 | Execute pre-finalize plugin | Happy | Internal | Plugin executed | - |
| PLG-TC-004 | Execute post-finalize plugin | Happy | Internal | Plugin executed | - |
| PLG-TC-005 | Plugin not found | Edge | Internal | Skip (logged) | - |
| PLG-TC-006 | Plugin execution failure | Error | Internal | 422 Unprocessable | PLG_002 |
| PLG-TC-007 | Nike plugin ispPRREC02 | Happy | Internal | Nike logic applied | - |
| PLG-TC-008 | H&M plugin ispPRREC01 | Happy | Internal | H&M logic applied | - |
| PLG-TC-009 | Columbia UCC plugin | Happy | Internal | UCC generated | - |
| PLG-TC-010 | Post-allocation plugin | Happy | Internal | Post-alloc executed | - |

---

## 4. SP → Microservice Migration Mapping

### 4.1 Core Population SPs

| SP Name | Legacy Code | Modern Service | Activity | Test Cases |
|---------|-------------|----------------|----------|------------|
| `WM.lsp_ASN_PopulatePOs_Wrapper` | 68800-68822 | `PopulationService` | `PopulatePOWorkflow` | POP-TC-001 to POP-TC-020 |
| `WM.lsp_ASN_PopulatePODs_Wrapper` | 68803-68815 | `PopulationService` | `MappingActivity` | POP-TC-006, POP-TC-007 |
| `ispPOLineSplit` | 68908 | `LineSplitService` | `MappingActivity` | POP-TC-006 |
| `ispLottableRule_Wrapper` | 69400-69430 | `LottableRulesService` | `MappingActivity` | LOT-TC-001 to LOT-TC-007 |
| `isp_ASN_ExtendedValidation` | 69120 | `ValidationActivityImpl` | `ValidationActivity` | POP-TC-008 |

### 4.2 Core Finalization SPs

| SP Name | Legacy Code | Modern Service | Activity | Test Cases |
|---------|-------------|----------------|----------|------------|
| `WM.lsp_FinalizeReceipt_Wrapper` | 68900-68928 | `ReceiptFinalizationService` | `FinalizeReceiptWorkflow` | FIN-TC-001 to FIN-TC-025 |
| `ispFinalizeReceipt` | 68900-68928 | `FinalizeReceiptService` | `ReceiptStatusActivity` | FIN-TC-001 to FIN-TC-005 |
| `nspInventoryPosting` | 68706, 68921 | `InventoryPostingService` | `InventoryPostingActivity` | FIN-TC-006, FIN-TC-007 |
| `nspInventoryHoldWrapper` | 68707-68709 | `InventoryHoldService` | `InventoryHoldActivity` | FIN-TC-008, FIN-TC-009 |
| `WM.lsp_ASNReleasePATask_Wrapper` | 69205 | `PutawayTaskService` | `PutawayReleaseActivity` | FIN-TC-010, FIN-TC-011 |
| `nspPASTD` | 69200-69207 | `PutawayStrategyService` | `PutawayReleaseActivity` | PA-TC-001 to PA-TC-008 |

### 4.3 Pre-Finalize Plugins (ispPRREC*)

| SP Name | Client | Modern Plugin | Error Code | Test Cases |
|---------|--------|---------------|------------|------------|
| `ispPRREC01` | H&M | `HMPreFinalizePlugin` | 69511 | PLG-TC-008, FIN-TC-017 |
| `ispPRREC02` | Nike | `NikePreFinalizePlugin` | 69512 | PLG-TC-007, FIN-TC-016 |
| `ispPRREC03` | Adidas | `AdidasPreFinalizePlugin` | 69513 | FIN-TC-018 |
| `ispPRREC04` | Columbia | `ColumbiaPreFinalizePlugin` | 69514 | PLG-TC-009 |
| `ispPRREC05` | Unilever | `UnileverPreFinalizePlugin` | 69515 | - |
| `ispPRREC06` | NewLook | `NewLookPreFinalizePlugin` | 69516 | - |
| `ispPRREC12` | India | `IndiaPreFinalizePlugin` | 69517 | - |
| `ispPRREC13` | DSG Thailand | `DSGThailandPreFinalizePlugin` | 69518 | - |

### 4.4 Post-Finalize Plugins (ispASNFZ*)

| SP Name | Function | Modern Plugin | Error Code | Test Cases |
|---------|----------|---------------|------------|------------|
| `ispASNFZ01` | Standard Post | `StandardPostFinalizePlugin` | 69520 | FIN-TC-014 |
| `ispASNFZ03` | Batch PA Release | `BatchPutawayReleasePlugin` | 69521 | PA-TC-007 |
| `ispASNFZ05` | Auto Allocate | `AutoAllocatePlugin` | 69528 | XD-TC-005 |
| `ispASNFZ06` | UCC Stamp | `UCCStampPlugin` | 69522 | PLG-TC-009 |
| `ispASNFZ07` | Auto PA Release | `AutoPutawayReleasePlugin` | 69523 | PA-TC-008 |
| `ispASNFZ08` | Quality Check | `QualityCheckPlugin` | 69526 | - |
| `ispASNFZ24` | Columbia UCC | `ColumbiaUCCPlugin` | 69530 | PLG-TC-009 |

### 4.5 Job Mappings

| Legacy Job | Cron | Modern Scheduler | Error Code | Test Cases |
|------------|------|------------------|------------|------------|
| `WMS Auto Populate PO` | `*/15 * * * *` | `@Scheduled AutoPopulateJob` | 69610 | JOB-001 |
| `WMS Auto Finalize ASN` | `*/15 * * * *` | `@Scheduled AutoFinalizeJob` | 69611 | JOB-002 |
| `WMS Auto PA Release` | `*/30 * * * *` | `@Scheduled AutoPutawayReleaseJob` | 69612 | JOB-003 |
| `WMS Generic Inbound PO` | `*/5 * * * *` | `@Scheduled GenericInboundPOJob` | 69621 | JOB-007 |
| `WMS Generic Inbound ASN` | `*/5 * * * *` | `@Scheduled GenericInboundASNJob` | 69622 | JOB-008 |

### 4.6 Trigger Mappings

| Legacy Trigger | Event | Modern Listener | Error Code | Test Cases |
|----------------|-------|-----------------|------------|------------|
| `tr_orders_insert` | PO Insert | `@PostPersist POEntity` | 69710 | TRG-001 |
| `tr_orders_update` | PO Update | `@PostUpdate POEntity` | 69711 | TRG-002 |
| `tr_orderdetail_insert` | PO Detail Insert | `@PostPersist PODetailEntity` | 69713 | TRG-003 |
| `tr_receipt_insert` | Receipt Insert | `@PostPersist ReceiptEntity` | 69720 | TRG-005 |
| `tr_receipt_update` | Receipt Update | `@PostUpdate ReceiptEntity` | 69721 | TRG-006 |
| `tr_lotxlocxid_insert` | Inventory Insert | `@PostPersist InventoryEntity` | - | TRG-009 |

---

## 5. Compensation Flow Testing

### 5.1 Saga Compensation Matrix

| Saga Step | Forward Action | Compensation Action | Test Cases |
|-----------|----------------|---------------------|------------|
| Step 1: Validation | Validate request | N/A (read-only) | POP-TC-020 |
| Step 2: Create Receipt Header | Insert RECEIPT | Delete RECEIPT | POP-TC-020 |
| Step 3: Create Receipt Details | Insert RECEIPTDETAIL | Delete RECEIPTDETAIL | POP-TC-020 |
| Step 4: Create Reservations | Insert RESERVATION | Release RESERVATION | POP-TC-020 |
| Step 5: Pre-Allocate Inventory | Update LOTxLOCxID | Release allocation | POP-TC-020 |
| Step 6: Sync to Legacy | Call legacy SP | Call rollback SP | POP-TC-020 |
| Step 7: Notify | Publish Kafka event | Publish cancellation event | POP-TC-020 |

### 5.2 Finalization Compensation Matrix

| Saga Step | Forward Action | Compensation Action | Test Cases |
|-----------|----------------|---------------------|------------|
| Step 1: Validate Receipt | Check receipt status | N/A | FIN-TC-023 |
| Step 2: Run Pre-Finalize Plugins | Execute plugins | N/A | FIN-TC-023 |
| Step 3: Update Receipt Status | Set status=9 | Set status=previous | FIN-TC-023 |
| Step 4: Post Inventory | Insert LOTxLOCxID | Delete LOTxLOCxID | FIN-TC-023 |
| Step 5: Apply Holds | Insert INVENTORYHOLD | Delete INVENTORYHOLD | FIN-TC-023 |
| Step 6: Update PO Quantities | Update PODETAIL.qtyreceived | Subtract qty | FIN-TC-023 |
| Step 7: Release Putaway Tasks | Insert TASK | Delete TASK | FIN-TC-023 |
| Step 8: Run Post-Finalize Plugins | Execute plugins | N/A (best effort) | FIN-TC-023 |
| Step 9: Close PO if fully received | Update PO status=9 | Revert PO status | FIN-TC-023 |

### 5.3 Compensation Test Scenarios

| TC ID | Scenario | Fail At Step | Expected Compensation | Priority |
|-------|----------|--------------|----------------------|----------|
| COMP-001 | Populate fails at reservation | Step 4 | Steps 3,2 rolled back | P1 |
| COMP-002 | Populate fails at legacy sync | Step 6 | Steps 5,4,3,2 rolled back | P1 |
| COMP-003 | Finalize fails at inventory posting | Step 4 | Step 3 rolled back | P1 |
| COMP-004 | Finalize fails at putaway release | Step 7 | Steps 6,5,4,3 rolled back | P1 |
| COMP-005 | Populate workflow timeout | Any | All completed steps rolled back | P1 |
| COMP-006 | Finalize workflow cancelled | Any | All completed steps rolled back | P1 |
| COMP-007 | Idempotency - retry after partial | Any | Skip completed, continue | P2 |
| COMP-008 | Concurrent populate same PO | Step 2 | Second request blocked | P2 |
| COMP-009 | Database deadlock during finalize | Any | Retry with backoff | P2 |
| COMP-010 | Kafka unavailable | Step 7 | Retry queue, eventual consistency | P2 |

---

## 6. Error & Edge Case Coverage

### 6.1 Error Code Coverage Matrix

| Error Range | Category | Count | Test Cases Required |
|-------------|----------|-------|---------------------|
| 68600-68699 | Task Processing | 17 | 17 |
| 68700-68799 | Inventory | 24 | 24 |
| 68800-68899 | PO/ASN | 23 | 23 |
| 68900-68999 | Receipt/Finalization | 20 | 20 |
| 69000-69099 | Integration | 15 | 15 |
| 69100-69199 | Validation | 15 | 15 |
| 69200-69299 | Putaway | 20 | 20 |
| 69300-69399 | XDock | 11 | 11 |
| 69400-69499 | Lottable | 16 | 16 |
| 69500-69599 | Plugin | 34 | 34 |
| 69600-69699 | Job | 21 | 21 |
| 69700-69799 | Trigger | 16 | 16 |
| 69800-69899 | View | 12 | 12 |
| 69900-69999 | Function/Utility | 16 | 16 |
| **TOTAL** | | **260** | **260** |

### 6.2 Edge Cases

| EC ID | Edge Case | Flow | Test Approach | Priority |
|-------|-----------|------|---------------|----------|
| EC-001 | Empty PO with no lines | Populate | Expect validation error | P2 |
| EC-002 | PO with 1000+ lines | Populate | Performance test, batch processing | P2 |
| EC-003 | Unicode characters in SKU | All | Ensure proper encoding | P2 |
| EC-004 | Null optional fields | All | Validate null handling | P2 |
| EC-005 | Boundary quantity values (0, MAX) | All | Boundary value testing | P2 |
| EC-006 | Date at boundaries (past, future) | All | Date validation | P2 |
| EC-007 | Concurrent same PO operations | All | Locking/deadlock handling | P1 |
| EC-008 | Network timeout during processing | All | Timeout/retry behavior | P1 |
| EC-009 | Database connection pool exhausted | All | Connection pool handling | P2 |
| EC-010 | Temporal worker crash | Workflow | Workflow recovery | P1 |
| EC-011 | Kafka broker unavailable | Events | Event retry/DLQ | P2 |
| EC-012 | Invalid client configuration | Plugin | Default fallback | P2 |
| EC-013 | Missing storer configuration | Variation | Error with helpful message | P2 |
| EC-014 | Circular plugin dependency | Plugin | Detect and break cycle | P3 |
| EC-015 | Very long field values | All | Truncation/validation | P2 |

### 6.3 Unhappy Path Scenarios

| UP ID | Scenario | Expected Behavior | Recovery Action | Priority |
|-------|----------|-------------------|-----------------|----------|
| UP-001 | All lines fail validation | Reject entire request | Return detailed errors | P1 |
| UP-002 | Partial line validation failure | Reject all (atomic) | Return which lines failed | P1 |
| UP-003 | Plugin throws unexpected exception | Catch, log, fail gracefully | Compensation triggered | P1 |
| UP-004 | Legacy system unavailable | Retry with backoff | Queue for later sync | P1 |
| UP-005 | Duplicate request (idempotency key) | Return existing result | No reprocessing | P1 |
| UP-006 | Request during maintenance | Return 503 Service Unavailable | Client retry | P2 |
| UP-007 | Invalid authentication | Return 401 Unauthorized | Client re-auth | P1 |
| UP-008 | Insufficient permissions | Return 403 Forbidden | Request access | P1 |
| UP-009 | Rate limit exceeded | Return 429 Too Many Requests | Client backoff | P2 |
| UP-010 | Payload too large | Return 413 Payload Too Large | Reduce batch size | P2 |

---

## 7. Test Data Preparation

### 7.1 Master Test Data Sets

#### 7.1.1 Storer Test Data
```sql
-- TD-STORER-001: Standard Storer
INSERT INTO STORER (storerkey, company, type, status) VALUES
('STORER001', 'Test Company A', 1, 'Active'),
('STORER002', 'Test Company B', 1, 'Active'),
('NIKE_STORER', 'Nike Inc', 1, 'Active'),
('HM_STORER', 'H&M Retail', 1, 'Active'),
('ADIDAS_STORER', 'Adidas AG', 1, 'Active');

-- TD-STORER-002: Storer Configuration
INSERT INTO CODELKUP (listname, code, value1, value2) VALUES
('CloseASNUponFinalize', 'STORER001', 'Y', ''),
('ChkASNVarianceTolerance', 'STORER001', '5', ''),
('AutoPARelease', 'STORER001', 'Y', ''),
('XDockEnabled', 'STORER001', 'Y', '');
```

#### 7.1.2 SKU Test Data
```sql
-- TD-SKU-001: Standard SKUs
INSERT INTO SKU (storerkey, sku, descr, packkey, unitprice, stdcube, stdgrosswgt) VALUES
('STORER001', 'SKU001', 'Test Product A', 'PACK001', 100.00, 1.5, 2.0),
('STORER001', 'SKU002', 'Test Product B', 'PACK001', 150.00, 2.0, 3.0),
('STORER001', 'SKU003', 'Test Product C', 'PACK002', 200.00, 1.0, 1.5),
('NIKE_STORER', 'NIKE-SKU-001', 'Nike Air Max', 'PACK001', 180.00, 0.5, 0.8);

-- TD-SKU-002: Pack Definitions
INSERT INTO PACK (packkey, descr, packuom1, casecnt) VALUES
('PACK001', 'Standard Pack', 'EA', 12),
('PACK002', 'Bulk Pack', 'EA', 48);
```

#### 7.1.3 Location Test Data
```sql
-- TD-LOC-001: Locations
INSERT INTO LOC (loc, loctype, locationflag, storerkey) VALUES
('RECV-001', 'RECV', 'A', 'STORER001'),
('RECV-002', 'RECV', 'A', 'STORER001'),
('STAGE-001', 'STAGE', 'A', 'STORER001'),
('STORE-A01', 'STORE', 'A', 'STORER001'),
('STORE-A02', 'STORE', 'A', 'STORER001'),
('XDOCK-001', 'XDOCK', 'A', 'STORER001');
```

#### 7.1.4 PO Test Data Sets

```sql
-- TD-PO-001: Standard PO for Populate
INSERT INTO ORDERS (pokey, storerkey, externpokey, orderdate, expecteddate, status) VALUES
('PO-TEST-001', 'STORER001', 'EXT-PO-001', GETDATE(), DATEADD(DAY, 7, GETDATE()), '0');

INSERT INTO ORDERDETAIL (pokey, polinenumber, storerkey, sku, qtyordered, unitprice, status) VALUES
('PO-TEST-001', '00001', 'STORER001', 'SKU001', 100, 100.00, '0'),
('PO-TEST-001', '00002', 'STORER001', 'SKU002', 200, 150.00, '0'),
('PO-TEST-001', '00003', 'STORER001', 'SKU003', 50, 200.00, '0');

-- TD-PO-002: PO for Multi-line Split Test
INSERT INTO ORDERS (pokey, storerkey, externpokey, status) VALUES
('PO-SPLIT-001', 'STORER001', 'EXT-SPLIT-001', '0');

INSERT INTO ORDERDETAIL (pokey, polinenumber, sku, qtyordered, lottable01, lottable02) VALUES
('PO-SPLIT-001', '00001', 'SKU001', 500, 'LOT-A', 'SUBLOT-1'),
('PO-SPLIT-001', '00001', 'SKU001', 300, 'LOT-A', 'SUBLOT-2'),
('PO-SPLIT-001', '00001', 'SKU001', 200, 'LOT-B', 'SUBLOT-1');

-- TD-PO-003: Nike Client PO
INSERT INTO ORDERS (pokey, storerkey, status) VALUES
('PO-NIKE-001', 'NIKE_STORER', '0');

INSERT INTO ORDERDETAIL (pokey, polinenumber, storerkey, sku, qtyordered) VALUES
('PO-NIKE-001', '00001', 'NIKE_STORER', 'NIKE-SKU-001', 500);

-- TD-PO-004: H&M Client PO
INSERT INTO ORDERS (pokey, storerkey, status) VALUES
('PO-HM-001', 'HM_STORER', '0');
```

#### 7.1.5 Receipt Test Data (Pre-created for Finalize Testing)

```sql
-- TD-RECEIPT-001: Receipt Ready for Finalize
INSERT INTO RECEIPT (receiptkey, pokey, storerkey, status, type) VALUES
('RCV-TEST-001', 'PO-TEST-001', 'STORER001', '5', 'Normal');

INSERT INTO RECEIPTDETAIL (receiptkey, receiptlinenumber, storerkey, sku, qtyexpected, qtyreceived, status) VALUES
('RCV-TEST-001', '00001', 'STORER001', 'SKU001', 100, 100, '5'),
('RCV-TEST-001', '00002', 'STORER001', 'SKU002', 200, 200, '5'),
('RCV-TEST-001', '00003', 'STORER001', 'SKU003', 50, 50, '5');

-- TD-RECEIPT-002: Receipt Already Finalized (for error case)
INSERT INTO RECEIPT (receiptkey, pokey, storerkey, status) VALUES
('RCV-FINALIZED', 'PO-TEST-001', 'STORER001', '9');

-- TD-RECEIPT-003: Receipt with Invalid Status
INSERT INTO RECEIPT (receiptkey, pokey, storerkey, status) VALUES
('RCV-INVALID', 'PO-TEST-001', 'STORER001', '0');
```

### 7.2 Test Data to Test Case Mapping

| Test Data ID | Test Data Description | Used By Test Cases |
|--------------|----------------------|-------------------|
| TD-STORER-001 | Standard Storers | All test cases |
| TD-STORER-002 | Storer Configuration | POP-TC-*, FIN-TC-* |
| TD-SKU-001 | Standard SKUs | All test cases |
| TD-SKU-002 | Pack Definitions | POP-TC-006, POP-TC-007 |
| TD-LOC-001 | Locations | FIN-TC-006, PA-TC-* |
| TD-PO-001 | Standard PO | POP-TC-001 to POP-TC-010 |
| TD-PO-002 | Multi-line Split PO | POP-TC-006 |
| TD-PO-003 | Nike Client PO | FIN-TC-016, PLG-TC-007 |
| TD-PO-004 | H&M Client PO | FIN-TC-017, PLG-TC-008 |
| TD-RECEIPT-001 | Receipt Ready for Finalize | FIN-TC-001 to FIN-TC-015 |
| TD-RECEIPT-002 | Already Finalized Receipt | FIN-TC-003 |
| TD-RECEIPT-003 | Invalid Status Receipt | FIN-TC-005 |

### 7.3 Dynamic Test Data Generation

```java
// Test Data Factory for dynamic generation
public class TestDataFactory {

    public static POCreateRequest createStandardPO() {
        return POCreateRequest.builder()
            .storerKey("STORER001")
            .facility("DC01")
            .supplierKey("SUPPLIER001")
            .lines(List.of(
                createPOLine("SKU001", 100),
                createPOLine("SKU002", 200)
            ))
            .build();
    }

    public static POCreateRequest createNikePO() {
        return POCreateRequest.builder()
            .storerKey("NIKE_STORER")
            .facility("DC01")
            .lines(List.of(createPOLine("NIKE-SKU-001", 500)))
            .build();
    }

    public static PopulateRequest createPopulateRequest(String... poKeys) {
        return PopulateRequest.builder()
            .poKeys(Arrays.asList(poKeys))
            .storerKey("STORER001")
            .facility("DC01")
            .userId("TEST_USER")
            .build();
    }

    public static FinalizeRequest createFinalizeRequest(String receiptKey) {
        return FinalizeRequest.builder()
            .receiptKey(receiptKey)
            .userId("TEST_USER")
            .finalizeAll(true)
            .build();
    }
}
```

---

## 8. Database Setup

### 8.1 Local Database Setup Script

```bash
#!/bin/bash
# setup-local-db.sh

# Start PostgreSQL in Docker (for local testing)
docker run -d \
  --name po-modernization-db \
  -e POSTGRES_USER=wms \
  -e POSTGRES_PASSWORD=wms123 \
  -e POSTGRES_DB=po_modernization \
  -p 5433:5432 \
  postgres:15

# Wait for DB to be ready
sleep 10

# Create schema
docker exec -i po-modernization-db psql -U wms -d po_modernization << 'EOF'
-- Create dbo schema (matches SQL Server convention)
CREATE SCHEMA IF NOT EXISTS dbo;
SET search_path TO dbo, public;

-- Core Tables
CREATE TABLE dbo.storer (
    storerkey VARCHAR(50) PRIMARY KEY,
    company VARCHAR(100),
    type INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'Active',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE dbo.sku (
    storerkey VARCHAR(50),
    sku VARCHAR(50),
    descr VARCHAR(200),
    packkey VARCHAR(50),
    unitprice DECIMAL(18,4),
    stdcube DECIMAL(18,4),
    stdgrosswgt DECIMAL(18,4),
    PRIMARY KEY (storerkey, sku)
);

CREATE TABLE dbo.pack (
    packkey VARCHAR(50) PRIMARY KEY,
    descr VARCHAR(100),
    packuom1 VARCHAR(10),
    casecnt INT DEFAULT 1
);

CREATE TABLE dbo.loc (
    loc VARCHAR(50) PRIMARY KEY,
    loctype VARCHAR(20),
    locationflag VARCHAR(5),
    storerkey VARCHAR(50)
);

CREATE TABLE dbo.orders (
    pokey VARCHAR(50) PRIMARY KEY,
    storerkey VARCHAR(50) NOT NULL,
    externpokey VARCHAR(50),
    orderdate TIMESTAMP,
    expecteddate TIMESTAMP,
    status VARCHAR(5) DEFAULT '0',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editwho VARCHAR(50)
);

CREATE TABLE dbo.orderdetail (
    pokey VARCHAR(50),
    polinenumber VARCHAR(10),
    storerkey VARCHAR(50),
    sku VARCHAR(50),
    qtyordered DECIMAL(18,4),
    qtyreceived DECIMAL(18,4) DEFAULT 0,
    unitprice DECIMAL(18,4),
    status VARCHAR(5) DEFAULT '0',
    lottable01 VARCHAR(50),
    lottable02 VARCHAR(50),
    lottable03 VARCHAR(50),
    lottable04 VARCHAR(50),
    lottable05 VARCHAR(50),
    PRIMARY KEY (pokey, polinenumber)
);

CREATE TABLE dbo.receipt (
    receiptkey VARCHAR(50) PRIMARY KEY,
    pokey VARCHAR(50),
    storerkey VARCHAR(50),
    externreceiptkey VARCHAR(50),
    type VARCHAR(20),
    status VARCHAR(5) DEFAULT '0',
    receiptdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editwho VARCHAR(50)
);

CREATE TABLE dbo.receiptdetail (
    receiptkey VARCHAR(50),
    receiptlinenumber VARCHAR(10),
    pokey VARCHAR(50),
    polinenumber VARCHAR(10),
    storerkey VARCHAR(50),
    sku VARCHAR(50),
    qtyexpected DECIMAL(18,4),
    qtyreceived DECIMAL(18,4) DEFAULT 0,
    status VARCHAR(5) DEFAULT '0',
    toloc VARCHAR(50),
    toid VARCHAR(50),
    lottable01 VARCHAR(50),
    lottable02 VARCHAR(50),
    lottable03 VARCHAR(50),
    lottable04 VARCHAR(50),
    lottable05 VARCHAR(50),
    PRIMARY KEY (receiptkey, receiptlinenumber)
);

CREATE TABLE dbo.lotxlocxid (
    lot VARCHAR(50),
    loc VARCHAR(50),
    id VARCHAR(50),
    storerkey VARCHAR(50),
    sku VARCHAR(50),
    qty DECIMAL(18,4),
    qtyallocated DECIMAL(18,4) DEFAULT 0,
    status VARCHAR(5) DEFAULT 'OK',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (lot, loc, id)
);

CREATE TABLE dbo.inventoryhold (
    holdkey VARCHAR(50) PRIMARY KEY,
    storerkey VARCHAR(50),
    sku VARCHAR(50),
    lot VARCHAR(50),
    loc VARCHAR(50),
    id VARCHAR(50),
    holdcode VARCHAR(20),
    status VARCHAR(5),
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE dbo.task (
    taskdetailkey VARCHAR(50) PRIMARY KEY,
    tasktype VARCHAR(20),
    status VARCHAR(5) DEFAULT '0',
    fromloc VARCHAR(50),
    toloc VARCHAR(50),
    sku VARCHAR(50),
    qty DECIMAL(18,4),
    assignedto VARCHAR(50),
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE dbo.codelkup (
    listname VARCHAR(50),
    code VARCHAR(50),
    value1 VARCHAR(100),
    value2 VARCHAR(100),
    value3 VARCHAR(100),
    PRIMARY KEY (listname, code)
);

CREATE TABLE dbo.ncounter (
    listname VARCHAR(50) PRIMARY KEY,
    startkey BIGINT DEFAULT 1,
    nextkey BIGINT DEFAULT 1
);

-- Indexes
CREATE INDEX idx_orders_storer ON dbo.orders(storerkey);
CREATE INDEX idx_receipt_storer ON dbo.receipt(storerkey);
CREATE INDEX idx_receipt_status ON dbo.receipt(status);
CREATE INDEX idx_lotxlocxid_sku ON dbo.lotxlocxid(storerkey, sku);

-- Initialize counters
INSERT INTO dbo.ncounter (listname, startkey, nextkey) VALUES
('POKEY', 1, 1000001),
('RECEIPTKEY', 1, 1000001),
('TASKKEY', 1, 1000001);

EOF

echo "Database setup complete!"
```

### 8.2 Flyway Migrations

```
po-api/src/main/resources/db/migration/
├── V1__create_core_tables.sql
├── V2__create_inventory_tables.sql
├── V3__create_task_tables.sql
├── V4__seed_storer_data.sql
├── V5__seed_sku_data.sql
├── V6__seed_codelkup_config.sql
└── V7__create_test_data.sql
```

### 8.3 Test Data Load Script

```bash
#!/bin/bash
# load-test-data.sh

# Load all test data sets
psql -h localhost -p 5433 -U wms -d po_modernization << 'EOF'
SET search_path TO dbo, public;

-- Clear existing test data
TRUNCATE TABLE dbo.receiptdetail CASCADE;
TRUNCATE TABLE dbo.receipt CASCADE;
TRUNCATE TABLE dbo.orderdetail CASCADE;
TRUNCATE TABLE dbo.orders CASCADE;

-- Load fresh test data
\i /path/to/test-data/TD-STORER-001.sql
\i /path/to/test-data/TD-SKU-001.sql
\i /path/to/test-data/TD-PO-001.sql
\i /path/to/test-data/TD-RECEIPT-001.sql

EOF

echo "Test data loaded!"
```

---

## 9. Automation Tool Recommendations

### 9.1 Tool Comparison

| Aspect | Karate | REST Assured | Cucumber | TestNG |
|--------|--------|--------------|----------|--------|
| **API Testing** | Excellent | Excellent | Good | Fair |
| **BDD Support** | Built-in | Via Cucumber | Native | Limited |
| **Parallel Execution** | Built-in | Yes | Yes | Yes |
| **Reporting** | Built-in HTML | Allure/ExtentReports | Allure | TestNG Reports |
| **Learning Curve** | Low | Medium | Medium | Low |
| **Data-Driven** | Built-in | Needs setup | Needs setup | Built-in |
| **DB Validation** | Via JS | Native Java | Native Java | Native Java |
| **Performance** | Built-in Gatling | JMeter integration | N/A | N/A |
| **Mocking** | Built-in | WireMock | WireMock | Mockito |

### 9.2 Recommended Stack

```
┌─────────────────────────────────────────────────────────────┐
│  Primary Testing Framework: Karate DSL                      │
│  - API Testing with BDD syntax                              │
│  - Built-in parallel execution                              │
│  - Data-driven testing                                      │
│  - Performance testing (Gatling integration)                │
├─────────────────────────────────────────────────────────────┤
│  Supplementary Tools:                                        │
│  - JUnit 5: Unit testing                                    │
│  - Mockito: Mocking                                         │
│  - Testcontainers: Database isolation                       │
│  - Allure: Enhanced reporting                               │
│  - WireMock: External service mocking                       │
├─────────────────────────────────────────────────────────────┤
│  CI/CD Integration:                                          │
│  - GitHub Actions / Jenkins                                 │
│  - Test parallelization                                     │
│  - Report publishing                                        │
│  - Slack/Teams notifications                                │
└─────────────────────────────────────────────────────────────┘
```

### 9.3 Karate Feature File Structure

```
po-test/src/test/java/com/wms/po/e2e/karate/
├── karate-config.js           # Global configuration
├── common/
│   ├── auth.feature           # Authentication helpers
│   ├── db-utils.feature       # Database utilities
│   └── test-data.feature      # Test data helpers
├── po/
│   ├── po-crud.feature        # PO CRUD operations
│   ├── po-validation.feature  # PO validation scenarios
│   └── po-edge-cases.feature  # PO edge cases
├── populate/
│   ├── populate-happy.feature # Happy path populate
│   ├── populate-error.feature # Error scenarios
│   └── populate-compensation.feature # Saga compensation
├── finalize/
│   ├── finalize-happy.feature # Happy path finalize
│   ├── finalize-error.feature # Error scenarios
│   └── finalize-compensation.feature # Saga compensation
├── clients/
│   ├── nike-workflow.feature  # Nike client tests
│   ├── hm-workflow.feature    # H&M client tests
│   └── adidas-workflow.feature # Adidas client tests
├── jobs/
│   ├── auto-populate.feature  # Auto populate job
│   ├── auto-finalize.feature  # Auto finalize job
│   └── generic-inbound.feature # Generic inbound jobs
└── regression/
    ├── legacy-comparison.feature # Legacy vs Modern comparison
    └── performance.feature       # Performance benchmarks
```

### 9.4 Sample Karate Feature

```gherkin
# populate-happy.feature
Feature: PO Population - Happy Path Scenarios

Background:
  * url baseUrl
  * def auth = call read('classpath:common/auth.feature')
  * header Authorization = 'Bearer ' + auth.token
  * def testData = call read('classpath:common/test-data.feature')

@POP-TC-001 @P1 @Happy
Scenario: Populate single PO successfully
  # Setup: Create test PO
  Given path '/api/v1/po'
  And request testData.standardPO
  When method POST
  Then status 201
  * def poKey = response.poKey

  # Populate
  Given path '/api/v1/populate'
  And request { poKeys: ['#(poKey)'], storerKey: 'STORER001', facility: 'DC01' }
  When method POST
  Then status 202
  * def workflowId = response.workflowId

  # Wait for completion
  * def result = call read('classpath:common/wait-workflow.feature') { workflowId: '#(workflowId)' }
  * match result.status == 'COMPLETED'

  # Verify receipt created
  Given path '/api/v1/receipt/' + result.receiptKey
  When method GET
  Then status 200
  And match response.status == '5'
  And match response.poKey == poKey

  # Verify DB state
  * def dbResult = db.readValue("SELECT COUNT(*) FROM dbo.receiptdetail WHERE receiptkey = '" + result.receiptKey + "'")
  * match dbResult == 3

@POP-TC-020 @P1 @Compensation
Scenario: Populate with saga compensation on failure
  # Setup: Create PO with invalid SKU for inventory failure
  Given path '/api/v1/po'
  And request testData.poWithInvalidSku
  When method POST
  Then status 201
  * def poKey = response.poKey

  # Populate - should fail at inventory step
  Given path '/api/v1/populate'
  And request { poKeys: ['#(poKey)'], storerKey: 'STORER001', facility: 'DC01' }
  When method POST
  Then status 202
  * def workflowId = response.workflowId

  # Wait for failure
  * def result = call read('classpath:common/wait-workflow.feature') { workflowId: '#(workflowId)' }
  * match result.status == 'FAILED'

  # Verify compensation executed - no receipt should exist
  * def receiptCount = db.readValue("SELECT COUNT(*) FROM dbo.receipt WHERE pokey = '" + poKey + "'")
  * match receiptCount == 0

  # Verify no orphan receipt details
  * def detailCount = db.readValue("SELECT COUNT(*) FROM dbo.receiptdetail rd JOIN dbo.receipt r ON rd.receiptkey = r.receiptkey WHERE r.pokey = '" + poKey + "'")
  * match detailCount == 0
```

### 9.5 Report Configuration

```java
// KarateTestRunner.java
@RunWith(Karate.class)
@KarateOptions(
    features = "classpath:com/wms/po/e2e/karate",
    tags = "~@ignore",
    outputCucumberJson = true
)
public class KarateTestRunner {

    @BeforeClass
    public static void beforeClass() {
        System.setProperty("karate.env", "local");
    }

    @AfterClass
    public static void generateReport() {
        Collection<File> jsonFiles = FileUtils.listFiles(
            new File("target/karate-reports"),
            new String[]{"json"},
            true
        );

        List<String> jsonPaths = jsonFiles.stream()
            .map(File::getAbsolutePath)
            .collect(Collectors.toList());

        Configuration config = new Configuration(
            new File("target"),
            "PO Modernization E2E Tests"
        );
        config.addClassifications("Environment", "Local");
        config.addClassifications("Branch", System.getenv("GIT_BRANCH"));

        ReportBuilder reportBuilder = new ReportBuilder(jsonPaths, config);
        reportBuilder.generateReports();
    }
}
```

---

## 10. Test Execution Tracker

### 10.1 Master Test Tracker Template

| TC ID | Test Case | Flow | Entry | Priority | Status | Last Run | Result | Notes |
|-------|-----------|------|-------|----------|--------|----------|--------|-------|
| PO-TC-001 | Create PO valid | PO | API | P1 | Ready | - | - | - |
| PO-TC-002 | Create PO missing fields | PO | API | P1 | Ready | - | - | - |
| ... | ... | ... | ... | ... | ... | ... | ... | ... |
| COMP-001 | Saga compensation populate | Populate | API | P1 | Ready | - | - | - |
| ... | ... | ... | ... | ... | ... | ... | ... | ... |

### 10.2 Execution Dashboard Metrics

```
┌─────────────────────────────────────────────────────────────┐
│  E2E Test Execution Summary                                 │
├─────────────────────────────────────────────────────────────┤
│  Total Test Cases:     260                                  │
│  ├── P1 (Critical):    120                                  │
│  ├── P2 (High):         90                                  │
│  └── P3 (Medium):       50                                  │
├─────────────────────────────────────────────────────────────┤
│  Execution Status:                                           │
│  ├── Passed:           ███████████████░░░░░  78%            │
│  ├── Failed:           ██░░░░░░░░░░░░░░░░░░  10%            │
│  ├── Blocked:          █░░░░░░░░░░░░░░░░░░░   5%            │
│  └── Not Run:          ██░░░░░░░░░░░░░░░░░░   7%            │
├─────────────────────────────────────────────────────────────┤
│  Coverage by Entry Point:                                    │
│  ├── API:              ██████████████████░░  90%            │
│  ├── EDI:              ████████████░░░░░░░░  60%            │
│  ├── Trigger:          ██████████████░░░░░░  70%            │
│  ├── Job:              ████████░░░░░░░░░░░░  40%            │
│  └── RDT:              ██████░░░░░░░░░░░░░░  30%            │
├─────────────────────────────────────────────────────────────┤
│  SP Migration Coverage:                                      │
│  ├── Population SPs:   ██████████████████░░  90%            │
│  ├── Finalization SPs: ████████████████████  100%           │
│  ├── Plugins:          ██████████████░░░░░░  70%            │
│  └── Jobs:             ████████████░░░░░░░░  60%            │
└─────────────────────────────────────────────────────────────┘
```

### 10.3 Migration Validation Checklist

```markdown
## SP Migration Validation Checklist

### Core Population Flow
- [ ] WM.lsp_ASN_PopulatePOs_Wrapper - All 15 test cases passed
- [ ] WM.lsp_ASN_PopulatePODs_Wrapper - All 10 test cases passed
- [ ] ispPOLineSplit - Line split scenarios verified
- [ ] ispLottableRule_Wrapper - All lottable rules tested
- [ ] isp_ASN_ExtendedValidation - Extended validation verified

### Core Finalization Flow
- [ ] WM.lsp_FinalizeReceipt_Wrapper - All 25 test cases passed
- [ ] ispFinalizeReceipt - Status transitions verified
- [ ] nspInventoryPosting - LOTxLOCxID updates verified
- [ ] nspInventoryHoldWrapper - Hold apply/release verified
- [ ] WM.lsp_ASNReleasePATask_Wrapper - Task creation verified

### Pre-Finalize Plugins
- [ ] ispPRREC01 (H&M) - H&M plugin logic verified
- [ ] ispPRREC02 (Nike) - Nike plugin logic verified
- [ ] ispPRREC03 (Adidas) - Adidas plugin logic verified
- [ ] ispPRREC04 (Columbia) - Columbia plugin logic verified
- [ ] ispPRREC05-13 (Regional) - Regional plugins verified

### Post-Finalize Plugins
- [ ] ispASNFZ01-09 - All post-finalize hooks verified
- [ ] ispASNFZ24 (Columbia UCC) - UCC generation verified

### Jobs
- [ ] Auto Populate Job - Batch populate verified
- [ ] Auto Finalize Job - Batch finalize verified
- [ ] Auto PA Release Job - Putaway release verified
- [ ] Generic Inbound Jobs - EDI processing verified

### Compensation/Saga
- [ ] All compensation scenarios verified
- [ ] Idempotency verified
- [ ] Concurrent access handled
- [ ] Timeout recovery verified

### Error Codes
- [ ] All 260 error codes tested
- [ ] HTTP status codes correct
- [ ] Error messages match legacy

### Performance
- [ ] Response time within SLA
- [ ] Throughput matches legacy
- [ ] No memory leaks
- [ ] Connection pool stable
```

---

## 11. Validation Checklist

### 11.1 Pre-Migration Validation

- [ ] All legacy SPs documented with behavior
- [ ] Error codes mapped to modern exceptions
- [ ] Test data extracted from production (anonymized)
- [ ] Performance baseline established
- [ ] Integration points identified

### 11.2 Migration Validation

- [ ] Unit tests pass (>80% coverage)
- [ ] Integration tests pass
- [ ] E2E tests pass
- [ ] Compensation flows verified
- [ ] Error handling matches legacy
- [ ] Performance meets or exceeds legacy

### 11.3 Post-Migration Validation

- [ ] Parallel run comparison (legacy vs modern)
- [ ] Data integrity verification
- [ ] Production smoke tests
- [ ] Performance monitoring established
- [ ] Rollback procedure tested

### 11.4 Sign-Off Criteria

| Criteria | Target | Actual | Status |
|----------|--------|--------|--------|
| Unit Test Coverage | >80% | - | Pending |
| Integration Test Pass Rate | 100% | - | Pending |
| E2E Test Pass Rate | 100% | - | Pending |
| Error Code Coverage | 100% | - | Pending |
| SP Migration Coverage | 100% | - | Pending |
| Performance (vs Legacy) | >=100% | - | Pending |
| Zero Data Loss | Yes | - | Pending |
| Compensation Verified | Yes | - | Pending |

---

## Appendix A: Quick Reference Commands

```bash
# Run all E2E tests
mvn test -pl po-test -Dkarate.env=local

# Run specific feature
mvn test -pl po-test -Dkarate.options="classpath:com/wms/po/e2e/karate/populate/populate-happy.feature"

# Run tests by tag
mvn test -pl po-test -Dkarate.options="--tags @P1"

# Run compensation tests only
mvn test -pl po-test -Dkarate.options="--tags @Compensation"

# Generate Allure report
mvn allure:report -pl po-test

# Run with parallel execution (5 threads)
mvn test -pl po-test -Dkarate.threads=5

# Run legacy comparison tests
mvn test -pl po-test -Dkarate.options="classpath:com/wms/po/e2e/karate/regression/legacy-comparison.feature"
```

---

## Appendix B: Contact & Support

| Role | Contact | Responsibility |
|------|---------|----------------|
| QA Lead | qa-lead@wms.com | Test strategy, sign-off |
| Dev Lead | dev-lead@wms.com | Technical guidance |
| DevOps | devops@wms.com | CI/CD, environments |
| DBA | dba@wms.com | Test data, DB setup |

---

*Document Version: 1.0*
*Last Updated: 2026-05-06*
*Author: Claude AI Assistant*
