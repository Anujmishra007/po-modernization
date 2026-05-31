# Complete Demo Flows - PO Modernization

## Overview

This document lists ALL available demo flows in the po-modernization microservice, including their status and demo commands.

---

## 1. CRUD Operations (Synchronous APIs)

### 1.1 PO CRUD Operations

| Operation | API | Status | Description |
|-----------|-----|--------|-------------|
| Create PO | `POST /api/v1/po` | **IMPLEMENTED** | Create a new Purchase Order |
| Get PO | `GET /api/v1/po/{poKey}` | **IMPLEMENTED** | Retrieve PO by key |
| List POs | `GET /api/v1/po?storerKey=X&facility=Y` | **IMPLEMENTED** | List POs by storer/facility |
| Update PO | `PUT /api/v1/po/{poKey}` | **IMPLEMENTED** | Update PO details |
| Delete PO | `DELETE /api/v1/po/{poKey}` | **IMPLEMENTED** | Delete PO |
| Update Status | `PATCH /api/v1/po/{poKey}/status?status=X` | **IMPLEMENTED** | Change PO status |

#### Demo: Create PO
```bash
curl -X POST http://localhost:8080/api/v1/po \
  -H "Content-Type: application/json" \
  -H "X-User-Id: demo-user" \
  -d '{
    "storerKey": "NIKE",
    "facility": "KR01",
    "externPoKey": "EXT-PO-001",
    "supplierKey": "SUP-001",
    "expectedDate": "2024-12-31",
    "poType": "STANDARD",
    "details": [
      {"sku": "NK-TEST-001", "qtyOrdered": 100, "uom": "EA"}
    ]
  }'
```

#### Demo: Get PO
```bash
curl http://localhost:8080/api/v1/po/PO-DEMO-001
```

#### Demo: Update PO Status
```bash
curl -X PATCH "http://localhost:8080/api/v1/po/PO-DEMO-001/status?status=9" \
  -H "X-User-Id: demo-user"
```

---

### 1.2 Receipt CRUD Operations

| Operation | API | Status | Description |
|-----------|-----|--------|-------------|
| Get Receipt | `GET /api/v1/receipt/{receiptKey}` | **IMPLEMENTED** | Retrieve receipt by key |
| List Receipts | `GET /api/v1/receipt?storerKey=X&facility=Y` | **IMPLEMENTED** | List receipts by storer/facility |
| Get by PO | `GET /api/v1/receipt/by-po/{poKey}` | **IMPLEMENTED** | Get receipts for a PO |
| Update Status | `PATCH /api/v1/receipt/{receiptKey}/status?status=X` | **IMPLEMENTED** | Change receipt status |

---

## 2. Workflow Operations (Temporal Orchestration)

### 2.1 PopulatePO Workflow

**Purpose**: Converts PO(s) into ASN/Receipt structure (replaces legacy SP `WM.lsp_ASN_PopulatePOs_Wrapper`)

| Operation | API | Status | Description |
|-----------|-----|--------|-------------|
| Sync Populate | `POST /api/v1/populate/populate` | **WORKING** | Synchronous - waits for result |
| Async Populate | `POST /api/v1/populate/populate/async` | **WORKING** | Returns workflow ID immediately |
| Get Status | `GET /api/v1/populate/populate/{workflowId}/status` | **WORKING** | Query workflow progress |
| Cancel | `POST /api/v1/populate/populate/{workflowId}/cancel` | **WORKING** | Cancel running workflow |

#### Workflow Steps (with Saga compensation)
```
STEP 1: RESOLVE_CONTEXT     → Determine V0/V2, region, client
STEP 2: VALIDATION          → Validate PO keys, status, storer
STEP 3: MAPPING             → Map PO fields to Receipt structure
STEP 4: CREATE_HEADER       → Create RECEIPT record (compensation: delete)
STEP 5: CREATE_DETAILS      → Create RECEIPTDETAIL records (compensation: delete)
STEP 6: RUN_PLUGINS         → Execute client-specific plugins
STEP 7: NOTIFICATION        → Send completion events
```

#### Demo: Sync Populate
```bash
curl -X POST http://localhost:8080/api/v1/populate/populate \
  -H "Content-Type: application/json" \
  -d '{
    "poKeys": ["PO-NIKE-IN-001"],
    "storerKey": "NIKE",
    "facility": "IN01",
    "userId": "demo-user"
  }'
```

