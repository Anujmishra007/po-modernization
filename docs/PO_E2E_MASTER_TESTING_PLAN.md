# PO Modernization - E2E Master Testing Plan

> **Status:** Production-Ready Testing Framework
> **Version:** 2.1 (Pragmatic 3-Layer Approach)
> **Date:** 2026-05-06
> **Target:** 100% Legacy SP Migration to Microservices
> **Coverage:** 260+ test cases | 10 flows | 5 entry points | 39+ SPs | 21 plugins
> **Testing Approach:** 3-Layer (Karate + JUnit/Spring + Temporal SDK)

---

## Quick Reference Dashboard

### Migration Coverage Summary

```
┌─────────────────────────────────────────────────────────────┐
│  MIGRATION VALIDATION DASHBOARD                             │
├─────────────────────────────────────────────────────────────┤
│  Stored Procedures:    39+ SPs      → 100% mapped          │
│  Triggers:             15 triggers  → 100% mapped          │
│  Jobs:                 10 jobs      → 100% mapped          │
│  Plugins:              21 plugins   → 100% mapped          │
│  Error Codes:          260 codes    → 100% mapped          │
├─────────────────────────────────────────────────────────────┤
│  Test Cases:           260 total                            │
│  ├── Happy Path:       52 tests (20%)  ✅                   │
│  ├── Unhappy Path:     78 tests (30%)  ❌                   │
│  ├── Edge Cases:       52 tests (20%)  🔸                   │
│  ├── Error Cases:      52 tests (20%)  ⚠️                   │
│  └── Compensation:     26 tests (10%)  🔄                   │
└─────────────────────────────────────────────────────────────┘
```

### 10 PO Flows Summary

| Flow | Description | Entry Points | Test Cases | Priority | Legacy SPs |
|------|-------------|--------------|------------|----------|------------|
| F1 | PO Creation | API, EDI, Job | 25 | CRITICAL | nspg_AddPO, nsp_GenericInbound_PO |
| F2 | ASN Population | API, Trigger, RDT | 30 | CRITICAL | WM.lsp_ASN_PopulatePOs_Wrapper |
| F3 | Receipt Finalization | API, Job, RDT | 35 | CRITICAL | WM.lsp_FinalizeReceipt_Wrapper |
| F4 | Cross-Dock Allocation | API, Job | 20 | HIGH | WM.lsp_XDockAllocation_Wrapper |
| F5 | Lottable Processing | Internal | 20 | HIGH | ispLottableRule_Wrapper |
| F6 | Putaway Release | API, Job | 20 | HIGH | WM.lsp_ASNReleasePATask_Wrapper |
| F7 | Trade Return | API | 15 | MEDIUM | WM.lsp_ASN_PopulateSOs_Wrapper |
| F8 | PO Cancellation | API, Job | 15 | MEDIUM | nspg_CancelPO |
| F9 | Archival/Purge | Job | 10 | LOW | nsp_ArchivePO |
| F10 | Compensation/Saga | All | 30 | CRITICAL | All (rollback scenarios) |
| **TOTAL** | | | **260** | | **39+ SPs** |

---

## 5 Entry Points Coverage

| Entry Point | Description | Flows | Test Cases | Modern Implementation |
|-------------|-------------|-------|------------|----------------------|
| **API Gateway** | REST API calls | F1-F9 | 100 | Spring Controllers |
| **EDI Interface** | 850/856 messages | F1, F2 | 30 | EDIInboundService |
| **DB Triggers** | Event-driven | F2, F8 | 25 | JPA EntityListeners |
| **SQL Jobs** | Scheduled batch | F1, F3, F6, F9 | 40 | @Scheduled Jobs |
| **RDT API** | Handheld devices | F1, F2, F3 | 65 | RDT Controllers |

---

## Test Automation Stack

### Pragmatic 3-Layer Approach (Recommended)

Rather than maintaining 5+ different testing frameworks, we recommend a focused **3-layer approach** that covers 90%+ of testing needs with lower maintenance overhead:

```
┌─────────────────────────────────────────────────────────────┐
│  ESSENTIAL LAYERS (Must Have)                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  LAYER 1: Karate DSL (API + DB + Performance)               │
│  ├── REST API contract testing                              │
│  ├── Database validation (via karate-jdbc)                  │
│  ├── Performance testing (Gatling integration)              │
│  ├── Data-driven testing (CSV, JSON, DB sources)            │
│  └── Dual-write comparison (legacy vs modern)               │
│                                                             │
│  LAYER 2: JUnit 5 + Spring Boot Test (Business Logic)       │
│  ├── Service layer unit tests                               │
│  ├── @Transactional boundary testing                        │
│  ├── Mock external dependencies                             │
│  └── Plugin execution testing                               │
│                                                             │
│  LAYER 3: Temporal TestWorkflowEnvironment (Workflows)      │
│  ├── Saga pattern verification                              │
│  ├── Activity execution testing                             │
│  ├── Compensation flow validation                           │
│  ├── Retry logic verification                               │
│  └── Workflow state transitions                             │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│  OPTIONAL LAYERS (If Resources Allow)                       │
├─────────────────────────────────────────────────────────────┤
│  Database Testing  → tSQLt (SQL Server) during dual-write   │
│                   → Only if complex SP validation needed    │
│  Chaos Testing     → Toxiproxy (fault injection)            │
└─────────────────────────────────────────────────────────────┘
```

### Why 3 Layers Instead of 5?

| Metric | 5-Layer Approach | 3-Layer Approach |
|--------|------------------|------------------|
| Frameworks to learn | 5 (Karate, TestNG, pgTAP, Temporal, Gatling) | 3 (Karate, JUnit, Temporal) |
| CI/CD pipeline stages | 5+ | 3 |
| Maintenance burden | High | Medium |
| Test coverage | ~95% | ~90% |
| Team ramp-up time | 4-6 weeks | 2-3 weeks |
| Framework overlap | Significant | Minimal |

### Layer Coverage Matrix

| Testing Need | Layer 1 (Karate) | Layer 2 (JUnit) | Layer 3 (Temporal) |
|--------------|------------------|-----------------|-------------------|
| API contracts | ✅ Primary | - | - |
| HTTP status codes | ✅ Primary | - | - |
| Response validation | ✅ Primary | - | - |
| Service business logic | ✅ Via API | ✅ Primary | - |
| Transaction boundaries | - | ✅ Primary | - |
| Database validation | ✅ karate-jdbc | ✅ @DataJpaTest | - |
| Saga pattern | ✅ Via API | - | ✅ Primary |
| Compensation flows | ✅ Via API | - | ✅ Primary |
| Retry logic | - | - | ✅ Primary |
| Performance/Load | ✅ Gatling | - | - |
| Plugin execution | ✅ Via API | ✅ Primary | - |

### Technology Stack Summary

```
┌─────────────────────────────────────────────────────────────┐
│  RECOMMENDED TECHNOLOGY STACK                               │
├─────────────────────────────────────────────────────────────┤
│  API/E2E Testing  → Karate DSL (BDD, parallel, data-driven) │
│  Unit Testing     → JUnit 5 + Mockito                       │
│  Integration      → Spring Boot Test + TestContainers       │
│  Workflow Testing → Temporal TestWorkflowEnvironment        │
│  Database         → H2 (local) / PostgreSQL (CI)            │
│  Performance      → Karate + Gatling (integrated)           │
│  Reporting        → Allure + Karate HTML Reports            │
│  Tracking         → TestRail / Zephyr (optional)            │
│  CI/CD            → GitHub Actions                          │
│  Local Env        → Docker Compose                          │
└─────────────────────────────────────────────────────────────┘
```

### Important Notes

**Database Testing Clarification:**
- Legacy system uses **SQL Server** (T-SQL)
- New microservices use **PostgreSQL**
- pgTAP is PostgreSQL-only - use **tSQLt** for SQL Server SP validation if needed
- During dual-write phase, validate both outputs via Karate API tests

---

## Karate DSL - Extended Capabilities

Karate is more than just an API testing tool. It can handle ~80% of testing needs:

