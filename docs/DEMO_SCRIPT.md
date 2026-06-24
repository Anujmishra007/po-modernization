# PO Modernization POC - Executive Demo Script

## Demo Duration: 15-20 minutes

---

## Pre-Demo Setup (Do Before Demo)

```bash
# 1. Start all services
cd /Users/anuj.mishra/Documents/WMS/po-modernization
docker-compose -f local-environment/docker-compose.yml up -d

# 2. Start the application
mvn spring-boot:run -pl po-api -Dspring-boot.run.profiles=local

# 3. Open these tabs in browser:
#    - Temporal UI: http://localhost:8233
#    - Swagger UI:  http://localhost:8080/swagger-ui.html
#    - Karate Report: po-test/target/karate-reports/karate-summary.html
```

---

## Demo Flow

### Part 1: Architecture Overview (3 min)

**Show**: Architecture diagram from `docs/ARCHITECTURE.md`

**Key Points**:
- "We've migrated 39 stored procedures totaling 22,000+ lines of SQL into a modern Java microservice"
- "The solution uses Temporal.io for workflow orchestration with automatic rollback on failure"
- "Client-specific logic is now in pluggable modules - no more 30+ IF statements in SQL"

---

### Part 2: Live Workflow Execution (5 min)

#### Step 1: Trigger a PO Population Workflow

**In Postman/Swagger, execute:**

```bash
curl -X POST http://localhost:8080/api/v1/po/populate \
  -H "Content-Type: application/json" \
  -d '{
    "poKey": "PO-DEMO-001",
    "storerKey": "NIKE",
    "facility": "KR01",
    "userId": "demo-user"
  }'
```

**Response shows:**
```json
{
  "workflowId": "po-populate-PO-DEMO-001-abc123",
  "status": "RUNNING",
  "message": "Workflow started"
}
```

#### Step 2: Watch Workflow in Temporal UI

**Navigate to**: `http://localhost:8233/namespaces/po-namespace/workflows`

**Show**:
- Workflow appearing in the list
- Click into workflow → Show 10-step execution
- Each step with timing, inputs, outputs
- Audit trail of every action

**Key Points**:
- "Every step is logged and auditable"
- "If any step fails, previous steps automatically roll back"
- "No more orphaned data from partial failures"

---

### Part 3: Saga Compensation Demo (4 min)

**This is the WOW moment - demonstrate automatic rollback**

#### Step 1: Trigger a workflow that will fail

```bash
curl -X POST http://localhost:8080/api/v1/po/populate \
  -H "Content-Type: application/json" \
  -d '{
    "poKey": "PO-FAIL-DEMO",
    "storerKey": "NIKE",
    "facility": "KR01",
    "userId": "demo-user",
    "simulateFailure": "INVENTORY_STEP"
  }'
```

#### Step 2: Watch in Temporal UI

**Show**:
- Workflow starts successfully (steps 1-5)
- Step 8 (Inventory) fails
- **Compensation kicks in** - Steps roll back in reverse order
- Final status: COMPENSATED (not FAILED with orphan data)

**Key Points**:
- "In the legacy system, a failure at step 8 would leave orphan receipt records"
- "Now, the system automatically cleans up - data integrity guaranteed"
- "This is the Saga pattern in action"

---

### Part 4: Client-Specific Plugin Demo (3 min)

**Show how different clients get different behavior without code changes**

#### Nike (Korea) Request:

```bash
curl -X POST http://localhost:8080/api/v1/po/populate \
  -H "Content-Type: application/json" \
  -d '{
    "poKey": "PO-NIKE-KR",
    "storerKey": "NIKE",
    "facility": "KR01"
  }'
```

**Show in logs/response**:
- Nike-specific lottable mapping (style→lottable01, color→lottable02, size→lottable03)
- Korea region customs validation
- NikeKRPrePopulatePlugin executed

#### H&M (Singapore) Request:

```bash
curl -X POST http://localhost:8080/api/v1/po/populate \
  -H "Content-Type: application/json" \
  -d '{
    "poKey": "PO-HM-SG",
    "storerKey": "HM",
    "facility": "SG01"
  }'
```

**Show in logs/response**:
- H&M-specific validation rules
- Singapore TradeNet integration
- Different plugin chain executed

**Key Points**:
- "Same API, same code - different behavior based on client/region"
- "Adding a new client = add a YAML file, not modify SQL"
- "65+ plugins handle all client variations"

