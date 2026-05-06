#!/bin/bash
# ═══════════════════════════════════════════════════════════
# PO Modernization - Run All Tests (3-Layer Approach)
# ═══════════════════════════════════════════════════════════
# Usage: ./scripts/run-all-tests.sh [OPTIONS]
#
# Executes tests in order:
#   Layer 2: Unit tests (JUnit + Mockito)
#   Layer 3: Workflow tests (Temporal SDK)
#   Layer 1: E2E tests (Karate)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
KARATE_THREADS=${KARATE_THREADS:-5}
KARATE_ENV=${KARATE_ENV:-local}
SKIP_UNIT=${SKIP_UNIT:-false}
SKIP_WORKFLOW=${SKIP_WORKFLOW:-false}
SKIP_E2E=${SKIP_E2E:-false}
START_ENV=${START_ENV:-true}

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "  $1"
    echo "═══════════════════════════════════════════════════════════"
    echo ""
}

# ═══════════════════════════════════════════════════════════
# Test Execution Functions
# ═══════════════════════════════════════════════════════════

run_layer2_tests() {
    if [ "$SKIP_UNIT" == "true" ]; then
        log_warn "Skipping Layer 2 (Unit) tests"
        return 0
    fi

    print_header "Layer 2: Unit & Integration Tests (JUnit + Spring)"

    cd "$PROJECT_ROOT"

    # Run unit tests for domain and service modules
    log_info "Running unit tests..."
    ./mvnw test -pl po-domain,po-service,po-activity,po-plugin -DskipITs \
        -Dtest="**/*Test.java" \
        --fail-at-end

    # Run integration tests
    log_info "Running integration tests..."
    ./mvnw verify -pl po-service -DskipUTs \
        --fail-at-end

    log_success "Layer 2 tests completed"
}

run_layer3_tests() {
    if [ "$SKIP_WORKFLOW" == "true" ]; then
        log_warn "Skipping Layer 3 (Workflow) tests"
        return 0
    fi

    print_header "Layer 3: Temporal Workflow Tests"

    cd "$PROJECT_ROOT"

    log_info "Running workflow tests..."
    ./mvnw test -pl po-workflow \
        -Dtest="**/*WorkflowTest.java,**/*CompensationTest.java" \
        --fail-at-end

    log_success "Layer 3 tests completed"
}

run_layer1_tests() {
    if [ "$SKIP_E2E" == "true" ]; then
        log_warn "Skipping Layer 1 (E2E) tests"
        return 0
    fi

    print_header "Layer 1: Karate E2E Tests"

    cd "$PROJECT_ROOT"

    log_info "Running Karate E2E tests with $KARATE_THREADS threads..."
    ./mvnw test -pl po-test \
        -Dtest=KarateTestRunner \
        -Dkarate.env="$KARATE_ENV" \
        -Dkarate.threads="$KARATE_THREADS" \
        --fail-at-end

    log_success "Layer 1 tests completed"
}

start_test_environment() {
    if [ "$START_ENV" == "false" ]; then
        log_warn "Skipping environment startup"
        return 0
    fi

    print_header "Starting Test Environment"

    "$SCRIPT_DIR/setup-local-env.sh" --test
}

stop_test_environment() {
    if [ "$START_ENV" == "false" ]; then
        return 0
    fi

    print_header "Stopping Test Environment"

    "$SCRIPT_DIR/setup-local-env.sh" --down
}

generate_reports() {
    print_header "Generating Reports"

    cd "$PROJECT_ROOT"

    # Check if Allure is available
    if command -v allure &> /dev/null; then
        log_info "Generating Allure report..."
        allure generate po-test/target/karate-reports -o target/allure-report --clean
        log_success "Allure report generated at: target/allure-report/index.html"
    else
        log_warn "Allure not installed. Skipping report generation."
        log_info "Karate HTML report available at: po-test/target/karate-reports/karate-summary.html"
    fi
}

show_help() {
    echo "PO Modernization - Run All Tests"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --skip-unit       Skip Layer 2 unit tests"
    echo "  --skip-workflow   Skip Layer 3 workflow tests"
    echo "  --skip-e2e        Skip Layer 1 E2E tests"
    echo "  --no-env          Don't start/stop test environment"
    echo "  --env ENV         Karate environment (default: local)"
    echo "  --threads N       Karate parallel threads (default: 5)"
    echo "  --help            Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                           # Run all tests"
    echo "  $0 --skip-e2e                # Run only unit and workflow tests"
    echo "  $0 --no-env --env docker     # Run against existing Docker env"
    echo ""
}

# ═══════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════

main() {
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --skip-unit)
                SKIP_UNIT=true
                shift
                ;;
            --skip-workflow)
                SKIP_WORKFLOW=true
                shift
                ;;
            --skip-e2e)
                SKIP_E2E=true
                shift
                ;;
            --no-env)
                START_ENV=false
                shift
                ;;
            --env)
                KARATE_ENV="$2"
                shift 2
                ;;
            --threads)
                KARATE_THREADS="$2"
                shift 2
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done

    # Print configuration
    print_header "PO Modernization - Test Execution"
    echo "Configuration:"
    echo "  Karate Environment: $KARATE_ENV"
    echo "  Karate Threads: $KARATE_THREADS"
    echo "  Skip Unit Tests: $SKIP_UNIT"
    echo "  Skip Workflow Tests: $SKIP_WORKFLOW"
    echo "  Skip E2E Tests: $SKIP_E2E"
    echo "  Start Environment: $START_ENV"
    echo ""

    # Track start time
    START_TIME=$(date +%s)

    # Execute tests
    trap stop_test_environment EXIT

    start_test_environment
    run_layer2_tests
    run_layer3_tests
    run_layer1_tests
    generate_reports

    # Calculate duration
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))

    print_header "Test Execution Complete"
    echo "Total time: ${DURATION}s"
    echo ""
    log_success "All tests passed!"
}

main "$@"