```
┌─────────────────────────────────────────────────────────────┐
│  KARATE CAPABILITIES (Beyond API Testing)                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  REST API Testing        ✅ Primary purpose                 │
│  ├── HTTP methods (GET, POST, PUT, DELETE)                  │
│  ├── Response validation (JSON, XML)                        │
│  ├── Schema validation (JSON Schema)                        │
│  └── Header/Cookie handling                                 │
│                                                             │
│  Database Validation     ✅ via karate-jdbc                 │
│  ├── Direct SQL queries                                     │
│  ├── Pre/post condition checks                              │
│  ├── Data cleanup                                           │
│  └── Dual-write comparison                                  │
│                                                             │
│  Performance Testing     ✅ Built-in Gatling integration    │
│  ├── Load testing (reuse functional tests)                  │
│  ├── Stress testing                                         │
│  ├── Response time assertions                               │
│  └── Throughput metrics                                     │
│                                                             │
│  Data-Driven Testing     ✅ Native support                  │
│  ├── CSV data sources                                       │
│  ├── JSON data sources                                      │
│  ├── Database as data source                                │
│  └── Dynamic scenario generation                            │
│                                                             │
│  Async/Parallel          ✅ Native support                  │
│  ├── Parallel test execution                                │
│  ├── Async response handling                                │
│  ├── Retry mechanisms                                       │
│  └── Polling patterns                                       │
│                                                             │
│  Mock Servers            ✅ via karate-netty                │
│  ├── Mock external services                                 │
│  ├── Simulate failures                                      │
│  └── Contract testing                                       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Karate + JDBC for Database Validation

```gherkin
# Example: Verify dual-write consistency
Feature: Dual-Write Validation

Background:
  * def dbConfig = { url: 'jdbc:postgresql://localhost:5433/po_test', ... }

Scenario: PO Creation writes to both legacy and modern
  # Call modern API
  Given url baseUrl + '/api/v1/po'
  And request { storerKey: 'NIKE', ... }
  When method post
  Then status 201

  # Verify modern database
  * def modernResult = db.query(dbConfig, "SELECT * FROM orders WHERE orderkey = '" + response.poKey + "'")
  * match modernResult[0].storerkey == 'NIKE'

  # Verify legacy database (if dual-write enabled)
  * def legacyResult = db.query(legacyDbConfig, "SELECT * FROM ORDERS WHERE ORDERKEY = '" + response.poKey + "'")
  * match legacyResult[0].STORERKEY == 'NIKE'

  # Compare outputs
  * match modernResult[0].storerkey == legacyResult[0].STORERKEY
```

### Karate for Performance Testing

```gherkin
# Same functional test, reused for performance
@performance
Feature: PO Creation Performance

Scenario: Load test PO creation
  Given url baseUrl + '/api/v1/po'
  And request read('test-data/po-create.json')
  When method post
  Then status 201
  And assert responseTime < 500
```

```java
// Gatling simulation using Karate
public class POPerformanceTest extends KarateGatlingTest {
    @Override
    public Simulation configure() {
        return karateProtocol()
            .feature("classpath:features/po-creation.feature")
            .tags("@performance")
            .constantUsersPerSec(50).during(Duration.ofMinutes(5))
            .assertions(
                global().responseTime().percentile3().lt(500),
                global().successfulRequests().percent().gt(99.0)
            );
    }
}
```

### Temporal TestWorkflowEnvironment for Saga Testing

The Temporal SDK provides `TestWorkflowEnvironment` for testing workflows without a running Temporal server:

```java
// Example: Testing FinalizeReceiptWorkflow compensation
public class FinalizeReceiptWorkflowTest {

    @RegisterExtension
    public static final TestWorkflowExtension testWorkflow =
        TestWorkflowExtension.newBuilder()
            .registerWorkflowImplementationTypes(FinalizeReceiptWorkflowImpl.class)
            .setActivityImplementations(
                new ValidationActivityImpl(),
                new ReceiptStatusActivityImpl(),
                new InventoryPostingActivityImpl(),
                new InventoryHoldActivityImpl(),
                new POQuantityActivityImpl(),
                new PutawayReleaseActivityImpl()
            )
            .build();

    @Test
    void testCompensationOnInventoryPostingFailure() {
        // Arrange: Mock activity to fail
        InventoryPostingActivity mockActivity = mock(InventoryPostingActivity.class);
        when(mockActivity.postInventory(any()))
            .thenThrow(new InventoryPostingException("Location full"));
        testWorkflow.setActivityImplementation(mockActivity);

        // Act: Start workflow
        FinalizeReceiptWorkflow workflow = testWorkflow.newWorkflowStub(FinalizeReceiptWorkflow.class);
        FinalizeResult result = workflow.finalize(new FinalizeRequest("RCV-001"));

        // Assert: Compensation executed
        assertThat(result.getStatus()).isEqualTo("COMPENSATED");
        assertThat(result.getCompensatedSteps())
            .containsExactly("REVERT_STATUS", "NOTIFY_FAILURE");

        // Verify status reverted in DB
        verify(receiptStatusActivity).revertStatus("RCV-001", 0); // Back to status 0
    }

