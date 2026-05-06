#!/bin/bash
# ═══════════════════════════════════════════════════════════
# PO Modernization - Run Flow-Specific Tests
# ═══════════════════════════════════════════════════════════
# Usage: ./scripts/run-flow-tests.sh <flow> [OPTIONS]
#
# Runs Karate tests for a specific flow (F1-F10)

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
KARATE_THREADS=${KARATE_THREADS:-3}
KARATE_ENV=${KARATE_ENV:-local}

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

show_help() {
    echo "PO Modernization - Run Flow-Specific Tests"
    echo ""
    echo "Usage: $0 <flow> [OPTIONS]"
    echo ""
    echo "Flows:"
    echo "  F1    PO Creation"
    echo "  F2    ASN Population"
    echo "  F3    Receipt Finalization"
    echo "  F4    Cross-Dock Allocation"
    echo "  F5    Lottable Processing"
    echo "  F6    Putaway Release"
    echo "  F7    Trade Return"
    echo "  F8    PO Cancellation"
    echo "  F9    Archival/Purge"
    echo "  F10   Compensation/Saga"
    echo ""
    echo "Options:"
    echo "  --env ENV         Karate environment (default: local)"
    echo "  --threads N       Karate parallel threads (default: 3)"
    echo "  --type TYPE       Test type: Happy, Unhappy, Edge, Error, Compensation"
    echo "  --priority P      Test priority: P1, P2, P3"
    echo "  --help            Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 F1                    # Run all F1 tests"
    echo "  $0 F2 --type Happy       # Run F2 happy path tests"
    echo "  $0 F3 --priority P1      # Run F3 P1 tests only"
    echo "  $0 F10 --type Compensation  # Run compensation tests"
    echo ""
}

# ═══════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════

main() {
    local flow=""
    local test_type=""
    local priority=""
    local tags=""

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            F[0-9]|F10)
                flow="$1"
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
            --type)
                test_type="$2"
                shift 2
                ;;
            --priority)
                priority="$2"
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

    if [ -z "$flow" ]; then
        log_error "Flow is required"
        show_help
        exit 1
    fi

    # Build tags
    tags="@$flow"

    if [ -n "$test_type" ]; then
        tags="$tags and @$test_type"
    fi

    if [ -n "$priority" ]; then
        tags="$tags and @$priority"
    fi

    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "  Running Flow Tests: $flow"
    echo "═══════════════════════════════════════════════════════════"
    echo ""
    echo "Configuration:"
    echo "  Tags: $tags"
    echo "  Environment: $KARATE_ENV"
    echo "  Threads: $KARATE_THREADS"
    echo ""

    cd "$PROJECT_ROOT"

    ./mvnw test -pl po-test \
        -Dtest=KarateTestRunner \
        -Dkarate.env="$KARATE_ENV" \
        -Dkarate.threads="$KARATE_THREADS" \
        -Dkarate.options="--tags '$tags'"

    log_success "Flow $flow tests completed"
    echo ""
    echo "Report: po-test/target/karate-reports/karate-summary.html"
}

main "$@"
