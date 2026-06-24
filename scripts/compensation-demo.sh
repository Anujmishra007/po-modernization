#!/bin/bash

echo "═══════════════════════════════════════════════════════════════"
echo "  ASYNC COMPENSATION DEMO"
echo "═══════════════════════════════════════════════════════════════"
echo ""

echo "STEP 1: Create Receipt via Populate (Sync)"
echo "─────────────────────────────────────────────────────────────"
POPULATE_RESP=$(curl -s -X POST "http://localhost:8080/api/v1/populate/populate" \
  -H "Content-Type: application/json" \
  -d '{"poKeys":["PO-XDOCK-001"],"storerKey":"NIKE","facility":"KR01","userId":"demo-user"}')
echo "$POPULATE_RESP" | python3 -m json.tool

RECEIPT_KEY=$(echo "$POPULATE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('receiptKey',''))")
echo ""
echo "Receipt Key: $RECEIPT_KEY"
echo ""

echo "STEP 2: Start ASYNC Finalize"
echo "─────────────────────────────────────────────────────────────"
FINALIZE_RESP=$(curl -s -X POST "http://localhost:8080/api/v1/receipts/$RECEIPT_KEY/finalize/async" \
  -H "Content-Type: application/json" \
  -d '{"storerKey":"NIKE","facility":"KR01"}')
echo "$FINALIZE_RESP" | python3 -m json.tool

WORKFLOW_ID=$(echo "$FINALIZE_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('workflowId',''))")
echo ""
echo "Workflow ID: $WORKFLOW_ID"
echo ""

echo "STEP 3: Check Status (Running)"
echo "─────────────────────────────────────────────────────────────"
sleep 1
curl -s "http://localhost:8080/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/status" | python3 -m json.tool
echo ""

echo "STEP 4: CANCEL Workflow -> Trigger Compensation"
echo "─────────────────────────────────────────────────────────────"
curl -s -X POST "http://localhost:8080/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/cancel" | python3 -m json.tool
echo ""

echo "STEP 5: Monitor Compensation"
echo "─────────────────────────────────────────────────────────────"
for i in 1 2 3; do
  sleep 1
  STATUS=$(curl -s "http://localhost:8080/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/status")
  CURRENT=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'Status: {d.get(\"status\")} | Step: {d.get(\"currentStep\")} | CanCancel: {d.get(\"canCancel\")}')")
  echo "  [$i] $CURRENT"
done
echo ""

echo "STEP 6: Final Result"
echo "─────────────────────────────────────────────────────────────"
curl -s "http://localhost:8080/api/v1/receipts/$RECEIPT_KEY/finalize/$WORKFLOW_ID/status" | python3 -m json.tool

echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "  COMPENSATION DEMO COMPLETE"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "  View in Temporal UI: http://localhost:8088"