    @Test
    void testRetryOnTransientFailure() {
        // Arrange: Fail twice, succeed on third attempt
        AtomicInteger attempts = new AtomicInteger(0);
        InventoryPostingActivity mockActivity = mock(InventoryPostingActivity.class);
        when(mockActivity.postInventory(any())).thenAnswer(inv -> {
            if (attempts.incrementAndGet() < 3) {
                throw new TransientException("DB timeout");
            }
            return new PostingResult("SUCCESS");
        });
        testWorkflow.setActivityImplementation(mockActivity);

        // Act
        FinalizeReceiptWorkflow workflow = testWorkflow.newWorkflowStub(FinalizeReceiptWorkflow.class);
        FinalizeResult result = workflow.finalize(new FinalizeRequest("RCV-001"));

        // Assert: Succeeded after retries
        assertThat(result.getStatus()).isEqualTo("FINALIZED");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void testWorkflowTimeout() {
        // Skip time to trigger timeout
        testWorkflow.getTestEnvironment().sleep(Duration.ofMinutes(30));

        // Assert: Workflow compensated due to timeout
        // ...
    }
}
```

### When to Use Each Layer

| Scenario | Use Layer |
|----------|-----------|
| Testing API contracts, HTTP responses | **Layer 1: Karate** |
| Testing API with database assertions | **Layer 1: Karate + JDBC** |
| Comparing legacy vs modern output | **Layer 1: Karate + JDBC** |
| Testing service business logic in isolation | **Layer 2: JUnit + Spring** |
| Testing @Transactional rollback | **Layer 2: JUnit + Spring** |
| Testing plugin execution logic | **Layer 2: JUnit + Mockito** |
| Testing saga compensation flows | **Layer 3: Temporal SDK** |
| Testing workflow retries | **Layer 3: Temporal SDK** |
| Testing activity timeouts | **Layer 3: Temporal SDK** |
| Load/stress testing | **Layer 1: Karate + Gatling** |
| Chaos testing (fault injection) | **Toxiproxy + Karate** |

---

## Section 1: Flow-Specific Test Cases

### Flow 1: PO Creation (25 TCs)

**Legacy SPs:** `nspg_AddPO`, `nsp_GenericInbound_PO`, `tr_orders_insert`

| TC ID | Test Case | Type | Entry | Error Code | Priority |
|-------|-----------|------|-------|------------|----------|
| F1-TC01 | Create single-line PO via API | ✅ Happy | API | - | P1 |
| F1-TC02 | Create multi-line PO (50 lines) | ✅ Happy | API | - | P1 |
| F1-TC03 | Create PO via EDI 850 | ✅ Happy | EDI | - | P1 |
| F1-TC04 | Create PO via batch job | ✅ Happy | Job | - | P1 |
| F1-TC05 | Duplicate PO key | ❌ Unhappy | API | VAL_003 (69102) | P1 |
| F1-TC06 | Missing required field (storerKey) | ❌ Unhappy | API | VAL_001 (69100) | P1 |
| F1-TC07 | Invalid SKU in PO line | ❌ Unhappy | API | PO_013 (68813) | P1 |
| F1-TC08 | Invalid supplier | ❌ Unhappy | API | PO_012 (68812) | P2 |
| F1-TC09 | Max lines boundary (500+) | 🔸 Edge | API | - | P2 |
| F1-TC10 | Unicode characters in address | 🔸 Edge | API | - | P2 |
| F1-TC11 | Zero quantity line | 🔸 Edge | API | VAL_008 (69107) | P2 |
| F1-TC12 | Negative quantity | 🔸 Edge | API | VAL_009 (69108) | P2 |
| F1-TC13 | Past expected date | 🔸 Edge | API | VAL_006 (69105) | P3 |
| F1-TC14 | DB connection timeout | ⚠️ Error | API | INT_022 (69022) | P2 |
| F1-TC15 | Concurrent PO creation (race) | ⚠️ Error | API | INT_021 (69021) | P2 |
| F1-TC16 | Malformed EDI 850 format | ⚠️ Error | EDI | INT_011 (69011) | P2 |
| F1-TC17 | EDI missing mandatory segment | ⚠️ Error | EDI | INT_011 (69011) | P2 |
| F1-TC18 | Trigger fires on PO insert | ✅ Happy | Trigger | - | P1 |
| F1-TC19 | Trigger handles duplicate | ⚠️ Error | Trigger | TRG_010 (69710) | P2 |
| F1-TC20 | Line insert fails at line 25/50 | 🔄 Comp | API | - | P1 |
| F1-TC21 | Header created, details fail | 🔄 Comp | API | - | P1 |
| F1-TC22 | Partial rollback verification | 🔄 Comp | API | - | P1 |
| F1-TC23 | Job retry on failure | 🔄 Comp | Job | JOB_001 (69600) | P2 |
| F1-TC24 | Idempotency check | 🔄 Comp | API | - | P1 |
| F1-TC25 | RDT PO creation | ✅ Happy | RDT | - | P2 |

### Flow 2: ASN Population (30 TCs)

**Legacy SPs:** `WM.lsp_ASN_PopulatePOs_Wrapper`, `WM.lsp_ASN_PopulatePODs_Wrapper`, `ispPOLineSplit`

| TC ID | Test Case | Type | Entry | Error Code | Priority |
|-------|-----------|------|-------|------------|----------|
| F2-TC01 | Populate single PO (100%) | ✅ Happy | API | - | P1 |
| F2-TC02 | Populate multiple POs (batch) | ✅ Happy | API | - | P1 |
| F2-TC03 | Populate with line splits | ✅ Happy | API | - | P1 |
| F2-TC04 | Populate with lottable mapping | ✅ Happy | API | - | P1 |
| F2-TC05 | Populate via RDT | ✅ Happy | RDT | - | P1 |
| F2-TC06 | Auto-populate via job | ✅ Happy | Job | - | P1 |
| F2-TC07 | PO already populated | ❌ Unhappy | API | PO_022 (68822) | P1 |
| F2-TC08 | PO not found | ❌ Unhappy | API | PO_001 (68800) | P1 |
| F2-TC09 | PO already closed | ❌ Unhappy | API | PO_002 (68801) | P1 |
| F2-TC10 | PO cancelled | ❌ Unhappy | API | PO_003 (68802) | P1 |
| F2-TC11 | Storer mismatch | ❌ Unhappy | API | PO_010 (68810) | P1 |
| F2-TC12 | Facility mismatch | ❌ Unhappy | API | PO_011 (68811) | P1 |
| F2-TC13 | Extended validation failure | ❌ Unhappy | API | VAL_020 (69120) | P1 |
| F2-TC14 | Qty mismatch (ASN > PO by 20%) | 🔸 Edge | API | PO_020 (68820) | P2 |
| F2-TC15 | Qty within tolerance (5%) | 🔸 Edge | API | - | P2 |
| F2-TC16 | Multi-client field mapping | 🔸 Edge | API | - | P2 |
| F2-TC17 | 1000+ line PO | 🔸 Edge | API | - | P2 |
| F2-TC18 | Pre-populate plugin execution | ✅ Happy | API | - | P1 |
| F2-TC19 | Pre-populate plugin timeout | ⚠️ Error | API | PLG_040 (69540) | P2 |
| F2-TC20 | Pre-populate plugin failure | ⚠️ Error | API | PLG_041 (69541) | P2 |
| F2-TC21 | Mapping failure | ⚠️ Error | API | PO_018 (68818) | P2 |
| F2-TC22 | Legacy sync timeout | ⚠️ Error | API | INT_001 (69000) | P1 |
| F2-TC23 | Kafka unavailable | ⚠️ Error | API | INT_002 (69001) | P2 |
| F2-TC24 | Rollback on reservation failure | 🔄 Comp | API | - | P1 |
| F2-TC25 | Rollback on legacy sync failure | 🔄 Comp | API | - | P1 |
| F2-TC26 | Workflow timeout compensation | 🔄 Comp | API | INT_003 (69002) | P1 |
| F2-TC27 | Workflow cancel compensation | 🔄 Comp | API | - | P1 |
| F2-TC28 | Idempotent retry | 🔄 Comp | API | - | P1 |
| F2-TC29 | Concurrent populate same PO | ⚠️ Error | API | INT_021 (69021) | P1 |
| F2-TC30 | Trigger-based auto-populate | ✅ Happy | Trigger | - | P2 |

### Flow 3: Receipt Finalization (35 TCs)

**Legacy SPs:** `WM.lsp_FinalizeReceipt_Wrapper`, `ispFinalizeReceipt`, `nspInventoryPosting`, `nspInventoryHoldWrapper`

| TC ID | Test Case | Type | Entry | Error Code | Priority |
|-------|-----------|------|-------|------------|----------|
| F3-TC01 | Finalize single receipt (100%) | ✅ Happy | API | - | P1 |
| F3-TC02 | Finalize partial receipt (80%) | ✅ Happy | API | - | P1 |
| F3-TC03 | Finalize batch receipts | ✅ Happy | API | - | P1 |
| F3-TC04 | Finalize via RDT | ✅ Happy | RDT | - | P1 |
| F3-TC05 | Auto-finalize via job | ✅ Happy | Job | - | P1 |
| F3-TC06 | Finalize with inventory posting | ✅ Happy | API | - | P1 |
| F3-TC07 | Finalize with hold application | ✅ Happy | API | - | P1 |
| F3-TC08 | Finalize with putaway release | ✅ Happy | API | - | P1 |
| F3-TC09 | Finalize with PO auto-close | ✅ Happy | API | - | P1 |
| F3-TC10 | Receipt not found | ❌ Unhappy | API | RCV_001 (68900) | P1 |
| F3-TC11 | Receipt already finalized | ❌ Unhappy | API | RCV_002 (68901) | P1 |
| F3-TC12 | Receipt invalid status | ❌ Unhappy | API | RCV_003 (68902) | P1 |
| F3-TC13 | Over-receipt (120%) | ❌ Unhappy | API | RCV_011 (68911) | P2 |
| F3-TC14 | Under-receipt blocked | ❌ Unhappy | API | RCV_012 (68912) | P2 |
| F3-TC15 | Missing lottable | ❌ Unhappy | API | LOT_003 (69402) | P2 |
| F3-TC16 | Lottable defaulting | 🔸 Edge | API | - | P2 |
| F3-TC17 | Cross-dock auto-fulfill | 🔸 Edge | API | - | P2 |
| F3-TC18 | Multi-lot receipt | 🔸 Edge | API | - | P2 |
| F3-TC19 | 500+ line receipt | 🔸 Edge | API | - | P3 |
| F3-TC20 | Pre-finalize plugin (Nike) | ✅ Happy | API | - | P1 |
| F3-TC21 | Pre-finalize plugin (H&M) | ✅ Happy | API | - | P1 |
| F3-TC22 | Pre-finalize plugin (Adidas) | ✅ Happy | API | - | P1 |
| F3-TC23 | Pre-finalize plugin failure | ⚠️ Error | API | PLG_010 (69510) | P1 |
| F3-TC24 | Post-finalize plugin execution | ✅ Happy | API | - | P1 |
| F3-TC25 | Post-finalize failure (best effort) | 🔸 Edge | API | PLG_020 (69520) | P2 |
| F3-TC26 | Inventory posting failure | ⚠️ Error | API | RCV_021 (68921) | P1 |
| F3-TC27 | Hold application failure | ⚠️ Error | API | RCV_023 (68923) | P2 |
| F3-TC28 | Putaway release failure | ⚠️ Error | API | RCV_024 (68924) | P2 |
| F3-TC29 | Location full | ⚠️ Error | API | INV_011 (68711) | P2 |
| F3-TC30 | Rollback inventory on failure | 🔄 Comp | API | - | P1 |
| F3-TC31 | Rollback status on failure | 🔄 Comp | API | - | P1 |
| F3-TC32 | Rollback holds on failure | 🔄 Comp | API | - | P1 |
| F3-TC33 | Rollback tasks on failure | 🔄 Comp | API | - | P1 |
| F3-TC34 | Workflow pause/resume | 🔄 Comp | API | - | P2 |
| F3-TC35 | Idempotent finalize | 🔄 Comp | API | - | P1 |

### Flow 4: Cross-Dock Allocation (20 TCs)

**Legacy SPs:** `WM.lsp_XDockAllocation_Wrapper`, `WM.lsp_FlowThruAllocate_Wrapper`, `nsp_xdockorderprocessing`

| TC ID | Test Case | Type | Entry | Error Code | Priority |
|-------|-----------|------|-------|------------|----------|
| F4-TC01 | XDock allocation success | ✅ Happy | API | - | P1 |
| F4-TC02 | Flow-thru allocation | ✅ Happy | API | - | P1 |
| F4-TC03 | Auto XDock via job | ✅ Happy | Job | - | P1 |
| F4-TC04 | XDock SO creation | ✅ Happy | API | - | P1 |
| F4-TC05 | Order not found | ❌ Unhappy | API | XD_001 (69300) | P1 |
| F4-TC06 | Allocation failed | ❌ Unhappy | API | XD_002 (69301) | P1 |
| F4-TC07 | Insufficient inventory | ❌ Unhappy | API | XD_003 (69302) | P1 |
| F4-TC08 | Processing failed | ❌ Unhappy | API | XD_004 (69303) | P2 |
| F4-TC09 | Partial allocation | 🔸 Edge | API | - | P2 |
| F4-TC10 | Multi-order allocation | 🔸 Edge | API | - | P2 |
| F4-TC11 | Priority-based allocation | 🔸 Edge | API | - | P2 |
| F4-TC12 | SO creation failure | ⚠️ Error | API | XD_005 (69304) | P2 |
| F4-TC13 | Auto allocate failure | ⚠️ Error | Job | XD_006 (69305) | P2 |
| F4-TC14 | Flow-thru routing failure | ⚠️ Error | API | XD_011 (69311) | P2 |
| F4-TC15 | Allocation build failure | ⚠️ Error | API | XD_020 (69320) | P2 |
| F4-TC16 | Allocation release failure | ⚠️ Error | API | XD_021 (69321) | P2 |
| F4-TC17 | Rollback allocation | 🔄 Comp | API | - | P1 |
| F4-TC18 | Rollback SO creation | 🔄 Comp | API | - | P1 |
| F4-TC19 | Post-allocation plugin | ✅ Happy | API | - | P2 |
| F4-TC20 | Post-allocation plugin failure | ⚠️ Error | API | PLG_060 (69560) | P2 |

### Flow 5: Lottable Processing (20 TCs)

**Legacy SPs:** `ispLottableRule_Wrapper`, `ispDefLot1FrRcptDtl`, `ispDefLot2FrRcptDtl`, `ispGenLot2BySuppLot`

| TC ID | Test Case | Type | Entry | Error Code | Priority |
|-------|-----------|------|-------|------------|----------|
| F5-TC01 | Standard lottable rule | ✅ Happy | Internal | - | P1 |
| F5-TC02 | Lot1 from receipt date | ✅ Happy | Internal | - | P1 |
| F5-TC03 | Lot2 from supplier lot | ✅ Happy | Internal | - | P1 |
| F5-TC04 | Drools rule execution | ✅ Happy | Internal | - | P1 |
| F5-TC05 | Thailand lottable rule | ✅ Happy | Internal | - | P1 |
| F5-TC06 | Taiwan lottable rule | ✅ Happy | Internal | - | P2 |
| F5-TC07 | Nike CN lottable rule | ✅ Happy | Internal | - | P2 |
| F5-TC08 | Rule not found | ❌ Unhappy | Internal | LOT_001 (69400) | P1 |
| F5-TC09 | Rule execution failed | ❌ Unhappy | Internal | LOT_002 (69401) | P1 |
| F5-TC10 | Mapping failed | ❌ Unhappy | Internal | LOT_003 (69402) | P1 |
| F5-TC11 | Validation failed | ❌ Unhappy | Internal | LOT_004 (69403) | P2 |
| F5-TC12 | Missing lottable source | 🔸 Edge | Internal | - | P2 |
| F5-TC13 | Empty lottable value | 🔸 Edge | Internal | - | P2 |
| F5-TC14 | Lottable overflow | 🔸 Edge | Internal | - | P3 |
| F5-TC15 | Lot1 generation failure | ⚠️ Error | Internal | LOT_010 (69410) | P2 |
| F5-TC16 | Lot2 generation failure | ⚠️ Error | Internal | LOT_011 (69411) | P2 |
| F5-TC17 | Supplier lot failure | ⚠️ Error | Internal | LOT_020 (69420) | P2 |
| F5-TC18 | Drools rule failure | ⚠️ Error | Internal | LOT_030 (69430) | P2 |
| F5-TC19 | Mondelez rule | ✅ Happy | Internal | - | P3 |
| F5-TC20 | Unilever rule | ✅ Happy | Internal | - | P3 |

### Flow 6: Putaway Release (20 TCs)

**Legacy SPs:** `WM.lsp_ASNReleasePATask_Wrapper`, `nspPASTD`, `ispPARL01-08`

| TC ID | Test Case | Type | Entry | Error Code | Priority |
|-------|-----------|------|-------|------------|----------|
| F6-TC01 | Create putaway task | ✅ Happy | API | - | P1 |
| F6-TC02 | Release putaway task | ✅ Happy | API | - | P1 |
| F6-TC03 | Batch putaway release | ✅ Happy | Job | - | P1 |
| F6-TC04 | Auto putaway release | ✅ Happy | Plugin | - | P1 |
| F6-TC05 | Complete putaway (RDT) | ✅ Happy | RDT | - | P1 |
| F6-TC06 | Strategy not found | ❌ Unhappy | API | PA_001 (69200) | P1 |
| F6-TC07 | Location not found | ❌ Unhappy | API | PA_002 (69201) | P1 |
| F6-TC08 | Location full | ❌ Unhappy | API | PA_003 (69202) | P1 |
| F6-TC09 | Restriction violated | ❌ Unhappy | API | PA_004 (69203) | P2 |
| F6-TC10 | Multi-zone putaway | 🔸 Edge | API | - | P2 |
| F6-TC11 | Weight-based location | 🔸 Edge | API | - | P2 |
| F6-TC12 | Task creation failure | ⚠️ Error | API | PA_005 (69204) | P2 |
| F6-TC13 | Task release failure | ⚠️ Error | API | PA_006 (69205) | P2 |
| F6-TC14 | Task assignment failure | ⚠️ Error | API | PA_007 (69206) | P2 |
| F6-TC15 | Batch PA standard | ✅ Happy | Job | - | P2 |
| F6-TC16 | Batch PA Nike CRW | ✅ Happy | Job | - | P2 |
| F6-TC17 | PA Release ULM | ✅ Happy | Job | - | P3 |
| F6-TC18 | Auto PA release failure | ⚠️ Error | Job | PA_030 (69230) | P2 |
| F6-TC19 | Rollback task creation | 🔄 Comp | API | - | P1 |
| F6-TC20 | Rollback task release | 🔄 Comp | API | - | P1 |

### Flow 7-10: Summary

| Flow | Total TCs | Happy | Unhappy | Edge | Error | Comp |
|------|-----------|-------|---------|------|-------|------|
| F7: Trade Return | 15 | 4 | 4 | 3 | 2 | 2 |
| F8: PO Cancellation | 15 | 3 | 4 | 3 | 3 | 2 |
| F9: Archival/Purge | 10 | 3 | 2 | 2 | 2 | 1 |
| F10: Compensation | 30 | 5 | 5 | 5 | 10 | 5 |

---

## Section 2: SP → Microservice → Test Case Mapping

### Core Population SPs

| SP Name | Error Codes | Modern Service | Activity | Test Cases |
|---------|-------------|----------------|----------|------------|
| `WM.lsp_ASN_PopulatePOs_Wrapper` | 68800-68822 | `PopulationService` | `PopulatePOWorkflow` | F2-TC01 to F2-TC30 |
| `WM.lsp_ASN_PopulatePODs_Wrapper` | 68803-68815 | `PopulationService` | `MappingActivity` | F2-TC03, F2-TC04 |
| `ispPOLineSplit` | 68908 | `LineSplitService` | `MappingActivity` | F2-TC03 |
| `ispLottableRule_Wrapper` | 69400-69430 | `LottableRulesService` | `MappingActivity` | F5-TC01 to F5-TC20 |
| `isp_ASN_ExtendedValidation` | 69120 | `ValidationActivityImpl` | `ValidationActivity` | F2-TC13 |

### Core Finalization SPs

| SP Name | Error Codes | Modern Service | Activity | Test Cases |
|---------|-------------|----------------|----------|------------|
| `WM.lsp_FinalizeReceipt_Wrapper` | 68900-68928 | `ReceiptFinalizationService` | `FinalizeReceiptWorkflow` | F3-TC01 to F3-TC35 |
| `ispFinalizeReceipt` | 68900-68928 | `FinalizeReceiptService` | `ReceiptStatusActivity` | F3-TC01 to F3-TC13 |
| `nspInventoryPosting` | 68706, 68921 | `InventoryPostingService` | `InventoryPostingActivity` | F3-TC06, F3-TC26 |
| `nspInventoryHoldWrapper` | 68707-68709 | `InventoryHoldService` | `InventoryHoldActivity` | F3-TC07, F3-TC27 |
| `WM.lsp_ASNReleasePATask_Wrapper` | 69205 | `PutawayTaskService` | `PutawayReleaseActivity` | F3-TC08, F6-TC01 to F6-TC20 |

### Pre-Finalize Plugins (ispPRREC*)

| SP Name | Client | Error Code | Modern Plugin | Test Cases |
|---------|--------|------------|---------------|------------|
| `ispPRREC01` | H&M | 69511 | `HMPreFinalizePlugin` | F3-TC21 |
| `ispPRREC02` | Nike | 69512 | `NikePreFinalizePlugin` | F3-TC20 |
| `ispPRREC03` | Adidas | 69513 | `AdidasPreFinalizePlugin` | F3-TC22 |
| `ispPRREC04` | Columbia | 69514 | `ColumbiaPreFinalizePlugin` | F3-TC24 |
| `ispPRREC05` | Unilever | 69515 | `UnileverPreFinalizePlugin` | - |
| `ispPRREC06` | NewLook | 69516 | `NewLookPreFinalizePlugin` | - |
| `ispPRREC12` | India | 69517 | `IndiaPreFinalizePlugin` | - |
| `ispPRREC13` | DSG Thailand | 69518 | `DSGThailandPreFinalizePlugin` | - |

### Post-Finalize Plugins (ispASNFZ*)

| SP Name | Function | Error Code | Modern Plugin | Test Cases |
|---------|----------|------------|---------------|------------|
| `ispASNFZ01` | Standard Post | 69520 | `StandardPostFinalizePlugin` | F3-TC24 |
| `ispASNFZ03` | Batch PA Release | 69521 | `BatchPutawayReleasePlugin` | F6-TC03 |
| `ispASNFZ05` | Auto Allocate | 69528 | `AutoAllocatePlugin` | F4-TC03 |
| `ispASNFZ06` | UCC Stamp | 69522 | `UCCStampPlugin` | - |
| `ispASNFZ07` | Auto PA Release | 69523 | `AutoPutawayReleasePlugin` | F6-TC04 |
| `ispASNFZ24` | Columbia UCC | 69530 | `ColumbiaUCCPlugin` | - |

### Job Mappings

| Legacy Job | Schedule | Modern Scheduler | Error Code | Test Cases |
|------------|----------|------------------|------------|------------|
| `WMS Auto Populate PO` | `*/15 * * * *` | `AutoPopulateJob` | 69610 | F2-TC06 |
| `WMS Auto Finalize ASN` | `*/15 * * * *` | `AutoFinalizeJob` | 69611 | F3-TC05 |
| `WMS Auto PA Release` | `*/30 * * * *` | `AutoPutawayReleaseJob` | 69612 | F6-TC03 |
| `WMS Generic Inbound PO` | `*/5 * * * *` | `GenericInboundPOJob` | 69621 | F1-TC04 |
| `WMS XDock Auto Allocate` | `*/15 * * * *` | `XDockAutoAllocateJob` | 69614 | F4-TC03 |

### Trigger Mappings

| Legacy Trigger | Event | Modern Listener | Error Code | Test Cases |
|----------------|-------|-----------------|------------|------------|
| `tr_orders_insert` | PO Insert | `@PostPersist POEntity` | 69710 | F1-TC18 |
| `tr_orders_update` | PO Update | `@PostUpdate POEntity` | 69711 | - |
| `tr_orderdetail_insert` | PO Detail Insert | `@PostPersist PODetailEntity` | 69713 | - |
| `tr_receipt_insert` | Receipt Insert | `@PostPersist ReceiptEntity` | 69720 | F2-TC30 |
| `tr_receipt_update` | Receipt Update | `@PostUpdate ReceiptEntity` | 69721 | - |
| `tr_lotxlocxid_insert` | Inventory Insert | `@PostPersist InventoryEntity` | - | F3-TC06 |

---

## Section 3: Error Code Coverage Matrix

### Complete Error Code Test Mapping (260 codes)

| Range | Category | Count | Sample Codes | Test Coverage |
|-------|----------|-------|--------------|---------------|
| 68600-68699 | Task Processing | 17 | TASK_001 to TASK_014 | F6-TC* |
| 68700-68799 | Inventory | 24 | INV_001 to INV_042 | F3-TC*, F4-TC* |
| 68800-68899 | PO/ASN | 23 | PO_001 to PO_022 | F1-TC*, F2-TC* |
| 68900-68999 | Receipt/Finalization | 20 | RCV_001 to RCV_028 | F3-TC* |
| 69000-69099 | Integration | 15 | INT_001 to INT_024 | All flows |
| 69100-69199 | Validation | 15 | VAL_001 to VAL_021 | F1-TC*, F2-TC* |
| 69200-69299 | Putaway | 20 | PA_001 to PA_030 | F6-TC* |
| 69300-69399 | XDock | 11 | XD_001 to XD_022 | F4-TC* |
| 69400-69499 | Lottable | 16 | LOT_001 to LOT_030 | F5-TC* |
| 69500-69599 | Plugin | 34 | PLG_001 to PLG_060 | F2-TC*, F3-TC* |
| 69600-69699 | Job | 21 | JOB_001 to JOB_050 | Job tests |
| 69700-69799 | Trigger | 16 | TRG_001 to TRG_030 | Trigger tests |
| 69800-69899 | View | 12 | VW_001 to VW_023 | Query tests |
| 69900-69999 | Function/Config | 20 | FN_001 to CFG_005 | Util tests |
| **TOTAL** | | **260** | | **100%** |

---

## Section 4: Compensation Flow Testing

### Saga Pattern Verification

#### Population Saga Compensation

```
┌─────────────────────────────────────────────────────────────┐
│  POPULATE PO SAGA                                           │
├─────────────────────────────────────────────────────────────┤
│  Step 1: Validate Request      → [No compensation needed]   │
│  Step 2: Create Receipt Header → Delete RECEIPT             │
│  Step 3: Create Receipt Detail → Delete RECEIPTDETAIL       │
│  Step 4: Create Reservations   → Release RESERVATION        │
│  Step 5: Pre-Allocate          → Release allocation         │
│  Step 6: Sync to Legacy        → Call rollback SP           │
│  Step 7: Notify                → Publish cancellation       │
└─────────────────────────────────────────────────────────────┘
```

#### Finalization Saga Compensation

```
┌─────────────────────────────────────────────────────────────┐
│  FINALIZE RECEIPT SAGA                                      │
├─────────────────────────────────────────────────────────────┤
│  Step 1: Validate Receipt      → [No compensation needed]   │
│  Step 2: Run Pre-Finalize      → [No compensation needed]   │
│  Step 3: Update Status (→9)    → Revert status              │
│  Step 4: Post Inventory        → Delete LOTxLOCxID          │
│  Step 5: Apply Holds           → Delete INVENTORYHOLD       │
│  Step 6: Update PO Qty         → Subtract received qty      │
│  Step 7: Release PA Tasks      → Delete TASK                │
│  Step 8: Run Post-Finalize     → [Best effort, no comp]     │
│  Step 9: Close PO              → Revert PO status           │
└─────────────────────────────────────────────────────────────┘
```

### Compensation Test Scenarios (26 TCs)

| TC ID | Scenario | Fail At | Expected Compensation | Priority |
|-------|----------|---------|----------------------|----------|
| COMP-01 | Populate: Header created, detail fails | Step 3 | Delete header | P1 |
| COMP-02 | Populate: Details created, reservation fails | Step 4 | Delete details + header | P1 |
| COMP-03 | Populate: Reservation done, allocation fails | Step 5 | Release reservations + delete details/header | P1 |
| COMP-04 | Populate: Legacy sync fails | Step 6 | Release allocation + reservations + delete | P1 |
| COMP-05 | Populate: Workflow timeout | Any | All completed steps rolled back | P1 |
| COMP-06 | Populate: User cancellation | Any | All completed steps rolled back | P1 |
| COMP-07 | Populate: Idempotent retry | N/A | Skip completed, continue pending | P1 |
| COMP-08 | Finalize: Status update fails | Step 3 | N/A (validation) | P1 |
| COMP-09 | Finalize: Inventory posting fails | Step 4 | Revert status | P1 |
| COMP-10 | Finalize: Hold apply fails | Step 5 | Delete inventory + revert status | P1 |
| COMP-11 | Finalize: PO qty update fails | Step 6 | Release holds + delete inventory + revert | P1 |
| COMP-12 | Finalize: Putaway release fails | Step 7 | Revert PO qty + release holds + delete inv | P1 |
| COMP-13 | Finalize: Workflow timeout | Any | All completed steps rolled back | P1 |
| COMP-14 | Finalize: User cancellation | Any | All completed steps rolled back | P1 |
| COMP-15 | Concurrent populate same PO | Step 2 | Second request blocked/rejected | P1 |
| COMP-16 | Database deadlock | Any | Retry with backoff | P1 |
| COMP-17 | Kafka unavailable | Step 7 | Queue for retry, eventual consistency | P2 |
| COMP-18 | Temporal worker crash | Any | Workflow resumes on new worker | P1 |
| COMP-19 | Partial compensation failure | Step 3 comp | Alert + manual intervention | P2 |
| COMP-20 | Double compensation prevention | Any | Idempotent compensation | P1 |
| COMP-21 | XDock allocation rollback | F4 | Release allocation, delete SO | P2 |
| COMP-22 | Lottable rule failure compensation | F5 | Revert to null values | P2 |
| COMP-23 | Putaway task rollback | F6 | Delete created tasks | P2 |
| COMP-24 | Plugin failure compensation | F2/F3 | Plugin-specific rollback | P2 |
| COMP-25 | Job failure compensation | Job | Retry with backoff, alert | P2 |
| COMP-26 | Full saga replay test | All | Complete end-to-end replay | P1 |

---

## Section 5: Test Data Strategy

### Test Data Sets

| TD ID | Description | Entities | Used By |
|-------|-------------|----------|---------|
| TD-STORER | 11 Storers (Standard + Client + Regional) | STORER, CODELKUP | All tests |
| TD-SKU | 15 SKUs across clients | SKU, PACK | All tests |
| TD-LOC | 15 Locations (Recv, Stage, Store, XDock) | LOC | F3, F4, F6 |
| TD-PO-HAPPY | 5 Standard POs for happy path | ORDERS, ORDERDETAIL | F1, F2 |
| TD-PO-ERROR | 3 POs for error scenarios | ORDERS, ORDERDETAIL | F1, F2 |
| TD-PO-LARGE | 1 PO with 100+ lines | ORDERS, ORDERDETAIL | F2 (edge) |
| TD-RCV-HAPPY | 5 Receipts ready for finalize | RECEIPT, RECEIPTDETAIL | F3 |
| TD-RCV-ERROR | 3 Receipts for error scenarios | RECEIPT, RECEIPTDETAIL | F3 |
| TD-CLIENT | Client-specific PO/Receipts | All | Plugin tests |

### Test Data to Test Case Mapping

```
┌─────────────────────────────────────────────────────────────┐
│  TEST DATA MAPPING                                          │
├─────────────────────────────────────────────────────────────┤
│  TD-PO-001 (PO-TEST-001)                                    │
│  └── Used by: F1-TC01, F2-TC01, F2-TC02                    │
│                                                             │
│  TD-PO-002 (PO-TEST-002 - Already Populated)               │
│  └── Used by: F2-TC07                                      │
│                                                             │
│  TD-PO-003 (PO-TEST-003 - Closed)                          │
│  └── Used by: F2-TC09                                      │
│                                                             │
│  TD-PO-NIKE (PO-NIKE-001)                                   │
│  └── Used by: F3-TC20 (Nike plugin test)                   │
│                                                             │
│  TD-PO-HM (PO-HM-001)                                       │
│  └── Used by: F3-TC21 (H&M plugin test)                    │
│                                                             │
│  TD-RCV-001 (RCV-TEST-001 - Ready for Finalize)            │
│  └── Used by: F3-TC01 to F3-TC09                           │
│                                                             │
│  TD-RCV-FIN (RCV-FINALIZED - Already Finalized)            │
│  └── Used by: F3-TC11                                      │
└─────────────────────────────────────────────────────────────┘
```

### SQL Test Data Location

```
po-test/src/test/resources/test-data/
├── TD-MASTER-SETUP.sql        # Complete test data setup
├── TD-STORER.sql              # Storer + configuration
├── TD-SKU.sql                 # SKU + pack definitions
├── TD-LOCATION.sql            # Location master
├── TD-PO-HAPPY.sql            # Happy path POs
├── TD-PO-ERROR.sql            # Error scenario POs
├── TD-RECEIPT-HAPPY.sql       # Happy path receipts
├── TD-RECEIPT-ERROR.sql       # Error scenario receipts
└── TD-CLIENT-SPECIFIC.sql     # Nike, H&M, Adidas data
```

---

## Section 6: Local Environment Setup

### Quick Start (5 Steps)

```bash
# 1. Clone & Navigate
git clone <repo>
cd po-modernization

# 2. Start Local Environment
docker-compose -f docker/docker-compose-test.yml up -d

# 3. Wait for Services & Load Test Data
./scripts/wait-for-services.sh
./scripts/load-test-data.sh

# 4. Run All Tests
JAVA_HOME=/usr/local/opt/openjdk@17 ./mvnw test -pl po-test

# 5. View Reports
allure serve po-test/target/allure-results
```

### Docker Compose Configuration

```yaml
# docker/docker-compose-test.yml
version: '3.8'
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: po_modernization_test
      POSTGRES_USER: wms
      POSTGRES_PASSWORD: wms123
    ports:
      - "5433:5432"
    volumes:
      - ./init-db:/docker-entrypoint-initdb.d

