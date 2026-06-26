#!/bin/bash
#
# PO Modernization - Full E2E Setup Script
# =========================================
# This script automates the complete E2E environment setup:
# 1. Checks prerequisites (Java 17, Maven, Docker)
# 2. Starts infrastructure (PostgreSQL, Temporal)
# 3. Builds the project
# 4. Starts the application
# 5. Runs E2E tests with Karate
#
# Usage:
#   ./scripts/setup-e2e.sh          # Full setup + run tests
#   ./scripts/setup-e2e.sh start    # Start infrastructure + app only
#   ./scripts/setup-e2e.sh test     # Run tests only (assumes app running)
#   ./scripts/setup-e2e.sh stop     # Stop everything
#   ./scripts/setup-e2e.sh status   # Check status of all services
#   ./scripts/setup-e2e.sh logs     # Show application logs
#   ./scripts/setup-e2e.sh clean    # Stop + remove volumes + clean build

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
MVN_CMD="${MVN_CMD:-/opt/homebrew/bin/mvn}"
APP_PORT=8080
TEMPORAL_PORT=7233
TEMPORAL_UI_PORT=8081
POSTGRES_APP_PORT=5433
POSTGRES_TEMPORAL_PORT=5432
APP_PID_FILE="$PROJECT_DIR/.app.pid"
APP_LOG_FILE="$PROJECT_DIR/logs/app.log"

# Ensure logs directory exists
mkdir -p "$PROJECT_DIR/logs"

# -----------------------------------------------------------------------------
# Helper Functions
# -----------------------------------------------------------------------------

print_header() {
    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
}

print_step() {
    echo -e "${GREEN}▶ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✖ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✔ $1${NC}"
}

check_command() {
    if command -v "$1" &> /dev/null; then
        return 0
    else
        return 1
    fi
}

wait_for_port() {
    local port=$1
    local name=$2
    local max_attempts=${3:-30}
    local attempt=1

    echo -n "  Waiting for $name (port $port)"
    while ! nc -z localhost "$port" 2>/dev/null; do
        if [ $attempt -ge $max_attempts ]; then
            echo ""
            print_error "$name failed to start within $max_attempts seconds"
            return 1
        fi
        echo -n "."
        sleep 1
        ((attempt++))
    done
    echo " ready!"
    return 0
}

wait_for_health() {
    local url=$1
    local name=$2
    local max_attempts=${3:-30}
    local attempt=1

    echo -n "  Waiting for $name health check"
    while ! curl -sf "$url" > /dev/null 2>&1; do
        if [ $attempt -ge $max_attempts ]; then
            echo ""
            print_error "$name health check failed within $max_attempts seconds"
            return 1
        fi
        echo -n "."
        sleep 1
        ((attempt++))
    done
    echo " healthy!"
    return 0
}

# -----------------------------------------------------------------------------
# Prerequisite Checks
# -----------------------------------------------------------------------------

check_prerequisites() {
    print_header "Checking Prerequisites"

    local all_ok=true

    # Check Java 17
    print_step "Checking Java 17..."
    if [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_VERSION=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)
        print_success "Java: $JAVA_VERSION"
    else
        print_error "Java 17 not found at $JAVA_HOME"
        echo "  Install with: brew install openjdk@17"
        all_ok=false
    fi

    # Check Maven
    print_step "Checking Maven..."
    if [ -x "$MVN_CMD" ]; then
        MVN_VERSION=$("$MVN_CMD" -version 2>&1 | head -1)
        print_success "Maven: $MVN_VERSION"
    else
        print_error "Maven not found at $MVN_CMD"
        echo "  Install with: brew install maven"
        all_ok=false
    fi

    # Check Docker
    print_step "Checking Docker..."
    if check_command docker; then
        if docker info > /dev/null 2>&1; then
            DOCKER_VERSION=$(docker --version)
            print_success "Docker: $DOCKER_VERSION"
        else
            print_error "Docker is installed but not running"
            echo "  Please start Docker Desktop from Applications"
            all_ok=false
        fi
    else
        print_error "Docker not found"
        echo "  Install with: brew install --cask docker"
        echo "  Then start Docker Desktop from Applications"
        all_ok=false
    fi

    # Check docker-compose
    print_step "Checking Docker Compose..."
    if check_command docker-compose || docker compose version > /dev/null 2>&1; then
        print_success "Docker Compose: available"
    else
        print_error "Docker Compose not found"
        all_ok=false
    fi

    if [ "$all_ok" = false ]; then
        echo ""
        print_error "Prerequisites check failed. Please install missing components."
        exit 1
    fi

    print_success "All prerequisites satisfied!"
}