#### Demo: Async Populate + Status Polling
```bash
# Start async workflow
WORKFLOW_ID=$(curl -s -X POST http://localhost:8080/api/v1/populate/populate/async \
  -H "Content-Type: application/json" \
  -d '{"poKeys":["PO-XDOCK-001"],"storerKey":"NIKE","facility":"KR01","userId":"demo"}' \
  | jq -r '.workflowId')

# Poll status
curl http://localhost:8080/api/v1/populate/populate/$WORKFLOW_ID/status
```

---

### 2.2 FinalizeReceipt Workflow

**Purpose**: Finalizes receipt and posts inventory (replaces legacy SP `ispFinalizeReceipt`)

| Operation | API | Status | Description |
|-----------|-----|--------|-------------|
| Sync Finalize | `POST /api/v1/receipts/{receiptKey}/finalize` | **WORKING** | Synchronous finalization |
| Async Finalize | `POST /api/v1/receipts/{receiptKey}/finalize/async` | **WORKING** | Returns workflow ID |
| Get Status | `GET /api/v1/receipts/{receiptKey}/finalize/{workflowId}/status` | **WORKING** | Query progress |
| Get Progress | `GET /api/v1/receipts/{receiptKey}/finalize/{workflowId}/progress` | **WORKING** | Inventory posting progress |
| Cancel | `POST /api/v1/receipts/{receiptKey}/finalize/{workflowId}/cancel` | **WORKING** | Cancel with compensation |
| Pause | `POST /api/v1/receipts/{receiptKey}/finalize/{workflowId}/pause` | **WORKING** | Pause workflow |
| Resume | `POST /api/v1/receipts/{receiptKey}/finalize/{workflowId}/resume` | **WORKING** | Resume paused workflow |

#### Workflow Steps (with Saga compensation)
```
STEP 1:  RESOLVE_CONTEXT        → Determine variation context
STEP 2:  VALIDATE               → Check receipt status (compensation: none)
STEP 3:  PRE_PLUGINS            → Pre-finalize client hooks (compensation: rollback)
STEP 4:  SET_STATUS_FINALIZING  → Status → '6' (compensation: revert)
------- POINT OF NO RETURN (clean cancel no longer possible) -------
STEP 5:  POST_INVENTORY         → Create LOTxLOCxID/ITRN (compensation: delete)
STEP 6:  APPLY_HOLDS            → Apply inventory holds (compensation: remove)
STEP 7:  UPDATE_PO_QTY          → Update PO received qty (compensation: revert)
STEP 8:  RELEASE_PUTAWAY        → Create putaway tasks (compensation: cancel)
STEP 9:  POST_PLUGINS           → Post-finalize hooks (best effort)
STEP 10: SET_STATUS_FINALIZED   → Status → '9' (no compensation - success)
```

#### Demo: Sync Finalize
```bash
curl -X POST http://localhost:8080/api/v1/receipts/RCV-PO-NIKE-IN-001/finalize \
  -H "Content-Type: application/json" \
  -H "X-User-Id: demo-user" \
  -d '{"storerKey": "NIKE", "facility": "IN01"}'
```

#### Demo: Async with Progress Monitoring
```bash
# Start async finalize
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/receipts/RCV-001/finalize/async \
  -H "Content-Type: application/json" \
  -d '{"storerKey":"NIKE","facility":"KR01"}')
WORKFLOW_ID=$(echo $RESPONSE | jq -r '.workflowId')

# Monitor progress
watch -n 1 "curl -s http://localhost:8080/api/v1/receipts/RCV-001/finalize/$WORKFLOW_ID/progress"
```

---

### 2.3 TradeReturn Workflow

**Purpose**: Creates Sales Order from Receipt for trade returns (replaces legacy SP `WM.lsp_ASN_PopulateSOs_Wrapper`)

| Operation | API | Status | Description |
|-----------|-----|--------|-------------|
| Populate SO | N/A (internal) | **IMPLEMENTED** | Creates SO from Receipt |
| Cancel | Signal-based | **IMPLEMENTED** | Cancel with compensation |

#### Workflow Steps (with Saga compensation)
```
STEP 1: RESOLVE_CONTEXT         → Determine context
STEP 2: VALIDATION              → Validate return request
STEP 3: MAPPING                 → Map Receipt to SO structure
STEP 4: CREATE_HEADER           → Create ORDERS record (compensation: delete)
STEP 5: CREATE_DETAILS          → Create ORDERDETAIL (compensation: delete)
STEP 6: CREATE_RESERVATIONS     → Reserve inventory (compensation: release)
STEP 7: UPDATE_RECEIPT          → Update receipt status
STEP 8: AUTO_RELEASE            → Optional auto-release
STEP 9: NOTIFICATION            → Send events
```

#### Demo: Trade Return
```bash
# Note: TradeReturn workflow is typically triggered internally
# For demo, you can call via Temporal CLI if exposed
```

