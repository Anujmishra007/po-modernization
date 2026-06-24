#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
#  DEMO 02: PO POPULATE - CANCEL WITH COMPENSATION
# ═══════════════════════════════════════════════════════════════════════════════
#  This demo shows how cancellation triggers the Saga compensation pattern
#  When cancelled, all completed steps are rolled back in REVERSE order
#
#  Uses PO-DEMO-001 (valid test data)
#  NOTE: If workflow completes before cancel, it shows the happy path instead
# ═══════════════════════════════════════════════════════════════════════════════

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  DEMO 02: PO POPULATE - CANCEL WITH COMPENSATION"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  This demo shows Saga compensation when workflow is cancelled"
echo "  Compensation runs in REVERSE order:"
echo "    Step 9 (Legacy)       ← Rollback"
echo "    Step 8 (Reservations) ← Release"
echo "    Step 7 (Details)      ← Delete"
echo "    Step 6 (Header)       ← Delete"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 1: Reset Test Data (ensure PO is available)"
echo "─────────────────────────────────────────────────────────────"
echo "  Resetting PO-DEMO-001 status to '0' (open)..."
curl -s -X POST "$BASE_URL/api/v1/test/reset-po/PO-DEMO-001" 2>/dev/null || echo "  (Reset endpoint not available - continuing)"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 2: Start ASYNC Population"
echo "─────────────────────────────────────────────────────────────"
POPULATE_RESP=$(curl -s -X POST "$BASE_URL/api/v1/populate/populate/async" \
  -H "Content-Type: application/json" \
  -d '{
    "poKeys": ["PO-DEMO-001"],
    "storerKey": "NIKE",
    "facility": "KR01",
    "userId": "demo-user"
  }')
echo "$POPULATE_RESP" | python3 -m json.tool 2>/dev/null || echo "$POPULATE_RESP"

WORKFLOW_ID=$(echo "$POPULATE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('workflowId',''))" 2>/dev/null || echo "")
echo ""
echo "Workflow ID: $WORKFLOW_ID"
echo ""

if [ -z "$WORKFLOW_ID" ]; then
  echo "ERROR: Failed to start workflow. Exiting."
  exit 1
fi

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 3: CANCEL Workflow IMMEDIATELY → Trigger Compensation"
echo "─────────────────────────────────────────────────────────────"
echo "  Sending cancellation signal immediately..."
# Send cancel right away to catch the workflow before it completes
CANCEL_RESP=$(curl -s -X POST "$BASE_URL/api/v1/populate/populate/$WORKFLOW_ID/cancel")
echo "  Cancel sent!"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 4: Monitor Workflow/Compensation Progress"
echo "─────────────────────────────────────────────────────────────"
for i in 1 2 3 4 5 6 7 8 9 10; do
  sleep 0.5
  STATUS=$(curl -s "$BASE_URL/api/v1/populate/populate/$WORKFLOW_ID/status")
  CURRENT_STATUS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status',''))" 2>/dev/null || echo "UNKNOWN")
  CURRENT_STEP=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('currentStep',''))" 2>/dev/null || echo "UNKNOWN")
  PROGRESS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('progress',0))" 2>/dev/null || echo "0")

  if [ "$CURRENT_STATUS" = "COMPENSATING" ]; then
    echo "  [$i] >>> COMPENSATING <<< Step: $CURRENT_STEP | Progress: $PROGRESS%"
  elif [ "$CURRENT_STATUS" = "CANCELLED" ]; then
    echo "  [$i] Status: CANCELLED | Compensation complete | Step: $CURRENT_STEP"
    break
  elif [ "$CURRENT_STATUS" = "COMPLETED" ]; then
    echo "  [$i] Status: COMPLETED | Workflow finished before cancel took effect"
    break
  elif [ "$CURRENT_STATUS" = "FAILED" ]; then
    echo "  [$i] Status: FAILED | Step: $CURRENT_STEP"
    break
  else
    echo "  [$i] Status: $CURRENT_STATUS | Step: $CURRENT_STEP | Progress: $PROGRESS%"
  fi
done
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 5: Final Result"
echo "─────────────────────────────────────────────────────────────"
FINAL_STATUS=$(curl -s "$BASE_URL/api/v1/populate/populate/$WORKFLOW_ID/status")
echo "$FINAL_STATUS" | python3 -m json.tool 2>/dev/null || echo "$FINAL_STATUS"

# Extract final status for summary
FINAL_STATE=$(echo "$FINAL_STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status',''))" 2>/dev/null || echo "UNKNOWN")
COMPLETED_STEPS=$(echo "$FINAL_STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); steps=d.get('completedSteps',[]); print(len(steps))" 2>/dev/null || echo "0")

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  COMPENSATION DEMO COMPLETE"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  Final Status: $FINAL_STATE"
echo "  Steps Completed Before Cancel: $COMPLETED_STEPS"
echo ""
if [ "$FINAL_STATE" = "CANCELLED" ]; then
  echo "  What happened:"
  echo "  1. Workflow started and progressed through steps"
  echo "  2. CANCEL signal was sent"
  echo "  3. Saga pattern triggered compensation in REVERSE order:"
  echo "     - releaseReservations() was called (if reservations were created)"
  echo "     - deleteReceiptDetails() was called (if details were created)"
  echo "     - deleteReceiptHeader() was called (if header was created)"
  echo "  4. Final status: CANCELLED"
  echo ""
  echo "  This demonstrates the 'Saga' pattern:"
  echo "  Each step registers a compensation action that runs if"
  echo "  the workflow fails or is cancelled."
elif [ "$FINAL_STATE" = "COMPLETED" ]; then
  echo "  NOTE: Workflow completed before cancel could take effect."
  echo "  This can happen with small POs that process quickly."
  echo "  Try with PO-SAGA-DEMO (25 lines) for longer processing time."
else
  echo "  Workflow ended with status: $FINAL_STATE"
fi
echo ""
echo "  View in Temporal UI: http://localhost:8088"
echo ""