  temporal:
    image: temporalio/auto-setup:1.22.3
    ports:
      - "7233:7233"
    environment:
      - DB=postgresql
      - DB_PORT=5432
      - POSTGRES_USER=wms
      - POSTGRES_PWD=wms123

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092

  app:
    build: ..
    depends_on:
      - postgres
      - temporal
      - kafka
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=test
```

### Database Setup Script

```bash
#!/bin/bash
# scripts/setup-test-db.sh

echo "Setting up test database..."

# Create schema
psql -h localhost -p 5433 -U wms -d po_modernization_test << 'EOF'
CREATE SCHEMA IF NOT EXISTS dbo;
SET search_path TO dbo, public;

-- Run Flyway migrations
-- Or load schema directly
\i po-api/src/main/resources/db/migration/V1__create_core_tables.sql

-- Load test data
\i po-test/src/test/resources/test-data/TD-MASTER-SETUP.sql
EOF

echo "Database setup complete!"
```

---

## Section 7: Test Execution Commands

### Maven Commands

```bash
# All E2E tests
mvn clean test -pl po-test

# Specific flow
mvn test -pl po-test -Dkarate.options="--tags @F1"
mvn test -pl po-test -Dkarate.options="--tags @F2"
mvn test -pl po-test -Dkarate.options="--tags @F3"

# By priority
mvn test -pl po-test -Dkarate.options="--tags @P1"
mvn test -pl po-test -Dkarate.options="--tags @P1 or @P2"

# By type
mvn test -pl po-test -Dkarate.options="--tags @Happy"
mvn test -pl po-test -Dkarate.options="--tags @Unhappy"
mvn test -pl po-test -Dkarate.options="--tags @Edge"
mvn test -pl po-test -Dkarate.options="--tags @Error"
mvn test -pl po-test -Dkarate.options="--tags @Compensation"

# By entry point
mvn test -pl po-test -Dkarate.options="--tags @API"
mvn test -pl po-test -Dkarate.options="--tags @EDI"
mvn test -pl po-test -Dkarate.options="--tags @Job"
mvn test -pl po-test -Dkarate.options="--tags @Trigger"
mvn test -pl po-test -Dkarate.options="--tags @RDT"

# Parallel execution (5 threads)
mvn test -pl po-test -Dkarate.threads=5

# With coverage report
mvn clean test jacoco:report -pl po-test

# Generate Allure report
mvn allure:serve -pl po-test
```

### Karate Feature Tags Convention

```gherkin
# Example feature file with proper tagging
@F2 @ASN @Population @API
Feature: ASN Population - Happy Path

@F2-TC01 @P1 @Happy @Regression
Scenario: Populate single PO successfully
  ...

@F2-TC07 @P1 @Unhappy @PO_022
Scenario: PO already populated returns error
  ...

@F2-TC24 @P1 @Compensation @Saga
Scenario: Rollback on reservation failure
  ...
```

---

## Section 8: CI/CD Pipeline (3-Layer Approach)

### GitHub Actions Workflow

```yaml
# .github/workflows/e2e-tests.yml
name: E2E Tests (3-Layer)

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]
  schedule:
    - cron: '0 2 * * *'  # Nightly at 2 AM

