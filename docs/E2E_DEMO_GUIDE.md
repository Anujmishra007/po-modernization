# E2E Demo Guide: PO Modernization Workflow

## Overview

The demo shows two Temporal workflows that replace legacy stored procedures:

```
┌──────────────┐      ┌─────────────────┐      ┌───────────────────┐
│      PO      │ ──►  │     RECEIPT     │ ──►  │    INVENTORY      │
│  (PODETAIL)  │      │ (RECEIPTDETAIL) │      │ (LOTxLOCxID/ITRN) │
└──────────────┘      └─────────────────┘      └───────────────────┘
       │                      │                        │
       │    PopulatePO        │    FinalizeReceipt     │
       │    Workflow          │    Workflow            │
       └──────────────────────┴────────────────────────┘
```

---

## API Execution Order

### Step 1: PopulatePO Workflow (PO → Receipt)

**What it does:** Creates a Receipt (ASN) from a Purchase Order by:
- Validating the PO exists and is in correct status
- Mapping PO header fields to Receipt header
- Mapping PO detail lines to Receipt detail lines
- Applying lottable rules (client/region specific)
- Creating the Receipt and ReceiptDetail records

**API Endpoint:**
```
POST /api/v1/populate/populate/async
```

**Request Body:**
```json
{
  "poKeys": ["PO-NIKE-IN-001"],
  "storerKey": "NIKE",
  "facility": "IN01"
}
```

**Response:**
```json
{
  "workflowId": "populate-NIKE-PO-NIKE-IN-001-1780214428649",
  "statusUrl": "/api/v1/po/populate/{workflowId}/status"
}
```

**Check Status:**
```
GET /api/v1/populate/populate/{workflowId}/status
```

**Status Response:**
```json
{
  "workflowId": "populate-NIKE-PO-NIKE-IN-001-1780214428649",
  "status": "COMPLETED",
  "completedSteps": [
    "RESOLVE_CONTEXT: V2/ASIA-IN",
    "PRE_PLUGINS",
    "VALIDATION",
    "MAPPING: 2 lines",
    "LOTTABLES: 4 rules applied",
    "CREATE_HEADER: RCV-1780214405046",
    "CREATE_DETAILS: 2 records",
    "CREATE_RESERVATIONS: 2 reservations",
    "NOTIFICATION"
  ],
  "progress": 100
}
```

---

### Step 2: FinalizeReceipt Workflow (Receipt → Inventory)

**What it does:** Finalizes a Receipt and posts inventory by:
- Validating the Receipt is ready for finalization (status=0)
- Changing status to "Finalizing" (status=6)
- Creating LOT records for lot tracking
- Creating LOTxLOCxID records (inventory balance)
- Creating ITRN records (inventory transactions)
- Updating PO quantities received
- Changing status to "Finalized" (status=9)

**API Endpoint:**
```
POST /api/v1/receipts/{receiptKey}/finalize/async
```

**Request Body:**
```json
{
  "storerKey": "NIKE",
  "facility": "KR01"
}
```

**Response:**
```json
{
  "workflowId": "finalize-NIKE-RCV-DEMO-001-1780214768036",
  "statusUrl": "/api/v1/receipts/{receiptKey}/finalize/{workflowId}/status"
}
```

**Check Status:**
```
GET /api/v1/receipts/{receiptKey}/finalize/{workflowId}/status
```

**Status Response:**
```json
{
  "workflowId": "finalize-NIKE-RCV-DEMO-001-1780214768036",
  "status": "COMPLETED",
  "completedSteps": [
    "RESOLVE_CONTEXT: V2/ASIA-KR",
    "VALIDATE: Receipt status=0",
    "PRE_PLUGINS: 0 plugins",
    "SET_STATUS_FINALIZING: 0 → 6",
    "POST_INVENTORY: 3 records, qty=450.00000",
    "APPLY_HOLDS: Skipped (disabled)",
    "UPDATE_PO_QTY: 0 PO lines updated",
    "RELEASE_PUTAWAY: Skipped (disabled)",
    "POST_PLUGINS: 0/0 successful",
    "SET_STATUS_FINALIZED: status=9"
  ],
  "progress": 111
}
```

