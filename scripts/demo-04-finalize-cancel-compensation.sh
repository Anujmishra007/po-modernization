#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
#  DEMO 04: RECEIPT FINALIZE - CANCEL WITH COMPENSATION
# ═══════════════════════════════════════════════════════════════════════════════
#  This demo shows cancellation during finalization and the compensation flow
#  IMPORTANT: Can only cancel BEFORE "SET_STATUS_FINALIZING" step
# ═══════════════════════════════════════════════════════════════════════════════

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  DEMO 04: RECEIPT FINALIZE - CANCEL WITH COMPENSATION"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  This demo shows Saga compensation for finalization"
echo ""
echo "  KEY CONCEPT: 'Point of No Return'"
echo "  - Before SET_STATUS_FINALIZING: canCancel=true"
echo "  - After SET_STATUS_FINALIZING:  canCancel=false"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 1: Create Receipt via Population (Sync)"
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
echo ""
echo "Receipt Key: $RECEIPT_KEY"
echo ""

if [ -z "$RECEIPT_KEY" ]; then
  echo "ERROR: Failed to create receipt. Exiting."
  exit 1
fi

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 2: Start ASYNC Finalization"
echo "─────────────────────────────────────────────────────────────"
FINALIZE_RESP=$(curl -s -X POST "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/async" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: demo-user" \
  -d '{"storerKey":"NIKE","facility":"KR01"}')
echo "$FINALIZE_RESP" | python3 -m json.tool 2>/dev/null || echo "$FINALIZE_RESP"

WORKFLOW_ID=$(echo "$FINALIZE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('workflowId',''))" 2>/dev/null || echo "")
echo ""
echo "Workflow ID: $WORKFLOW_ID"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 3: Monitor Until Cancelable"
echo "─────────────────────────────────────────────────────────────"
echo "  Waiting for workflow to progress (demo delay enables cancellation)..."
for i in 1 2 3; do
  sleep 1
  STATUS=$(curl -s "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/status")
  CURRENT_STEP=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('currentStep',''))" 2>/dev/null || echo "UNKNOWN")
  CAN_CANCEL=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('canCancel',False))" 2>/dev/null || echo "False")
  echo "  [$i] Step: $CURRENT_STEP | CanCancel: $CAN_CANCEL"
done
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 4: CANCEL Workflow → Trigger Compensation"
echo "─────────────────────────────────────────────────────────────"
echo "  Sending cancellation signal..."
CANCEL_RESP=$(curl -s -X POST "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/cancel")
echo "$CANCEL_RESP" | python3 -m json.tool 2>/dev/null || echo "$CANCEL_RESP"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 5: Monitor Compensation"
echo "─────────────────────────────────────────────────────────────"
for i in 1 2 3 4 5; do
  sleep 1
  STATUS=$(curl -s "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/status")

  CURRENT_STATUS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status',''))" 2>/dev/null || echo "UNKNOWN")
  CURRENT_STEP=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('currentStep',''))" 2>/dev/null || echo "UNKNOWN")

  echo "  [$i] Status: $CURRENT_STATUS | Step: $CURRENT_STEP"

  if [ "$CURRENT_STATUS" = "CANCELLED" ] || [ "$CURRENT_STATUS" = "FAILED" ]; then
    break
  fi
done
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 6: Final Result"
echo "─────────────────────────────────────────────────────────────"
FINAL_STATUS=$(curl -s "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/status")
echo "$FINAL_STATUS" | python3 -m json.tool 2>/dev/null || echo "$FINAL_STATUS"

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  FINALIZE CANCEL COMPENSATION COMPLETE"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  Compensation Actions (in reverse order):"
echo "  - revertStatus()              - Status reverted to previous"
echo "  - rollbackPreFinalizePlugins() - Plugin effects undone"
echo ""
echo "  NOTE: Cancellation only works BEFORE 'point of no return'"
echo "  After SET_STATUS_FINALIZING, the system is committed and"
echo "  cancellation is rejected to maintain data integrity."
echo ""
echo "  View in Temporal UI: http://localhost:8088"
echo ""