# -----------------------------------------------------------------------------
# Infrastructure Management
# -----------------------------------------------------------------------------

start_infrastructure() {
    print_header "Starting Infrastructure"

    cd "$PROJECT_DIR"

    # Check if already running
    if docker ps | grep -q "temporal-server"; then
        print_warning "Infrastructure already running"
        return 0
    fi

    print_step "Starting Docker containers..."

    # Use docker compose (v2) or docker-compose (v1)
    if docker compose version > /dev/null 2>&1; then
        docker compose up -d
    else
        docker-compose up -d
    fi

    print_step "Waiting for services to be ready..."

    # Wait for PostgreSQL (Temporal)
    wait_for_port $POSTGRES_TEMPORAL_PORT "PostgreSQL (Temporal)" 60

    # Wait for PostgreSQL (App)
    wait_for_port $POSTGRES_APP_PORT "PostgreSQL (App)" 60

    # Wait for Temporal
    wait_for_port $TEMPORAL_PORT "Temporal Server" 90

    # Wait for Temporal UI
    wait_for_port $TEMPORAL_UI_PORT "Temporal UI" 60

    print_success "Infrastructure started successfully!"
    echo ""
    echo "  Services:"
    echo "    - PostgreSQL (App):     localhost:$POSTGRES_APP_PORT"
    echo "    - PostgreSQL (Temporal): localhost:$POSTGRES_TEMPORAL_PORT"
    echo "    - Temporal Server:       localhost:$TEMPORAL_PORT"
    echo "    - Temporal UI:           http://localhost:$TEMPORAL_UI_PORT"
}

stop_infrastructure() {
    print_header "Stopping Infrastructure"

    cd "$PROJECT_DIR"

    print_step "Stopping Docker containers..."

    if docker compose version > /dev/null 2>&1; then
        docker compose down
    else
        docker-compose down
    fi

    print_success "Infrastructure stopped"
}

# -----------------------------------------------------------------------------
# Application Management
# -----------------------------------------------------------------------------

build_project() {
    print_header "Building Project"

    cd "$PROJECT_DIR"

    print_step "Running Maven build..."

    JAVA_HOME="$JAVA_HOME" "$MVN_CMD" clean install -DskipTests -q

    print_success "Build completed successfully!"
}

start_application() {
    print_header "Starting Application"

    cd "$PROJECT_DIR"

    # Check if already running
    if [ -f "$APP_PID_FILE" ]; then
        local pid=$(cat "$APP_PID_FILE")
        if ps -p "$pid" > /dev/null 2>&1; then
            print_warning "Application already running (PID: $pid)"
            return 0
        fi
    fi

    # Check if port is in use
    if nc -z localhost $APP_PORT 2>/dev/null; then
        print_warning "Port $APP_PORT already in use"
        return 0
    fi

    print_step "Starting PO Modernization API..."

    # Determine profile based on infrastructure
    local profile="local"
    if docker ps | grep -q "temporal-server"; then
        profile="docker"
        print_step "Using 'docker' profile (Temporal + PostgreSQL enabled)"
    else
        print_step "Using 'local' profile (H2 + Temporal disabled)"
    fi

    # Start application in background
    cd "$PROJECT_DIR/po-api"
    nohup "$JAVA_HOME/bin/java" \
        -jar target/po-api-1.0.0-SNAPSHOT-exec.jar \
        --spring.profiles.active=$profile \
        > "$APP_LOG_FILE" 2>&1 &

    echo $! > "$APP_PID_FILE"

    print_step "Application starting (PID: $(cat $APP_PID_FILE))..."

    # Wait for application to be ready
    wait_for_health "http://localhost:$APP_PORT/actuator/health" "Application" 60

    print_success "Application started successfully!"
    echo ""
    echo "  Endpoints:"
    echo "    - API Base:      http://localhost:$APP_PORT/api/v1"
    echo "    - Health:        http://localhost:$APP_PORT/api/v1/health"
    echo "    - Swagger UI:    http://localhost:$APP_PORT/swagger-ui.html"
    echo "    - H2 Console:    http://localhost:$APP_PORT/h2-console"
    echo "    - Actuator:      http://localhost:$APP_PORT/actuator"
}