**Check Progress (Real-time):**
```
GET /api/v1/receipts/{receiptKey}/finalize/{workflowId}/progress
```

---

## Test Data Required for Higher Environments

### 1. PO Table (dbo.PO)

| Column | Description | Example |
|--------|-------------|---------|
| POKEY | Primary key | `PO-TEST-001` |
| STORERKEY | Client/customer code | `NIKE`, `HM`, `ADIDAS` |
| EXTERNPOKEY | External PO reference | `NIKE-EXT-001` |
| FACILITY | Warehouse code | `KR01`, `SG01`, `IN01` |
| SUPPLIERKEY | Supplier code | `NIKE-SUPPLIER-001` |
| STATUS | PO status (0=Open) | `0` |
| POTYPE | PO type | `STANDARD`, `XDOCK`, `FAST` |
| ORDERDATE | Order date | `2026-05-31` |
| EXPECTEDDATE | Expected receipt date | `2026-06-05` |

**Sample Insert:**
```sql
INSERT INTO dbo.PO (POKEY, STORERKEY, EXTERNPOKEY, FACILITY, SUPPLIERKEY,
                    STATUS, POTYPE, ORDERDATE, EXPECTEDDATE, ADDWHO, ADDDATE, VERSION)
VALUES ('PO-TEST-001', 'NIKE', 'NIKE-EXT-001', 'KR01', 'NIKE-SUPPLIER-001',
        '0', 'STANDARD', GETDATE(), GETDATE(), 'SYSTEM', GETDATE(), 0);
```

---

### 2. PO Detail Table (dbo.PODETAIL)

| Column | Description | Example |
|--------|-------------|---------|
| PODETAILKEY | Primary key | `PO-TEST-001-001` |
| POKEY | Foreign key to PO | `PO-TEST-001` |
| POLINENUMBER | Line number | `1`, `2`, `3` |
| SKU | Item/product code | `NK-AIRMAX90-BLK-10` |
| QTYORDERED | Quantity ordered | `100` |
| QTYRECEIVED | Quantity received | `0` |
| UOM | Unit of measure | `EA`, `CS`, `PL` |
| PACKKEY | Pack configuration | `STD` |
| STATUS | Line status | `0` |

**Sample Insert:**
```sql
INSERT INTO dbo.PODETAIL (PODETAILKEY, POKEY, POLINENUMBER, SKU, QTYORDERED,
                          QTYRECEIVED, UOM, PACKKEY, STATUS, ADDWHO, ADDDATE)
VALUES ('PO-TEST-001-001', 'PO-TEST-001', 1, 'NK-AIRMAX90-BLK-10', 100,
        0, 'EA', 'STD', '0', 'SYSTEM', GETDATE());
```

---

### 3. Receipt Table (dbo.RECEIPT) - For Finalize Demo

| Column | Description | Example |
|--------|-------------|---------|
| RECEIPTKEY | Primary key | `RCV-TEST-001` |
| STORERKEY | Client code | `NIKE` |
| EXTERNRECEIPTKEY | External reference | `RCV-EXT-001` |
| FACILITY | Warehouse code | `KR01` |
| STATUS | Receipt status (0=Open) | `0` |
| RECEIPTTYPE | Type | `NORMAL`, `PO` |

**Sample Insert:**
```sql
INSERT INTO dbo.RECEIPT (RECEIPTKEY, STORERKEY, EXTERNRECEIPTKEY, FACILITY,
                         STATUS, RECEIPTTYPE, RECEIPTDATE, ADDWHO, ADDDATE, VERSION)
VALUES ('RCV-TEST-001', 'NIKE', 'RCV-EXT-001', 'KR01',
        '0', 'NORMAL', GETDATE(), 'SYSTEM', GETDATE(), 0);
```

---

### 4. Receipt Detail Table (dbo.RECEIPTDETAIL) - For Finalize Demo

