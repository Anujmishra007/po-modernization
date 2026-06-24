#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
#  DEMO 05: RECEIPT FINALIZE - PAUSE AND RESUME
# ═══════════════════════════════════════════════════════════════════════════════
#  This demo shows how to pause a running finalization workflow
#  and then resume it to continue processing
# ═══════════════════════════════════════════════════════════════════════════════

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  DEMO 05: RECEIPT FINALIZE - PAUSE AND RESUME"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  This demo shows workflow pause/resume capability"
echo "  Use case: Need to investigate or fix something mid-workflow"
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
echo "STEP 3: Let Workflow Progress"
echo "─────────────────────────────────────────────────────────────"
for i in 1 2 3; do
  sleep 1
  STATUS=$(curl -s "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/status")
  CURRENT_STEP=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('currentStep',''))" 2>/dev/null || echo "UNKNOWN")
  PROGRESS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('progress',0))" 2>/dev/null || echo "0")
  echo "  [$i] Step: $CURRENT_STEP | Progress: $PROGRESS%"
done
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 4: PAUSE Workflow"
echo "─────────────────────────────────────────────────────────────"
echo "  Sending PAUSE signal..."
curl -s -X POST "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/pause"
echo "  PAUSE requested!"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 5: Observe Paused State"
echo "─────────────────────────────────────────────────────────────"
echo "  Workflow should be holding at current step..."
for i in 1 2 3; do
  sleep 1
  STATUS=$(curl -s "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/status")
  CURRENT_STATUS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status',''))" 2>/dev/null || echo "UNKNOWN")
  CURRENT_STEP=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('currentStep',''))" 2>/dev/null || echo "UNKNOWN")
  PROGRESS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('progress',0))" 2>/dev/null || echo "0")
  echo "  [$i] Status: $CURRENT_STATUS | Step: $CURRENT_STEP | Progress: $PROGRESS% (should not change)"
done
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 6: RESUME Workflow"
echo "─────────────────────────────────────────────────────────────"
echo "  Sending RESUME signal..."
curl -s -X POST "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/resume"
echo "  RESUME requested!"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 7: Monitor Resumed Progress"
echo "─────────────────────────────────────────────────────────────"
for i in 1 2 3 4 5 6 7 8 9 10; do
  sleep 1
  STATUS=$(curl -s "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/status")

  CURRENT_STATUS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status',''))" 2>/dev/null || echo "UNKNOWN")
  CURRENT_STEP=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('currentStep',''))" 2>/dev/null || echo "UNKNOWN")
  PROGRESS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('progress',0))" 2>/dev/null || echo "0")

  printf "  [%2d] Status: %-12s | Step: %-25s | Progress: %3s%%\n" "$i" "$CURRENT_STATUS" "$CURRENT_STEP" "$PROGRESS"

  if [ "$CURRENT_STATUS" = "COMPLETED" ] || [ "$CURRENT_STATUS" = "FAILED" ]; then
    break
  fi
done
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 8: Final Result"
echo "─────────────────────────────────────────────────────────────"
FINAL_STATUS=$(curl -s "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/status")
echo "$FINAL_STATUS" | python3 -m json.tool 2>/dev/null || echo "$FINAL_STATUS"

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  PAUSE/RESUME DEMO COMPLETE"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  This demo showed:"
echo "  1. Workflow started normally"
echo "  2. PAUSE signal sent - workflow held at current step"
echo "  3. During pause: time to investigate, fix data, etc."
echo "  4. RESUME signal sent - workflow continued"
echo "  5. Workflow completed successfully"
echo ""
echo "  Use cases for pause/resume:"
echo "  - Investigating unexpected data"
echo "  - Fixing downstream system issues"
echo "  - Coordinating with other processes"
echo "  - Manual intervention required"
echo ""
echo "  View in Temporal UI: http://localhost:8088"
echo ""