---

## 3. Saga/Compensation Demonstrations

### 3.1 Trigger Compensation via Cancellation

**Scenario**: Start a workflow, then cancel it to see compensation run

```bash
# Start async finalize
WORKFLOW_ID=$(curl -s -X POST http://localhost:8080/api/v1/receipts/RCV-001/finalize/async \
  -H "Content-Type: application/json" \
  -d '{"storerKey":"NIKE","facility":"KR01"}' | jq -r '.workflowId')

# Cancel while running (before point-of-no-return)
curl -X POST http://localhost:8080/api/v1/receipts/RCV-001/finalize/$WORKFLOW_ID/cancel

# Check status - should show COMPENSATING → CANCELLED
curl http://localhost:8080/api/v1/receipts/RCV-001/finalize/$WORKFLOW_ID/status
```

#### Expected Response After Cancellation
```json
{
  "workflowId": "finalize-receipt-xxx",
  "status": "CANCELLED",
  "currentStep": "CANCELLED",
  "completedSteps": [
    "RESOLVE_CONTEXT: V2/ASIA",
    "VALIDATE: Receipt status=0",
    "PRE_PLUGINS: 2 plugins"
  ],
  "canCancel": false
}
```

### 3.2 Trigger Compensation via Error

**Scenario**: Inject an error to see automatic compensation

```bash
# Use invalid receipt key to trigger error
curl -X POST http://localhost:8080/api/v1/receipts/INVALID-RECEIPT/finalize \
  -H "Content-Type: application/json" \
  -d '{"storerKey":"NIKE","facility":"KR01"}'
```

#### Expected Error Response with Compensation Details
```json
{
  "success": false,
  "receiptKey": "INVALID-RECEIPT",
  "workflowId": "finalize-receipt-xxx",
  "workflowStatus": "FAILED",
  "errors": ["Finalization failed at VALIDATE: Receipt not found"]
}
```

### 3.3 Pause/Resume Demonstration

**Scenario**: Show workflow can be paused and resumed mid-execution

```bash
# Start async finalize
WORKFLOW_ID=$(curl -s -X POST http://localhost:8080/api/v1/receipts/RCV-001/finalize/async \
  -H "Content-Type: application/json" \
  -d '{"storerKey":"NIKE","facility":"KR01"}' | jq -r '.workflowId')

# Pause (workflow will stop at next checkpoint)
curl -X POST http://localhost:8080/api/v1/receipts/RCV-001/finalize/$WORKFLOW_ID/pause

# Check status - should show paused
curl http://localhost:8080/api/v1/receipts/RCV-001/finalize/$WORKFLOW_ID/status

# Resume
curl -X POST http://localhost:8080/api/v1/receipts/RCV-001/finalize/$WORKFLOW_ID/resume
```

---

## 4. Variation/Plugin Demonstrations

### 4.1 Region-Specific Behavior

**Korea (KR01)**: Customs code generation in Lottable03
```bash
curl -X POST http://localhost:8080/api/v1/populate/populate \
  -H "Content-Type: application/json" \
  -d '{
    "poKeys": ["PO-DEMO-001"],
    "storerKey": "NIKE",
    "facility": "KR01",
    "userId": "demo-user"
  }'
```

**India (IN01)**: GST code in Lottable04
```bash
curl -X POST http://localhost:8080/api/v1/populate/populate \
  -H "Content-Type: application/json" \
  -d '{
    "poKeys": ["PO-NIKE-IN-001"],
    "storerKey": "NIKE",
    "facility": "IN01",
    "userId": "demo-user"
  }'
```

### 4.2 Client-Specific Behavior

**Nike**: Style code rules applied
```bash
# Nike has specific lottable rules configured
curl -X POST http://localhost:8080/api/v1/receipts/RCV-001/finalize \
  -H "Content-Type: application/json" \
  -d '{"storerKey":"NIKE","facility":"KR01"}'
```

**H&M**: Different tolerance settings
```bash
curl -X POST http://localhost:8080/api/v1/receipts/RCV-HM-001/finalize \
  -H "Content-Type: application/json" \
  -d '{"storerKey":"HM","facility":"SG01"}'
```

---

## 5. Complete E2E Demo Sequence

