#!/bin/bash
# ═══════════════════════════════════════════════════════════
# PO Modernization - Generate Test Reports
# ═══════════════════════════════════════════════════════════
# Usage: ./scripts/generate-reports.sh [OPTIONS]
#
# Generates and optionally serves Allure reports

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

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

check_allure() {
    if ! command -v allure &> /dev/null; then
        log_warn "Allure CLI is not installed"
        echo ""
        echo "Install Allure CLI:"
        echo "  macOS:   brew install allure"
        echo "  Linux:   npm install -g allure-commandline"
        echo "  Windows: scoop install allure"
        echo ""
        return 1
    fi
    return 0
}

generate_allure_report() {
    log_info "Generating Allure report..."

    cd "$PROJECT_ROOT"

    # Collect results from all test modules
    mkdir -p target/allure-results

    # Copy Karate results
    if [ -d "po-test/target/karate-reports" ]; then
        cp -r po-test/target/karate-reports/* target/allure-results/ 2>/dev/null || true
    fi

    # Copy surefire results
    for module in po-domain po-service po-activity po-workflow po-test; do
        if [ -d "$module/target/surefire-reports" ]; then
            cp -r "$module/target/surefire-reports/"*.xml target/allure-results/ 2>/dev/null || true
        fi
    done

    # Generate report
    allure generate target/allure-results -o target/allure-report --clean

    log_success "Allure report generated: target/allure-report/index.html"
}

serve_allure_report() {
    log_info "Starting Allure report server..."

    cd "$PROJECT_ROOT"

    if [ -d "target/allure-report" ]; then
        allure open target/allure-report
    else
        log_warn "No report found. Generating first..."
        generate_allure_report
        allure open target/allure-report
    fi
}

show_karate_report() {
    log_info "Opening Karate HTML report..."

    cd "$PROJECT_ROOT"

    local report="po-test/target/karate-reports/karate-summary.html"

    if [ -f "$report" ]; then
        if command -v open &> /dev/null; then
            open "$report"
        elif command -v xdg-open &> /dev/null; then
            xdg-open "$report"
        else
            log_info "Report available at: $report"
        fi
    else
        log_error "Karate report not found. Run tests first."
        exit 1
    fi
}

generate_coverage_report() {
    log_info "Generating JaCoCo coverage report..."

    cd "$PROJECT_ROOT"

    ./mvnw jacoco:report -pl po-domain,po-service,po-activity,po-workflow

    log_success "Coverage reports generated in each module's target/site/jacoco/"
}

show_help() {
    echo "PO Modernization - Generate Test Reports"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --allure          Generate Allure report"
    echo "  --serve           Generate and serve Allure report"
    echo "  --karate          Open Karate HTML report"
    echo "  --coverage        Generate JaCoCo coverage report"
    echo "  --all             Generate all reports"
    echo "  --help            Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 --serve        # Generate and open Allure report"
    echo "  $0 --karate       # Open Karate HTML report"
    echo "  $0 --all          # Generate all reports"
    echo ""
}

# ═══════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════

main() {
    local action=""

    while [[ $# -gt 0 ]]; do
        case $1 in
            --allure)
                action="allure"
                shift
                ;;
            --serve)
                action="serve"
                shift
                ;;
            --karate)
                action="karate"
                shift
                ;;
            --coverage)
                action="coverage"
                shift
                ;;
            --all)
                action="all"
                shift
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

    if [ -z "$action" ]; then
        action="serve"  # Default action
    fi

    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "  PO Modernization - Test Reports"
    echo "═══════════════════════════════════════════════════════════"
    echo ""

    case $action in
        allure)
            if check_allure; then
                generate_allure_report
            fi
            ;;
        serve)
            if check_allure; then
                serve_allure_report
            fi
            ;;
        karate)
            show_karate_report
            ;;
        coverage)
            generate_coverage_report
            ;;
        all)
            if check_allure; then
                generate_allure_report
            fi
            generate_coverage_report
            show_karate_report
            ;;
    esac
}

main "$@"
