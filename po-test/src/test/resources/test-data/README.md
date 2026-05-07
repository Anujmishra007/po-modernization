# PO Modernization - Test Data

This folder contains comprehensive test data for all 5 entry points of the PO Modernization E2E tests.

## Test Data Files

| File | Purpose | Records | Entry Points |
|------|---------|---------|--------------|
| `TD-CODELKUP.sql` | Status codes, hold codes, UOMs | 50+ | All |
| `TD-STORER.sql` | Storers + addresses + facilities | 15 storers, 12 facilities | All |
| `TD-LOCATION.sql` | Locations + putaway zones | 70+ | F6 Putaway, F4 XDock |
| `TD-SKU.sql` | SKUs + packs + SKUxLOC | 25+ | All |
| `TD-CLIENT.sql` | Client-specific plugin configs | 4 clients | F2, F3, F5 |
| `TD-PO-HAPPY.sql` | Happy path POs | 10 | F1, F2 |
| `TD-PO-ERROR.sql` | Error/edge case POs | 8+ | F1 |
| `TD-RCV-HAPPY.sql` | Happy path receipts | 5 | F3 |
| `TD-RCV-ERROR.sql` | Error/edge case receipts | 7 | F3, F10 |
| `TD-INVENTORY.sql` | LOTxLOCxID, holds, lots | 20+ | F3, F5, F6 |
| `TD-TASK.sql` | Putaway/pick tasks | 10+ | F6, RDT |
| `TD-JOB.sql` | Job configurations | 12 | Jobs entry point |
| `TD-TRIGGER.sql` | Trigger config, audit data | 10+ | Triggers entry point |
| `TD-RDT.sql` | Users, devices, sessions | 10+ | RDT entry point |
| `TD-ORDER.sql` | Sales orders for XDock | 10+ | F4 XDock |

## Data Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  TEST DATA SYNCHRONIZATION                                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  SOURCE (Master Copy)                     TARGET (Docker Loads)             │
│  ─────────────────────                    ─────────────────────             │
│  po-test/resources/test-data/             local-environment/seed-data/      │
│  ├── TD-CODELKUP.sql         ────sync───▶ ├── 02-TD-CODELKUP.sql           │
│  ├── TD-STORER.sql           ────sync───▶ ├── 03-TD-STORER.sql             │
│  ├── TD-LOCATION.sql         ────sync───▶ ├── 04-TD-LOCATION.sql           │
│  ├── TD-SKU.sql              ────sync───▶ ├── 05-TD-SKU.sql                │
│  ├── TD-CLIENT.sql           ────sync───▶ ├── 06-TD-CLIENT.sql             │
│  ├── TD-PO-HAPPY.sql         ────sync───▶ ├── 07-TD-PO-HAPPY.sql           │
│  ├── TD-PO-ERROR.sql         ────sync───▶ ├── 08-TD-PO-ERROR.sql           │
│  ├── TD-RCV-HAPPY.sql        ────sync───▶ ├── 09-TD-RCV-HAPPY.sql          │
│  ├── TD-RCV-ERROR.sql        ────sync───▶ ├── 10-TD-RCV-ERROR.sql          │
│  ├── TD-INVENTORY.sql        ────sync───▶ ├── 11-TD-INVENTORY.sql          │
│  ├── TD-TASK.sql             ────sync───▶ ├── 12-TD-TASK.sql               │
│  ├── TD-JOB.sql              ────sync───▶ ├── 13-TD-JOB.sql                │
│  ├── TD-TRIGGER.sql          ────sync───▶ ├── 14-TD-TRIGGER.sql            │
│  ├── TD-RDT.sql              ────sync───▶ ├── 15-TD-RDT.sql                │
│  └── TD-ORDER.sql            ────sync───▶ └── 16-TD-ORDER.sql              │
│                                                                              │
│  Run: ./scripts/sync-test-data.sh                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## How to Use

### 1. Sync Test Data (After Modifying TD-*.sql files)

```bash
./scripts/sync-test-data.sh
```

This copies all TD-*.sql files to `local-environment/seed-data/` with numeric prefixes for proper load order.

