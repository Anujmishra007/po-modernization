# PO Modernization - Architecture

## Overview

The PO Modernization project transforms legacy stored procedure-based PO population logic into a modern, maintainable microservice architecture using Temporal workflow orchestration with the Saga pattern.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              API Gateway                                      │
│                         (Spring Boot REST API)                                │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Variation Layer                                     │
│         (Region/Client Resolution, Plugin Registry, Rules Engine)            │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Temporal Workflow                                     │
│                    (Saga Pattern Orchestration)                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐     │
│  │Validation│→ │ Mapping  │→ │ Receipt  │→ │Inventory │→ │  Status  │     │
│  │ Activity │  │ Activity │  │ Creation │  │Allocation│  │  Update  │     │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘     │
│       ↑              ↑              ↑              ↑              ↑         │
│       │              │              │              │              │         │
│  ┌────┴──────────────┴──────────────┴──────────────┴──────────────┴────┐  │
│  │                    Compensation Logic (Rollback)                     │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Legacy Bridge                                         │
│              (V0/V2 Database Adapters, Dual-Write Support)                   │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    ▼                               ▼
            ┌───────────────┐               ┌───────────────┐
            │   V0 Database │               │   V2 Database │
            │   (Legacy)    │               │   (Current)   │
            └───────────────┘               └───────────────┘
```

## Module Structure

### po-domain
Core domain entities, DTOs, and repository interfaces.
- Entities: `POEntity`, `ReceiptEntity`, `ReceiptDetailEntity`
- Models: `PopulateRequest`, `PopulateResult`, `VariationContext`
- Exceptions: `ValidationException`, `CancelledException`

### po-rules
Drools-based business rules engine.
- Rules for PO validation, SKU validation
- Lottable field mapping by region/client
- Dynamic rule evaluation

### po-variation
Variation layer for region/client-specific behavior.
- `VariationResolver`: Determines context from request
- `PluginRegistry`: Manages pre/post-populate plugins
- `RuleEngine`: Executes business rules

### po-service
Core business services.
- `POService`: CRUD operations
- `POValidationService`: Validation logic
- `POEnrichmentService`: Data enrichment
- Workflow interfaces for Temporal

### po-activity
Temporal activity implementations.
- Saga activities with compensation methods
- Each activity handles one step of the workflow

### po-workflow
Temporal workflow orchestration.
- `PopulatePOWorkflow`: Main orchestration workflow
- Saga pattern with automatic compensation on failure

### po-plugin
Extensible plugin system.
- Client plugins: Nike, H&M, Zara
- Region plugins: Korea, India, Singapore
- Lifecycle hooks: Audit, Metrics, Notifications

### po-legacy-bridge
Bridge to legacy WMS systems.
- `V0Adapter` / `V2Adapter`: Version-specific database access
- `LegacyBridgeService`: Abstraction layer
- Dual-write support for migration

### po-api
REST API layer.
- Controllers: PO CRUD, Population, Receipts
- Configuration: Temporal, DataSource, Security
- Exception handling

### po-config
YAML-based configuration.
- Region configurations
- Client configurations
- Feature flags

### po-test
Comprehensive test suite.
- Karate E2E tests
- JUnit unit tests
- Integration tests with Testcontainers

## Key Design Patterns

### Saga Pattern
Each workflow step has a corresponding compensation action. On failure, completed steps are rolled back in reverse order.

### Plugin Architecture
Client and region-specific logic is encapsulated in plugins, loaded dynamically based on context.

### Variation Layer
Centralizes the logic for determining which version, region, and client-specific behavior to apply.

### Dual-Write
During migration, writes can be sent to both legacy and new systems for validation.

## Technology Stack

- **Java 17** - Language
- **Spring Boot 3.2.5** - Framework
- **Temporal 1.22.3** - Workflow Orchestration
- **Drools 8.44** - Rules Engine
- **PostgreSQL** - Database (development)
- **SQL Server** - Database (production)
- **Redis** - Caching
- **Kafka** - Event streaming
- **Karate** - E2E Testing
- **JUnit 5 + Mockito** - Unit Testing
- **Testcontainers** - Integration Testing
