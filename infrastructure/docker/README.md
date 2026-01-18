# Local Development Infrastructure

This directory contains Docker Compose configuration for local development of the Bookstore microservices system.

## Services Included

- **PostgreSQL**: Primary database for transactional services
- **Redis**: Caching and job queue storage
- **Elasticsearch**: Full-text search and analytics
- **LocalStack**: AWS services simulation (SQS, SNS, SES, Cognito, DynamoDB)
- **Auth Service**: Authentication and authorization microservice
- **pgAdmin**: Database administration interface
- **Redis Commander**: Redis management interface
- **Kibana**: Elasticsearch visualization

## Prerequisites

- Docker and Docker Compose
- At least 4GB RAM allocated to Docker
- Ports 5432, 6379, 9200, 5601, 4566, 8081, 5050, 8082 available

## Quick Start

1. **Clone and navigate to infrastructure directory:**
   ```bash
   cd infrastructure/docker
   ```

2. **Start all services:**
   ```bash
   docker-compose up -d
   ```

3. **Check service health:**
   ```bash
   docker-compose ps
   ```

4. **View logs:**
   ```bash
   docker-compose logs -f auth-service
   ```

## Service URLs

- **Auth Service API**: http://localhost:8081/api/v1/auth
- **User Service API**: http://localhost:8082/api/v1/users
- **Product Catalog API**: http://localhost:8083/api/v1/products
- **Order Service API**: http://localhost:8084/api/v1/orders
- **pgAdmin**: http://localhost:5050 (admin@bookstore.com / admin123)
- **Redis Commander**: http://localhost:8082
- **Kibana**: http://localhost:5601
- **LocalStack Dashboard**: http://localhost:4566

## Environment Configuration

Create a `.env` file in this directory with the following variables:

```bash
# PostgreSQL
POSTGRES_DB=bookstore
POSTGRES_USER=postgres
POSTGRES_PASSWORD=password

# JWT Configuration
JWT_SECRET=development-jwt-secret-key-change-in-production-32-chars-minimum
JWT_EXPIRATION=3600000

# AWS Configuration (LocalStack)
AWS_REGION=us-east-1
AWS_ENDPOINT=http://localhost:4566
COGNITO_USER_POOL_ID=us-east-1_test_pool
COGNITO_CLIENT_ID=test-client-id
COGNITO_ISSUER_URI=http://localhost:4566

# Service URLs
USER_SERVICE_URL=http://localhost:8082/api/v1/users
PRODUCT_CATALOG_URL=http://localhost:8083/api/v1/products
ORDER_SERVICE_URL=http://localhost:8084/api/v1/orders
SEARCH_SERVICE_URL=http://localhost:8085/api/v1/search
NOTIFICATIONS_URL=http://localhost:8086/api/v1/notifications
PAYMENT_SERVICE_URL=http://localhost:8087/api/v1/payments

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Elasticsearch
ELASTICSEARCH_HOST=localhost
ELASTICSEARCH_PORT=9200

# Observability
CLOUDWATCH_METRICS_ENABLED=false
XRAY_ENABLED=false
```

## Database Access

- **Host**: localhost:5432
- **Database**: bookstore_auth, bookstore_users, bookstore_catalog, bookstore_orders
- **User**: postgres
- **Password**: password

## Testing the Auth Service

1. **Health Check:**
   ```bash
   curl http://localhost:8081/actuator/health
   ```

2. **Register User:**
   ```bash
   curl -X POST http://localhost:8081/api/v1/auth/register \
     -H "Content-Type: application/json" \
     -d '{"email":"user@example.com","password":"password123"}'
   ```

3. **Login:**
   ```bash
   curl -X POST http://localhost:8081/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"user@example.com","password":"password123"}'
   ```

## Troubleshooting

### Common Issues

1. **Port conflicts**: Ensure no other services are using the required ports
2. **Memory issues**: Increase Docker memory allocation to at least 4GB
3. **Slow startup**: Services may take 2-3 minutes to fully initialize

### Reset Everything

```bash
# Stop and remove all containers and volumes
docker-compose down -v

# Rebuild and start fresh
docker-compose up --build -d
```

### View Service Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f auth-service

# Last 100 lines
docker-compose logs --tail=100 auth-service
```

## Development Workflow

1. Make code changes in the respective service directories
2. Rebuild specific service:
   ```bash
   docker-compose up --build -d auth-service
   ```
3. Test changes
4. Check logs for errors

## Next Steps

After setting up local infrastructure:
1. Implement remaining microservices
2. Set up AWS infrastructure with Terraform
3. Configure CI/CD pipelines
4. Add monitoring and observability