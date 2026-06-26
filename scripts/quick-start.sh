#!/bin/bash
#
# PO Modernization - Quick Start (No Docker Required)
# ====================================================
# Starts the application in standalone mode with H2 database
# Perfect for local development and quick testing
#
# Usage:
#   ./scripts/quick-start.sh          # Start app
#   ./scripts/quick-start.sh stop     # Stop app
#   ./scripts/quick-start.sh test     # Run Karate tests

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
MVN_CMD="${MVN_CMD:-/opt/homebrew/bin/mvn}"
APP_PORT=8080

cd "$PROJECT_DIR"

case "${1:-start}" in
    start)
        echo "🚀 Starting PO Modernization (Standalone Mode)..."
        echo ""

        # Build if needed
        if [ ! -f "po-api/target/po-api-1.0.0-SNAPSHOT-exec.jar" ]; then
            echo "📦 Building project..."
            JAVA_HOME="$JAVA_HOME" "$MVN_CMD" clean install -DskipTests -q
        fi

        # Start application
        echo "🌐 Starting application on http://localhost:$APP_PORT"
        cd po-api
        JAVA_HOME="$JAVA_HOME" "$MVN_CMD" spring-boot:run -Dspring-boot.run.profiles=local &

        # Wait for startup
        echo ""
        echo "⏳ Waiting for application to start..."
        for i in {1..30}; do
            if curl -sf "http://localhost:$APP_PORT/actuator/health" > /dev/null 2>&1; then
                echo ""
                echo "✅ Application started successfully!"
                echo ""
                echo "📍 Endpoints:"
                echo "   • Health:     http://localhost:$APP_PORT/api/v1/health"
                echo "   • Swagger:    http://localhost:$APP_PORT/swagger-ui.html"
                echo "   • H2 Console: http://localhost:$APP_PORT/h2-console"
                echo ""
                echo "Press Ctrl+C to stop"
                wait
                exit 0
            fi
            sleep 1
        done
        echo "❌ Application failed to start"
        exit 1
        ;;

    stop)
        echo "🛑 Stopping application..."
        pkill -f "po-api" 2>/dev/null || true
        lsof -ti:$APP_PORT | xargs kill 2>/dev/null || true
        echo "✅ Stopped"
        ;;

    test)
        echo "🧪 Running Karate E2E Tests..."
        echo ""

        # Check if app is running
        if ! curl -sf "http://localhost:$APP_PORT/actuator/health" > /dev/null 2>&1; then
            echo "❌ Application not running. Start it first with: ./scripts/quick-start.sh"
            exit 1
        fi

        JAVA_HOME="$JAVA_HOME" "$MVN_CMD" test -pl po-test \
            -Dtest=KarateRunner \
            -Dkarate.env=local

        echo ""
        echo "📊 Test reports: po-test/target/karate-reports/karate-summary.html"
        ;;

    *)
        echo "Usage: $0 [start|stop|test]"
        ;;
esac
