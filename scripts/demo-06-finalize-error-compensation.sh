#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
#  DEMO 06: RECEIPT FINALIZE - ERROR TRIGGERS AUTOMATIC COMPENSATION
# ═══════════════════════════════════════════════════════════════════════════════
#  This demo shows how an error during finalization automatically triggers
#  the Saga compensation pattern - all completed steps are rolled back
#
#  Since we can't easily trigger a mid-workflow error in a stable system,
#  this demo shows cancel compensation which uses the same Saga mechanism
# ═══════════════════════════════════════════════════════════════════════════════

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  DEMO 06: FINALIZE ERROR → AUTOMATIC COMPENSATION"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  This demo shows automatic Saga compensation on error/interruption"
echo "  When a step fails, all previous steps are automatically rolled back"
echo ""
echo "  Saga Pattern: If step N fails →"
echo "    compensate(N-1) → compensate(N-2) → ... → compensate(1)"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 1: Create Valid Receipt"
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
echo ""

if [ -z "$RECEIPT_KEY" ] || [ "$SUCCESS" != "True" ]; then
  echo "ERROR: Failed to create receipt. Exiting."
  exit 1
fi

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 2: Start ASYNC Finalization"
echo "─────────────────────────────────────────────────────────────"
FINALIZE_RESP=$(curl -s -X POST "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/async" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: demo-user" \
  -d '{
    "storerKey": "NIKE",
    "facility": "KR01"
  }')
echo "$FINALIZE_RESP" | python3 -m json.tool 2>/dev/null || echo "$FINALIZE_RESP"

WORKFLOW_ID=$(echo "$FINALIZE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('workflowId',''))" 2>/dev/null || echo "")
echo ""
echo "Workflow ID: $WORKFLOW_ID"
echo ""

if [ -z "$WORKFLOW_ID" ]; then
  echo "ERROR: Failed to start workflow. Exiting."
  exit 1
fi

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 3: Simulate Error by Sending CANCEL (same Saga compensation)"
echo "─────────────────────────────────────────────────────────────"
echo "  In production, errors trigger the same compensation mechanism"
echo "  Sending cancel to demonstrate Saga rollback..."
sleep 0.3
CANCEL_RESP=$(curl -s -X POST "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/cancel")
echo "  Cancel/Error signal sent!"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 4: Monitor Compensation Execution"
echo "─────────────────────────────────────────────────────────────"
for i in 1 2 3 4 5 6 7 8; do
  sleep 0.5
  STATUS=$(curl -s "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/status")

  CURRENT_STATUS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status',''))" 2>/dev/null || echo "UNKNOWN")
  CURRENT_STEP=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('currentStep',''))" 2>/dev/null || echo "UNKNOWN")
  PROGRESS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('progress',0))" 2>/dev/null || echo "0")
  COMPLETED=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('completedSteps',[])))" 2>/dev/null || echo "0")

  if [ "$CURRENT_STATUS" = "COMPENSATING" ]; then
    echo "  [$i] >>> COMPENSATING <<< Rolling back $COMPLETED completed steps..."
  elif [ "$CURRENT_STATUS" = "CANCELLED" ] || [ "$CURRENT_STATUS" = "FAILED" ]; then
    echo "  [$i] Status: $CURRENT_STATUS | Compensation complete | Steps rolled back"
    break
  else
    printf "  [%d] Status: %-12s | Step: %-20s | Progress: %3s%%\n" "$i" "$CURRENT_STATUS" "$CURRENT_STEP" "$PROGRESS"
  fi
done
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 5: Final Result"
echo "─────────────────────────────────────────────────────────────"
FINAL_STATUS=$(curl -s "$BASE_URL/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/status")
echo "$FINAL_STATUS" | python3 -m json.tool 2>/dev/null || echo "$FINAL_STATUS"

# Extract for summary
FINAL_STATE=$(echo "$FINAL_STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status',''))" 2>/dev/null || echo "UNKNOWN")
COMPLETED_STEPS=$(echo "$FINAL_STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); steps=d.get('completedSteps',[]); print(len(steps))" 2>/dev/null || echo "0")

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  ERROR/CANCEL COMPENSATION DEMO COMPLETE"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  Final Status: $FINAL_STATE"
echo "  Steps Completed Before Interruption: $COMPLETED_STEPS"
echo ""
echo "  What happens on ERROR or CANCEL:"
echo "  1. Workflow starts and completes some steps"
echo "  2. Error/Cancel occurs at step N"
echo "  3. Saga automatically triggers compensation"
echo "  4. All completed steps are rolled back in REVERSE order:"
echo ""
echo "     Compensation Actions (reverse order):"
echo "       ← rollbackPostPlugins()      (if POST_PLUGINS completed)"
echo "       ← releasePutaway()           (if RELEASE_PUTAWAY completed)"
echo "       ← revertPOQty()              (if UPDATE_PO_QTY completed)"
echo "       ← removeHolds()              (if APPLY_HOLDS completed)"
echo "       ← deleteInventory()          (if POST_INVENTORY completed)"
echo "       ← revertStatus()             (if SET_STATUS_FINALIZING completed)"
echo "       ← rollbackPrePlugins()       (if PRE_PLUGINS completed)"
echo ""
echo "  5. Final status: CANCELLED/FAILED (but data is consistent!)"
echo ""
echo "  KEY BENEFIT: No manual cleanup required!"
echo "  The system automatically maintains data consistency."
echo ""
echo "  View in Temporal UI: http://localhost:8088"
echo ""
