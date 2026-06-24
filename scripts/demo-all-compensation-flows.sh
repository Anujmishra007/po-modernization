#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════════════
#  MASTER DEMO: ALL COMPENSATION FLOWS
# ═══════════════════════════════════════════════════════════════════════════════
#  This script runs all compensation demos in sequence
#  Each demo shows a different aspect of the Saga compensation pattern
# ═══════════════════════════════════════════════════════════════════════════════

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"

echo ""
echo "╔══════════════════════════════════════════════════════════════════════════╗"
echo "║           PO MODERNIZATION - SAGA COMPENSATION DEMOS                     ║"
echo "╠══════════════════════════════════════════════════════════════════════════╣"
echo "║                                                                          ║"
echo "║  These demos show the Temporal Saga pattern for compensation             ║"
echo "║                                                                          ║"
echo "║  Available Demos:                                                        ║"
echo "║    1. Populate Happy Path      - Successful PO → ASN flow                ║"
echo "║    2. Populate Cancel          - Cancel triggers compensation            ║"
echo "║    3. Finalize Happy Path      - Successful finalization                 ║"
echo "║    4. Finalize Cancel          - Cancel before point-of-no-return        ║"
echo "║    5. Finalize Pause/Resume    - Pause and resume workflow               ║"
echo "║    6. Finalize Error           - Error triggers auto-compensation        ║"
echo "║    7. Trade Return Cancel      - Trade return with compensation          ║"
echo "║                                                                          ║"
echo "╚══════════════════════════════════════════════════════════════════════════╝"
echo ""

# Check if server is running
echo "Checking server at $BASE_URL..."
if curl -s --connect-timeout 5 "$BASE_URL/actuator/health" > /dev/null 2>&1; then
  echo "✓ Server is running"
else
  echo "✗ Server not reachable at $BASE_URL"
  echo "  Please start the PO Modernization server first:"
  echo "    cd po-modernization && ./gradlew bootRun"
  echo ""
  exit 1
fi
echo ""

# Menu
echo "Select demo to run (or 'all' for all demos, 'q' to quit):"
echo ""
echo "  1) Populate - Happy Path"
echo "  2) Populate - Cancel Compensation"
echo "  3) Finalize - Happy Path"
echo "  4) Finalize - Cancel Compensation"
echo "  5) Finalize - Pause/Resume"
echo "  6) Finalize - Error Compensation"
echo "  7) Trade Return - Cancel Compensation"
echo "  a) Run ALL demos"
echo "  q) Quit"
echo ""

run_demo() {
  local demo_num=$1
  case $demo_num in
    1) bash "$SCRIPT_DIR/demo-01-populate-happy-path.sh" ;;
    2) bash "$SCRIPT_DIR/demo-02-populate-cancel-compensation.sh" ;;
    3) bash "$SCRIPT_DIR/demo-03-finalize-happy-path.sh" ;;
    4) bash "$SCRIPT_DIR/demo-04-finalize-cancel-compensation.sh" ;;
    5) bash "$SCRIPT_DIR/demo-05-finalize-pause-resume.sh" ;;
    6) bash "$SCRIPT_DIR/demo-06-finalize-error-compensation.sh" ;;
    7) bash "$SCRIPT_DIR/demo-07-trade-return-compensation.sh" ;;
    *) echo "Invalid selection" ;;
  esac
}

while true; do
  read -p "Enter selection: " choice

  case $choice in
    1|2|3|4|5|6|7)
      run_demo "$choice"
      echo ""
      read -p "Press Enter to continue..."
      ;;
    a|A|all)
      echo ""
      echo "Running ALL demos..."
      echo ""
      for i in 1 2 3 4 5 6 7; do
        echo ""
        echo "════════════════════════════════════════════════════════════════"
        echo "  Running Demo $i of 7"
        echo "════════════════════════════════════════════════════════════════"
        run_demo "$i"
        echo ""
        if [ "$i" -lt 7 ]; then
          read -p "Press Enter for next demo..."
        fi
      done
      echo ""
      echo "╔══════════════════════════════════════════════════════════════════════════╗"
      echo "║                    ALL DEMOS COMPLETED                                   ║"
      echo "╚══════════════════════════════════════════════════════════════════════════╝"
      ;;
    q|Q|quit|exit)
      echo "Goodbye!"
      exit 0
      ;;
    *)
      echo "Invalid selection. Please enter 1-7, 'a' for all, or 'q' to quit."
      ;;
  esac
done