env:
  JAVA_VERSION: '17'

jobs:
  # ═══════════════════════════════════════════════════════════
  # LAYER 2: JUnit + Spring Boot Tests (Fast feedback)
  # ═══════════════════════════════════════════════════════════
  unit-integration-tests:
    name: "Layer 2: Unit & Integration Tests"
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: maven

      - name: Run Unit Tests
        run: mvn test -pl po-domain,po-service -DskipITs

      - name: Run Integration Tests
        run: mvn verify -pl po-service -DskipUTs

      - name: Upload Test Results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: junit-results
          path: '**/target/surefire-reports/*.xml'

  # ═══════════════════════════════════════════════════════════
  # LAYER 3: Temporal Workflow Tests
  # ═══════════════════════════════════════════════════════════
  workflow-tests:
    name: "Layer 3: Temporal Workflow Tests"
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: maven

      - name: Run Workflow Tests
        run: mvn test -pl po-workflow -Dtest="*WorkflowTest,*CompensationTest"

      - name: Upload Workflow Test Results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: workflow-results
          path: 'po-workflow/target/surefire-reports/*.xml'

  # ═══════════════════════════════════════════════════════════
  # LAYER 1: Karate E2E Tests (Requires full environment)
  # ═══════════════════════════════════════════════════════════
  e2e-tests:
    name: "Layer 1: Karate E2E Tests"
    needs: [unit-integration-tests, workflow-tests]
    runs-on: ubuntu-latest
    strategy:
      matrix:
        flow: [F1-F3, F4-F6, F7-F10]  # Split by flow groups

    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_DB: po_test
          POSTGRES_USER: wms
          POSTGRES_PASSWORD: wms123
        ports:
          - 5433:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: maven

      - name: Start Temporal (test mode)
        run: |
          docker run -d --name temporal \
            -p 7233:7233 \
            temporalio/auto-setup:1.22.3

      - name: Load Test Data
        run: |
          PGPASSWORD=wms123 psql -h localhost -p 5433 -U wms -d po_test \
            -f po-test/src/test/resources/test-data/TD-MASTER-SETUP.sql

      - name: Start Application
        run: |
          mvn spring-boot:run -pl po-api \
            -Dspring-boot.run.profiles=test &
          ./scripts/wait-for-app.sh

      - name: Run Karate Tests (${{ matrix.flow }})
        run: |
          mvn test -pl po-test \
            -Dkarate.options="--tags @${{ matrix.flow }}" \
            -Dkarate.threads=5

      - name: Upload Karate Results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: karate-results-${{ matrix.flow }}
          path: po-test/target/karate-reports/

  # ═══════════════════════════════════════════════════════════
  # Performance Tests (Nightly only)
  # ═══════════════════════════════════════════════════════════
  performance-tests:
    name: "Performance: Karate + Gatling"
    if: github.event_name == 'schedule'
    needs: e2e-tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: maven

      - name: Start Full Environment
        run: docker-compose -f docker/docker-compose-test.yml up -d

      - name: Wait for Services
        run: ./scripts/wait-for-services.sh

      - name: Run Performance Tests
        run: |
          mvn test -pl po-test \
            -Dkarate.options="--tags @performance" \
            -Dkarate.threads=10

      - name: Upload Performance Report
        uses: actions/upload-artifact@v4
        with:
          name: performance-report
          path: po-test/target/gatling/

  # ═══════════════════════════════════════════════════════════
  # Aggregate Reports
  # ═══════════════════════════════════════════════════════════
  report:
    name: "Generate Allure Report"
    needs: e2e-tests
    runs-on: ubuntu-latest
    if: always()
    steps:
      - name: Download All Results
        uses: actions/download-artifact@v4
        with:
          pattern: '*-results*'
          merge-multiple: true

      - name: Generate Allure Report
        uses: simple-elf/allure-report-action@master
        with:
          allure_results: '**/karate-reports,**/surefire-reports'
          allure_report: allure-report

      - name: Publish to GitHub Pages
        uses: peaceiris/actions-gh-pages@v4
        if: github.ref == 'refs/heads/main'
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./allure-report
```

### Pipeline Summary (3-Layer)

```
┌─────────────────────────────────────────────────────────────┐
│  CI/CD PIPELINE STAGES                                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Stage 1: Layer 2 Tests (Parallel, ~5 min)                  │
│  ├── Unit Tests (po-domain, po-service)                     │
│  └── Integration Tests (po-service)                         │
│                                                             │
│  Stage 2: Layer 3 Tests (Parallel, ~3 min)                  │
│  └── Temporal Workflow Tests (po-workflow)                  │
│                                                             │
│  Stage 3: Layer 1 Tests (After Stage 1+2, ~15 min)          │
│  ├── Karate E2E Tests (F1-F3)                               │
│  ├── Karate E2E Tests (F4-F6)                               │
│  └── Karate E2E Tests (F7-F10)                              │
│                                                             │
│  Stage 4: Performance Tests (Nightly only, ~30 min)         │
│  └── Karate + Gatling Load Tests                            │
│                                                             │
│  Stage 5: Reporting                                         │
│  └── Allure Report Generation                               │
│                                                             │
└─────────────────────────────────────────────────────────────┘

