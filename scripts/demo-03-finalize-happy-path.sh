#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
#  DEMO 03: RECEIPT FINALIZE - HAPPY PATH
# ═══════════════════════════════════════════════════════════════════════════════
#  This demo shows the complete receipt finalization flow
#  Posts inventory to LOTxLOCxID, applies holds, updates PO quantities
#
#  Uses PO-XDOCK-001 (valid test data with 5 lines)
# ═══════════════════════════════════════════════════════════════════════════════

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  DEMO 03: RECEIPT FINALIZE - HAPPY PATH"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  This demo shows successful receipt finalization"
echo "  Flow: Create Receipt → Finalize → Inventory Posted"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 1: Reset Test Data (ensure PO is available)"
echo "─────────────────────────────────────────────────────────────"
echo "  Resetting PO-XDOCK-001 status to '0' (open)..."
curl -s -X POST "$BASE_URL/api/v1/test/reset-po/PO-XDOCK-001" 2>/dev/null || echo "  (Reset endpoint not available - continuing)"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 2: Create Receipt via Population (Sync)"
echo "─────────────────────────────────────────────────────────────"
POPULATE_RESP=$(curl -s -X POST "$BASE_URL/api/v1/populate/populate" \
  -H "Content-Type: application/json" \
  -d '{
    "poKeys": ["PO-XDOCK-001"],
    "storerKey": "NIKE",
    "facility": "KR01",
    "userId": "demo-user"
  }')
echo "$POPULATE_RESP" | python3 -m json.tool 2>/dev/null || echo "$POPULATE_RESP"

RECEIPT_KEY=$(echo "$POPULATE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('receiptKey',''))" 2>/dev/null || echo "")
SUCCESS=$(echo "$POPULATE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('success',False))" 2>/dev/null || echo "false")

echo ""
echo "Receipt Key: $RECEIPT_KEY"
echo "Success: $SUCCESS"
echo ""

if [ -z "$RECEIPT_KEY" ] || [ "$SUCCESS" != "True" ]; then
  echo "ERROR: Failed to create receipt. Checking if receipt already exists..."
  # Try to get existing receipt for this PO
  EXISTING=$(curl -s "$BASE_URL/api/v1/receipts?poKey=PO-XDOCK-001" 2>/dev/null || echo "")
  echo "$EXISTING"

  # Use a default receipt key for demo purposes
  if [ -z "$RECEIPT_KEY" ]; then
    echo ""
    echo "NOTE: Using existing receipt or mock for demo..."
    RECEIPT_KEY="RCV-PO-XDOCK-001"
  fi
fi

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 3: Start ASYNC Finalization"
echo "─────────────────────────────────────────────────────────────"
FINALIZE_RESP=$(curl -s -X POST "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/async" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: demo-user" \
  -d '{
    "storerKey": "NIKE",
    "facility": "KR01",
    "autoClose": true,
    "releasePutaway": true,
    "applyHolds": true
  }')
echo "$FINALIZE_RESP" | python3 -m json.tool 2>/dev/null || echo "$FINALIZE_RESP"

WORKFLOW_ID=$(echo "$FINALIZE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('workflowId',''))" 2>/dev/null || echo "")
echo ""
echo "Workflow ID: $WORKFLOW_ID"
echo ""

if [ -z "$WORKFLOW_ID" ]; then
  echo "ERROR: Failed to start finalization workflow."
  exit 1
fi

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 4: Monitor Finalization Progress"
echo "─────────────────────────────────────────────────────────────"
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  sleep 1
  STATUS=$(curl -s "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/status")

  CURRENT_STATUS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status',''))" 2>/dev/null || echo "UNKNOWN")
  CURRENT_STEP=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('currentStep',''))" 2>/dev/null || echo "UNKNOWN")
  PROGRESS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('progress',0))" 2>/dev/null || echo "0")
  CAN_CANCEL=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('canCancel',False))" 2>/dev/null || echo "False")

  # Color code the canCancel flag
  if [ "$CAN_CANCEL" = "True" ]; then
    CANCEL_DISPLAY="YES"
  else
    CANCEL_DISPLAY="NO (past point of no return)"
  fi

  printf "  [%2d] Status: %-12s | Step: %-22s | Progress: %3s%% | CanCancel: %s\n" \
    "$i" "$CURRENT_STATUS" "$CURRENT_STEP" "$PROGRESS" "$CANCEL_DISPLAY"

  if [ "$CURRENT_STATUS" = "COMPLETED" ] || [ "$CURRENT_STATUS" = "FAILED" ] || [ "$CURRENT_STATUS" = "CANCELLED" ]; then
    break
  fi
done
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 5: Final Result"
echo "─────────────────────────────────────────────────────────────"
FINAL_STATUS=$(curl -s "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/status")
echo "$FINAL_STATUS" | python3 -m json.tool 2>/dev/null || echo "$FINAL_STATUS"

# Extract completed steps for display
COMPLETED_STEPS=$(echo "$FINAL_STATUS" | python3 -c "
import sys, json
d = json.load(sys.stdin)
steps = d.get('completedSteps', [])
for s in steps:
    print(f'    - {s}')
" 2>/dev/null || echo "    (unable to parse)")

FINAL_STATE=$(echo "$FINAL_STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status',''))" 2>/dev/null || echo "UNKNOWN")

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  FINALIZE HAPPY PATH COMPLETE"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  Final Status: $FINAL_STATE"
echo ""
if [ "$FINAL_STATE" = "COMPLETED" ]; then
  echo "  Completed Steps:"
  echo "$COMPLETED_STEPS"
  echo ""
  echo "  Finalization Steps Explained:"
  echo "  1. RESOLVE_CONTEXT       - Determined variation context (V2/ASIA-KR)"
  echo "  2. VALIDATE              - Validated receipt state"
  echo "  3. PRE_PLUGINS           - Ran pre-finalize plugins"
  echo "  4. SET_STATUS_FINALIZING - Updated status to 'Finalizing' (point of no return)"
  echo "  5. POST_INVENTORY        - Posted inventory to LOTxLOCxID"
  echo "  6. APPLY_HOLDS           - Applied inventory holds (if applicable)"
  echo "  7. UPDATE_PO_QTY         - Updated PO received quantities"
  echo "  8. RELEASE_PUTAWAY       - Released putaway tasks"
  echo "  9. POST_PLUGINS          - Ran post-finalize plugins"
  echo "  10. SET_STATUS_FINALIZED - Updated status to 'Finalized'"
  echo ""
  echo "  KEY CONCEPT: 'Point of No Return'"
  echo "  - Before SET_STATUS_FINALIZING: canCancel=true"
  echo "  - After SET_STATUS_FINALIZING:  canCancel=false"
  echo "  - This ensures data consistency once inventory posting begins"
else
  echo "  Workflow ended with status: $FINAL_STATE"
  echo ""
  echo "  Completed Steps:"
  echo "$COMPLETED_STEPS"
fi
echo ""
echo "  View in Temporal UI: http://localhost:8088"
echo ""
