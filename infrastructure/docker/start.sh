#!/bin/bash

# Bookstore Local Development Startup Script

set -e

echo "🚀 Starting Bookstore Local Development Environment"
echo "=================================================="

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker and try again."
    exit 1
fi

# Check if Docker Compose is available
if ! command -v docker-compose &> /dev/null; then
    echo "❌ docker-compose is not installed. Please install Docker Compose."
    exit 1
fi

echo "✅ Docker and Docker Compose are available"

# Create .env file if it doesn't exist
if [ ! -f .env ]; then
    echo "📝 Creating .env file from template..."
    cp .env.example .env 2>/dev/null || echo "⚠️  .env.example not found. Please create .env manually."
fi

echo "🐳 Starting services with Docker Compose..."
docker-compose up -d

echo "⏳ Waiting for services to be healthy..."
echo "   This may take 2-3 minutes..."

# Wait for PostgreSQL
echo "   Waiting for PostgreSQL..."
timeout=60
while [ $timeout -gt 0 ]; do
    if docker-compose exec -T postgres pg_isready -U postgres > /dev/null 2>&1; then
        echo "   ✅ PostgreSQL is ready"
        break
    fi
    echo "   ⏳ PostgreSQL not ready yet... ($timeout seconds remaining)"
    sleep 2
    timeout=$((timeout - 2))
done

if [ $timeout -le 0 ]; then
    echo "❌ PostgreSQL failed to start within 60 seconds"
    exit 1
fi

# Wait for Redis
echo "   Waiting for Redis..."
timeout=30
while [ $timeout -gt 0 ]; do
    if docker-compose exec -T redis redis-cli ping | grep -q PONG; then
        echo "   ✅ Redis is ready"
        break
    fi
    echo "   ⏳ Redis not ready yet... ($timeout seconds remaining)"
    sleep 2
    timeout=$((timeout - 2))
done

if [ $timeout -le 0 ]; then
    echo "❌ Redis failed to start within 30 seconds"
    exit 1
fi

# Wait for Auth Service
echo "   Waiting for Auth Service..."
timeout=120
while [ $timeout -gt 0 ]; do
    if curl -f http://localhost:8081/actuator/health > /dev/null 2>&1; then
        echo "   ✅ Auth Service is ready"
        break
    fi
    echo "   ⏳ Auth Service not ready yet... ($timeout seconds remaining)"
    sleep 5
    timeout=$((timeout - 5))
done

if [ $timeout -le 0 ]; then
    echo "❌ Auth Service failed to start within 2 minutes"
    echo "   Check logs: docker-compose logs auth-service"
    exit 1
fi

# Wait for User Service
echo "   Waiting for User Service..."
timeout=120
while [ $timeout -gt 0 ]; do
    if curl -f http://localhost:8082/actuator/health > /dev/null 2>&1; then
        echo "   ✅ User Service is ready"
        break
    fi
    echo "   ⏳ User Service not ready yet... ($timeout seconds remaining)"
    sleep 5
    timeout=$((timeout - 5))
done

if [ $timeout -le 0 ]; then
    echo "❌ User Service failed to start within 2 minutes"
    echo "   Check logs: docker-compose logs user-service"
    exit 1
fi

# Wait for Product Catalog Service
echo "   Waiting for Product Catalog Service..."
timeout=120
while [ $timeout -gt 0 ]; do
    if curl -f http://localhost:8083/actuator/health > /dev/null 2>&1; then
        echo "   ✅ Product Catalog Service is ready"
        break
    fi
    echo "   ⏳ Product Catalog Service not ready yet... ($timeout seconds remaining)"
    sleep 5
    timeout=$((timeout - 5))
done

if [ $timeout -le 0 ]; then
    echo "❌ Product Catalog Service failed to start within 2 minutes"
    echo "   Check logs: docker-compose logs product-catalog"
    exit 1
fi

# Wait for Order Service
echo "   Waiting for Order Service..."
timeout=120
while [ $timeout -gt 0 ]; do
    if curl -f http://localhost:8084/actuator/health > /dev/null 2>&1; then
        echo "   ✅ Order Service is ready"
        break
    fi
    echo "   ⏳ Order Service not ready yet... ($timeout seconds remaining)"
    sleep 5
    timeout=$((timeout - 5))
done

if [ $timeout -le 0 ]; then
    echo "❌ Order Service failed to start within 2 minutes"
    echo "   Check logs: docker-compose logs order-service"
    exit 1
fi

echo ""
echo "🎉 All services are up and running!"
echo ""
echo "📋 Service URLs:"
echo "   Auth Service API:     http://localhost:8081/api/v1/auth"
echo "   pgAdmin:             http://localhost:5050 (admin@bookstore.com / admin123)"
echo "   Redis Commander:     http://localhost:8082"
echo "   Kibana:              http://localhost:5601"
echo "   LocalStack:          http://localhost:4566"
echo ""
echo "🧪 Test the Auth Service:"
echo "   Health Check: curl http://localhost:8081/actuator/health"
echo "   Register: curl -X POST http://localhost:8081/api/v1/auth/register \\"
echo "             -H 'Content-Type: application/json' \\"
echo "             -d '{\"email\":\"test@example.com\",\"password\":\"password123\"}'"
echo ""
echo "📊 View logs: docker-compose logs -f [service-name]"
echo "🛑 Stop all: docker-compose down"
echo "🔄 Restart: docker-compose restart [service-name]"
echo ""
echo "Happy coding! 🎯"