stop_application() {
    print_header "Stopping Application"

    if [ -f "$APP_PID_FILE" ]; then
        local pid=$(cat "$APP_PID_FILE")
        if ps -p "$pid" > /dev/null 2>&1; then
            print_step "Stopping application (PID: $pid)..."
            kill "$pid" 2>/dev/null || true
            sleep 2
            # Force kill if still running
            if ps -p "$pid" > /dev/null 2>&1; then
                kill -9 "$pid" 2>/dev/null || true
            fi
            print_success "Application stopped"
        else
            print_warning "Application not running (stale PID file)"
        fi
        rm -f "$APP_PID_FILE"
    else
        # Try to find and kill by port
        local pid=$(lsof -ti:$APP_PORT 2>/dev/null || true)
        if [ -n "$pid" ]; then
            print_step "Stopping application on port $APP_PORT (PID: $pid)..."
            kill "$pid" 2>/dev/null || true
            print_success "Application stopped"
        else
            print_warning "Application not running"
        fi
    fi
}

# -----------------------------------------------------------------------------
# Testing
# -----------------------------------------------------------------------------

run_unit_tests() {
    print_header "Running Unit Tests"

    cd "$PROJECT_DIR"

    print_step "Executing unit tests..."

    JAVA_HOME="$JAVA_HOME" "$MVN_CMD" test -q

    print_success "Unit tests completed!"
}

run_e2e_tests() {
    print_header "Running E2E Tests (Karate)"

    cd "$PROJECT_DIR"

    # Check if application is running
    if ! curl -sf "http://localhost:$APP_PORT/actuator/health" > /dev/null 2>&1; then
        print_error "Application not running. Start it first with: ./scripts/setup-e2e.sh start"
        exit 1
    fi

    print_step "Executing Karate E2E tests..."

    JAVA_HOME="$JAVA_HOME" "$MVN_CMD" verify -pl po-test \
        -Dkarate.env=local \
        -Dkarate.options="--tags ~@ignore" \
        -DskipUTs=true

    print_success "E2E tests completed!"
    echo ""
    echo "  Reports available at:"
    echo "    - Karate: $PROJECT_DIR/po-test/target/karate-reports/karate-summary.html"
}

run_all_tests() {
    print_header "Running All Tests"

    run_unit_tests
    run_e2e_tests

    print_success "All tests completed!"
}

# -----------------------------------------------------------------------------
# Status & Logs
# -----------------------------------------------------------------------------

