# PO Modernization Service

Microservice for modernizing the Purchase Order (PO) population workflow, migrating from legacy stored procedures to a Java-based service with Temporal workflow orchestration.

## Architecture

This project implements a **Saga pattern** using **Temporal.io** to orchestrate the PO-to-Receipt population workflow with full compensation support.

### Modules

| Module | Description |
|--------|-------------|
| `po-domain` | Domain models, entities, DTOs, repositories, and exceptions |
| `po-infrastructure` | Multi-tenant database, Redis caching, distributed locks, saga transactions |
| `po-kafka` | Kafka event streaming for PO lifecycle events and saga coordination |
| `po-rules` | Drools rules engine for validation and lottable mapping |
| `po-variation` | Variation layer for handling V0/V2 database versions and client plugins |
| `po-service` | Business services (POService, POValidationService, POEnrichmentService) |
| `po-activity` | Temporal activity interfaces and implementations |
| `po-workflow` | Temporal workflow definitions with Saga pattern |
| `po-legacy-bridge` | Bridge to legacy system with dual-write support |
| `po-plugin` | Plugin architecture for client/region-specific customizations |
| `po-api` | REST API controllers and Spring Boot application |
| `po-config` | Configuration management (region/client YAML configs) |
| `po-test` | Unit tests, integration tests, and Karate E2E feature tests |

### Workflow Steps (Saga Pattern)

1. **Resolve Context** - Determine V0/V2 version, region, client
2. **Pre-Populate Plugins** - Run client-specific pre-processing
3. **Validate** - Apply business rules validation
4. **Map PO to ASN** - Transform PO data to receipt format
5. **Apply Lottables** - Apply lottable mapping rules
6. **Create Receipt Header** - Persist header (with compensation)
7. **Create Receipt Details** - Persist details (with compensation)
8. **Create Inventory Reservations** - Reserve inventory (with compensation)
9. **Sync to Legacy** - Dual-write to legacy system (with compensation)
10. **Send Notifications** - Notify completion (best-effort)

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose
- Temporal Server

### Build

```bash
mvn clean install
```

### Run Locally

```bash
# Start dependencies (Temporal, SQL Server)
docker-compose -f docker-compose-local.yml up -d

# Run the application
mvn spring-boot:run -pl po-api -Dspring-boot.run.profiles=local
```

### Run Tests

```bash
# Unit tests
mvn test

# Integration tests
mvn verify -Pintegration

# E2E tests (Karate)
mvn test -pl po-test -Dtest=KarateTestRunner
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `TEMPORAL_SERVICE_ADDRESS` | Temporal server address | `localhost:7233` |
| `TEMPORAL_NAMESPACE` | Temporal namespace | `po-namespace` |
| `DB_HOST` | Database host | `localhost` |
| `DB_PORT` | Database port | `1433` |
| `DUAL_WRITE_ENABLED` | Enable legacy dual-write | `true` |

### Client Configuration

Client-specific configurations are in `po-config/src/main/resources/config/clients/`:

```yaml
# nike.yaml
client:
  code: NIKE
  features:
    customLottables: true
    prePopulateHook: NikePrePopulatePlugin
```

### Region Configuration

Region-specific configurations are in `po-config/src/main/resources/config/regions/`:

```yaml
# asia_kr.yaml
region:
  code: KR
  timezone: Asia/Seoul
  version: V2
  features:
    koreaCustomsIntegration: true
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/po/populate` | Start PO population workflow |
| `GET` | `/api/v1/po/{poKey}` | Get PO details |
| `GET` | `/api/v1/receipt/{receiptKey}` | Get receipt details |
| `GET` | `/api/v1/workflow/{workflowId}/status` | Get workflow status |
| `POST` | `/api/v1/workflow/{workflowId}/cancel` | Cancel running workflow |

## Makefile Commands

```bash
make build      # Build all modules
make test       # Run all tests
make run        # Run application locally
make docker     # Build Docker image
make e2e        # Run E2E tests
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md) - System architecture and design decisions
- [Discovery](docs/DISCOVERY.md) - Discovery phase findings and SP mappings
- [Local Development](docs/LOCAL_DEVELOPMENT.md) - Development setup guide

## Legacy SP Mapping

| Legacy SP | Modern Activity |
|-----------|-----------------|
| `nsp_ValidatePO` | `ValidationActivity.validate()` |
| `nsp_MapLottables` | `MappingActivity.applyLottables()` |
| `nsp_CreateReceipt` | `PersistenceActivity.createReceiptHeader()` |
| `nsp_AllocateInventory` | `InventoryActivity.createReservations()` |
| `nsp_UpdatePOStatus` | `PersistenceActivity.updateReceiptStatus()` |
| `nsp_SendNotification` | `NotificationActivity.sendPopulationComplete()` |
