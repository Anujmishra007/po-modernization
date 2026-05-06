# PO Modernization - Test Scripts

Shell scripts for test execution and environment management.

## Available Scripts

| Script | Purpose |
|--------|---------|
| `setup-local-env.sh` | Start/stop Docker environment |
| `wait-for-services.sh` | Wait for services to be healthy |
| `run-all-tests.sh` | Run all tests (3-layer approach) |
| `run-flow-tests.sh` | Run tests for specific flow |
| `load-test-data.sh` | Load/reset test data |
| `generate-reports.sh` | Generate Allure/coverage reports |

## Quick Reference

### Environment Setup

```bash
# Start test environment
./scripts/setup-local-env.sh --test

# Start full environment (with UIs)
./scripts/setup-local-env.sh --full

# Stop all containers
./scripts/setup-local-env.sh --down
```

### Run Tests

```bash
# Run all tests (Layer 1 + 2 + 3)
./scripts/run-all-tests.sh

# Skip specific layers
./scripts/run-all-tests.sh --skip-unit        # Skip Layer 2
./scripts/run-all-tests.sh --skip-workflow    # Skip Layer 3
./scripts/run-all-tests.sh --skip-e2e         # Skip Layer 1

# Run against Docker environment
./scripts/run-all-tests.sh --env docker
```

### Run Flow Tests

```bash
# Run specific flow
./scripts/run-flow-tests.sh F1              # PO Creation
./scripts/run-flow-tests.sh F2              # ASN Population
./scripts/run-flow-tests.sh F3              # Receipt Finalization
./scripts/run-flow-tests.sh F10             # Compensation

# Filter by type
./scripts/run-flow-tests.sh F1 --type Happy
./scripts/run-flow-tests.sh F3 --type Compensation

# Filter by priority
./scripts/run-flow-tests.sh F2 --priority P1
```

### Test Data

```bash
# Load all test data
./scripts/load-test-data.sh

# Reset and reload
./scripts/load-test-data.sh --cleanup

# Verify counts
./scripts/load-test-data.sh --verify

# Load from po-test module
./scripts/load-test-data.sh --from-po-test
```

### Reports

```bash
# Generate and open Allure report
./scripts/generate-reports.sh --serve

# Open Karate HTML report
./scripts/generate-reports.sh --karate

# Generate coverage report
./scripts/generate-reports.sh --coverage

# Generate all reports
./scripts/generate-reports.sh --all
```

## 3-Layer Test Execution

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 2: Unit + Integration (JUnit/Spring)                │
│  ├── po-domain unit tests                                   │
│  ├── po-service unit tests                                  │
│  ├── po-activity unit tests                                 │
│  └── po-service integration tests                           │
├─────────────────────────────────────────────────────────────┤
│  Layer 3: Workflow (Temporal SDK)                           │
│  ├── PopulatePOWorkflowTest                                 │
│  ├── FinalizeReceiptWorkflowTest                            │
│  └── CompensationTest                                       │
├─────────────────────────────────────────────────────────────┤
│  Layer 1: E2E (Karate)                                      │
│  ├── F1: PO Creation                                        │
│  ├── F2: ASN Population                                     │
│  ├── F3: Receipt Finalization                               │
│  ├── F4-F9: Other flows                                     │
│  └── F10: Compensation                                      │
└─────────────────────────────────────────────────────────────┘
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `KARATE_ENV` | local | Karate environment (local/docker/staging) |
| `KARATE_THREADS` | 5 | Parallel test threads |
| `DB_HOST` | localhost | Database host |
| `DB_PORT` | 5433 | Database port |
| `DB_NAME` | po_test | Database name |
| `SKIP_UNIT` | false | Skip Layer 2 tests |
| `SKIP_WORKFLOW` | false | Skip Layer 3 tests |
| `SKIP_E2E` | false | Skip Layer 1 tests |

## CI/CD Integration

```bash
# Run in CI (no environment startup needed if services exist)
./scripts/run-all-tests.sh --no-env --env docker

# Run specific flow in CI
./scripts/run-flow-tests.sh F1 --env docker --threads 3
```