| Column | Description | Example |
|--------|-------------|---------|
| RECEIPTDETAILKEY | Primary key | `RCV-TEST-001-001` |
| RECEIPTKEY | Foreign key | `RCV-TEST-001` |
| RECEIPTLINENUMBER | Line number | `1` |
| SKU | Item code | `NK-AIRMAX90-BLK-10` |
| QTYEXPECTED | Expected qty | `100` |
| QTYRECEIVED | Received qty | `100` |
| TOLOC | Target location | `RECV-01` |
| TOID | License plate ID | `LP001` |
| STORERKEY | Client code | `NIKE` |
| LOTTABLE01-10 | Lot attributes | Style, color, size, etc. |

**Sample Insert:**
```sql
INSERT INTO dbo.RECEIPTDETAIL (RECEIPTDETAILKEY, RECEIPTKEY, RECEIPTLINENUMBER,
                               SKU, QTYEXPECTED, QTYRECEIVED, UOM, PACKKEY, STATUS,
                               POKEY, POLINENUMBER, TOLOC, TOID, STORERKEY,
                               LOTTABLE01, LOTTABLE02, LOTTABLE03, ADDWHO, ADDDATE)
VALUES ('RCV-TEST-001-001', 'RCV-TEST-001', 1,
        'NK-AIRMAX90-BLK-10', 100, 100, 'EA', 'STD', '0',
        'PO-TEST-001', 1, 'RECV-01', 'LP001', 'NIKE',
        'STYLE-AM90', 'COLOR-BLK', 'SIZE-10', 'SYSTEM', GETDATE());
```

---

## Complete Test Data Script for Higher Environment