### 2. Start Docker Environment

```bash
./scripts/setup-local-env.sh --test
```

Docker automatically loads SQL files from `seed-data/` in alphabetical order:
- `00-schema.sql` → Database schema
- `02-TD-CODELKUP.sql` → Reference data
- ... (in order)
- `99-cleanup.sql` → Cleanup procedures

### 3. Run Tests

```bash
# Run all tests
./scripts/run-all-tests.sh

# Run specific flow
./scripts/run-flow-tests.sh F1
```

## Key Test Data Values

These values are pre-loaded and available for tests:

### Storers
| Key | Company | Facility | Country |
|-----|---------|----------|---------|
| `NIKE_KR` | Nike Korea | KR01 | KR |
| `HM_KR` | H&M Korea | KR02 | KR |
| `NIKE_IN` | Nike India | IN01 | IN |
| `TEST_STORER_001` | Test Storer | TEST01 | KR |
| `TEST_STORER_ERR` | Error Storer (Inactive) | TEST01 | KR |

### SKUs
| Key | Description | Storer |
|-----|-------------|--------|
| `NK-AIRMAX90-BLK` | Nike Air Max 90 Black | NIKE_KR |
| `NK-AF1-WHT` | Nike Air Force 1 White | NIKE_KR |
| `HM-BASIC-TEE-M` | H&M Basic Tee Medium | HM_KR |
| `TEST-SKU-001` | Test SKU Valid | TEST_STORER_001 |
| `TEST-SKU-ERR` | Test SKU Inactive | TEST_STORER_001 |

### Pre-loaded POs
| Key | Storer | Status | Purpose |
|-----|--------|--------|---------|
| `PO-HAPPY-001` | TEST_STORER_001 | 0 (Open) | Happy path |
| `PO-NIKE-001` | NIKE_KR | 0 (Open) | Plugin test |
| `PO-CLOSED-001` | TEST_STORER_001 | 9 (Closed) | Error test |
| `PO-CANCELLED-001` | TEST_STORER_001 | X (Cancelled) | Error test |

### Pre-loaded Receipts
| Key | PO | Status | Purpose |
|-----|-----|--------|---------|
| `RCV-FINALIZE-001` | PO-HAPPY-002 | 5 (In Progress) | Finalization test |
| `RCV-NIKE-001` | PO-NIKE-001 | 5 (In Progress) | Plugin test |
| `RCV-FINALIZED-001` | - | 9 (Finalized) | Error test |

## EDI Test Files

Located in `edi/` subfolder:

| File | Type | Purpose |
|------|------|---------|
| `EDI-850-SAMPLE-001.txt` | PO | Standard 3-line PO |
| `EDI-850-SAMPLE-002-NIKE.txt` | PO | Nike with lottables |
| `EDI-850-SAMPLE-003-HM.txt` | PO | H&M fast fashion |
| `EDI-850-SAMPLE-004-ERROR-*.txt` | PO | Missing segments |
| `EDI-850-SAMPLE-005-ERROR-*.txt` | PO | Malformed data |
| `EDI-856-SAMPLE-001.txt` | ASN | Nike ASN |
| `EDI-856-SAMPLE-002-HM.txt` | ASN | H&M ASN |

## Accessing Test Data in Karate

```gherkin
# Use pre-loaded data references
* def storer = preloaded.storers.NIKE_KR
* def sku = preloaded.skus.NIKE_AIRMAX
* def existingPO = preloaded.pos.HAPPY_001

# Generate new test data
* def request = testData.validPORequest()
* def nikeRequest = testData.nikePORequest()

# Query pre-loaded data
* def result = db.query("SELECT * FROM dbo.orders WHERE orderkey = 'PO-HAPPY-001'")
```

## Maintenance

**When modifying test data:**
1. Edit files in `po-test/resources/test-data/`
2. Run `./scripts/sync-test-data.sh`
3. Restart Docker to reload: `./scripts/setup-local-env.sh --clean && ./scripts/setup-local-env.sh --test`
4. Update `karate-config.js` if new constants needed