---

### Part 5: Drools Rules Engine Demo (2 min)

**Show**: Lottable rules execution

```bash
# Get lottable mapping for an item
curl http://localhost:8080/api/v1/rules/lottable/preview \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "NIKE-SHOE-001",
    "storerKey": "NIKE",
    "style": "AIR-MAX-90",
    "color": "WHITE",
    "size": "10.5"
  }'
```

**Response**:
```json
{
  "lottable01": "AIR-MAX-90",
  "lottable02": "WHITE",
  "lottable03": "10.5",
  "appliedRules": [
    "Lot01-FromStyle",
    "Lot02-FromColor (Nike override)",
    "Lot03-FromSize"
  ]
}
```

**Key Points**:
- "Business rules are now in readable DRL files, not buried in SQL"
- "Rules can be changed without recompiling the application"
- "Each rule application is logged for auditability"

---

### Part 6: E2E Test Results (2 min)

**Open**: `po-test/target/karate-reports/karate-summary.html`

**Show**:
- 40+ test scenarios
- Coverage by flow (PO Creation, ASN Population, Finalization, Cross-Dock, Trade Return)
- Happy path + unhappy path + edge cases
- Compensation tests (Saga rollback verification)

**Key Points**:
- "Every SP behavior is validated by automated tests"
- "Regression testing is now automated - takes 5 minutes, not 2 days"
- "Tests run on every code change via CI/CD"

---

### Part 7: Dual-Write Validation (Optional - 2 min)

**If dual-write is enabled, show reconciliation**:

```bash
curl http://localhost:8080/api/v1/reconciliation/receipt/RCP-001
```

**Response**:
```json
{
  "status": "MATCHED",
  "legacyReceiptKey": "RCP-001",
  "newReceiptKey": "RCP-001",
  "matchedLines": 5,
  "mismatchedLines": 0,
  "differences": []
}
```

**Key Points**:
- "During migration, we write to BOTH legacy and new systems"
- "Automatic comparison ensures data parity"
- "Zero risk migration - can switch back instantly"

---

## Summary Slide Points

### What We Built
| Component | Legacy | Modern |
|-----------|--------|--------|
| **Code** | 22,000+ lines SQL (39 SPs) | 8,500 lines Java (13 modules) |
| **Client Logic** | 30+ IF statements per SP | 65+ pluggable modules |
| **Error Handling** | Orphan data on failure | Saga auto-rollback |
| **Testing** | Manual (2 days) | Automated (5 min) |
| **Observability** | SQL Profiler | Temporal UI + Metrics |
| **Deployment** | Database-coupled | Independent microservice |

### Business Benefits
1. **Faster Onboarding**: New client = YAML config (hours, not weeks)
2. **Zero Data Corruption**: Saga pattern guarantees consistency
3. **Full Auditability**: Every step logged in Temporal
4. **Faster Bug Fixes**: Readable Java vs cryptic SQL
5. **Independent Scaling**: Microservice can scale independently

### Technical Achievements
- 39 stored procedures modernized
- 10-step Saga workflow with compensation
- 65+ client/region plugins
- 40+ E2E test scenarios
- Dual-write validation for safe migration

---

## Q&A Preparation

**Q: How do we ensure parity with legacy system?**
A: Dual-write mode + ReconciliationService compares every field

**Q: What if the new system fails in production?**
A: Feature flag routes back to legacy SPs instantly

**Q: How long to onboard a new client?**
A: Create YAML config + optional plugin = 1-2 days (was 2-4 weeks)

**Q: Can we see the performance difference?**
A: Show Gatling/performance test results if available

**Q: What's the rollout plan?**
A: Phase 1: Shadow mode (dual-write), Phase 2: New system primary, Phase 3: Legacy decommission

---

## Demo Environment URLs

| Service | URL |
|---------|-----|
| **Application** | http://localhost:8080 |
| **Swagger UI** | http://localhost:8080/swagger-ui.html |
| **Temporal UI** | http://localhost:8233 |
| **Actuator Health** | http://localhost:8080/actuator/health |
| **Metrics** | http://localhost:8080/actuator/prometheus |

---

## Backup: If Services Are Down

Have these ready:
1. **Screenshots** of Temporal workflow execution
2. **Video recording** of successful demo
3. **Karate HTML report** (static file, no server needed)
4. **Architecture diagrams** from docs/ARCHITECTURE.md