```sql
-- ============================================
-- PO MODERNIZATION E2E DEMO - TEST DATA SCRIPT
-- ============================================

-- Clean up existing test data (optional)
-- DELETE FROM dbo.RECEIPTDETAIL WHERE RECEIPTKEY LIKE 'RCV-TEST-%';
-- DELETE FROM dbo.RECEIPT WHERE RECEIPTKEY LIKE 'RCV-TEST-%';
-- DELETE FROM dbo.PODETAIL WHERE POKEY LIKE 'PO-TEST-%';
-- DELETE FROM dbo.PO WHERE POKEY LIKE 'PO-TEST-%';

-- ============================================
-- 1. PO HEADERS
-- ============================================

-- PO for PopulatePO demo (NIKE / Korea)
INSERT INTO dbo.PO (POKEY, STORERKEY, EXTERNPOKEY, FACILITY, SUPPLIERKEY,
                    STATUS, POTYPE, ORDERDATE, EXPECTEDDATE, ADDWHO, ADDDATE, VERSION)
VALUES ('PO-TEST-001', 'NIKE', 'NIKE-EXT-TEST-001', 'KR01', 'NIKE-SUPPLIER-001',
        '0', 'STANDARD', GETDATE(), DATEADD(DAY, 7, GETDATE()), 'SYSTEM', GETDATE(), 0);

-- PO for PopulatePO demo (NIKE / India)
INSERT INTO dbo.PO (POKEY, STORERKEY, EXTERNPOKEY, FACILITY, SUPPLIERKEY,
                    STATUS, POTYPE, ORDERDATE, EXPECTEDDATE, ADDWHO, ADDDATE, VERSION)
VALUES ('PO-TEST-002', 'NIKE', 'NIKE-EXT-TEST-002', 'IN01', 'NIKE-SUPPLIER-INDIA',
        '0', 'STANDARD', GETDATE(), DATEADD(DAY, 7, GETDATE()), 'SYSTEM', GETDATE(), 0);

-- PO for PopulatePO demo (H&M / Singapore)
INSERT INTO dbo.PO (POKEY, STORERKEY, EXTERNPOKEY, FACILITY, SUPPLIERKEY,
                    STATUS, POTYPE, ORDERDATE, EXPECTEDDATE, ADDWHO, ADDDATE, VERSION)
VALUES ('PO-TEST-003', 'HM', 'HM-EXT-TEST-001', 'SG01', 'HM-SUPPLIER-ASIA',
        '0', 'FAST', GETDATE(), DATEADD(DAY, 5, GETDATE()), 'SYSTEM', GETDATE(), 0);

-- ============================================
-- 2. PO DETAILS (Lines)
-- ============================================

-- PO-TEST-001 Lines (3 lines, 450 units total)
INSERT INTO dbo.PODETAIL (PODETAILKEY, POKEY, POLINENUMBER, SKU, QTYORDERED,
                          QTYRECEIVED, UOM, PACKKEY, STATUS, ADDWHO, ADDDATE)
VALUES ('PO-TEST-001-001', 'PO-TEST-001', 1, 'NK-AIRMAX90-BLK-10', 100, 0, 'EA', 'STD', '0', 'SYSTEM', GETDATE());

INSERT INTO dbo.PODETAIL (PODETAILKEY, POKEY, POLINENUMBER, SKU, QTYORDERED,
                          QTYRECEIVED, UOM, PACKKEY, STATUS, ADDWHO, ADDDATE)
VALUES ('PO-TEST-001-002', 'PO-TEST-001', 2, 'NK-AIRMAX90-WHT-10', 150, 0, 'EA', 'STD', '0', 'SYSTEM', GETDATE());

INSERT INTO dbo.PODETAIL (PODETAILKEY, POKEY, POLINENUMBER, SKU, QTYORDERED,
                          QTYRECEIVED, UOM, PACKKEY, STATUS, ADDWHO, ADDDATE)
VALUES ('PO-TEST-001-003', 'PO-TEST-001', 3, 'NK-AF1-BLK-9', 200, 0, 'EA', 'STD', '0', 'SYSTEM', GETDATE());

-- PO-TEST-002 Lines (2 lines, 125 units total)
INSERT INTO dbo.PODETAIL (PODETAILKEY, POKEY, POLINENUMBER, SKU, QTYORDERED,
                          QTYRECEIVED, UOM, PACKKEY, STATUS, ADDWHO, ADDDATE)
VALUES ('PO-TEST-002-001', 'PO-TEST-002', 1, 'NK-DUNKS-BLK-11', 50, 0, 'EA', 'STD', '0', 'SYSTEM', GETDATE());

INSERT INTO dbo.PODETAIL (PODETAILKEY, POKEY, POLINENUMBER, SKU, QTYORDERED,
                          QTYRECEIVED, UOM, PACKKEY, STATUS, ADDWHO, ADDDATE)
VALUES ('PO-TEST-002-002', 'PO-TEST-002', 2, 'NK-DUNKS-WHT-11', 75, 0, 'EA', 'STD', '0', 'SYSTEM', GETDATE());

-- PO-TEST-003 Lines (2 lines, 300 units total)
INSERT INTO dbo.PODETAIL (PODETAILKEY, POKEY, POLINENUMBER, SKU, QTYORDERED,
                          QTYRECEIVED, UOM, PACKKEY, STATUS, ADDWHO, ADDDATE)
VALUES ('PO-TEST-003-001', 'PO-TEST-003', 1, 'HM-TSHIRT-BLK-M', 200, 0, 'EA', 'STD', '0', 'SYSTEM', GETDATE());

INSERT INTO dbo.PODETAIL (PODETAILKEY, POKEY, POLINENUMBER, SKU, QTYORDERED,
                          QTYRECEIVED, UOM, PACKKEY, STATUS, ADDWHO, ADDDATE)
VALUES ('PO-TEST-003-002', 'PO-TEST-003', 2, 'HM-TSHIRT-WHT-M', 100, 0, 'EA', 'STD', '0', 'SYSTEM', GETDATE());

-- ============================================
-- 3. PRE-SEEDED RECEIPT (For FinalizeReceipt demo)
-- ============================================

INSERT INTO dbo.RECEIPT (RECEIPTKEY, STORERKEY, EXTERNRECEIPTKEY, FACILITY,
                         STATUS, RECEIPTTYPE, RECEIPTDATE, ADDWHO, ADDDATE, VERSION)
VALUES ('RCV-TEST-001', 'NIKE', 'RCV-EXT-TEST-001', 'KR01',
        '0', 'NORMAL', GETDATE(), 'SYSTEM', GETDATE(), 0);

-- ============================================
-- 4. PRE-SEEDED RECEIPT DETAILS (For FinalizeReceipt demo)
-- ============================================

INSERT INTO dbo.RECEIPTDETAIL (RECEIPTDETAILKEY, RECEIPTKEY, RECEIPTLINENUMBER,
                               SKU, QTYEXPECTED, QTYRECEIVED, UOM, PACKKEY, STATUS,
                               POKEY, POLINENUMBER, TOLOC, TOID, STORERKEY,
                               LOTTABLE01, LOTTABLE02, LOTTABLE03, ADDWHO, ADDDATE)
VALUES ('RCV-TEST-001-001', 'RCV-TEST-001', 1,
        'NK-AIRMAX90-BLK-10', 100, 100, 'EA', 'STD', '0',
        'PO-TEST-001', 1, 'RECV-01', 'LP001', 'NIKE',
        'STYLE-AM90', 'COLOR-BLK', 'SIZE-10', 'SYSTEM', GETDATE());

INSERT INTO dbo.RECEIPTDETAIL (RECEIPTDETAILKEY, RECEIPTKEY, RECEIPTLINENUMBER,
                               SKU, QTYEXPECTED, QTYRECEIVED, UOM, PACKKEY, STATUS,
                               POKEY, POLINENUMBER, TOLOC, TOID, STORERKEY,
                               LOTTABLE01, LOTTABLE02, LOTTABLE03, ADDWHO, ADDDATE)
VALUES ('RCV-TEST-001-002', 'RCV-TEST-001', 2,
        'NK-AIRMAX90-WHT-10', 150, 150, 'EA', 'STD', '0',
        'PO-TEST-001', 2, 'RECV-01', 'LP002', 'NIKE',
        'STYLE-AM90', 'COLOR-WHT', 'SIZE-10', 'SYSTEM', GETDATE());

INSERT INTO dbo.RECEIPTDETAIL (RECEIPTDETAILKEY, RECEIPTKEY, RECEIPTLINENUMBER,
                               SKU, QTYEXPECTED, QTYRECEIVED, UOM, PACKKEY, STATUS,
                               POKEY, POLINENUMBER, TOLOC, TOID, STORERKEY,
                               LOTTABLE01, LOTTABLE02, LOTTABLE03, ADDWHO, ADDDATE)
VALUES ('RCV-TEST-001-003', 'RCV-TEST-001', 3,
        'NK-AF1-BLK-9', 200, 200, 'EA', 'STD', '0',
        'PO-TEST-001', 3, 'RECV-01', 'LP003', 'NIKE',
        'STYLE-AF1', 'COLOR-BLK', 'SIZE-9', 'SYSTEM', GETDATE());

-- ============================================
-- VERIFICATION QUERIES
-- ============================================

-- Verify PO data
SELECT * FROM dbo.PO WHERE POKEY LIKE 'PO-TEST-%';
SELECT * FROM dbo.PODETAIL WHERE POKEY LIKE 'PO-TEST-%';

-- Verify Receipt data
SELECT * FROM dbo.RECEIPT WHERE RECEIPTKEY LIKE 'RCV-TEST-%';
SELECT * FROM dbo.RECEIPTDETAIL WHERE RECEIPTKEY LIKE 'RCV-TEST-%';
```