Total CI Time: ~20 min (daily) | ~50 min (nightly with perf)
```

---

## Section 9: Implementation Timeline (9 Weeks)

### 3-Layer Implementation Approach

| Week | Phase | Layer Focus | Tasks | Deliverables | Owner |
|------|-------|-------------|-------|--------------|-------|
| 1 | Foundation | All | Docker, Karate, Spring Test setup | Local env working | DevOps |
| 2 | Foundation | Layer 2 | JUnit tests for po-domain, po-service | Unit test suite | Dev |
| 3 | Critical | Layer 3 | Temporal test setup, FinalizeWorkflowTest | Workflow tests | Dev |
| 4 | Critical | Layer 1 | F1 PO Creation (25 TCs via Karate) | F1 E2E complete | QA |
| 5 | Critical | Layer 1 | F2 ASN Population (30 TCs) | F2 E2E complete | QA |
| 6 | Critical | Layer 1 | F3 Receipt Finalization (35 TCs) | F3 E2E complete | QA |
| 7 | Extended | Layer 1 | F4-F6 (60 TCs) + F7-F10 (70 TCs) | All flows complete | QA |
| 8 | Compensation | Layer 3 | All saga scenarios (26 TCs) | Saga verified | QA Lead |
| 9 | Validation | Layer 1 | Performance (Karate+Gatling), sign-off | Go-live ready | Tech Lead |

### Week-by-Week Breakdown

```
┌─────────────────────────────────────────────────────────────┐
│  WEEK 1-2: FOUNDATION                                       │
├─────────────────────────────────────────────────────────────┤
│  Week 1:                                                    │
│  ├── Set up Docker Compose (postgres, temporal, app)        │
│  ├── Configure Karate DSL + karate-jdbc                     │
│  ├── Create test data SQL scripts                           │
│  └── Set up Allure reporting                                │
│                                                             │
│  Week 2:                                                    │
│  ├── Write JUnit tests for domain classes                   │
│  ├── Write Spring Boot integration tests                    │
│  ├── Configure GitHub Actions CI pipeline                   │
│  └── Test data loading automation                           │
├─────────────────────────────────────────────────────────────┤
│  WEEK 3: TEMPORAL WORKFLOW TESTING (Layer 3)                │
├─────────────────────────────────────────────────────────────┤
│  ├── Set up TestWorkflowEnvironment                         │
│  ├── FinalizeReceiptWorkflowTest (compensation)             │
│  ├── PopulatePOWorkflowTest (compensation)                  │
│  ├── Activity mock/stub setup                               │
│  └── Retry logic verification tests                         │
├─────────────────────────────────────────────────────────────┤
│  WEEK 4-7: KARATE E2E TESTS (Layer 1)                       │
├─────────────────────────────────────────────────────────────┤
│  Week 4: F1 PO Creation                                     │
│  ├── Happy path (API, EDI, Job, Trigger)                    │
│  ├── Unhappy path (validation errors)                       │
│  └── Database validation via karate-jdbc                    │
│                                                             │
│  Week 5: F2 ASN Population                                  │
│  ├── Happy path (all entry points)                          │
│  ├── Plugin execution tests                                 │
│  └── Dual-write comparison tests                            │
│                                                             │
│  Week 6: F3 Receipt Finalization                            │
│  ├── Full finalization flows                                │
│  ├── Pre/post plugin execution                              │
│  └── Inventory posting verification                         │
│                                                             │
│  Week 7: F4-F10 (remaining flows)                           │
│  ├── Cross-dock, Lottable, Putaway                          │
│  ├── Cancellation, Archival                                 │
│  └── Edge cases and error scenarios                         │
├─────────────────────────────────────────────────────────────┤
│  WEEK 8: COMPENSATION DEEP DIVE (Layer 3)                   │
├─────────────────────────────────────────────────────────────┤
│  ├── All 26 compensation scenarios                          │
│  ├── Failure injection tests                                │
│  ├── Timeout handling tests                                 │
│  └── Idempotency verification                               │
├─────────────────────────────────────────────────────────────┤
│  WEEK 9: PERFORMANCE & SIGN-OFF                             │
├─────────────────────────────────────────────────────────────┤
│  ├── Karate + Gatling performance tests                     │
│  ├── Load test: 200 req/s sustained                         │
│  ├── Stress test: find breaking point                       │
│  ├── Final regression run (all 260 TCs)                     │
│  └── Sign-off documentation                                 │
└─────────────────────────────────────────────────────────────┘
```

---

## Section 10: Test Execution Tracker

### Master Tracker Template

| TC ID | Flow | Test Case | Type | Entry | Priority | Status | Last Run | Result | Bug ID |
|-------|------|-----------|------|-------|----------|--------|----------|--------|--------|
| F1-TC01 | F1 | Create single PO | Happy | API | P1 | Ready | - | - | - |
| F1-TC02 | F1 | Create multi-line PO | Happy | API | P1 | Ready | - | - | - |
| ... | ... | ... | ... | ... | ... | ... | ... | ... | ... |

### Coverage Dashboard

```
┌─────────────────────────────────────────────────────────────┐
│  E2E TEST EXECUTION DASHBOARD                               │
├─────────────────────────────────────────────────────────────┤
│  Overall Progress:                                           │
│  ├── Total:     ████████████████████░░░░  260/260 (100%)    │
│  ├── Passed:    ████████████████░░░░░░░░  208/260 (80%)     │
│  ├── Failed:    ██░░░░░░░░░░░░░░░░░░░░░░   26/260 (10%)     │
│  └── Blocked:   ██░░░░░░░░░░░░░░░░░░░░░░   26/260 (10%)     │
├─────────────────────────────────────────────────────────────┤
│  By Flow:                                                    │
│  F1 (PO Creation):        ████████████████████  25/25       │
│  F2 (ASN Population):     ██████████████████░░  27/30       │
│  F3 (Finalization):       ████████████████░░░░  28/35       │
│  F4 (Cross-Dock):         ████████████████████  20/20       │
│  F5 (Lottable):           ██████████████░░░░░░  14/20       │
│  F6 (Putaway):            ████████████████████  20/20       │
│  F7-F10:                  ████████████████░░░░  74/90       │
├─────────────────────────────────────────────────────────────┤
│  By Entry Point:                                             │
│  API:     ████████████████████  100%                        │
│  EDI:     ██████████████░░░░░░   70%                        │
│  Job:     ████████████████░░░░   80%                        │
│  Trigger: ██████████████████░░   90%                        │
│  RDT:     ████████████░░░░░░░░   60%                        │
├─────────────────────────────────────────────────────────────┤
│  By Type:                                                    │
│  Happy:       ████████████████████  100%                    │
│  Unhappy:     ████████████████░░░░   80%                    │
│  Edge:        ██████████████░░░░░░   70%                    │
│  Error:       ████████████░░░░░░░░   60%                    │
│  Compensation:████████████████████  100%                    │
└─────────────────────────────────────────────────────────────┘
```

---

## Section 11: Success Criteria & Sign-Off

### Go-Live Checklist

| # | Criteria | Target | Actual | Status |
|---|----------|--------|--------|--------|
| 1 | All 260 test cases executed | 100% | - | ⏳ |
| 2 | P1 test cases passed | 100% | - | ⏳ |
| 3 | P2 test cases passed | 95%+ | - | ⏳ |
| 4 | All 5 entry points tested | 100% | - | ⏳ |
| 5 | All 10 flows tested | 100% | - | ⏳ |
| 6 | All 39+ SPs mapped & tested | 100% | - | ⏳ |
| 7 | All 260 error codes tested | 100% | - | ⏳ |
| 8 | Compensation flows verified | 100% | - | ⏳ |
| 9 | Performance meets SLA | ≥Legacy | - | ⏳ |
| 10 | Zero data loss verified | Yes | - | ⏳ |
| 11 | Dual-write comparison passed | 100% match | - | ⏳ |
| 12 | Rollback procedure tested | Yes | - | ⏳ |

### Sign-Off Matrix

| Role | Name | Sign-Off Date | Comments |
|------|------|---------------|----------|
| QA Lead | TBD | - | - |
| Dev Lead | TBD | - | - |
| Tech Lead | TBD | - | - |
| Product Owner | TBD | - | - |

---

## Best Practices

### 3-Layer Testing Guidelines

#### Layer 1 (Karate) Best Practices
```
✅ DO:
├── Use data-driven tests with CSV/JSON for similar scenarios
├── Leverage karate-jdbc for database assertions
├── Reuse functional tests for performance testing
├── Tag tests properly (@F1, @Happy, @P1, @API)
├── Use Karate's built-in retry for flaky network calls
└── Generate comprehensive reports with Allure

