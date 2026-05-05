.PHONY: help setup deps start stop restart build build-fast test run run-debug docker-build docker-up docker-down status logs health clean db-connect db-reset

# Default target
help:
	@echo "PO Modernization - Available Commands"
	@echo ""
	@echo "Setup & Dependencies:"
	@echo "  make setup        - Full setup (deps + build)"
	@echo "  make deps         - Start dependencies only"
	@echo "  make start        - Start dependencies"
	@echo "  make stop         - Stop dependencies"
	@echo "  make restart      - Restart dependencies"
	@echo ""
	@echo "Build & Run:"
	@echo "  make build        - Build with tests"
	@echo "  make build-fast   - Build without tests"
	@echo "  make test         - Run tests"
	@echo "  make run          - Run application"
	@echo "  make run-debug    - Run with debug port 5005"
	@echo ""
	@echo "Docker:"
	@echo "  make docker-build - Build Docker image"
	@echo "  make docker-up    - Start full stack"
	@echo "  make docker-down  - Stop full stack"
	@echo ""
	@echo "Utilities:"
	@echo "  make status       - Show service URLs"
	@echo "  make logs         - Show dependency logs"
	@echo "  make health       - Check service health"
	@echo "  make clean        - Clean build + volumes"
	@echo "  make db-connect   - Connect to PostgreSQL"
	@echo "  make db-reset     - Reset database"

# Setup
setup: deps build-fast
	@echo "Setup complete!"

deps:
	docker-compose -f docker-compose-local.yml up -d
	@echo "Waiting for services to be ready..."
	@sleep 10
	@make health

start:
	docker-compose -f docker-compose-local.yml up -d

stop:
	docker-compose -f docker-compose-local.yml down

restart: stop start

# Build
build:
	./mvnw clean install

build-fast:
	./mvnw clean install -DskipTests -Dmaven.test.skip=true

test:
	./mvnw test

# Run
run:
	./mvnw spring-boot:run -pl po-api -Dspring-boot.run.profiles=local

run-debug:
	./mvnw spring-boot:run -pl po-api -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

# Docker
docker-build:
	docker build -t po-modernization:latest .

docker-up:
	docker-compose up -d

docker-down:
	docker-compose down

# Utilities
status:
	@echo ""
	@echo "=== Service URLs ==="
	@echo "PostgreSQL:    localhost:5432 (postgres/postgres)"
	@echo "Temporal:      localhost:7233"
	@echo "Temporal UI:   http://localhost:8088"
	@echo "Redis:         localhost:6379"
	@echo ""
	@echo "=== Application URLs ==="
	@echo "API Base:      http://localhost:8080/api"
	@echo "Swagger UI:    http://localhost:8080/api/swagger-ui.html"
	@echo "Health:        http://localhost:8080/api/actuator/health"
	@echo ""

logs:
	docker-compose -f docker-compose-local.yml logs -f

health:
	@echo "Checking service health..."
	@docker-compose -f docker-compose-local.yml ps
	@echo ""
	@echo "PostgreSQL:"
	@docker exec po-postgres pg_isready -U postgres || echo "Not ready"
	@echo ""
	@echo "Redis:"
	@docker exec po-redis redis-cli ping || echo "Not ready"

clean:
	./mvnw clean
	docker-compose -f docker-compose-local.yml down -v
	rm -rf target/

db-connect:
	docker exec -it po-postgres psql -U postgres -d po_service

db-reset:
	docker exec po-postgres psql -U postgres -c "DROP DATABASE IF EXISTS po_service;"
	docker exec po-postgres psql -U postgres -c "CREATE DATABASE po_service;"
	@echo "Database reset complete"
