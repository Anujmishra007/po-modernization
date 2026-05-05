# Local Development Setup

This guide helps you set up the PO Modernization project for local development.

## Prerequisites

- **Java 17+** - OpenJDK or Oracle JDK
- **Docker** - For running dependencies
- **Docker Compose** - v2.x recommended
- **Maven 3.8+** - Or use included `mvnw`

## Quick Start

```bash
# 1. Start dependencies (PostgreSQL, Temporal, Redis)
make deps

# 2. Build the application
make build-fast

# 3. Run the application
make run

# Or use the setup script for full setup:
./scripts/setup-local.sh
```

## Service URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| **PostgreSQL** | localhost:5432 | postgres/postgres |
| **Temporal** | localhost:7233 | - |
| **Temporal UI** | http://localhost:8088 | - |
| **Redis** | localhost:6379 | - |
| **Kafka** | localhost:9092 | - |
| **Kafka UI** | http://localhost:8089 | - |
| **MailHog** | http://localhost:8025 | SMTP: 1025 |

After starting the application:

| Endpoint | URL |
|----------|-----|
| **API Base** | http://localhost:8080/api |
| **Swagger UI** | http://localhost:8080/api/swagger-ui.html |
| **Health** | http://localhost:8080/api/actuator/health |
| **Metrics** | http://localhost:8080/api/actuator/metrics |

## Makefile Targets

```bash
make help          # Show all available targets

# Setup
make setup         # Full setup (deps + build)
make deps          # Start dependencies only
make start         # Start dependencies
make stop          # Stop dependencies
make restart       # Restart dependencies

# Build & Run
make build         # Build with tests
make build-fast    # Build without tests
make test          # Run tests
make run           # Run application
make run-debug     # Run with debug port 5005

# Docker
make docker-build  # Build Docker image
make docker-up     # Start full stack
make docker-down   # Stop full stack

# Utilities
make status        # Show service URLs
make logs          # Show dependency logs
make health        # Check service health
make clean         # Clean build + volumes
make db-connect    # Connect to PostgreSQL
make db-reset      # Reset database
```

## Running from IDE

### IntelliJ IDEA

1. Import as Maven project
2. Enable annotation processing (Lombok)
3. Create Run Configuration:
   - Main class: `com.wms.po.api.POModernizationApplication`
   - Active profiles: `local`
   - Working directory: `$MODULE_WORKING_DIR$`

### VS Code

1. Install Java Extension Pack
2. Open project folder
3. Run via `Spring Boot Dashboard`

## Running Tests

### Unit Tests
```bash
./mvnw test
```

### Integration Tests
```bash
./mvnw verify -P integration
```

### Karate E2E Tests
```bash
# Start the application first
make run

# In another terminal
./mvnw test -pl po-test -Dtest=KarateTestRunner -Dkarate.env=local
```

### Run specific test tags
```bash
./mvnw test -pl po-test -Dtest=KarateTestRunner -Dkarate.options="--tags @smoke"
./mvnw test -pl po-test -Dtest=KarateTestRunner -Dkarate.options="--tags @populate"
./mvnw test -pl po-test -Dtest=KarateTestRunner -Dkarate.options="--tags @saga"
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | 8080 | API server port |
| `SPRING_PROFILES_ACTIVE` | local | Active profile |
| `TEMPORAL_HOST` | localhost:7233 | Temporal server |
| `DB_HOST` | localhost | Database host |
| `DB_PORT` | 5432 | Database port |
| `REDIS_HOST` | localhost | Redis host |

## Troubleshooting

### Temporal not starting
```bash
docker logs po-temporal
# Check if PostgreSQL is ready first
docker exec po-postgres pg_isready -U postgres
```

### Build failures
```bash
# Clean and rebuild
./mvnw clean install -U
```

### Port conflicts
```bash
# Check what's using the port
lsof -i :8080
# Kill the process or change the port
```

## API Examples

### Create PO
```bash
curl -X POST http://localhost:8080/api/v1/po \
  -H "Content-Type: application/json" \
  -H "X-User-Id: testuser" \
  -H "X-Region: US" \
  -d '{
    "storerKey": "STORER001",
    "facility": "DC01",
    "externPoKey": "PO-001",
    "supplierKey": "SUPPLIER001"
  }'
```

### Populate PO
```bash
curl -X POST http://localhost:8080/api/v1/population/po/PO-001 \
  -H "Content-Type: application/json" \
  -H "X-User-Id: testuser" \
  -H "X-Region: US" \
  -d '{
    "storerKey": "STORER001",
    "facility": "DC01"
  }'
```

### Check Health
```bash
curl http://localhost:8080/api/actuator/health
```
