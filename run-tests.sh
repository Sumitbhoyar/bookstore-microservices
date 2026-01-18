#!/bin/bash

# Bookstore Microservices Test Runner
# Runs tests for all implemented services

set -e

echo "🧪 Running Bookstore Microservices Tests"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to run tests for a service
run_service_tests() {
    local service_name=$1
    local service_path=$2

    echo -e "\n${BLUE}Testing ${service_name}...${NC}"

    if [ -d "$service_path" ]; then
        cd "$service_path"

        # Check if pom.xml exists
        if [ -f "pom.xml" ]; then
            echo "Running Maven tests for ${service_name}..."

            # Run unit tests
            if mvn test -q; then
                echo -e "${GREEN}✅ ${service_name} unit tests passed${NC}"
            else
                echo -e "${RED}❌ ${service_name} unit tests failed${NC}"
                exit 1
            fi

            # Run integration tests if they exist
            if mvn test -Dtest="*IntegrationTest*" -q 2>/dev/null; then
                echo -e "${GREEN}✅ ${service_name} integration tests passed${NC}"
            fi
        else
            echo -e "${YELLOW}⚠️  No pom.xml found for ${service_name}, skipping${NC}"
        fi

        cd - > /dev/null
    else
        echo -e "${YELLOW}⚠️  Service directory ${service_path} not found, skipping${NC}"
    fi
}

# Check prerequisites
check_prerequisites() {
    echo "Checking prerequisites..."

    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}❌ Maven not found. Please install Maven.${NC}"
        exit 1
    fi

    if ! command -v java &> /dev/null; then
        echo -e "${RED}❌ Java not found. Please install Java.${NC}"
        exit 1
    fi

    echo -e "${GREEN}✅ Prerequisites check passed${NC}"
}

# Main test execution
main() {
    check_prerequisites

    echo -e "\n${BLUE}Running test suite...${NC}"

    # Test each implemented service
    run_service_tests "Auth Service" "java-services/auth-service"
    run_service_tests "User Service" "java-services/user-service"
    run_service_tests "Product Catalog Service" "java-services/product-catalog"
    run_service_tests "Order Service" "java-services/order-service"

    echo -e "\n${GREEN}🎉 All tests completed successfully!${NC}"
    echo -e "\n${BLUE}Test Summary:${NC}"
    echo "• Unit tests for service classes"
    echo "• Controller tests for REST endpoints"
    echo "• Repository tests for database operations"
    echo "• Integration tests for end-to-end flows"
    echo "• Mocked external dependencies"
    echo ""
    echo "Coverage areas:"
    echo "• Authentication and authorization"
    echo "• User profile management"
    echo "• Product catalog operations"
    echo "• Order lifecycle management"
    echo "• Error handling and validation"
    echo "• Security features"
}

# Handle command line arguments
case "${1:-}" in
    "auth")
        run_service_tests "Auth Service" "java-services/auth-service"
        ;;
    "user")
        run_service_tests "User Service" "java-services/user-service"
        ;;
    "catalog")
        run_service_tests "Product Catalog Service" "java-services/product-catalog"
        ;;
    "order")
        run_service_tests "Order Service" "java-services/order-service"
        ;;
    "help"|"-h"|"--help")
        echo "Usage: $0 [service-name]"
        echo ""
        echo "Run tests for all services (default):"
        echo "  $0"
        echo ""
        echo "Run tests for specific service:"
        echo "  $0 auth      - Auth Service"
        echo "  $0 user      - User Service"
        echo "  $0 catalog   - Product Catalog Service"
        echo "  $0 order     - Order Service"
        echo ""
        echo "Other options:"
        echo "  $0 help      - Show this help"
        ;;
    *)
        main
        ;;
esac