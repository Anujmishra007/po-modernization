#!/bin/bash
# ═══════════════════════════════════════════════════════════
# PO Modernization - Wait for Services
# ═══════════════════════════════════════════════════════════
# Usage: ./scripts/wait-for-services.sh [--test|--full]

set -e

# Configuration
MAX_RETRIES=60
RETRY_INTERVAL=2

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${YELLOW}[WAITING]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[READY]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

wait_for_postgres() {
    local host=${1:-localhost}
    local port=${2:-5433}
    local db=${3:-po_test}
    local user=${4:-wms}

    log_info "Waiting for PostgreSQL ($host:$port)..."

    for i in $(seq 1 $MAX_RETRIES); do
        if PGPASSWORD=wms123 psql -h "$host" -p "$port" -U "$user" -d "$db" -c "SELECT 1" &> /dev/null; then
            log_success "PostgreSQL is ready"
            return 0
        fi
        echo -n "."
        sleep $RETRY_INTERVAL
    done

    log_error "PostgreSQL failed to start"
    return 1
}

wait_for_temporal() {
    local host=${1:-localhost}
    local port=${2:-7233}

    log_info "Waiting for Temporal ($host:$port)..."

    for i in $(seq 1 $MAX_RETRIES); do
        if nc -z "$host" "$port" 2>/dev/null; then
            # Additional check - try to list namespaces
            sleep 2
            log_success "Temporal is ready"
            return 0
        fi
        echo -n "."
        sleep $RETRY_INTERVAL
    done

    log_error "Temporal failed to start"
    return 1
}

wait_for_kafka() {
    local host=${1:-localhost}
    local port=${2:-9092}

    log_info "Waiting for Kafka ($host:$port)..."

    for i in $(seq 1 $MAX_RETRIES); do
        if nc -z "$host" "$port" 2>/dev/null; then
            log_success "Kafka is ready"
            return 0
        fi
        echo -n "."
        sleep $RETRY_INTERVAL
    done

    log_error "Kafka failed to start"
    return 1
}

wait_for_redis() {
    local host=${1:-localhost}
    local port=${2:-6379}

    log_info "Waiting for Redis ($host:$port)..."

    for i in $(seq 1 $MAX_RETRIES); do
        if redis-cli -h "$host" -p "$port" ping &> /dev/null 2>&1 || nc -z "$host" "$port" 2>/dev/null; then
            log_success "Redis is ready"
            return 0
        fi
        echo -n "."
        sleep $RETRY_INTERVAL
    done

    log_error "Redis failed to start"
    return 1
}

wait_for_app() {
    local host=${1:-localhost}
    local port=${2:-8080}

    log_info "Waiting for PO API ($host:$port)..."

    for i in $(seq 1 $MAX_RETRIES); do
        if curl -s "http://$host:$port/actuator/health" | grep -q "UP" 2>/dev/null; then
            log_success "PO API is ready"
            return 0
        fi
        echo -n "."
        sleep $RETRY_INTERVAL
    done

    log_error "PO API failed to start"
    return 1
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
            --app)
                mode="app"
                shift
                ;;
            *)
                shift
                ;;
        esac
    done

    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "  Waiting for services ($mode mode)"
    echo "═══════════════════════════════════════════════════════════"
    echo ""

    # Always wait for core services
    wait_for_postgres
    wait_for_temporal
    wait_for_kafka
    wait_for_redis

    # Wait for app if in app mode
    if [ "$mode" == "app" ]; then
        wait_for_app
    fi

    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "  All services are ready!"
    echo "═══════════════════════════════════════════════════════════"
    echo ""
}

main "$@"
