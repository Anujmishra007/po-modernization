# PO Modernization - Saga Compensation Demos

This directory contains demo scripts showcasing the **Temporal Saga pattern** for distributed transaction compensation in the PO Modernization microservice.

## Prerequisites

1. **Start the services:**
   ```bash
   # Terminal 1: Start Temporal server
   docker-compose up -d temporal temporal-admin-tools temporal-ui postgresql

   # Terminal 2: Start PO Modernization service
   cd po-modernization
   ./gradlew bootRun
   ```

2. **Verify services are running:**
   - Application: http://localhost:8080/actuator/health
   - Temporal UI: http://localhost:8088

## Quick Start

```bash
# Run the interactive demo menu
./scripts/demo-all-compensation-flows.sh

# Or run a specific demo
./scripts/demo-01-populate-happy-path.sh
```

## Available Demos

| Demo | Script | Description |
|------|--------|-------------|
| **01** | `demo-01-populate-happy-path.sh` | Shows successful PO → ASN population flow |
| **02** | `demo-02-populate-cancel-compensation.sh` | Shows cancellation triggering Saga compensation |
| **03** | `demo-03-finalize-happy-path.sh` | Shows successful receipt finalization |
| **04** | `demo-04-finalize-cancel-compensation.sh` | Shows cancel with "point of no return" concept |
| **05** | `demo-05-finalize-pause-resume.sh` | Shows workflow pause and resume capability |
| **06** | `demo-06-finalize-error-compensation.sh` | Shows automatic compensation on error |
| **07** | `demo-07-trade-return-compensation.sh` | Shows Trade Return flow with compensation |

## Demo Details

### Demo 01: Populate Happy Path

**Flow:**
```
RESOLVE_CONTEXT → PRE_PLUGINS → VALIDATION → MAPPING → LOTTABLES
    → CREATE_HEADER → CREATE_DETAILS → CREATE_RESERVATIONS
    → LEGACY_SYNC → NOTIFICATION → COMPLETED
```

**No compensation needed** - all steps complete successfully.

---

### Demo 02: Populate Cancel Compensation

**Scenario:** User cancels during workflow execution

**Saga Compensation (reverse order):**
```
CANCEL signal received
    ↩ rollbackLegacy()
    ↩ releaseReservations()
    ↩ deleteReceiptDetails()
    ↩ deleteReceiptHeader()
Status: CANCELLED
```

---

### Demo 03: Finalize Happy Path

**Flow:**
```
RESOLVE_CONTEXT → VALIDATE → PRE_PLUGINS → SET_STATUS_FINALIZING
    → POST_INVENTORY → APPLY_HOLDS → UPDATE_PO_QTY
    → RELEASE_PUTAWAY → POST_PLUGINS → SET_STATUS_FINALIZED
    → COMPLETED
```

**Key Concept:** After `SET_STATUS_FINALIZING`, `canCancel` becomes `false`.

---

### Demo 04: Finalize Cancel Compensation

**Scenario:** User cancels BEFORE the "point of no return"

**Point of No Return:**
- Before `SET_STATUS_FINALIZING`: `canCancel = true`
- After `SET_STATUS_FINALIZING`: `canCancel = false`

**Saga Compensation:**
```
CANCEL signal received (if canCancel=true)
    ↩ rollbackPreFinalizePlugins()
    ↩ revertStatus()
Status: CANCELLED
```

---

### Demo 05: Finalize Pause/Resume

**Scenario:** Operator needs to investigate mid-workflow

**Flow:**
```
Workflow running...
    PAUSE signal → Workflow holds at current step
    (time for investigation/fix)
    RESUME signal → Workflow continues
    → COMPLETED
```

**Use Cases:**
- Investigating unexpected data
- Fixing downstream system issues
- Coordinating with other processes

---

### Demo 06: Finalize Error Compensation

**Scenario:** Error occurs during workflow execution

**Automatic Saga Compensation:**
```
Error at step N
    ↩ compensate(N-1)
    ↩ compensate(N-2)
    ↩ ...
    ↩ compensate(1)
Status: FAILED (but data is consistent!)
```

**Key Benefit:** No manual cleanup required!

---

### Demo 07: Trade Return Compensation

**Flow:**
```
Receipt → Sales Order (for customer returns)

RESOLVE_CONTEXT → VALIDATION → MAPPING
    → CREATE_HEADER → CREATE_DETAILS → CREATE_RESERVATIONS
    → UPDATE_RECEIPT → AUTO_RELEASE → NOTIFICATION
```

**Saga Compensation:**
```
CANCEL signal received
    ↩ releaseReservations()
    ↩ deleteSalesOrderDetails()
    ↩ deleteSalesOrderHeader()
Status: CANCELLED
```

---

## Architecture: Saga Pattern

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SAGA COMPENSATION PATTERN                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Step 1: Validate       (No compensation needed - read-only)                │
│      │                                                                      │
│      ▼                                                                      │
│  Step 2: Create Header  ←──┐                                                │
│      │                     │ Compensation: deleteHeader()                   │
│      ▼                     │                                                │
│  Step 3: Create Details ←──┼─┐                                              │
│      │                     │ │ Compensation: deleteDetails()                │
│      ▼                     │ │                                              │
│  Step 4: Reserve Inv    ←──┼─┼─┐                                            │
│      │                     │ │ │ Compensation: releaseReservations()        │
│      ▼                     │ │ │                                            │
│  Step 5: Sync Legacy    ←──┼─┼─┼─┐                                          │
│      │                     │ │ │ │ Compensation: rollbackLegacy()           │
│      ▼                     │ │ │ │                                          │
│  SUCCESS                   │ │ │ │                                          │
│                            │ │ │ │                                          │
│  ─── OR ON FAILURE ───     │ │ │ │                                          │
│                            │ │ │ │                                          │
│  Error/Cancel at Step N    │ │ │ │                                          │
│      │                     │ │ │ │                                          │
│      └─ Run compensations ─┴─┴─┴─┘ (in reverse order)                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## API Endpoints Used

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/populate/populate` | POST | Sync populate |
| `/api/v1/populate/populate/async` | POST | Async populate |
| `/api/v1/populate/populate/{id}/status` | GET | Check populate status |
| `/api/v1/populate/populate/{id}/cancel` | POST | Cancel populate |
| `/api/v1/receipts/{key}/finalize` | POST | Sync finalize |
| `/api/v1/receipts/{key}/finalize/async` | POST | Async finalize |
| `/api/v1/receipts/{key}/finalize/{id}/status` | GET | Check finalize status |
| `/api/v1/receipts/{key}/finalize/{id}/cancel` | POST | Cancel finalize |
| `/api/v1/receipts/{key}/finalize/{id}/pause` | POST | Pause finalize |
| `/api/v1/receipts/{key}/finalize/{id}/resume` | POST | Resume finalize |

## Viewing in Temporal UI

After running demos, view the workflow history at:
**http://localhost:8088**

You can see:
- Workflow execution timeline
- Activity inputs/outputs
- Compensation execution (if triggered)
- Error details and stack traces

## Troubleshooting

**Server not reachable:**
```bash
# Check if service is running
curl http://localhost:8080/actuator/health

# Check Temporal is running
curl http://localhost:8088/api/v1/namespaces
```

**Scripts not executable:**
```bash
chmod +x scripts/demo-*.sh
```

**JSON parsing errors:**
- Ensure Python 3 is installed
- The scripts use `python3 -m json.tool` for formatting
