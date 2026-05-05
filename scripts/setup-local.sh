#!/bin/bash

# PO Modernization - Local Development Setup Script
# Usage: ./scripts/setup-local.sh

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="$( cd "$SCRIPT_DIR/.." && pwd )"

echo "========================================"
echo "PO Modernization - Local Setup"
echo "========================================"

# Check prerequisites
check_prerequisites() {
    echo "Checking prerequisites..."

    # Check Java
    if ! command -v java &> /dev/null; then
        echo "ERROR: Java is not installed. Please install Java 17+."
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        echo "ERROR: Java 17+ is required. Found Java $JAVA_VERSION."
        exit 1
    fi
    echo "  ✓ Java $JAVA_VERSION"

    # Check Docker
    if ! command -v docker &> /dev/null; then
        echo "WARNING: Docker is not installed. Temporal will need to be started manually."
    else
        echo "  ✓ Docker"
    fi

    # Check Maven wrapper
    if [ ! -f "$PROJECT_DIR/mvnw" ]; then
        echo "ERROR: Maven wrapper not found. Please run from project root."
        exit 1
    fi
    echo "  ✓ Maven wrapper"
}

# Start Temporal
start_temporal() {
    echo ""
    echo "Starting Temporal..."

    if command -v docker &> /dev/null; then
        cd "$PROJECT_DIR"
        docker-compose -f docker-compose-local.yml up -d

        echo "Waiting for Temporal to start..."
        sleep 10

        # Check if Temporal is ready
        if curl -s http://localhost:7233 > /dev/null 2>&1; then
            echo "  ✓ Temporal is running on port 7233"
            echo "  ✓ Temporal UI available at http://localhost:8081"
        else
            echo "  ! Temporal may still be starting. Please wait and check http://localhost:8081"
        fi
    else
        echo "  ! Docker not available. Please start Temporal manually."
    fi
}

# Build project
build_project() {
    echo ""
    echo "Building project..."

    cd "$PROJECT_DIR"
    ./mvnw clean install -DskipTests

    if [ $? -eq 0 ]; then
        echo "  ✓ Build successful"
    else
        echo "  ✗ Build failed"
        exit 1
    fi
}

# Print next steps
print_next_steps() {
    echo ""
    echo "========================================"
    echo "Setup Complete!"
    echo "========================================"
    echo ""
    echo "Next steps:"
    echo ""
    echo "1. Start the application:"
    echo "   ./mvnw -pl po-api spring-boot:run -Dspring-boot.run.profiles=local"
    echo ""
    echo "2. Test the API:"
    echo "   # Check health"
    echo "   curl http://localhost:8080/actuator/health"
    echo ""
    echo "   # Create a PO"
    echo "   curl -X POST http://localhost:8080/api/v1/po \\"
    echo "     -H 'Content-Type: application/json' \\"
    echo "     -d '{\"storerKey\":\"TEST01\",\"facility\":\"WH01\",\"supplierCode\":\"SUP001\",\"details\":[{\"sku\":\"SKU001\",\"qtyOrdered\":100}]}'"
    echo ""
    echo "3. View Temporal workflows:"
    echo "   Open http://localhost:8081 in your browser"
    echo ""
    echo "4. View H2 Console (for debugging):"
    echo "   Open http://localhost:8080/h2-console"
    echo "   JDBC URL: jdbc:h2:file:./data/podb"
    echo ""
}

# Main
main() {
    check_prerequisites
    start_temporal
    build_project
    print_next_steps
}

main "$@"
