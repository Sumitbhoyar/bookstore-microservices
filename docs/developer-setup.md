# Developer Setup Guide

This guide will help you set up your development environment for the Online Bookstore Microservices project.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Development Environment Setup](#development-environment-setup)
3. [Project Structure Overview](#project-structure-overview)
4. [Running Individual Services](#running-individual-services)
5. [Running with Docker Compose](#running-with-docker-compose)
6. [Database Setup](#database-setup)
7. [Testing](#testing)
8. [Debugging](#debugging)
9. [Troubleshooting](#troubleshooting)
10. [Contributing Guidelines](#contributing-guidelines)

---

## Prerequisites

### Required Software

| Tool | Version | Download Link | Purpose |
|------|---------|---------------|---------|
| **Java** | 21 LTS | [Adoptium](https://adoptium.net/) | Java services (Auth, User, Catalog, Order) |
| **Python** | 3.11+ | [Python.org](https://python.org) | Search and Recommendation services |
| **Node.js** | 18+ | [Node.js](https://nodejs.org) | TypeScript services (Payment, Notifications) |
| **Docker** | 24.0+ | [Docker Desktop](https://docker.com) | Containerization and local infrastructure |
| **Docker Compose** | 2.0+ | Included with Docker Desktop | Multi-container orchestration |
| **Git** | 2.30+ | [Git](https://git-scm.com) | Version control |
| **AWS CLI** | 2.0+ | [AWS CLI](https://aws.amazon.com/cli/) | AWS resource management |

### Optional Tools

| Tool | Version | Download Link | Purpose |
|------|---------|---------------|---------|
| **IntelliJ IDEA** | 2023+ | [JetBrains](https://jetbrains.com/idea/) | Java development (recommended) |
| **VS Code** | 1.80+ | [VS Code](https://code.visualstudio.com) | Python and TypeScript development |
| **Postman** | Latest | [Postman](https://postman.com) | API testing and exploration |
| **pgAdmin** | Latest | [pgAdmin](https://pgadmin.org) | PostgreSQL database management |
| **DBeaver** | Latest | [DBeaver](https://dbeaver.io) | Universal database client |
| **Lens** | Latest | [Lens](https://k8slens.dev) | Kubernetes dashboard (if using k8s) |

### System Requirements

- **Operating System**: Windows 10/11, macOS 12+, Ubuntu 20.04+
- **RAM**: Minimum 16GB, Recommended 32GB
- **Disk Space**: 20GB free space
- **CPU**: 4+ cores recommended

### Verification Commands

Verify your installations:

```bash
# Java
java -version
# Should show: OpenJDK 21.x.x

# Python
python --version
# Should show: Python 3.11.x

# Node.js and npm
node --version
# Should show: v18.x.x
npm --version
# Should show: 9.x.x

# Docker
docker --version
# Should show: Docker version 24.x.x
docker-compose --version
# Should show: Docker Compose version 2.x.x

# AWS CLI
aws --version
# Should show: aws-cli/2.x.x

# Git
git --version
# Should show: git version 2.30.x
```

---

## Development Environment Setup

### 1. Clone the Repository

```bash
git clone https://github.com/your-repo/bookstore-microservices.git
cd bookstore-microservices
```

### 2. Set Up Environment Variables

Create a `.env` file in the project root:

```bash
# Copy the example file
cp .env.example .env

# Edit with your values
nano .env  # or use your preferred editor
```

Example `.env` file:

```bash
# Database Configuration
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=bookstore
POSTGRES_USER=bookstore_user
POSTGRES_PASSWORD=bookstore_password

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Elasticsearch Configuration
ELASTICSEARCH_HOST=localhost
ELASTICSEARCH_PORT=9200

# AWS Configuration (for local development)
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key

# JWT Configuration
JWT_SECRET=your-super-secret-jwt-key-change-this-in-production
JWT_EXPIRATION=3600000

# Stripe Configuration (test keys)
STRIPE_PUBLIC_KEY=pk_test_your-stripe-public-key
STRIPE_SECRET_KEY=sk_test_your-stripe-secret-key

# Email Configuration (AWS SES)
AWS_SES_REGION=us-east-1
AWS_SES_FROM_EMAIL=noreply@bookstore.com

# Service URLs (for inter-service communication)
AUTH_SERVICE_URL=http://localhost:8081
USER_SERVICE_URL=http://localhost:8082
CATALOG_SERVICE_URL=http://localhost:8083
ORDER_SERVICE_URL=http://localhost:8084
SEARCH_SERVICE_URL=http://localhost:8085
PAYMENT_SERVICE_URL=http://localhost:8086
NOTIFICATION_SERVICE_URL=http://localhost:8087
```

### 3. Install Dependencies

#### Java Services Dependencies

```bash
# Auth Service
cd java-services/auth-service
./mvnw dependency:resolve

# User Service
cd ../user-service
./mvnw dependency:resolve

# Product Catalog
cd ../product-catalog
./mvnw dependency:resolve

# Order Service
cd ../order-service
./mvnw dependency:resolve
```

#### Python Services Dependencies

```bash
# Search Service
cd ../../python-services/search-service
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt

# Recommendation Service
cd ../recommendation-service
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

#### TypeScript Services Dependencies

```bash
# Payment Service
cd ../../typescript-services/payment-service
npm install

# Notifications Service
cd ../notifications-service
npm install
```

---

## Project Structure Overview

```
bookstore-microservices/
├── specs/                          # System specifications and documentation
├── java-services/                  # Java Spring Boot microservices
│   ├── auth-service/              # Authentication and authorization
│   ├── user-service/              # User profile management
│   ├── product-catalog/           # Product catalog and inventory
│   └── order-service/             # Order processing and fulfillment
├── python-services/               # Python FastAPI services
│   ├── search-service/            # Full-text search and filtering
│   └── recommendation-service/    # ML-based recommendations
├── typescript-services/           # TypeScript NestJS services
│   ├── payment-service/           # Payment processing with Stripe
│   └── notifications-service/     # Email/SMS/webhook notifications
├── infrastructure/                # Infrastructure as Code
│   ├── aws/                       # Terraform AWS deployment
│   └── docker/                    # Local development with Docker
├── docs/                          # Documentation
└── run-tests.sh                   # Build and test script
```

### Service Ports

| Service | Port | Technology | Purpose |
|---------|------|------------|---------|
| Auth Service | 8081 | Java/Spring Boot | JWT tokens, user auth |
| User Service | 8082 | Java/Spring Boot | User profiles, accounts |
| Product Catalog | 8083 | Java/Spring Boot | Products, inventory, categories |
| Order Service | 8084 | Java/Spring Boot | Order lifecycle, cart |
| Search Service | 8085 | Python/FastAPI | Full-text search, filtering |
| Payment Service | 8086 | TypeScript/NestJS | Stripe payment processing |
| Notifications | 8087 | TypeScript/NestJS | Email, SMS, webhooks |
| API Gateway | 8080 | Nginx/Traefik | Request routing, rate limiting |

---

## Running Individual Services

### Option 1: Manual Service Startup

#### Start Infrastructure First

```bash
# Start PostgreSQL, Redis, and Elasticsearch
cd infrastructure/docker
docker-compose up -d postgres redis elasticsearch
```

#### Start Java Services

```bash
# Terminal 1: Auth Service
cd java-services/auth-service
./mvnw spring-boot:run

# Terminal 2: User Service
cd ../user-service
./mvnw spring-boot:run

# Terminal 3: Product Catalog
cd ../product-catalog
./mvnw spring-boot:run

# Terminal 4: Order Service
cd ../order-service
./mvnw spring-boot:run
```

#### Start Python Services

```bash
# Terminal 5: Search Service
cd python-services/search-service
source venv/bin/activate
uvicorn app.main:app --reload --host 0.0.0.0 --port 8085

# Terminal 6: Recommendation Service
cd ../recommendation-service
source venv/bin/activate
uvicorn app.main:app --reload --host 0.0.0.0 --port 8088
```

#### Start TypeScript Services

```bash
# Terminal 7: Payment Service
cd typescript-services/payment-service
npm run start:dev

# Terminal 8: Notifications Service
cd ../notifications-service
npm run start:dev
```

### Option 2: IDE-Based Development

#### IntelliJ IDEA (Java Services)

1. **Import Project**: File → Open → Select `java-services/pom.xml`
2. **Run Configuration**: Create Spring Boot run configurations for each service
3. **Environment Variables**: Set environment variables in run configuration
4. **Debug Mode**: Use debug configurations for breakpoints

#### VS Code (Python/TypeScript Services)

1. **Open Folder**: File → Open Folder → Select service directory
2. **Install Extensions**:
   - Python
   - Pylance
   - TypeScript Importer
   - Prettier
   - ESLint
3. **Configure Launch**: Create `.vscode/launch.json` for debugging

---

## Running with Docker Compose

### Quick Start with Docker Compose

```bash
# Start all services
cd infrastructure/docker
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down
```

### Development with Docker Compose

For development with hot reload:

```bash
# Use the development compose file
cd infrastructure/docker
docker-compose -f docker-compose.dev.yml up -d

# This mounts source code volumes for hot reload
```

### Docker Compose Services

```yaml
# Key services in docker-compose.yml
services:
  postgres:
    image: postgres:15
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: bookstore
      POSTGRES_USER: bookstore_user
      POSTGRES_PASSWORD: bookstore_password

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  elasticsearch:
    image: elasticsearch:8.11.0
    ports: ["9200:9200", "9300:9300"]
    environment:
      discovery.type: single-node
      xpack.security.enabled: false

  auth-service:
    build: ../../java-services/auth-service
    ports: ["8081:8080"]
    depends_on: [postgres, redis]

  # ... other services
```

---

## Database Setup

### PostgreSQL Database Initialization

```bash
# Connect to PostgreSQL
psql -h localhost -U bookstore_user -d bookstore

# Run initialization scripts
\i infrastructure/docker/init-scripts/01-create-databases.sql
```

### Database Schema Migration

Each Java service handles its own schema:

```bash
# Auth Service migrations
cd java-services/auth-service
./mvnw flyway:migrate

# User Service migrations
cd ../user-service
./mvnw flyway:migrate

# And so on for other services...
```

### Sample Data

```bash
# Insert sample data (optional)
psql -h localhost -U bookstore_user -d bookstore -f scripts/sample-data.sql
```

### Database Tools

#### pgAdmin Setup

1. **Install pgAdmin**
2. **Create Server Connection**:
   - Host: localhost
   - Port: 5432
   - Database: bookstore
   - Username: bookstore_user
   - Password: bookstore_password

#### Useful Queries

```sql
-- Check service databases
\l

-- Check tables in auth database
\c auth
\dt

-- View recent orders
\c orders
SELECT * FROM orders ORDER BY created_at DESC LIMIT 10;

-- Check product inventory
\c catalog
SELECT p.title, i.quantity_available
FROM products p
JOIN inventory i ON p.id = i.product_id;
```

---

## Testing

### Unit Tests

```bash
# Java services
cd java-services/auth-service
./mvnw test

# Python services
cd python-services/search-service
source venv/bin/activate
pytest

# TypeScript services
cd typescript-services/payment-service
npm test
```

### Integration Tests

```bash
# Run all integration tests
cd infrastructure/docker
docker-compose -f docker-compose.test.yml up --abort-on-container-exit
```

### API Testing with Postman

1. **Import Collection**: `docs/postman/bookstore-api.postman_collection.json`
2. **Set Environment Variables**:
   - `base_url`: `http://localhost:8080`
   - `jwt_token`: Get from auth/login endpoint

### Load Testing

```bash
# Install k6
# https://k6.io/docs/get-started/installation/

# Run load tests
k6 run tests/load/auth-service.js
k6 run tests/load/order-creation.js
```

---

## Debugging

### Java Services (Spring Boot)

#### Enable Debug Mode

```bash
# Run with debug enabled
cd java-services/auth-service
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

#### IntelliJ Debug Configuration

1. **Run → Edit Configurations**
2. **Add Remote JVM Debug**:
   - Host: localhost
   - Port: 5005
3. **Set breakpoints and debug**

### Python Services (FastAPI)

#### Debug with VS Code

```json
// .vscode/launch.json
{
  "version": "0.2.0",
  "configurations": [
    {
      "name": "Python: FastAPI",
      "type": "python",
      "request": "launch",
      "module": "uvicorn",
      "args": ["app.main:app", "--reload", "--host", "0.0.0.0", "--port", "8085"],
      "env": {"PYTHONPATH": "${workspaceFolder}"}
    }
  ]
}
```

### TypeScript Services (NestJS)

#### Debug with VS Code

```json
// .vscode/launch.json
{
  "version": "0.2.0",
  "configurations": [
    {
      "name": "NestJS Debug",
      "type": "node",
      "request": "launch",
      "program": "${workspaceFolder}/node_modules/.bin/nest",
      "args": ["start", "--debug", "0.0.0.0:9229"],
      "env": {
        "NODE_ENV": "development"
      }
    }
  ]
}
```

### Logging

#### View Application Logs

```bash
# All services logs
cd infrastructure/docker
docker-compose logs -f

# Specific service logs
docker-compose logs -f auth-service

# Java service logs with filtering
docker-compose logs auth-service | grep ERROR
```

#### Structured Logging

All services use structured JSON logging:

```json
{
  "timestamp": "2026-01-18T12:27:00Z",
  "level": "INFO",
  "service": "auth-service",
  "correlation_id": "req_abc123",
  "message": "User authentication successful",
  "user_id": "user_456",
  "ip_address": "192.168.1.100"
}
```

---

## Troubleshooting

### Common Issues

#### Port Conflicts

```bash
# Check what's using ports
netstat -tulpn | grep :8080

# Kill process using port
sudo kill -9 $(sudo lsof -t -i:8080)
```

#### Database Connection Issues

```bash
# Test PostgreSQL connection
psql -h localhost -U bookstore_user -d bookstore -c "SELECT version();"

# Reset database
cd infrastructure/docker
docker-compose down -v
docker-compose up -d postgres
```

#### Service Startup Failures

```bash
# Check service logs
docker-compose logs auth-service

# Check environment variables
docker-compose exec auth-service env

# Check dependencies
docker-compose ps
```

#### Memory Issues

```bash
# Check Docker resource usage
docker stats

# Increase Docker memory limit in Docker Desktop settings
# Or add memory limits to docker-compose.yml
services:
  auth-service:
    deploy:
      resources:
        limits:
          memory: 1G
```

### Performance Issues

```bash
# Check CPU usage
top -p $(pgrep java)

# Java heap dump
jmap -dump:live,format=b,file=heap.hprof <pid>

# Database slow queries
# Enable query logging in PostgreSQL
```

### Network Issues

```bash
# Test service connectivity
curl http://localhost:8081/actuator/health

# Check service discovery
docker-compose exec auth-service nslookup postgres

# Test inter-service communication
curl http://localhost:8082/api/v1/users -H "Authorization: Bearer <token>"
```

---

## Contributing Guidelines

### Code Style

#### Java (Google Style Guide)

```xml
<!-- pom.xml -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-checkstyle-plugin</artifactId>
  <version>3.1.2</version>
  <configuration>
    <configLocation>google_checks.xml</configLocation>
  </configuration>
</plugin>
```

#### Python (PEP 8)

```bash
# Install black and flake8
pip install black flake8

# Format code
black .

# Check style
flake8 .
```

#### TypeScript (Airbnb)

```json
// package.json
{
  "scripts": {
    "lint": "eslint src/**/*.ts",
    "lint:fix": "eslint src/**/*.ts --fix",
    "format": "prettier --write src/**/*.ts"
  }
}
```

### Git Workflow

1. **Create feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make changes and commit**
   ```bash
   git add .
   git commit -m "feat: add user registration endpoint"
   ```

3. **Push and create PR**
   ```bash
   git push origin feature/your-feature-name
   ```

### Commit Message Format

```
type(scope): description

[optional body]

[optional footer]
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

### Pull Request Process

1. **Create PR** with descriptive title
2. **Add labels**: `enhancement`, `bug`, `documentation`
3. **Request reviews** from team members
4. **Address feedback** and update PR
5. **Merge** after approval

### Testing Requirements

- **Unit tests**: Minimum 80% coverage
- **Integration tests**: All new endpoints
- **API contract tests**: Request/response validation
- **Load tests**: For performance-critical changes

---

## Additional Resources

### Learning Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
- [NestJS Documentation](https://nestjs.com/)
- [AWS SDK Documentation](https://docs.aws.amazon.com/sdk-for-javascript/)
- [PostgreSQL Documentation](https://postgresql.org/docs/)

### Development Tools

- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/actuator-api/html/)
- [FastAPI Swagger UI](http://localhost:8085/docs)
- [NestJS Swagger](http://localhost:8086/api)
- [pgAdmin](https://pgadmin.org/)
- [Redis Commander](https://rediscommander.com/)

### Getting Help

- **Documentation**: Check the `specs/` and `docs/` directories
- **Issues**: [GitHub Issues](https://github.com/your-repo/bookstore-microservices/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-repo/bookstore-microservices/discussions)
- **Team Chat**: Slack/Teams channel for real-time help

Remember: This is a complex distributed system. Start with one service, understand its patterns, then expand to others. Happy coding! 🚀