---

## Complete Demo Script for Higher Environment

### Prerequisites
1. Temporal server running and accessible
2. Application deployed with correct database connection
3. Test data inserted (see above)

### Execution Steps

```bash
# ============================================
# STEP 1: Verify PO exists
# ============================================
curl -s "http://{host}/api/v1/po/PO-TEST-001" | jq '.'

# ============================================
# STEP 2: Run PopulatePO workflow
# ============================================
curl -X POST "http://{host}/api/v1/populate/populate/async" \
  -H "Content-Type: application/json" \
  -d '{
    "poKeys": ["PO-TEST-001"],
    "storerKey": "NIKE",
    "facility": "KR01"
  }'

# Save the workflowId from the response

# ============================================
# STEP 3: Check PopulatePO status
# ============================================
curl -s "http://{host}/api/v1/populate/populate/{workflowId}/status" | jq '.'

# Wait for status = "COMPLETED"
# Note the receiptKey from CREATE_HEADER step (e.g., "RCV-xxxxx")

# ============================================
# STEP 4: Run FinalizeReceipt workflow
# ============================================
# Option A: Use the receipt created by PopulatePO
curl -X POST "http://{host}/api/v1/receipts/{receiptKey}/finalize/async" \
  -H "Content-Type: application/json" \
  -d '{
    "storerKey": "NIKE",
    "facility": "KR01"
  }'

# Option B: Use pre-seeded receipt RCV-TEST-001
curl -X POST "http://{host}/api/v1/receipts/RCV-TEST-001/finalize/async" \
  -H "Content-Type: application/json" \
  -d '{
    "storerKey": "NIKE",
    "facility": "KR01"
  }'

# ============================================
# STEP 5: Check FinalizeReceipt status
# ============================================
curl -s "http://{host}/api/v1/receipts/{receiptKey}/finalize/{workflowId}/status" | jq '.'

# Wait for status = "COMPLETED"
# Verify POST_INVENTORY step shows records created
```

