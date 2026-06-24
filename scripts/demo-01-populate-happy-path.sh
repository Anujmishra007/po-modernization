#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
#  DEMO 01: PO POPULATE - HAPPY PATH
# ═══════════════════════════════════════════════════════════════════════════════
#  This demo shows the successful PO → ASN Population flow
#  No compensation needed - all steps complete successfully
# ═══════════════════════════════════════════════════════════════════════════════

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  DEMO 01: PO POPULATE - HAPPY PATH"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  This demo shows successful PO to ASN population"
echo "  Steps: Context → Plugins → Validate → Map → Lottables → Persist"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 1: Start ASYNC Population"
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

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 2: Monitor Progress"
echo "─────────────────────────────────────────────────────────────"
for i in 1 2 3 4 5 6 7 8 9 10; do
  sleep 1
  STATUS=$(curl -s "$BASE_URL/api/v1/populate/populate/$WORKFLOW_ID/status")

  CURRENT_STATUS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status',''))" 2>/dev/null || echo "UNKNOWN")
  CURRENT_STEP=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('currentStep',''))" 2>/dev/null || echo "UNKNOWN")
  PROGRESS=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('progress',0))" 2>/dev/null || echo "0")

  printf "  [%2d] Status: %-12s | Step: %-20s | Progress: %s%%\n" "$i" "$CURRENT_STATUS" "$CURRENT_STEP" "$PROGRESS"

  if [ "$CURRENT_STATUS" = "COMPLETED" ] || [ "$CURRENT_STATUS" = "FAILED" ]; then
    break
  fi
done
echo ""

# ─────────────────────────────────────────────────────────────────────────────
echo "STEP 3: Final Result"
echo "─────────────────────────────────────────────────────────────"
FINAL_STATUS=$(curl -s "$BASE_URL/api/v1/populate/populate/$WORKFLOW_ID/status")
echo "$FINAL_STATUS" | python3 -m json.tool 2>/dev/null || echo "$FINAL_STATUS"

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  POPULATE HAPPY PATH COMPLETE"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  Saga Steps Completed:"
echo "  1. RESOLVE_CONTEXT     - Determined client/region context"
echo "  2. PRE_PLUGINS         - Ran pre-populate plugins (NIKE/KR)"
echo "  3. VALIDATION          - Validated PO data"
echo "  4. MAPPING             - Mapped PO to ASN structure"
echo "  5. LOTTABLES           - Applied lottable rules"
echo "  6. CREATE_HEADER       - Created Receipt header"
echo "  7. CREATE_DETAILS      - Created Receipt details"
echo "  8. CREATE_RESERVATIONS - Created inventory reservations"
echo "  9. LEGACY_SYNC         - Synced to legacy (if dual-write enabled)"
echo "  10. NOTIFICATION       - Sent completion notification"
echo ""
echo "  View in Temporal UI: http://localhost:8088"
echo ""