❌ DON'T:
├── Test internal Java logic via API (use Layer 2)
├── Test workflow compensation via API only (use Layer 3)
├── Create complex test data setup in Karate (use SQL scripts)
└── Ignore response time assertions in API tests
```

#### Layer 2 (JUnit + Spring) Best Practices
```
✅ DO:
├── Use @MockBean for external dependencies
├── Use @Transactional for automatic rollback
├── Use TestContainers for realistic DB testing
├── Test edge cases in business logic
└── Verify exception messages and error codes

❌ DON'T:
├── Test HTTP layer (use Layer 1)
├── Test workflow orchestration (use Layer 3)
├── Create overly complex mock setups
└── Skip @Transactional boundary tests
```

#### Layer 3 (Temporal) Best Practices
```
✅ DO:
├── Use TestWorkflowEnvironment (no server needed)
├── Test compensation for every failure point
├── Verify retry behavior with mocked failures
├── Test timeout scenarios using time skipping
└── Verify idempotency of activities

❌ DON'T:
├── Test against a real Temporal server in unit tests
├── Skip compensation testing
├── Assume activities will always succeed
└── Ignore workflow timeout scenarios
```

### General Best Practices

### ✅ DO
- Run dual-write during migration (compare legacy vs modern outputs via Karate+JDBC)
- Implement chaos engineering (inject failures in Layer 3 tests)
- Perform daily data reconciliation
- Test at production scale (1M+ POs) using Karate+Gatling
- Run regression tests after every hotfix
- Monitor performance during test execution
- Keep test data isolated and repeatable
- **Layer appropriately**: API contracts in Layer 1, business logic in Layer 2, sagas in Layer 3

### ❌ DON'T
- Skip edge cases
- Test only happy paths
- Rely on manual testing
- Ignore timeout scenarios
- Skip compensation testing
- Use production data directly
- Skip error code validation
- **Over-engineer**: Don't add more frameworks than necessary (stick to 3 layers)
- **Duplicate tests across layers**: Each layer should test distinct concerns

---

## Quick Reference Commands

```bash
# Setup
docker-compose -f docker/docker-compose-test.yml up -d
./scripts/load-test-data.sh

# Run all tests
mvn test -pl po-test

# Run by flow
mvn test -pl po-test -Dkarate.options="--tags @F1"

# Run by priority
mvn test -pl po-test -Dkarate.options="--tags @P1"

# Run compensation tests
mvn test -pl po-test -Dkarate.options="--tags @Compensation"

# Generate report
mvn allure:serve -pl po-test

# Cleanup
docker-compose -f docker/docker-compose-test.yml down -v
```

---

## References

- [Legacy SP Documentation](../docs/sql/)
- [Error Code Registry](../po-domain/src/main/java/com/wms/po/domain/exception/ErrorCode.java)
- [Existing Karate Tests](../po-test/src/test/java/com/wms/po/e2e/karate/)
- [Test Data SQL](../po-test/src/test/resources/test-data/)
- [Architecture Documentation](./PO_Modernization_Architecture.md)

---

**Version:** 2.1 (Pragmatic 3-Layer) | **Last Updated:** 2026-05-06 | **Status:** Ready for Implementation

*This document combines the best of both Haiku and Opus testing strategies, refined with a pragmatic 3-layer approach (Karate + JUnit/Spring + Temporal SDK) for comprehensive migration validation with reduced maintenance overhead.*