show_status() {
    print_header "Service Status"

    echo "Infrastructure:"
    echo "───────────────"

    # PostgreSQL (Temporal)
    if nc -z localhost $POSTGRES_TEMPORAL_PORT 2>/dev/null; then
        print_success "PostgreSQL (Temporal):  Running on port $POSTGRES_TEMPORAL_PORT"
    else
        print_error "PostgreSQL (Temporal):  Not running"
    fi

    # PostgreSQL (App)
    if nc -z localhost $POSTGRES_APP_PORT 2>/dev/null; then
        print_success "PostgreSQL (App):       Running on port $POSTGRES_APP_PORT"
    else
        print_error "PostgreSQL (App):       Not running"
    fi

    # Temporal
    if nc -z localhost $TEMPORAL_PORT 2>/dev/null; then
        print_success "Temporal Server:        Running on port $TEMPORAL_PORT"
    else
        print_error "Temporal Server:        Not running"
    fi

    # Temporal UI
    if nc -z localhost $TEMPORAL_UI_PORT 2>/dev/null; then
        print_success "Temporal UI:            Running on http://localhost:$TEMPORAL_UI_PORT"
    else
        print_error "Temporal UI:            Not running"
    fi

    echo ""
    echo "Application:"
    echo "────────────"

    # Application
    if curl -sf "http://localhost:$APP_PORT/actuator/health" > /dev/null 2>&1; then
        local health=$(curl -s "http://localhost:$APP_PORT/actuator/health" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
        print_success "PO Modernization API:   Running on http://localhost:$APP_PORT (Status: $health)"

        if [ -f "$APP_PID_FILE" ]; then
            echo "                        PID: $(cat $APP_PID_FILE)"
        fi
    else
        print_error "PO Modernization API:   Not running"
    fi

    echo ""
    echo "Docker Containers:"
    echo "──────────────────"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null | grep -E "temporal|postgres|po-" || echo "  No containers running"
}

show_logs() {
    print_header "Application Logs"

    if [ -f "$APP_LOG_FILE" ]; then
        tail -100 "$APP_LOG_FILE"
    else
        print_warning "No log file found at $APP_LOG_FILE"
    fi
}

follow_logs() {
    print_header "Following Application Logs (Ctrl+C to stop)"

    if [ -f "$APP_LOG_FILE" ]; then
        tail -f "$APP_LOG_FILE"
    else
        print_warning "No log file found at $APP_LOG_FILE"
    fi
}

# -----------------------------------------------------------------------------
# Cleanup
# -----------------------------------------------------------------------------

clean_all() {
    print_header "Cleaning Everything"

    # Stop application
    stop_application

    # Stop infrastructure with volumes
    cd "$PROJECT_DIR"
    print_step "Removing Docker containers and volumes..."
    if docker compose version > /dev/null 2>&1; then
        docker compose down -v
    else
        docker-compose down -v
    fi

    # Clean Maven build
    print_step "Cleaning Maven build..."
    JAVA_HOME="$JAVA_HOME" "$MVN_CMD" clean -q

    # Remove logs
    print_step "Removing logs..."
    rm -rf "$PROJECT_DIR/logs"

    print_success "Cleanup completed!"
}

# -----------------------------------------------------------------------------
# Full E2E Setup
# -----------------------------------------------------------------------------

full_setup() {
    print_header "PO Modernization - Full E2E Setup"

    echo "This script will:"
    echo "  1. Check prerequisites"
    echo "  2. Start infrastructure (PostgreSQL, Temporal)"
    echo "  3. Build the project"
    echo "  4. Start the application"
    echo "  5. Run E2E tests"
    echo ""

    check_prerequisites
    start_infrastructure
    build_project
    start_application
    run_e2e_tests

    print_header "Setup Complete!"

    echo "Your E2E environment is now running:"
    echo ""
    echo "  Application:    http://localhost:$APP_PORT"
    echo "  Swagger UI:     http://localhost:$APP_PORT/swagger-ui.html"
    echo "  Temporal UI:    http://localhost:$TEMPORAL_UI_PORT"
    echo "  H2 Console:     http://localhost:$APP_PORT/h2-console"
    echo ""
    echo "Commands:"
    echo "  ./scripts/setup-e2e.sh status  - Check service status"
    echo "  ./scripts/setup-e2e.sh test    - Run tests again"
    echo "  ./scripts/setup-e2e.sh logs    - View application logs"
    echo "  ./scripts/setup-e2e.sh stop    - Stop everything"
    echo "  ./scripts/setup-e2e.sh clean   - Clean up completely"
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

cd "$PROJECT_DIR"

case "${1:-}" in
    start)
        check_prerequisites
        start_infrastructure
        build_project
        start_application
        show_status
        ;;
    stop)
        stop_application
        stop_infrastructure
        ;;
    restart)
        stop_application
        stop_infrastructure
        start_infrastructure
        start_application
        ;;
    test)
        run_e2e_tests
        ;;
    test-all)
        run_all_tests
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs
        ;;
    logs-f)
        follow_logs
        ;;
    clean)
        clean_all
        ;;
    build)
        build_project
        ;;
    infra)
        check_prerequisites
        start_infrastructure
        ;;
    app)
        start_application
        ;;
    help|--help|-h)
        echo "PO Modernization E2E Setup Script"
        echo ""
        echo "Usage: $0 [command]"
        echo ""
        echo "Commands:"
        echo "  (none)    Full setup: prerequisites + infra + build + app + tests"
        echo "  start     Start infrastructure and application"
        echo "  stop      Stop application and infrastructure"
        echo "  restart   Restart everything"
        echo "  test      Run E2E tests only (Karate)"
        echo "  test-all  Run unit tests + E2E tests"
        echo "  status    Show status of all services"
        echo "  logs      Show application logs"
        echo "  logs-f    Follow application logs"
        echo "  clean     Stop everything and remove volumes/build artifacts"
        echo "  build     Build the project only"
        echo "  infra     Start infrastructure only"
        echo "  app       Start application only"
        echo "  help      Show this help message"
        ;;
    *)
        full_setup
        ;;
esac