### Happy Path: PO → Receipt → Finalize → Inventory
```bash
# Step 1: Create PO (or use seeded data)
# PO-NIKE-IN-001 already exists in seed data

# Step 2: Populate PO → Receipt
curl -X POST http://localhost:8080/api/v1/populate/populate \
  -H "Content-Type: application/json" \
  -d '{
    "poKeys": ["PO-NIKE-IN-001"],
    "storerKey": "NIKE",
    "facility": "IN01",
    "userId": "demo-user"
  }'
# Returns: receiptKey = "RCV-PO-NIKE-IN-001"

# Step 3: Finalize Receipt → Post Inventory
curl -X POST http://localhost:8080/api/v1/receipts/RCV-PO-NIKE-IN-001/finalize \
  -H "Content-Type: application/json" \
  -d '{"storerKey":"NIKE","facility":"IN01"}'
# Returns: inventory posted, holds applied, putaway tasks created
```

### Error Path: Compensation Demo
```bash
# Use a receipt that will fail validation
curl -X POST http://localhost:8080/api/v1/receipts/RCV-FAIL-DEMO/finalize \
  -H "Content-Type: application/json" \
  -d '{"storerKey":"NIKE","facility":"KR01"}'
# Returns: error with compensation executed
```

---

## 6. Test Data Summary

| PO Key | Storer | Facility | Lines | Purpose |
|--------|--------|----------|-------|---------|
| PO-DEMO-001 | NIKE | KR01 | 3 | Korea demo, already has receipt |
| PO-FAIL-DEMO | NIKE | KR01 | 0 | Failure scenario testing |
| PO-HM-SG-001 | HM | SG01 | 0 | Singapore H&M demo |
| PO-NIKE-IN-001 | NIKE | IN01 | 2 | India GST demo |
| PO-XDOCK-001 | NIKE | KR01 | 0 | Cross-dock demo |

---

## 7. Status Summary

| Flow | Status | Demo Ready |
|------|--------|------------|
| PO CRUD | **IMPLEMENTED** | YES |
| Receipt CRUD | **IMPLEMENTED** | YES |
| PopulatePO Workflow | **WORKING** | YES |
| FinalizeReceipt Workflow | **WORKING** | YES |
| TradeReturn Workflow | **IMPLEMENTED** | Needs API exposure |
| Saga Compensation | **WORKING** | YES |
| Pause/Resume | **WORKING** | YES |
| Async + Progress | **WORKING** | YES |
| Region Variation | **WORKING** | YES |
| Client Variation | **WORKING** | YES |
| XDock Flow | **IMPLEMENTED** | Needs seed data |
| Plugin Hooks | **IMPLEMENTED** | YES |

---

## 8. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        REST API Layer                                │
├─────────────────┬─────────────────┬─────────────────────────────────┤
│  POController   │ PopulateController │ FinalizeController           │
│  ReceiptController│                 │                               │
└────────┬────────┴────────┬─────────┴────────────┬───────────────────┘
         │                 │                       │
         ▼                 ▼                       ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Temporal Workflow Engine                          │
├─────────────────┬─────────────────┬─────────────────────────────────┤
│ PopulatePOWorkflow │ FinalizeReceiptWorkflow │ TradeReturnWorkflow  │
│   (Saga Pattern)   │    (Saga Pattern)       │   (Saga Pattern)     │
└────────┬───────────┴────────┬────────────────┴──────────────────────┘
         │                    │
         ▼                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        Activity Layer                                │
├───────────────────────────────────────────────────────────────────────┤
│ ValidationActivity │ MappingActivity │ PersistenceActivity           │
│ InventoryPostingActivity │ InventoryHoldActivity │ POQuantityActivity│
│ PutawayReleaseActivity │ PluginActivity │ NotificationActivity       │
│ ReceiptStatusActivity │ FinalizePluginActivity │ TradeReturnActivity │
└────────────────────────────────┬────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        Domain Layer                                  │
├─────────────────────────────────────────────────────────────────────┤
│  Entities: PO, PODetail, Receipt, ReceiptDetail, Lot, LotxLocxId    │
│  Repositories, Services, Plugins, Rules                             │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 9. Quick Reference Commands

```bash
# Health Check
curl http://localhost:8080/api/actuator/health

# List POs
curl "http://localhost:8080/api/v1/po?storerKey=NIKE&facility=KR01"

# Populate PO (sync)
curl -X POST http://localhost:8080/api/v1/populate/populate \
  -H "Content-Type: application/json" \
  -d '{"poKeys":["PO-NIKE-IN-001"],"storerKey":"NIKE","facility":"IN01","userId":"demo"}'

# Finalize Receipt (sync)
curl -X POST http://localhost:8080/api/v1/receipts/{receiptKey}/finalize \
  -H "Content-Type: application/json" \
  -d '{"storerKey":"NIKE","facility":"IN01"}'

# Check Workflow Status
curl http://localhost:8080/api/v1/receipts/{receiptKey}/finalize/{workflowId}/status
```

---

*Last Updated: 2024*
