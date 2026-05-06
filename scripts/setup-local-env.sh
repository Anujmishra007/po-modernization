#!/bin/bash
# ═══════════════════════════════════════════════════════════
# PO Modernization - Local Environment Setup
# ═══════════════════════════════════════════════════════════
# Usage: ./scripts/setup-local-env.sh [--test|--full|--down]
#
# Options:
#   --test    Start minimal test environment (default)
#   --full    Start full development environment
#   --down    Stop all containers
#   --clean   Stop and remove all volumes

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
LOCAL_ENV_DIR="$PROJECT_ROOT/local-environment"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# ═══════════════════════════════════════════════════════════
# Functions
# ═══════════════════════════════════════════════════════════

check_prerequisites() {
    log_info "Checking prerequisites..."

    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed. Please install Docker first."
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        log_error "Docker Compose is not installed. Please install Docker Compose first."
        exit 1
    fi

    if ! docker info &> /dev/null; then
        log_error "Docker daemon is not running. Please start Docker first."
        exit 1
    fi

    log_success "Prerequisites check passed"
}

start_test_env() {
    log_info "Starting TEST environment..."
    cd "$LOCAL_ENV_DIR"

    docker compose -f docker-compose-test.yml up -d postgres temporal kafka redis

    log_info "Waiting for services to be healthy..."
    "$SCRIPT_DIR/wait-for-services.sh" --test

    log_success "Test environment is ready!"
    print_connection_info "test"
}

start_full_env() {
    log_info "Starting FULL development environment..."
    cd "$LOCAL_ENV_DIR"

    docker compose -f docker-compose.yml up -d

    log_info "Waiting for services to be healthy..."
    "$SCRIPT_DIR/wait-for-services.sh" --full

    log_success "Full development environment is ready!"
    print_connection_info "full"
}

stop_env() {
    log_info "Stopping all containers..."
    cd "$LOCAL_ENV_DIR"

    docker compose -f docker-compose-test.yml down 2>/dev/null || true
    docker compose -f docker-compose.yml down 2>/dev/null || true

    log_success "All containers stopped"
}

clean_env() {
    log_info "Stopping and removing all containers and volumes..."
    cd "$LOCAL_ENV_DIR"

    docker compose -f docker-compose-test.yml down -v 2>/dev/null || true
    docker compose -f docker-compose.yml down -v 2>/dev/null || true

    log_success "All containers and volumes removed"
}

print_connection_info() {
    local env_type=$1

    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "  Connection Information ($env_type)"
    echo "═══════════════════════════════════════════════════════════"
    echo ""
    echo "  PostgreSQL:"
    echo "    Host: localhost"
    echo "    Port: 5433"
    echo "    Database: po_${env_type}"
    echo "    User: wms"
    echo "    Password: wms123"
    echo "    JDBC URL: jdbc:postgresql://localhost:5433/po_${env_type}"
    echo ""
    echo "  Temporal:"
    echo "    Server: localhost:7233"
    if [ "$env_type" == "full" ]; then
        echo "    UI: http://localhost:8088"
    fi
    echo ""
    echo "  Kafka:"
    echo "    Bootstrap: localhost:9092"
    if [ "$env_type" == "full" ]; then
        echo "    UI: http://localhost:8089"
    fi
    echo ""
    echo "  Redis:"
    echo "    Host: localhost:6379"
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo ""
}

show_help() {
    echo "PO Modernization - Local Environment Setup"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --test    Start minimal test environment (default)"
    echo "  --full    Start full development environment with UIs"
    echo "  --down    Stop all containers"
    echo "  --clean   Stop and remove all containers and volumes"
    echo "  --help    Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                 # Start test environment"
    echo "  $0 --full          # Start full environment"
    echo "  $0 --down          # Stop all containers"
    echo ""
}

# ═══════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════

main() {
    local mode="test"

    while [[ $# -gt 0 ]]; do
        case $1 in
            --test)
                mode="test"
                shift
                ;;
            --full)
                mode="full"
                shift
                ;;
            --down)
                mode="down"
                shift
                ;;
            --clean)
                mode="clean"
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

    check_prerequisites

    case $mode in
        test)
            start_test_env
            ;;
        full)
            start_full_env
            ;;
        down)
            stop_env
            ;;
        clean)
            clean_env
            ;;
    esac
}

main "$@"
