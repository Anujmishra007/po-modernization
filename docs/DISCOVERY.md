# PO Modernization - Discovery Document

## Executive Summary

This document captures the discovery phase findings for modernizing the Purchase Order (PO) population workflow from legacy stored procedures to a modern microservice architecture.

## Current State Analysis

### Legacy Architecture
- **Database**: SQL Server with complex stored procedure chains
- **Entry Point**: `nsp_PopulateReceiptFromPO` (V0) / `lsp_PopulateReceiptFromPO` (V2)
- **Lines of Code**: ~5,000+ LOC across 15+ stored procedures
- **Coupling**: Tight coupling between business logic and database operations
- **Testability**: Limited - requires full database setup

### Pain Points Identified

1. **Maintainability**
   - Complex nested procedure calls
   - Business logic buried in SQL
   - Difficult to understand flow

2. **Extensibility**
   - Hard-coded client/region logic
   - Adding new clients requires SP modifications
   - No plugin architecture

3. **Testing**
   - No unit tests for SP logic
   - Integration tests require full DB
   - Long test execution times

4. **Monitoring**
   - Limited visibility into workflow steps
   - No retry/compensation mechanisms
   - Error handling is inconsistent

## Target State Architecture

### Modernization Goals

1. **Workflow Orchestration** - Use Temporal for reliable workflow execution
2. **Saga Pattern** - Implement compensation for failed operations
3. **Plugin Architecture** - Enable client/region extensibility
4. **Rules Engine** - Extract validation rules to Drools
5. **Comprehensive Testing** - E2E, integration, and unit tests

### Key Components

| Component | Technology | Purpose |
|-----------|------------|---------|
| Workflow | Temporal | Orchestration, retry, visibility |
| Rules | Drools | Business rule externalization |
| API | Spring Boot | REST endpoints |
| Testing | Karate | E2E testing |

## Variation Matrix

### Database Versions
| Version | Description | Deployments |
|---------|-------------|-------------|
| V0 | Legacy schema | US, EU legacy |
| V2 | Current schema | APAC, new deployments |

### Regions
| Region | Code | Special Requirements |
|--------|------|---------------------|
| Korea | ASIA-KR | Customs clearance, KC mark |
| India | ASIA-IN | GST validation, HSN codes |
| Singapore | ASIA-SG | Trade permits |
| Thailand | ASIA-TH | Standard |

### Clients
| Client | Code | Special Requirements |
|--------|------|---------------------|
| Nike | NIKE | Style-color validation, UPC |
| H&M | HM | Article number, EAN13 |
| Zara | ZARA | Fashion codes |
| Standard | GENERIC | Base validation |

## Migration Strategy

### Phase 1: Foundation
- Set up module structure
- Implement domain models
- Create activity interfaces

### Phase 2: Workflow
- Implement Temporal workflows
- Add compensation logic
- Create variation layer

### Phase 3: Plugins
- Extract client-specific logic
- Implement region plugins
- Add lifecycle hooks

### Phase 4: Testing
- Karate E2E tests
- Unit test coverage
- Integration tests

### Phase 5: Migration
- Dual-write implementation
- Shadow mode testing
- Gradual cutover

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Data inconsistency | High | Dual-write validation |
| Performance regression | Medium | Benchmarking, optimization |
| Feature parity gaps | High | Comprehensive E2E tests |
| Rollback complexity | Medium | Feature flags |

## Success Criteria

1. **Functional Parity** - All legacy features supported
2. **Test Coverage** - >80% unit test coverage
3. **Performance** - Within 10% of legacy latency
4. **Extensibility** - New client in <1 day
5. **Observability** - Full workflow visibility

## Timeline

| Phase | Duration | Deliverables |
|-------|----------|--------------|
| Discovery | 2 weeks | This document |
| Foundation | 3 weeks | Core modules |
| Workflow | 4 weeks | Temporal integration |
| Plugins | 3 weeks | Client/region plugins |
| Testing | 2 weeks | Test suite |
| Migration | 4 weeks | Production rollout |

## Appendix

### Stored Procedure Mapping

| Legacy SP | New Component |
|-----------|---------------|
| `nsp_ValidatePO` | `ValidationActivity` |
| `nsp_CreateReceipt` | `ReceiptCreationActivity` |
| `nsp_MapLottables` | `MappingActivity` |
| `nsp_AllocateInventory` | `InventoryAllocationActivity` |
| `nsp_UpdatePOStatus` | `POStatusUpdateActivity` |

### Data Model Mapping

| Legacy Table | New Entity |
|--------------|------------|
| `PO` | `POEntity` |
| `RECEIPT` | `ReceiptEntity` |
| `RECEIPTDETAIL` | `ReceiptDetailEntity` |
| `LOTxLOCxID` | `InventoryEntity` |
