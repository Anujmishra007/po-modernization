#!/bin/bash
#
# Run CI Pipeline Locally
# =======================
# Simulates the CI pipeline on your local machine using Docker.
# This is useful for testing before pushing to GitHub/GitLab.
#
# Usage:
#   ./scripts/run-ci-local.sh           # Run full pipeline
#   ./scripts/run-ci-local.sh unit      # Run unit tests only
#   ./scripts/run-ci-local.sh e2e       # Run E2E tests only
#   ./scripts/run-ci-local.sh build     # Build only

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
MVN_CMD="${MVN_CMD:-/opt/homebrew/bin/mvn}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_header() {
    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  CI Stage: $1${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo ""
}

print_success() {
    echo -e "${GREEN}✔ $1${NC}"
}

print_error() {
    echo -e "${RED}✖ $1${NC}"
}

cd "$PROJECT_DIR"
export PATH="$HOME/.rd/bin:$PATH"

# Track overall status
FAILED=0

# ============================================
# Build Stage
# ============================================
run_build() {
    print_header "BUILD"

    echo "Building project..."
    if JAVA_HOME="$JAVA_HOME" "$MVN_CMD" clean install -DskipTests --batch-mode -q; then
        print_success "Build completed"
    else
        print_error "Build failed"
        FAILED=1
    fi
}

# ============================================
# Unit Tests Stage
# ============================================
run_unit_tests() {
    print_header "UNIT TESTS"

    echo "Running unit tests..."
    if JAVA_HOME="$JAVA_HOME" "$MVN_CMD" test -DskipITs=true --batch-mode; then
        print_success "Unit tests passed"
    else
        print_error "Unit tests failed"
        FAILED=1
    fi
}

# ============================================
# E2E Tests Stage
# ============================================
run_e2e_tests() {
    print_header "E2E TESTS"

    # Check if infrastructure is running
    if ! nc -z localhost 5433 2>/dev/null; then
        echo "Starting infrastructure..."
        docker compose up -d

        echo "Waiting for services..."
        for i in {1..60}; do
            nc -z localhost 5433 2>/dev/null && break || sleep 2
        done
        for i in {1..90}; do
            nc -z localhost 7233 2>/dev/null && break || sleep 2
        done
    fi

    # Check if application is running
    if ! curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "Starting application..."
        cd po-api
        nohup "$JAVA_HOME/bin/java" -jar target/po-api-1.0.0-SNAPSHOT-exec.jar \
            --spring.profiles.active=docker \
            > "$PROJECT_DIR/logs/ci-app.log" 2>&1 &
        APP_PID=$!
        cd "$PROJECT_DIR"

        echo "Waiting for application (PID: $APP_PID)..."
        for i in {1..60}; do
            curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1 && break || sleep 2
        done
    fi

    # Verify application is healthy
    if ! curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        print_error "Application failed to start"
        FAILED=1
        return
    fi

    echo "Running Karate E2E tests..."
    if JAVA_HOME="$JAVA_HOME" "$MVN_CMD" verify -pl po-test \
        -Dkarate.env=local \
        -Dkarate.options="--tags ~@ignore" \
        -DskipUTs=true \
        --batch-mode; then
        print_success "E2E tests passed"
        echo ""
        echo "Reports: po-test/target/karate-reports/karate-summary.html"
    else
        print_error "E2E tests failed"
        FAILED=1
    fi
}

# ============================================
# Summary
# ============================================
print_summary() {
    print_header "PIPELINE SUMMARY"

    if [ $FAILED -eq 0 ]; then
        echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║         ✅ CI PIPELINE PASSED              ║${NC}"
        echo -e "${GREEN}╚════════════════════════════════════════════╝${NC}"
    else
        echo -e "${RED}╔════════════════════════════════════════════╗${NC}"
        echo -e "${RED}║         ❌ CI PIPELINE FAILED              ║${NC}"
        echo -e "${RED}╚════════════════════════════════════════════╝${NC}"
    fi
}

# ============================================
# Main
# ============================================
mkdir -p "$PROJECT_DIR/logs"

case "${1:-all}" in
    build)
        run_build
        ;;
    unit)
        run_unit_tests
        ;;
    e2e)
        run_e2e_tests
        ;;
    all)
        run_build
        run_unit_tests
        run_e2e_tests
        print_summary
        ;;
    *)
        echo "Usage: $0 [build|unit|e2e|all]"
        exit 1
        ;;
esac

exit $FAILED
