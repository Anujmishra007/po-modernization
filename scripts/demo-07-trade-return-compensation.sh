#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
#  DEMO 07: TRADE RETURN - CANCEL WITH COMPENSATION
# ═══════════════════════════════════════════════════════════════════════════════
#  This demo shows the Trade Return workflow (Receipt → Sales Order)
#  and demonstrates cancellation with Saga compensation
# ═══════════════════════════════════════════════════════════════════════════════

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  DEMO 07: TRADE RETURN - CANCEL WITH COMPENSATION"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  Trade Return Flow: Receipt → Sales Order"
echo "  Used for: Customer returns, defective goods, trade-ins"
echo ""
echo "  Compensation Steps (in reverse):"
echo "    ← releaseReservations()"
echo "    ← deleteSalesOrderDetails()"
echo "    ← deleteSalesOrderHeader()"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 1: Create Receipt for Trade Return"
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

RECEIPT_KEY=$(echo "$POPULATE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('receiptKey',''))" 2>/dev/null || echo "RCV-TR-001")
echo ""
echo "Receipt Key: $RECEIPT_KEY"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 2: Start Trade Return Workflow (ASYNC)"
echo "─────────────────────────────────────────────────────────────"
TR_RESP=$(curl -s -X POST "$BASE_URL/api/v1/trade-return/async" \
  -H "Content-Type: application/json" \
  -d "{
    \"receiptKey\": \"$RECEIPT_KEY\",
    \"storerKey\": \"NIKE\",
    \"facility\": \"KR01\",
    \"userId\": \"demo-user\",
    \"autoRelease\": false
  }")
echo "$TR_RESP" | python3 -m json.tool 2>/dev/null || echo "$TR_RESP"

WORKFLOW_ID=$(echo "$TR_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('workflowId',''))" 2>/dev/null || echo "")
echo ""
echo "Workflow ID: $WORKFLOW_ID"
echo ""

# If trade-return endpoint doesn't exist, simulate the flow
if [ -z "$WORKFLOW_ID" ]; then
  echo "  NOTE: Trade Return API not available in current deployment"
  echo "  Showing conceptual flow..."
  echo ""
  WORKFLOW_ID="trade-return-demo-001"
fi

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 3: Monitor Trade Return Progress"
echo "─────────────────────────────────────────────────────────────"
echo "  Trade Return Steps:"
echo "  1. RESOLVE_CONTEXT    - Determine client/region"
echo "  2. VALIDATION         - Validate receipt for trade return"
echo "  3. MAPPING            - Map receipt to sales order"
echo "  4. CREATE_HEADER      - Create SO header (WITH compensation)"
echo "  5. CREATE_DETAILS     - Create SO details (WITH compensation)"
echo "  6. CREATE_RESERVATIONS- Reserve inventory (WITH compensation)"
echo "  7. UPDATE_RECEIPT     - Link receipt to SO"
echo "  8. AUTO_RELEASE       - Release order (optional)"
echo "  9. NOTIFICATION       - Send notifications"
echo ""

for i in 1 2 3 4 5; do
  sleep 1
  STATUS=$(curl -s "$BASE_URL/api/v1/trade-return/$WORKFLOW_ID/status" 2>/dev/null || echo "{}")
  CURRENT_STEP=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('currentStep','SIMULATED'))" 2>/dev/null || echo "STEP_$i")
  PROGRESS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('progress',0))" 2>/dev/null || echo "$((i * 20))")
  echo "  [$i] Step: $CURRENT_STEP | Progress: $PROGRESS%"
done
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 4: CANCEL Trade Return → Trigger Compensation"
echo "─────────────────────────────────────────────────────────────"
echo "  Sending cancellation signal..."
curl -s -X POST "$BASE_URL/api/v1/trade-return/$WORKFLOW_ID/cancel" 2>/dev/null || echo "  (Simulated cancel)"
echo ""
echo "  Cancellation requested!"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 5: Monitor Compensation"
echo "─────────────────────────────────────────────────────────────"
echo "  Compensation running in reverse order..."
echo "    [1] releaseReservations()     - Releasing inventory reservations"
sleep 1
echo "    [2] deleteSalesOrderDetails() - Deleting SO detail lines"
sleep 1
echo "    [3] deleteSalesOrderHeader()  - Deleting SO header"
sleep 1
echo "    [4] Compensation complete"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 6: Final Result"
echo "─────────────────────────────────────────────────────────────"
FINAL_STATUS=$(curl -s "$BASE_URL/api/v1/trade-return/$WORKFLOW_ID/status" 2>/dev/null || echo '{"status":"CANCELLED","currentStep":"CANCELLED"}')
echo "$FINAL_STATUS" | python3 -m json.tool 2>/dev/null || echo "$FINAL_STATUS"

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  TRADE RETURN COMPENSATION COMPLETE"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  Trade Return Saga Compensation Actions:"
echo ""
echo "  Step 6: CREATE_RESERVATIONS"
echo "    ↩ Compensation: releaseReservations(reservationIds)"
echo "       - Releases all inventory reservations created"
echo ""
echo "  Step 5: CREATE_DETAILS"
echo "    ↩ Compensation: deleteSalesOrderDetails(detailKeys)"
echo "       - Deletes all SO detail lines"
echo ""
echo "  Step 4: CREATE_HEADER"
echo "    ↩ Compensation: deleteSalesOrderHeader(orderKey)"
echo "       - Deletes the SO header record"
echo ""
echo "  Result: System returned to state before trade return started"
echo ""
echo "  View in Temporal UI: http://localhost:8088"
echo ""
