#!/bin/bash
# ═══════════════════════════════════════════════════════════
# PO Modernization - Load Test Data
# ═══════════════════════════════════════════════════════════
# Usage: ./scripts/load-test-data.sh [OPTIONS]
#
# Loads test data into the database

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SEED_DATA_DIR="$PROJECT_ROOT/local-environment/seed-data"

# Database configuration
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5433}
DB_NAME=${DB_NAME:-po_test}
DB_USER=${DB_USER:-wms}
DB_PASSWORD=${DB_PASSWORD:-wms123}

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

run_sql() {
    local file=$1
    PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$file"
}

run_sql_query() {
    local query=$1
    PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "$query"
}

load_schema() {
    log_info "Loading database schema..."

    if [ -f "$SEED_DATA_DIR/00-schema.sql" ]; then
        run_sql "$SEED_DATA_DIR/00-schema.sql"
        log_success "Schema loaded"
    else
        log_warn "Schema file not found: $SEED_DATA_DIR/00-schema.sql"
    fi
}

load_test_data() {
    log_info "Loading test data..."

    if [ -f "$SEED_DATA_DIR/01-test-data.sql" ]; then
        run_sql "$SEED_DATA_DIR/01-test-data.sql"
        log_success "Test data loaded"
    else
        log_warn "Test data file not found: $SEED_DATA_DIR/01-test-data.sql"
    fi
}

cleanup_data() {
    log_info "Cleaning up existing data..."

    if [ -f "$SEED_DATA_DIR/99-cleanup.sql" ]; then
        run_sql "$SEED_DATA_DIR/99-cleanup.sql"
        log_success "Data cleaned up"
    else
        # Manual cleanup if file doesn't exist
        run_sql_query "TRUNCATE TABLE dbo.inventoryhold, dbo.lotxlocxid, dbo.task, dbo.receiptdetail, dbo.receipt, dbo.orderdetail, dbo.orders CASCADE;"
        log_success "Data cleaned up (manual)"
    fi
}

load_from_po_test() {
    log_info "Loading test data from po-test module..."

    local po_test_data="$PROJECT_ROOT/po-test/src/test/resources/test-data"

    if [ -f "$po_test_data/TD-MASTER-SETUP.sql" ]; then
        run_sql "$po_test_data/TD-MASTER-SETUP.sql"
        log_success "Test data loaded from po-test"
    else
        log_warn "TD-MASTER-SETUP.sql not found in po-test"
    fi
}

verify_data() {
    log_info "Verifying loaded data..."

    echo ""
    echo "Data Counts:"
    echo "───────────────────────────────────────"

    run_sql_query "
        SELECT 'Storers' as entity, COUNT(*) as count FROM dbo.storer
        UNION ALL SELECT 'Facilities', COUNT(*) FROM dbo.facility
        UNION ALL SELECT 'SKUs', COUNT(*) FROM dbo.sku
        UNION ALL SELECT 'Locations', COUNT(*) FROM dbo.loc
        UNION ALL SELECT 'POs', COUNT(*) FROM dbo.orders
        UNION ALL SELECT 'PO Lines', COUNT(*) FROM dbo.orderdetail
        UNION ALL SELECT 'Receipts', COUNT(*) FROM dbo.receipt
        UNION ALL SELECT 'Receipt Lines', COUNT(*) FROM dbo.receiptdetail
        ORDER BY 1;
    "

    echo "───────────────────────────────────────"
}

show_help() {
    echo "PO Modernization - Load Test Data"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --schema          Load schema only"
    echo "  --data            Load test data only"
    echo "  --cleanup         Cleanup and reload data"
    echo "  --verify          Verify loaded data"
    echo "  --from-po-test    Load data from po-test module"
    echo "  --all             Load schema + data (default)"
    echo "  --host HOST       Database host (default: localhost)"
    echo "  --port PORT       Database port (default: 5433)"
    echo "  --db NAME         Database name (default: po_test)"
    echo "  --help            Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                    # Load all (schema + data)"
    echo "  $0 --cleanup          # Cleanup and reload"
    echo "  $0 --verify           # Show data counts"
    echo ""
}

# ═══════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════

main() {
    local action="all"

    while [[ $# -gt 0 ]]; do
        case $1 in
            --schema)
                action="schema"
                shift
                ;;
            --data)
                action="data"
                shift
                ;;
            --cleanup)
                action="cleanup"
                shift
                ;;
            --verify)
                action="verify"
                shift
                ;;
            --from-po-test)
                action="po-test"
                shift
                ;;
            --all)
                action="all"
                shift
                ;;
            --host)
                DB_HOST="$2"
                shift 2
                ;;
            --port)
                DB_PORT="$2"
                shift 2
                ;;
            --db)
                DB_NAME="$2"
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

    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "  PO Modernization - Load Test Data"
    echo "═══════════════════════════════════════════════════════════"
    echo ""
    echo "Database: $DB_HOST:$DB_PORT/$DB_NAME"
    echo ""

    case $action in
        schema)
            load_schema
            ;;
        data)
            load_test_data
            ;;
        cleanup)
            cleanup_data
            load_test_data
            verify_data
            ;;
        verify)
            verify_data
            ;;
        po-test)
            load_from_po_test
            verify_data
            ;;
        all)
            load_schema
            load_test_data
            verify_data
            ;;
    esac

    echo ""
    log_success "Done!"
}

main "$@"
