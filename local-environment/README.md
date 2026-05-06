# PO Modernization - Local Environment

Docker Compose setup for local development and testing.

## Quick Start

```bash
# Start test environment (minimal)
./scripts/setup-local-env.sh --test

# Start full development environment (with UIs)
./scripts/setup-local-env.sh --full

# Stop all containers
./scripts/setup-local-env.sh --down

# Clean up (remove volumes)
./scripts/setup-local-env.sh --clean
```

## Services

### Test Environment (`docker-compose-test.yml`)

| Service | Port | Purpose |
|---------|------|---------|
| PostgreSQL | 5433 | Test database |
| Temporal | 7233 | Workflow engine |
| Kafka | 9092 | Event streaming |
| Redis | 6379 | Caching/locks |

### Full Environment (`docker-compose.yml`)

| Service | Port | Purpose |
|---------|------|---------|
| PostgreSQL | 5433 | Development database |
| Temporal | 7233 | Workflow engine |
| Temporal UI | 8088 | Workflow visualization |
| Kafka | 9092 | Event streaming |
| Kafka UI | 8089 | Kafka visualization |
| Redis | 6379 | Caching/locks |
| Zookeeper | 2181 | Kafka coordination |

## Database Connection

```
Host:     localhost
Port:     5433
Database: po_test (test) / po_modernization (full)
User:     wms
Password: wms123

JDBC URL: jdbc:postgresql://localhost:5433/po_test
```

## Seed Data

The `seed-data/` directory contains SQL scripts that run automatically:

- `00-schema.sql` - Database schema (tables, indexes, sequences)
- `01-test-data.sql` - Test data (storers, SKUs, POs, receipts)
- `99-cleanup.sql` - Reset script (truncate and reload)

### Test Data Summary

| Entity | Count | Examples |
|--------|-------|----------|
| Storers | 11 | NIKE_KR, HM_KR, TEST_STORER_001 |
| Facilities | 6 | KR01, IN01, SG01, TEST01 |
| SKUs | 15 | TEST-SKU-001, SHOE-001 |
| Locations | 15 | RECV-01, A-01-01, TEST-LOC-FULL |
| POs | 6 | PO-TEST-001 to PO-HM-001 |
| Receipts | 3 | RCV-TEST-001, RCV-NIKE-001 |

## Manual Database Operations

```bash
# Connect to database
PGPASSWORD=wms123 psql -h localhost -p 5433 -U wms -d po_test

# Load test data
./scripts/load-test-data.sh

# Cleanup and reload
./scripts/load-test-data.sh --cleanup

# Verify data
./scripts/load-test-data.sh --verify
```

## Troubleshooting

### Port already in use

```bash
# Find process using port
lsof -i :5433

# Or use different ports
DB_PORT=5434 docker-compose -f docker-compose-test.yml up -d
```

### Container logs

```bash
docker logs po-test-postgres
docker logs po-test-temporal
docker logs po-test-kafka
```

### Reset everything

```bash
./scripts/setup-local-env.sh --clean
./scripts/setup-local-env.sh --test
```
