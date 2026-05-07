#!/bin/bash
# ═══════════════════════════════════════════════════════════
# PO Modernization - Test Data Sync Script
# ═══════════════════════════════════════════════════════════
# Syncs test data from po-test/resources/test-data/ to
# local-environment/seed-data/ with proper numeric prefixes
# for PostgreSQL Docker initialization order.
#
# Usage: ./scripts/sync-test-data.sh
# ═══════════════════════════════════════════════════════════

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SOURCE_DIR="$PROJECT_ROOT/po-test/src/test/resources/test-data"
TARGET_DIR="$PROJECT_ROOT/local-environment/seed-data"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

echo "═══════════════════════════════════════════════════════════"
echo "  PO Modernization - Test Data Sync"
echo "═══════════════════════════════════════════════════════════"
echo ""

log_info "Source: $SOURCE_DIR"
log_info "Target: $TARGET_DIR"
echo ""

# File mapping with load order (PostgreSQL runs scripts alphabetically)
# Format: "source_name:target_prefix"
declare -a FILE_MAP=(
    "TD-CODELKUP.sql:02"
    "TD-STORER.sql:03"
    "TD-LOCATION.sql:04"
    "TD-SKU.sql:05"
    "TD-CLIENT.sql:06"
    "TD-PO-HAPPY.sql:07"
    "TD-PO-ERROR.sql:08"
    "TD-RCV-HAPPY.sql:09"
    "TD-RCV-ERROR.sql:10"
    "TD-INVENTORY.sql:11"
    "TD-TASK.sql:12"
    "TD-JOB.sql:13"
    "TD-TRIGGER.sql:14"
    "TD-RDT.sql:15"
    "TD-ORDER.sql:16"
)

# Sync SQL files
log_info "Syncing SQL test data files..."

for mapping in "${FILE_MAP[@]}"; do
    SOURCE_FILE="${mapping%%:*}"
    PREFIX="${mapping##*:}"
    TARGET_FILE="${PREFIX}-${SOURCE_FILE}"

    if [ -f "$SOURCE_DIR/$SOURCE_FILE" ]; then
        cp "$SOURCE_DIR/$SOURCE_FILE" "$TARGET_DIR/$TARGET_FILE"
        echo "  ✓ $SOURCE_FILE → $TARGET_FILE"
    else
        echo "  ⚠ $SOURCE_FILE not found, skipping"
    fi
done

# Sync EDI files
log_info "Syncing EDI test files..."
mkdir -p "$TARGET_DIR/edi"
if [ -d "$SOURCE_DIR/edi" ]; then
    cp "$SOURCE_DIR/edi/"*.txt "$TARGET_DIR/edi/" 2>/dev/null || true
    echo "  ✓ EDI files synced"
else
    echo "  ⚠ EDI folder not found"
fi

echo ""
log_success "Test data sync complete!"
echo ""

# Show summary
log_info "Files in $TARGET_DIR:"
ls -la "$TARGET_DIR" | grep -E "\.sql$"
echo ""

log_info "Load Order:"
echo "  00-schema.sql        → Database schema"
echo "  02-TD-CODELKUP.sql   → Reference data (status codes, hold codes)"
echo "  03-TD-STORER.sql     → Storers + addresses + facilities"
echo "  04-TD-LOCATION.sql   → Locations + putaway zones"
echo "  05-TD-SKU.sql        → SKUs + packs"
echo "  06-TD-CLIENT.sql     → Client configurations (plugins)"
echo "  07-TD-PO-HAPPY.sql   → Happy path POs"
echo "  08-TD-PO-ERROR.sql   → Error/edge case POs"
echo "  09-TD-RCV-HAPPY.sql  → Happy path receipts"
echo "  10-TD-RCV-ERROR.sql  → Error/edge case receipts"
echo "  11-TD-INVENTORY.sql  → Inventory (LOTxLOCxID, holds)"
echo "  12-TD-TASK.sql       → Tasks (putaway, pick)"
echo "  13-TD-JOB.sql        → Job configurations"
echo "  14-TD-TRIGGER.sql    → Trigger configurations"
echo "  15-TD-RDT.sql        → RDT users, devices, sessions"
echo "  16-TD-ORDER.sql      → Sales orders (XDock)"
echo "  99-cleanup.sql       → Cleanup script"
echo ""
echo "═══════════════════════════════════════════════════════════"
