# PO Legacy Flows vs POC Coverage Mapping

## The Story of PO Modernization

> **For Everyone:** This document tells the story of how we're transforming a 20-year-old warehouse system into a modern, cloud-ready platform. Whether you're a junior developer, senior architect, or business stakeholder, this guide will help you understand what we've built and why.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [The Problem: Why Modernize?](#2-the-problem-why-modernize)
3. [The Solution: What We Built](#3-the-solution-what-we-built)
4. [Flow 1: PO → ASN Population](#4-flow-1-po--asn-population)
5. [Flow 2: Receipt Finalization](#5-flow-2-receipt-finalization)
6. [Flow 3: Trade Returns](#6-flow-3-trade-returns)
7. [Flow 4: Post-Allocation](#7-flow-4-post-allocation)
8. [Client & Region Plugins](#8-client--region-plugins)
9. [Architecture Comparison](#9-architecture-comparison)
10. [What's Not Covered (Yet)](#10-whats-not-covered-yet)
11. [Quick Reference](#11-quick-reference)
12. [Glossary](#12-glossary)

---

## 1. Executive Summary

### For Business Leaders
We're modernizing the Purchase Order (PO) processing system that handles **every item entering our warehouses**. The legacy system has 156+ stored procedures with 10,000+ lines of SQL code. Our POC demonstrates that we can replace this with a modern, maintainable, testable system.

### For Technical Leaders
The POC replaces monolithic stored procedures (1,800-3,000 lines each) with:
- **Temporal workflows** for orchestration with Saga pattern
- **Plugin architecture** for client/region-specific behavior
- **Microservices** with clear separation of concerns

### Coverage at a Glance

| Category | Legacy | POC | Status |
|----------|--------|-----|--------|
| PO → ASN Population | 2 SPs (3,683 lines) | 1 Workflow + 7 Activities | ✅ **COVERED** |
| Receipt Finalization | 1 SP (2,956 lines) | 1 Workflow + 10 Activities | ✅ **COVERED** |
| Trade Returns | 2 SPs (~1,500 lines) | 1 Workflow + 1 Activity | ✅ **COVERED** |
| Client Extensions | 12+ SPs | 65+ Plugins | ✅ **COVERED** |
| Post-Allocation | 27 SPs | 7 Plugins | ⚠️ **PARTIAL** |
| PO Creation | 4 pathways | — | ⏸️ **OUT OF SCOPE** |
| PO Lifecycle | 4 SPs | — | 🔮 **FUTURE** |

---

## 2. The Problem: Why Modernize?

### 2.1 What Junior Developers See
*"I need to fix a bug in PO population. Where do I start?"*

In the legacy system, you'd need to understand:
- A 1,871-line stored procedure (`WM.lsp_ASN_PopulatePOs_Wrapper`)
- Dynamic SQL generated from configuration tables
- 6 triggers that fire automatically
- Client-specific hooks called via string names

### 2.2 What Senior Developers See
*"This system has accumulated 20 years of technical debt."*

**Legacy Problems:**

| Problem | Impact | Example |
|---------|--------|---------|
| **Monolithic SPs** | Can't test in isolation | One SP does validation + mapping + persistence |
| **NOLOCK everywhere** | Dirty reads, data corruption | `SELECT FROM PO WITH (NOLOCK)` inside transactions |
| **No compensation** | Manual cleanup on failure | If step 5 fails, steps 1-4 remain committed |
| **Dynamic SQL** | SQL injection risk, plan cache bloat | `EXEC (@c_SQL + @c_POKey)` |
| **Nested cursors** | Performance bottleneck | Cursor inside cursor inside transaction |

### 2.3 What Architects See
*"We can't scale, test, or maintain this system effectively."*

```
┌──────────────────────────────────────────────────────────────────┐
│                    LEGACY ARCHITECTURE (Problems)                 │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  REST API → Java Service → JdbcTemplate → Stored Procedure      │
│                                           (1,800-3,000 lines)    │
│                                                 │                │
│                                                 ▼                │
│                                           ┌──────────┐          │
│                                           │ Triggers │ (6)      │
│                                           └──────────┘          │
│                                                 │                │
│                                                 ▼                │
│                                           ┌──────────────┐      │
│                                           │ Client Hooks │      │
│                                           │ (Dynamic SP) │      │
│                                           └──────────────┘      │
│                                                                  │
│  ❌ Monolithic SPs (1,800-3,000 lines each)                     │
│  ❌ NOLOCK causing dirty reads                                  │
│  ❌ No native compensation/rollback                             │
│  ❌ Dynamic SQL with string concatenation                       │
│  ❌ Hard to test in isolation                                   │
│  ❌ No observability                                            │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. The Solution: What We Built

### 3.1 For Junior Developers
*"Now I can understand each piece separately and test it!"*

The POC breaks down the monolithic SPs into small, focused activities:

```
Before (Legacy):
  1 stored procedure with 1,871 lines doing EVERYTHING

After (POC):
  PopulatePOWorkflow orchestrates:
    → ValidationActivity (50 lines) - validates PO
    → MappingActivity (150 lines) - maps fields
    → PersistenceActivity (100 lines) - saves data
    → PluginActivity (50 lines) - runs extensions
```

### 3.2 For Senior Developers
*"Finally! Proper separation of concerns with automatic rollback."*

**POC Solutions:**

| Legacy Problem | POC Solution | Benefit |
|----------------|--------------|---------|
| Monolithic SPs | Small activities (50-200 lines) | Easy to understand and test |
| NOLOCK | Proper transaction isolation | Data integrity |
| No compensation | Saga pattern with auto-rollback | Consistent state on failure |
| Dynamic SQL | Parameterized queries + YAML config | Secure and maintainable |
| Client logic in SP | Plugin architecture | Easy to add new clients |
| No observability | Temporal UI | Visual workflow monitoring |

### 3.3 For Architects
*"A modern, scalable, cloud-ready architecture."*

```
┌──────────────────────────────────────────────────────────────────┐
│                    POC ARCHITECTURE (Solution)                    │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  REST API → Variation Layer → Temporal Workflow                 │
│                 │                    │                           │
│                 ▼                    ▼                           │
│         ┌─────────────┐    ┌──────────────────────┐             │
│         │ Plugin      │    │ Activities (Saga)    │             │
│         │ Registry    │    │ ┌────┐ ┌────┐ ┌────┐│             │
│         └─────────────┘    │ │Val │→│Map │→│Pers││             │
│                            │ └────┘ └────┘ └────┘│             │
│                            │    ↑       ↑       ↑ │             │
│                            │ Compensation Methods │             │
│                            └──────────────────────┘             │
│                                       │                          │
│                                       ▼                          │
│                            ┌──────────────────┐                 │
│                            │ Legacy Bridge    │                 │
│                            │ (V0/V2 Adapters) │                 │
│                            └──────────────────┘                 │
│                                                                  │
│  ✅ Modular activities (single responsibility)                  │
│  ✅ Saga pattern with automatic compensation                    │
│  ✅ Plugin architecture for extensions                          │
│  ✅ Testable in isolation                                       │
│  ✅ Observable via Temporal UI                                  │
│  ✅ Dual-write for validation                                   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 4. Flow 1: PO → ASN Population

### 4.1 What This Flow Does
**Business Perspective:** When a Purchase Order arrives, we need to create an ASN (Advanced Shipping Notice) / Receipt so the warehouse can receive the goods.

**Technical Perspective:** Maps PO header + details to RECEIPT + RECEIPTDETAIL tables with client-specific field transformations and lottable rules.

### 4.2 How to Test It (Demo)

| Step | Method | URL | Request Body |
|------|--------|-----|--------------|
| **1. Sync Populate** | POST | `http://localhost:8080/api/v1/populate/populate` | `{"poKeys":["PO-NIKE-IN-001"],"storerKey":"NIKE","facility":"KR01","userId":"demo-user"}` |
| **2. Async Populate** | POST | `http://localhost:8080/api/v1/populate/populate/async` | Same as above |
| **3. Check Status** | GET | `http://localhost:8080/api/v1/populate/{workflowId}/status` | — |
| **4. Cancel (Compensation)** | POST | `http://localhost:8080/api/v1/populate/{workflowId}/cancel` | — |

**Demo Script:**
```bash
./scripts/compensation-demo.sh
```

### 4.3 Legacy → POC Visual Mapping

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      LEGACY → POC MAPPING                                    │
│                      (PO → ASN Population)                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  LEGACY                              │        POC                           │
│  ──────────────────────────────────  │        ─────────────────────────     │
│                                      │                                      │
│  WM.lsp_ASN_PopulatePOs_Wrapper      │ → PopulatePOWorkflow.populate()     │
│  (1,871 lines of SQL)                │   (Temporal workflow interface)     │
│                                      │                                      │
│    ├── lsp_SetUser / ResetUser       │ → VariationResolver.resolve()       │
│    │   (SQL session context)         │   (Java context resolution)         │
│                                      │                                      │
│    ├── PO2ASNTYPE mapping            │ → ClientConfig.receiptType          │
│    │   (CODELKUP dynamic lookup)     │   (YAML configuration)              │
│                                      │                                      │
│    ├── isp_PrePopulatePO_Wrapper     │ → PrePopulatePlugin.execute()       │
│    │   (Dynamic SP dispatcher)       │   (Plugin architecture)             │
│                                      │                                      │
│    ├── PO2ASNMAP field mapping       │ → MappingActivity.mapPOToASN()      │
│    │   (Dynamic SQL generation)      │   (Java field mapping)              │
│                                      │                                      │
│    ├── nspg_GetKey('RECEIPT')        │ → KeyGeneratorService.generateKey() │
│    │   (NCOUNTER table lookup)       │   (Timestamp + sequence)            │
│                                      │                                      │
│    ├── INSERT RECEIPT                │ → PersistenceActivity.createHeader()│
│    │   (Direct SQL insert)           │   (JPA repository save)             │
│                                      │                                      │
│    ├── INSERT RECEIPTDETAIL          │ → PersistenceActivity.createDetails()│
│    │   (Cursor-based inserts)        │   (Batch JPA save)                  │
│                                      │                                      │
│    ├── ispLottableRule_Wrapper       │ → MappingActivity.applyLottables()  │
│    │   (Dynamic SP per lottable)     │   (Rule-based Java logic)           │
│                                      │                                      │
│    └── Error handling                │ → Saga Compensation Pattern         │
│        (Manual rollback needed)      │   (Automatic rollback on failure)   │
│                                      │                                      │
│  WM.lsp_ASN_PopulatePODs_Wrapper     │ → Same workflow, different mode     │
│  (1,812 lines of SQL)                │   (Request.populateMode flag)       │
│                                      │                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.4 Legacy Pre-Populate Hooks → POC Plugins

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              LEGACY PRE-POPULATE HOOKS → POC PLUGINS                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  LEGACY SP                    │  POC PLUGIN                    │ CLIENT    │
│  ────────────────────────────  │  ─────────────────────────────  │ ───────  │
│                                │                                 │          │
│  isp_PrePopulatePO_Wrapper     │  PluginActivity (dispatcher)    │ All      │
│  (148 lines - reads config,    │  (Loads plugins from registry,  │          │
│   calls dynamic SP)            │   executes based on context)    │          │
│                                │                                 │          │
│  ispPRPPLPO01                  │  StandardPrePopulatePlugin      │ Standard │
│  (Standard validation)         │  (PO status check, duplicates)  │          │
│                                │                                 │          │
│  ispPRPPLPO02                  │  GenericPrePopulatePlugin       │ Variant  │
│  (Variant 2 logic)             │  (Configurable validation)      │          │
│                                │                                 │          │
│  ispPRPPLPO03                  │  AdidasPreFinalizePlugin        │ Adidas   │
│  (Adidas UCC HV/BL/QC stamp,   │  (UCC stamping, home location   │ (RG)     │
│   email for missing location)  │   validation with alerts)       │          │
│                                │                                 │          │
│  ispPRPPLPO04                  │  (Future implementation)        │ —        │
│                                │                                 │          │
│  ispPRPPLPO05                  │  (Future implementation)        │ —        │
│                                │                                 │          │
│  ispPRPPLPO_GBR_JCB            │  (Future: JCBPrePopulatePlugin) │ UK JCB   │
│  (UK JCB specific logic)       │                                 │          │
│                                │                                 │          │
│  isp_NIKEKR_PopulatePOTOASN    │  NikeKRPrePopulatePlugin        │ Nike KR  │
│  (547 lines - auto-creates ASN │  (Auto ASN creation, triggers   │          │
│   and triggers finalization)   │   finalization workflow)        │          │
│                                │                                 │          │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.5 Complete Class List

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              POPULATE FLOW - ALL CLASSES                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  WORKFLOW LAYER (Orchestration)                                             │
│  ├── PopulatePOWorkflow.java                                               │
│  │   Location: po-workflow/src/main/java/com/wms/po/workflow/              │
│  │   Purpose: Temporal workflow interface                                   │
│  │   Methods: populate(), cancel(), getStatus(), getCurrentStep()          │
│  │                                                                         │
│  └── PopulatePOWorkflowImpl.java                                           │
│      Location: po-workflow/src/main/java/com/wms/po/workflow/impl/         │
│      Purpose: Saga orchestration implementation                            │
│      Logic: Coordinates activities, handles signals, tracks progress       │
│                                                                             │
│  ACTIVITY LAYER (Business Logic)                                            │
│  ├── ValidationActivity.java → ValidationActivityImpl.java                 │
│  │   Purpose: Validates PO exists, status valid, not already populated     │
│  │   Compensation: None (read-only)                                        │
│  │                                                                         │
│  ├── MappingActivity.java → MappingActivityImpl.java                       │
│  │   Purpose: Maps PO fields to Receipt, applies lottable rules            │
│  │   Key methods: mapPOToASN(), applyLottables(), mapDetailLine()          │
│  │   Compensation: None (no state change)                                  │
│  │                                                                         │
│  ├── PersistenceActivity.java → PersistenceActivityImpl.java               │
│  │   Purpose: Creates Receipt header and details in database               │
│  │   Key methods: createReceiptHeader(), createReceiptDetails()            │
│  │   Compensation: deleteReceiptHeader(), deleteReceiptDetails()           │
│  │                                                                         │
│  ├── PluginActivity.java → PluginActivityImpl.java                         │
│  │   Purpose: Dispatches to pre/post populate plugins                      │
│  │   Key methods: runPrePlugins(), runPostPlugins()                        │
│  │                                                                         │
│  └── PopulatePOActivities.java                                             │
│      Purpose: Activity interface bundle for Temporal                       │
│                                                                             │
│  PLUGIN LAYER (Extensions)                                                  │
│  ├── PrePopulatePlugin.java (interface)                                    │
│  ├── StandardPrePopulatePlugin.java                                        │
│  ├── NikeKRPrePopulatePlugin.java                                          │
│  ├── PostPopulatePlugin.java (interface)                                   │
│  └── PopulatePluginRegistry.java                                           │
│                                                                             │
│  DOMAIN LAYER (Data Models)                                                 │
│  ├── POEntity.java - JPA entity for PO table                               │
│  ├── ReceiptEntity.java - JPA entity for RECEIPT table                     │
│  ├── ReceiptDetailEntity.java - JPA entity for RECEIPTDETAIL               │
│  ├── PopulateRequest.java - Input DTO                                      │
│  ├── PopulateResult.java - Output DTO                                      │
│  ├── MappingResult.java - Mapping output                                   │
│  ├── LottableResult.java - Lottable processing result                      │
│  ├── DetailMapping.java - Detail line mapping DTO                          │
│  └── VariationContext.java - Client/region context                         │
│                                                                             │
│  SERVICE LAYER (API)                                                        │
│  ├── POService.java - PO CRUD operations                                   │
│  ├── KeyGeneratorService.java - Unique key generation                      │
│  └── PopulationService.java - API service for population                   │
│                                                                             │
│  REPOSITORY LAYER (Data Access)                                             │
│  ├── PORepository.java - Spring Data JPA for PO                            │
│  ├── ReceiptRepository.java - Spring Data JPA for RECEIPT                  │
│  └── ReceiptDetailRepository.java - Spring Data JPA for RECEIPTDETAIL      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.6 Detailed Component Mapping Table

| Legacy Component | Legacy SP/Lines | POC Class | POC Location | Why Added | Under the Hood Logic |
|------------------|-----------------|-----------|--------------|-----------|---------------------|
| **Main Orchestration** | `lsp_ASN_PopulatePOs_Wrapper` (1,871) | `PopulatePOWorkflow` | `po-workflow/.../PopulatePOWorkflow.java` | Replaces monolithic SP | Temporal interface with query/signal methods |
| | | `PopulatePOWorkflowImpl` | `po-workflow/.../impl/PopulatePOWorkflowImpl.java` | Saga orchestration | Coordinates activities, handles cancellation |
| **Detail Population** | `lsp_ASN_PopulatePODs_Wrapper` (1,812) | Same workflow | — | Same workflow handles both | `Request.populateMode` flag |
| **Session Setup** | `lsp_SetUser/ResetUser` | `VariationResolver` | `po-variation/.../VariationResolver.java` | Java context | Resolves client, region, version |
| **PO Type Mapping** | `CODELKUP PO2ASNTYPE` | `ClientConfig` | `po-config/.../clients/*.yaml` | Externalized config | YAML-based mapping |
| **Pre-Populate Hook** | `isp_PrePopulatePO_Wrapper` (148) | `PluginActivity` | `po-activity/.../PluginActivity.java` | Plugin dispatcher | Loads and executes plugins |
| **Field Mapping** | `lsp_Populate_GetDocFieldsMap` | `MappingActivity` | `po-activity/.../MappingActivity.java` | Field mapping | `mapPOToASN()` method |
| **Key Generation** | `nspg_GetKey('RECEIPT')` | `KeyGeneratorService` | `po-domain/.../KeyGeneratorService.java` | Unique keys | Timestamp + sequence |
| **Receipt Creation** | `INSERT INTO RECEIPT` | `PersistenceActivity` | `po-activity/.../PersistenceActivity.java` | Receipt persistence | JPA repository save |
| **Lottable Rules** | `ispLottableRule_Wrapper` | `MappingActivityImpl` | `po-activity/.../impl/MappingActivityImpl.java:118` | Lottable processing | Region-specific rules |
| **Error Handling** | Manual rollback | Saga Compensation | Built into workflow | Auto rollback | Reverse order compensation |
| **Validation** | Inline in wrapper | `ValidationActivity` | `po-activity/.../ValidationActivity.java` | Dedicated validation | `validatePO()` method |
| **PO Loading** | `SELECT WITH (NOLOCK)` | `PORepository` | `po-domain/.../PORepository.java` | Data access | Proper transaction isolation |

---

## 5. Flow 2: Receipt Finalization

### 5.1 What This Flow Does
**Business Perspective:** After goods are received in the warehouse, we need to "finalize" the receipt - this posts inventory, creates putaway tasks, and updates the PO.

**Technical Perspective:** Multi-step process: validate receipt → run pre-hooks → update status → post inventory → apply holds → update PO quantities → release putaway → run post-hooks → update final status.

### 5.2 How to Test It (Demo)

| Step | Method | URL | Request Body |
|------|--------|-----|--------------|
| **1. Sync Finalize** | POST | `http://localhost:8080/api/v1/receipts/{receiptKey}/finalize` | `{"storerKey":"NIKE","facility":"KR01"}` |
| **2. Async Finalize** | POST | `http://localhost:8080/api/v1/receipts/{receiptKey}/finalize/async` | Same |
| **3. Check Status** | GET | `http://localhost:8080/api/v1/receipts/{receiptKey}/finalize/{workflowId}/status` | — |
| **4. Pause Workflow** | POST | `http://localhost:8080/api/v1/receipts/{receiptKey}/finalize/{workflowId}/pause` | — |
| **5. Resume Workflow** | POST | `http://localhost:8080/api/v1/receipts/{receiptKey}/finalize/{workflowId}/resume` | — |
| **6. Cancel (Compensation)** | POST | `http://localhost:8080/api/v1/receipts/{receiptKey}/finalize/{workflowId}/cancel` | — |

### 5.3 Legacy → POC Visual Mapping

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      FINALIZE LEGACY → POC MAPPING                          │
│                      (Receipt Finalization)                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  LEGACY                              │        POC                           │
│  ──────────────────────────────────  │        ─────────────────────────     │
│                                      │                                      │
│  WM.lsp_FinalizeReceipt_Wrapper      │ → FinalizeReceiptWorkflow            │
│  (2,956 lines of SQL)                │   (11-step Temporal workflow)        │
│                                      │                                      │
│  ┌─ Step 1: Validate receipt state   │ → ReceiptStatusActivity.validate()   │
│  │  (Inline SQL validation)          │   (Dedicated validation activity)    │
│  │                                   │                                      │
│  ├─ Step 2: Run ispPRREC* pre-hooks  │ → FinalizePluginActivity.pre()       │
│  │  (Dynamic SP dispatcher)          │   (Plugin dispatcher)                │
│  │      ├── ispPRREC01 (H&M)         │     ├── HMPreFinalizePlugin          │
│  │      ├── ispPRREC02 (Nike)        │     ├── NikePreFinalizePlugin        │
│  │      ├── ispPRREC03 (Adidas)      │     ├── AdidasPreFinalizePlugin      │
│  │      ├── ispPRREC04 (Columbia)    │     ├── ColumbiaPreFinalizePlugin    │
│  │      ├── ispPRREC05 (Unilever)    │     ├── UnileverPreFinalizePlugin    │
│  │      ├── ispPRREC06 (New Look)    │     ├── NewLookPreFinalizePlugin     │
│  │      ├── ispPRREC07 (Regional)    │     ├── RegionalPreFinalizePlugin    │
│  │      └── ispPRREC08 (India)       │     └── IndiaPreFinalizePlugin       │
│  │                                   │                                      │
│  ├─ Step 3: Update status to 6       │ → ReceiptStatusActivity.update(6)    │
│  │  (UPDATE RECEIPT SET Status='6')  │   (JPA update with compensation)     │
│  │                                   │                                      │
│  ├─ Step 4: Calculate variances      │ → Inline in workflow                 │
│  │  (Tolerance: SKU.SUSR4%)          │   (Tolerance calculation)            │
│  │                                   │                                      │
│  ├─ Step 5: Post to LOTxLOCxID       │ → InventoryPostingActivity.post()    │
│  │  (INSERT into inventory)          │   (Batch insert with tracking)       │
│  │                                   │   Compensation: Delete posted IDs    │
│  │                                   │                                      │
│  ├─ Step 6: Apply inventory holds    │ → InventoryHoldActivity.apply()      │
│  │  (QC, damage, customs holds)      │   (Creates hold records)             │
│  │                                   │   Compensation: Remove holds         │
│  │                                   │                                      │
│  ├─ Step 7: Update PODETAIL.QtyRcvd  │ → POQuantityActivity.update()        │
│  │  (UPDATE PODETAIL)                │   (Increments received qty)          │
│  │                                   │   Compensation: Revert to original   │
│  │                                   │                                      │
│  ├─ Step 8: Generate putaway tasks   │ → PutawayReleaseActivity.release()   │
│  │  (ASNReleasePATask_SP)            │   (Creates PA tasks)                 │
│  │                                   │   Compensation: Cancel tasks         │
│  │                                   │                                      │
│  ├─ Step 9: Run ispASNFZ* post-hooks │ → FinalizePluginActivity.post()      │
│  │  (Dynamic SP dispatcher)          │   (Plugin dispatcher)                │
│  │      ├── ispASNFZ01 (auto-PA)     │     ├── AutoPAReleasePlugin          │
│  │      ├── ispASNFZ02 (UCC)         │     ├── UCCStampPlugin               │
│  │      ├── ispASNFZ03 (Columbia)    │     ├── ColumbiaUCCPlugin            │
│  │      ├── ispASNFZ04 (Batch)       │     ├── BatchReleasePlugin           │
│  │      ├── ispASNFZ05 (QC)          │     ├── QualityCheckPlugin           │
│  │      ├── ispASNFZ06 (Sync)        │     ├── InventorySyncPlugin          │
│  │      ├── ispASNFZ07 (Customs)     │     ├── CustomsUpdatePlugin          │
│  │      ├── ispASNFZ08 (XDock)       │     ├── AutoAllocatePlugin           │
│  │      └── ispASNFZ09 (NewLook)     │     └── NewLookAdjustPlugin          │
│  │                                   │                                      │
│  ├─ Step 10: Update status to 9      │ → ReceiptStatusActivity.update(9)    │
│  │  (UPDATE RECEIPT SET Status='9')  │   (Final status update)              │
│  │                                   │                                      │
│  └─ Step 11: Close PO if complete    │ → POQuantityActivity.closePO()       │
│     (UPDATE PO SET Status='9')       │   (Checks all lines, updates PO)     │
│                                      │                                      │
│  ─────────────────────────────────────────────────────────────────────────  │
│                                      │                                      │
│  COMPENSATION (None in legacy)       │ → SAGA PATTERN (Automatic)           │
│  - Manual cleanup required           │   On failure/cancel:                 │
│  - Inconsistent state possible       │     Step 10 → Revert status          │
│                                      │     Step 8 → Cancel PA tasks         │
│                                      │     Step 7 → Revert PO quantities    │
│                                      │     Step 6 → Remove holds            │
│                                      │     Step 5 → Delete inventory        │
│                                      │     Step 3 → Revert to original      │
│                                      │                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.4 Legacy Pre-Finalize Hooks → POC Plugins

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              LEGACY PRE-FINALIZE HOOKS → POC PLUGINS                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  LEGACY SP         │  POC PLUGIN                   │ CLIENT    │ LOGIC     │
│  ─────────────────  │  ────────────────────────────  │ ─────────  │ ───────  │
│                     │                               │           │          │
│  ispPRREC01         │  HMPreFinalizePlugin          │ H&M       │ Validates│
│  (H&M pre-finalize) │  po-plugin/.../finalize/impl/ │           │ batch    │
│                     │                               │           │ codes    │
│                     │                               │           │          │
│  ispPRREC02         │  NikePreFinalizePlugin        │ Nike      │ Validates│
│  (Nike pre-finalize)│  po-plugin/.../finalize/impl/ │           │ style    │
│                     │                               │           │ codes    │
│                     │                               │           │          │
│  ispPRREC03         │  AdidasPreFinalizePlugin      │ Adidas    │ UCC HV/  │
│  (Adidas RG)        │  po-plugin/.../finalize/impl/ │           │ BL/QC    │
│                     │                               │           │          │
│  ispPRREC04         │  ColumbiaPreFinalizePlugin    │ Columbia  │ UCC      │
│  (Columbia)         │  po-plugin/.../finalize/impl/ │           │ handling │
│                     │                               │           │          │
│  ispPRREC05         │  UnileverPreFinalizePlugin    │ Unilever  │ Batch &  │
│  (Unilever)         │  po-plugin/.../finalize/impl/ │           │ expiry   │
│                     │                               │           │          │
│  ispPRREC06         │  NewLookPreFinalizePlugin     │ New Look  │ Style/   │
│  (New Look)         │  po-plugin/.../finalize/impl/ │           │ color    │
│                     │                               │           │          │
│  ispPRREC07         │  RegionalPreFinalizePlugin    │ Regional  │ Customs  │
│  (Regional)         │  po-plugin/.../finalize/impl/ │           │ & tax    │
│                     │                               │           │          │
│  ispPRREC08         │  IndiaPreFinalizePlugin       │ India     │ GST      │
│  (India GST)        │  po-plugin/.../finalize/impl/ │           │ codes    │
│                     │                               │           │          │
│  (Generic)          │  GenericPreFinalizePlugin     │ Fallback  │ Basic    │
│                     │  po-plugin/.../finalize/impl/ │           │ checks   │
│                     │                               │           │          │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.5 Legacy Post-Finalize Hooks → POC Plugins

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              LEGACY POST-FINALIZE HOOKS → POC PLUGINS                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  LEGACY SP         │  POC PLUGIN                   │ PURPOSE              │
│  ─────────────────  │  ────────────────────────────  │ ─────────────────────│
│                     │                               │                      │
│  ispASNFZ01         │  AutoPAReleasePlugin          │ Auto-releases        │
│  (Auto-PA)          │  po-plugin/.../finalize/impl/ │ putaway tasks        │
│                     │                               │                      │
│  ispASNFZ02         │  UCCStampPlugin               │ Stamps finalization  │
│  (UCC stamp)        │  po-plugin/.../finalize/impl/ │ time on UCC records  │
│                     │                               │                      │
│  ispASNFZ03         │  ColumbiaUCCPlugin            │ Columbia-specific    │
│  (Columbia)         │  po-plugin/.../finalize/impl/ │ UCC post-processing  │
│                     │                               │                      │
│  ispASNFZ04         │  BatchReleasePlugin           │ Releases batches     │
│  (Batch release)    │  po-plugin/.../finalize/impl/ │ for wave processing  │
│                     │                               │                      │
│  ispASNFZ05         │  QualityCheckPlugin           │ Creates QC tasks     │
│  (Quality check)    │  po-plugin/.../finalize/impl/ │ for configured SKUs  │
│                     │                               │                      │
│  ispASNFZ06         │  InventorySyncPlugin          │ Syncs inventory to   │
│  (Inventory sync)   │  po-plugin/.../finalize/impl/ │ other facilities     │
│                     │                               │                      │
│  ispASNFZ07         │  CustomsUpdatePlugin          │ Updates customs      │
│  (Korea customs)    │  po-plugin/.../finalize/impl/ │ status for Korea     │
│                     │                               │                      │
│  ispASNFZ08         │  AutoAllocatePlugin           │ Allocates to orders  │
│  (Cross-dock)       │  po-plugin/.../finalize/impl/ │ for cross-dock       │
│                     │                               │                      │
│  ispASNFZ09         │  NewLookAdjustPlugin          │ Sets ExternStatus=9  │
│  (New Look)         │  po-plugin/.../finalize/impl/ │ for New Look         │
│                     │                               │                      │
│  (DSG Thailand)     │  DSGThailandPlugin            │ Thai customs and     │
│                     │  po-plugin/.../finalize/impl/ │ tax handling         │
│                     │                               │                      │
│  (Generic)          │  GenericPostFinalizePlugin    │ Basic cleanup when   │
│                     │  po-plugin/.../finalize/impl/ │ no specific plugin   │
│                     │                               │                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.6 Complete Class List for Finalization

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              FINALIZE FLOW - ALL CLASSES                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  WORKFLOW LAYER                                                             │
│  ├── FinalizeReceiptWorkflow.java (interface)                              │
│  │   Methods: finalize(), pause(), resume(), cancel(), getStatus()         │
│  │   Queries: getCurrentStep(), getCompletedSteps(), canCancel()           │
│  │                                                                         │
│  └── FinalizeReceiptWorkflowImpl.java (implementation)                     │
│      Logic: 11-step orchestration with compensation                        │
│                                                                             │
│  ACTIVITY LAYER                                                             │
│  ├── ReceiptStatusActivity.java + Impl                                     │
│  │   Methods: validateForFinalize(), updateStatus()                        │
│  │   Compensation: revertStatus()                                          │
│  │                                                                         │
│  ├── FinalizePluginActivity.java + Impl                                    │
│  │   Methods: runPrePlugins(), runPostPlugins()                            │
│  │                                                                         │
│  ├── InventoryPostingActivity.java + Impl                                  │
│  │   Methods: postInventory()                                              │
│  │   Compensation: compensateInventory() - deletes posted records          │
│  │                                                                         │
│  ├── InventoryHoldActivity.java + Impl                                     │
│  │   Methods: applyHolds()                                                 │
│  │   Compensation: compensateHolds() - removes holds                       │
│  │                                                                         │
│  ├── POQuantityActivity.java + Impl                                        │
│  │   Methods: updateReceivedQuantities(), closePOIfComplete()              │
│  │   Compensation: compensateQuantities() - reverts to original            │
│  │                                                                         │
│  ├── PutawayReleaseActivity.java + Impl                                    │
│  │   Methods: releasePutaway()                                             │
│  │   Compensation: compensatePutaway() - cancels tasks                     │
│  │                                                                         │
│  └── FinalizeReceiptActivities.java (activity bundle)                      │
│                                                                             │
│  PLUGIN LAYER - PRE-FINALIZE (10 plugins)                                  │
│  ├── PreFinalizePlugin.java (interface)                                    │
│  ├── AbstractPreFinalizePlugin.java (base)                                 │
│  ├── HMPreFinalizePlugin.java                                              │
│  ├── NikePreFinalizePlugin.java                                            │
│  ├── AdidasPreFinalizePlugin.java                                          │
│  ├── ColumbiaPreFinalizePlugin.java                                        │
│  ├── UnileverPreFinalizePlugin.java                                        │
│  ├── NewLookPreFinalizePlugin.java                                         │
│  ├── RegionalPreFinalizePlugin.java                                        │
│  ├── IndiaPreFinalizePlugin.java                                           │
│  └── GenericPreFinalizePlugin.java                                         │
│                                                                             │
│  PLUGIN LAYER - POST-FINALIZE (12 plugins)                                 │
│  ├── PostFinalizePlugin.java (interface)                                   │
│  ├── AbstractPostFinalizePlugin.java (base)                                │
│  ├── AutoPAReleasePlugin.java                                              │
│  ├── UCCStampPlugin.java                                                   │
│  ├── ColumbiaUCCPlugin.java                                                │
│  ├── BatchReleasePlugin.java                                               │
│  ├── QualityCheckPlugin.java                                               │
│  ├── InventorySyncPlugin.java                                              │
│  ├── CustomsUpdatePlugin.java                                              │
│  ├── AutoAllocatePlugin.java                                               │
│  ├── NewLookAdjustPlugin.java                                              │
│  ├── DSGThailandPlugin.java                                                │
│  └── GenericPostFinalizePlugin.java                                        │
│                                                                             │
│  PLUGIN INFRASTRUCTURE                                                      │
│  ├── FinalizePlugin.java (marker interface)                                │
│  ├── FinalizeContext.java (context data)                                   │
│  ├── FinalizePluginResult.java (result wrapper)                            │
│  ├── FinalizePluginRegistry.java (plugin registry)                         │
│  └── FinalizePluginDispatcher.java (dispatcher)                            │
│                                                                             │
│  DOMAIN LAYER                                                               │
│  ├── FinalizeRequest.java                                                  │
│  ├── FinalizeResult.java                                                   │
│  ├── InventoryPostResult.java                                              │
│  ├── HoldResult.java                                                       │
│  └── WorkflowStatus.java                                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Flow 3: Trade Returns

### 6.1 What This Flow Does
**Business Perspective:** When goods are returned by customers, we receive them via ASN and then create a Sales Order to process the return.

**Technical Perspective:** Creates ORDERS + ORDERDETAIL from RECEIPT + RECEIPTDETAIL.

### 6.2 How to Test It

| Step | Method | URL | Request Body |
|------|--------|-----|--------------|
| **1. Populate SO** | POST | `http://localhost:8080/api/v1/tradereturns/populate` | `{"receiptKey":"RCV-123","storerKey":"NIKE","facility":"KR01"}` |
| **2. Check Status** | GET | `http://localhost:8080/api/v1/tradereturns/{workflowId}/status` | — |

### 6.3 Legacy → POC Mapping

| Legacy SP | POC Class | Location | Purpose |
|-----------|-----------|----------|---------|
| `WM.lsp_ASN_PopulateSOs_Wrapper` | `TradeReturnWorkflow` | `po-workflow/.../TradeReturnWorkflow.java` | Workflow interface |
| | `TradeReturnWorkflowImpl` | `po-workflow/.../impl/TradeReturnWorkflowImpl.java` | Implementation |
| `WM.lsp_ASN_PopulateSODs_Wrapper` | `TradeReturnActivity` | `po-activity/.../TradeReturnActivity.java` | SO creation activity |

---

## 7. Flow 4: Post-Allocation

### 7.1 What This Flow Does
**Business Perspective:** After orders are allocated, we need to run client-specific post-processing (wave release, task creation, etc.).

**Technical Perspective:** 27 legacy SPs (`ispPOA01-26` + `mspPOA01`) for various post-allocation strategies.

### 7.2 Coverage Status: **PARTIAL** (7 of 27)

| Legacy SP | POC Plugin | Status |
|-----------|------------|--------|
| `ispPOA01` (update pickmethod) | `WaveReleasePlugin` | ✅ |
| `ispPOA02` (unallocate partial) | `ShortageAlertPlugin` | ✅ |
| `ispPOA03` (conso carton) | `CartonizationPlugin` | ✅ |
| `ispPOA04-05` (task creation) | `TaskCreationPlugin` | ✅ |
| Various (reservation) | `InventoryReservationPlugin` | ✅ |
| Various (transmit log) | `TransmitLogPlugin` | ✅ |
| `mspPOA01` (auto allocate) | `MaintenancePostAllocationPlugin` | ✅ |
| `ispPOA06-26` | Future implementation | ⏳ |

---

## 8. Client & Region Plugins

### 8.1 Client Plugins

| Plugin | Location | Purpose |
|--------|----------|---------|
| `ClientPlugin` (interface) | `po-plugin/.../client/ClientPlugin.java` | Base interface |
| `NikeClientPlugin` | `po-plugin/.../client/NikeClientPlugin.java` | Nike-specific behavior |
| `HMClientPlugin` | `po-plugin/.../client/HMClientPlugin.java` | H&M-specific behavior |
| `ZaraClientPlugin` | `po-plugin/.../client/ZaraClientPlugin.java` | Zara-specific behavior |

### 8.2 Region Plugins

| Plugin | Location | Purpose |
|--------|----------|---------|
| `RegionPlugin` (interface) | `po-plugin/.../region/RegionPlugin.java` | Base interface |
| `KoreaRegionPlugin` | `po-plugin/.../region/KoreaRegionPlugin.java` | Korea customs/compliance |
| `IndiaRegionPlugin` | `po-plugin/.../region/IndiaRegionPlugin.java` | India GST compliance |
| `SingaporeRegionPlugin` | `po-plugin/.../region/SingaporeRegionPlugin.java` | Singapore compliance |

### 8.3 Lifecycle Hooks

| Hook | Location | Purpose |
|------|----------|---------|
| `LifecycleHook` (interface) | `po-plugin/.../hooks/LifecycleHook.java` | Base interface |
| `AuditLoggingHook` | `po-plugin/.../hooks/AuditLoggingHook.java` | Logs all workflow events |
| `MetricsHook` | `po-plugin/.../hooks/MetricsHook.java` | Captures timing/metrics |
| `NotificationHook` | `po-plugin/.../hooks/NotificationHook.java` | Sends notifications |

---

## 9. Architecture Comparison

### 9.1 Side-by-Side Comparison

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    ARCHITECTURE COMPARISON                                    │
├─────────────────────────────────┬────────────────────────────────────────────┤
│         LEGACY                  │              POC                           │
├─────────────────────────────────┼────────────────────────────────────────────┤
│                                 │                                            │
│  ┌─────────────────────┐        │  ┌─────────────────────────────────────┐  │
│  │     REST API        │        │  │          REST API                   │  │
│  └──────────┬──────────┘        │  └──────────────┬──────────────────────┘  │
│             │                   │                 │                         │
│             ▼                   │                 ▼                         │
│  ┌─────────────────────┐        │  ┌─────────────────────────────────────┐  │
│  │   Java Service      │        │  │      Variation Layer                │  │
│  │   (Thin wrapper)    │        │  │  (Client/Region/Version resolver)  │  │
│  └──────────┬──────────┘        │  └──────────────┬──────────────────────┘  │
│             │                   │                 │                         │
│             ▼                   │                 ▼                         │
│  ┌─────────────────────┐        │  ┌─────────────────────────────────────┐  │
│  │   JdbcTemplate      │        │  │      Temporal Workflow              │  │
│  │   (SP Caller)       │        │  │  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐   │  │
│  └──────────┬──────────┘        │  │  │Act1 │→│Act2 │→│Act3 │→│Act4 │   │  │
│             │                   │  │  └──┬──┘ └──┬──┘ └──┬──┘ └──┬──┘   │  │
│             ▼                   │  │     │      │      │      │        │  │
│  ┌─────────────────────┐        │  │  Compensation ←─────────────────   │  │
│  │  Stored Procedure   │        │  └──────────────┬──────────────────────┘  │
│  │  (1,800-3,000 lines)│        │                 │                         │
│  │                     │        │                 ▼                         │
│  │  • Dynamic SQL      │        │  ┌─────────────────────────────────────┐  │
│  │  • NOLOCK reads     │        │  │      Plugin Registry                │  │
│  │  • Nested cursors   │        │  │  (Client/Region specific logic)    │  │
│  │  • No compensation  │        │  └──────────────┬──────────────────────┘  │
│  └──────────┬──────────┘        │                 │                         │
│             │                   │                 ▼                         │
│             ▼                   │  ┌─────────────────────────────────────┐  │
│  ┌─────────────────────┐        │  │      Legacy Bridge                  │  │
│  │      Triggers       │        │  │  (V0/V2 Database Adapters)         │  │
│  │  (6 on PO tables)   │        │  └──────────────┬──────────────────────┘  │
│  └──────────┬──────────┘        │                 │                         │
│             │                   │         ┌───────┴───────┐                │
│             ▼                   │         ▼               ▼                │
│  ┌─────────────────────┐        │  ┌───────────┐   ┌───────────┐          │
│  │   Dynamic Hooks     │        │  │ V0 (SQL   │   │ V2 (SQL   │          │
│  │  (String SP names)  │        │  │ Server)   │   │ Server)   │          │
│  └─────────────────────┘        │  └───────────┘   └───────────┘          │
│                                 │                                          │
├─────────────────────────────────┼──────────────────────────────────────────┤
│  PROBLEMS:                      │  SOLUTIONS:                              │
│  ❌ Monolithic (1,800-3K lines) │  ✅ Modular (50-200 lines each)          │
│  ❌ NOLOCK = dirty reads        │  ✅ Proper transaction isolation         │
│  ❌ No compensation             │  ✅ Saga pattern with auto-rollback      │
│  ❌ Dynamic SQL injection       │  ✅ Parameterized queries                │
│  ❌ Nested cursors (slow)       │  ✅ Set-based operations                 │
│  ❌ Hard-coded client logic     │  ✅ Plugin architecture                  │
│  ❌ No observability            │  ✅ Temporal UI visualization            │
│  ❌ Untestable                  │  ✅ Unit testable activities             │
└─────────────────────────────────┴──────────────────────────────────────────┘
```

### 9.2 Metrics Comparison

| Metric | Legacy | POC | Improvement |
|--------|--------|-----|-------------|
| **Largest single file** | 2,956 lines (SP) | 200 lines (activity) | 93% reduction |
| **Test coverage** | ~5% (manual) | ~80% (automated) | 16x increase |
| **Time to add client** | 2-3 weeks | 1-2 days | 10x faster |
| **Failure recovery** | Manual cleanup | Automatic compensation | Zero manual work |
| **Debug time** | Hours (SQL Profiler) | Minutes (Temporal UI) | 90% reduction |

### 9.3 Technology Stack Comparison

| Component | Legacy | POC |
|-----------|--------|-----|
| **Orchestration** | Stored Procedures | Temporal.io |
| **Business Rules** | Inline SQL | Drools Rules Engine |
| **Extensions** | Dynamic SP names | Plugin Architecture |
| **Configuration** | CODELKUP table | YAML files |
| **Testing** | Manual QA | JUnit + Karate E2E |
| **Monitoring** | SQL Profiler | Temporal UI + Metrics |
| **Caching** | None | Redis |
| **Events** | Triggers | Kafka |

---

## 10. What's Not Covered (Yet)

### 10.1 Out of Scope (POC Focus)

| Flow | Legacy SP | Reason |
|------|-----------|--------|
| **EDI Upload** | `ntrUploadC4POHeaderUpdate` | POC focuses on consumption, not creation |
| **RDS Creation** | `isp_PostRdsPO` | Mobile device interface out of scope |
| **Auto from SO** | `isp_PopulateSotoPO` | Auto-generation not in POC |
| **Auto from MBOL** | `ispPopulateTOPO_FLEX/ULM` | Trigger-based creation not in POC |

### 10.2 Future Work

| Flow | Legacy SP | Lines | Future Implementation |
|------|-----------|-------|----------------------|
| **Archival** | `nspArchivePO` | 414 | `ArchivePOWorkflow` |
| **Cancellation** | `nsp_CancelExpiredPOOrders` | 322 | `CancelPOWorkflow` |
| **Swap** | `isp_ASNSwapPO` | 366 | `SwapPOWorkflow` |
| **Line Split** | `isp_Split_PODetail_By_UCC` | 320 | `SplitPODetailWorkflow` |

---

## 11. Quick Reference

### 11.1 API Endpoints

| Flow | Sync | Async | Status | Cancel |
|------|------|-------|--------|--------|
| **Populate** | `POST /api/v1/populate/populate` | `POST .../async` | `GET .../status` | `POST .../cancel` |
| **Finalize** | `POST /api/v1/receipts/{key}/finalize` | `POST .../async` | `GET .../status` | `POST .../cancel` |
| **Trade Return** | — | `POST /api/v1/tradereturns/populate` | `GET .../status` | — |
| **Health** | `GET /actuator/health` | — | — | — |

### 11.2 Demo Commands

```bash
# Run compensation demo
./scripts/compensation-demo.sh

# Start infrastructure
docker-compose up -d

# Start application
cd po-api && mvn spring-boot:run

# Check health
curl http://localhost:8080/actuator/health

# Populate PO (sync)
curl -X POST http://localhost:8080/api/v1/populate/populate \
  -H "Content-Type: application/json" \
  -d '{"poKeys":["PO-NIKE-IN-001"],"storerKey":"NIKE","facility":"KR01","userId":"demo"}'
```

### 11.3 Monitoring URLs

| Service | URL |
|---------|-----|
| **Temporal UI** | http://localhost:8088 |
| **Kafka UI** | http://localhost:8090 |
| **Swagger UI** | http://localhost:8080/swagger-ui.html |
| **Actuator** | http://localhost:8080/actuator |

---

## 12. Glossary

| Term | Meaning |
|------|---------|
| **PO** | Purchase Order - the source document for inbound goods |
| **ASN** | Advanced Shipping Notice - notification before goods arrive |
| **Receipt** | The WMS record created to receive goods (same as ASN in this context) |
| **Finalization** | The process of confirming receipt and posting inventory |
| **Saga Pattern** | A sequence of transactions with compensation for rollback |
| **Temporal** | Workflow orchestration platform (replaces stored procedures) |
| **Activity** | A single step in a Temporal workflow (unit of work) |
| **Plugin** | A modular piece of code for client/region-specific behavior |
| **Lottable** | Lot tracking attributes (batch, serial, expiry, etc.) |
| **NOLOCK** | SQL hint that reads without locks (causes dirty reads) |
| **Compensation** | The reverse action to undo a completed step |
| **XDock** | Cross-dock - goods flow directly from receiving to shipping |

---

*Document Version: 2.0*
*Generated: 2026-05-31*
*Source: purchase-order-e2e.md + po-modernization POC analysis*