---

## Summary Table

| Step | API | Input | Output |
|------|-----|-------|--------|
| 1 | `POST /api/v1/populate/populate/async` | PO Key + Storer + Facility | Receipt created |
| 2 | `GET /api/v1/populate/populate/{id}/status` | Workflow ID | Progress/completion |
| 3 | `POST /api/v1/receipts/{key}/finalize/async` | Receipt Key + Storer + Facility | Inventory posted |
| 4 | `GET /api/v1/receipts/{key}/finalize/{id}/status` | Workflow ID | Progress/completion |

---

## Data Flow Diagram

```
PO (3 test records)
    │
    ├── PODETAIL (7 lines total)
    │       │
    │       ▼ [PopulatePO Workflow]
    │
RECEIPT (created automatically)
    │
    ├── RECEIPTDETAIL (lines mapped from PODETAIL)
    │       │
    │       ▼ [FinalizeReceipt Workflow]
    │
    ├── LOT (lot tracking records)
    ├── LOTxLOCxID (inventory balance)
    └── ITRN (inventory transactions)
```

---

## Architecture Demonstrated

```
  ┌─────────────────────────────────────────────────────────────┐
  │                    REST API Layer                           │
  │   POST /api/v1/populate/async → PopulatePO Workflow         │
  │   POST /api/v1/receipts/{key}/finalize/async → Finalize     │
  └─────────────────────────────────────────────────────────────┘
                              │
                              ▼
  ┌─────────────────────────────────────────────────────────────┐
  │                Temporal Workflow Engine                      │
  │  ┌────────────────────┐  ┌──────────────────────────────┐  │
  │  │ po-populate-queue  │  │ po-finalize-queue            │  │
  │  │ PopulatePOWorkflow │  │ FinalizeReceiptWorkflow     │  │
  │  └────────────────────┘  └──────────────────────────────┘  │
  └─────────────────────────────────────────────────────────────┘
                              │
                              ▼
  ┌─────────────────────────────────────────────────────────────┐
  │              Activity Layer (Business Logic)                 │
  │  ValidationActivity → MappingActivity → PersistenceActivity │
  │  InventoryActivity → NotificationActivity                   │
  └─────────────────────────────────────────────────────────────┘
                              │
                              ▼
  ┌─────────────────────────────────────────────────────────────┐
  │                 Database Layer (SQL Server)                  │
  │  PO → PODETAIL → RECEIPT → RECEIPTDETAIL → LOTxLOCxID       │
  │                                          → ITRN             │
  │                                          → LOT              │
  └─────────────────────────────────────────────────────────────┘
```

---

## Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| "Storer key is required" | Missing storerKey in request | Add `storerKey` to request body |
| "PO not found" | PO doesn't exist or wrong key | Verify PO exists in database |
| "MAPPING: 0 lines" | PODETAIL records missing | Insert PODETAIL records for the PO |
| Workflow stuck | Temporal worker not running | Check Temporal server and worker status |
| "Receipt status invalid" | Receipt already finalized | Use a fresh receipt with status=0 |

---

## Environment Checklist

- [ ] Temporal server running (port 7233)
- [ ] Temporal UI accessible (port 8088)
- [ ] Application deployed and healthy (`/actuator/health`)
- [ ] Database connection configured
- [ ] Test data inserted (PO, PODETAIL, RECEIPT, RECEIPTDETAIL)
- [ ] Both task queues registered (po-populate-queue, po-finalize-queue)
