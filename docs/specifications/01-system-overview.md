# Online Bookstore Backend - System Overview & Architecture Specification

**Version:** 1.0.0  
**Last Updated:** 2026-01-18  
**Status:** Foundation Specification  
**Audience:** Architects, Backend Engineers, Cursor IDE Development

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [System Goals & Scope](#system-goals--scope)
3. [Non-Functional Requirements](#non-functional-requirements)
4. [Polyglot Architecture Rationale](#polyglot-architecture-rationale)
5. [High-Level Component Breakdown](#high-level-component-breakdown)
6. [System Architecture Diagram](#system-architecture-diagram)
7. [Data Flow Patterns](#data-flow-patterns)
8. [Architectural Principles & Constraints](#architectural-principles--constraints)
9. [Explicit Assumptions](#explicit-assumptions)
10. [Technology Stack Summary](#technology-stack-summary)

---

## Executive Summary

**Online Bookstore Backend** is a **backend-only, production-grade microservices system** designed to showcase advanced technical architecture, scalability, and polyglot engineering practices. The system demonstrates:

- **Microservices decomposition** with clear service boundaries
- **Polyglot architecture** using Java, Python, and TypeScript for domain-specific optimization
- **REST-based integration** across heterogeneous services
- **Asynchronous event-driven patterns** for loose coupling
- **Enterprise-grade security** with centralized authentication
- **AWS-native deployment** with ECS Fargate orchestration
- **Observability & traceability** across all services

The system is **not a toy example**: it reflects real-world constraints, failure modes, distributed system challenges, and production operations considerations.

---

## System Goals & Scope

### Primary Goals

1. **Transactional Reliability**
   - Consistent order processing and payment handling
   - ACID compliance for financial transactions
   - Idempotent APIs for retry safety

2. **Scalability & Performance**
   - Handle 1000+ concurrent users
   - Sub-200ms API response times for read operations
   - Horizontal scaling of independent services
   - Support multiple geographic deployments

3. **Extensibility**
   - Clean service boundaries for new feature development
   - Event-driven hooks for third-party integrations
   - Pluggable payment processors and notification channels
   - ML-ready infrastructure for personalization

4. **Operational Excellence**
   - Complete observability (logs, metrics, traces)
   - Graceful degradation under partial failures
   - Automated deployments and rollbacks
   - Self-healing through health checks and auto-recovery

5. **Security & Compliance**
   - Role-based access control (RBAC)
   - Encrypted credentials and sensitive data
   - API rate limiting and DDoS protection
   - Audit trails for compliance (PCI-DSS ready)

### Scope Definition

#### In Scope
- ✅ REST API design and implementation
- ✅ Authentication and authorization (OAuth2/JWT via AWS Cognito)
- ✅ Core business domain (users, products, orders, payments)
- ✅ Search functionality (full-text, filters, aggregations)
- ✅ Recommendation engine (collaborative filtering)
- ✅ Asynchronous notifications (email, SMS, webhooks)
- ✅ AWS infrastructure automation and IaC
- ✅ Distributed tracing and observability
- ✅ CI/CD pipelines and deployment automation

#### Out of Scope
- ❌ Frontend or mobile applications
- ❌ Real-time communication (WebSockets) — async events only
- ❌ Content delivery network (CDN) optimization — assumes API Gateway handles routing
- ❌ Legacy system integration — greenfield microservices only
- ❌ Blockchain or distributed ledger technologies
- ❌ Machine learning model training — inference via SageMaker only

---

## Non-Functional Requirements

### Performance

| Metric | Target | Context |
|--------|--------|---------|
| API Response Time (P95) | < 200ms | Read-heavy operations (product search, catalog) |
| API Response Time (P95) | < 500ms | Write operations (order creation, payment) |
| Order Processing Latency (E2E) | < 2s | From API call to order persisted and event published |
| Database Query Response Time | < 50ms | 95th percentile for indexed queries |
| Search Query Response Time | < 300ms | Full-text search across 1M products |
| Message Processing Latency | < 5s | Event published to all consumers acknowledged |
| API Throughput | 1000 req/s | Minimum sustained throughput per service |

### Scalability

- **Horizontal scaling**: Each microservice independently scalable via ECS task replication
- **Data growth**: Support 10M products, 1M users, 100M orders without performance degradation
- **Concurrent connections**: 10,000+ concurrent API clients
- **Database connections**: Connection pooling (HikariCP for Java, asyncpg for Python)
- **Load shedding**: API Gateway rate limiting prevents cascading failures

### Availability

| Target | Definition |
|--------|-----------|
| Uptime SLA | 99.9% (4.38 hours downtime/month) |
| Recovery Time Objective (RTO) | ≤ 15 minutes for critical services |
| Recovery Point Objective (RPO) | ≤ 5 minutes (automated backups) |
| Multi-AZ deployment | All stateful services replicated across 3 AZs |
| Database failover | Automated RDS failover (2-3 minutes) |
| Service health check | Every 30 seconds with 3 consecutive failures → termination |

### Security

- **Authentication**: OAuth2/OpenID Connect via AWS Cognito + JWT tokens
- **Authorization**: RBAC with scopes (read:products, write:orders, admin:all)
- **Encryption in transit**: TLS 1.2+ for all communications
- **Encryption at rest**: AES-256 for sensitive data (passwords, payment info)
- **API key rotation**: Every 90 days (automated)
- **Credential storage**: AWS Secrets Manager (zero hardcoded secrets)
- **DDoS protection**: AWS Shield + WAF rules on API Gateway
- **Audit logging**: All authentication attempts, authorization failures, data access

### Reliability

- **Fault isolation**: Failure in one service doesn't cascade to others
- **Circuit breakers**: Prevent cascading failures on dependent services
- **Retry logic**: Exponential backoff with jitter for transient failures
- **Idempotency**: All unsafe operations (POST/PUT/PATCH/DELETE) include idempotency keys
- **Dead letter queues (DLQ)**: Failed async messages sent to DLQ for manual recovery
- **Graceful degradation**: Search service failure → fall back to basic catalog browsing

### Maintainability

- **Code consistency**: Unified logging, error handling, and configuration across all services
- **Documentation**: OpenAPI 3.1 specs, architecture decision records (ADRs), runbooks
- **Testing**: Minimum 80% code coverage, contract testing between services
- **Observability**: Centralized logging, distributed tracing (X-Ray), custom metrics
- **Deployment**: Blue-green deployments, canary releases, automated rollbacks

---

## Polyglot Architecture Rationale

### Why Polyglot?

**Polyglot microservices architecture** means using **different languages and frameworks for different services based on domain-specific requirements**, not for variety. Each technology choice is justified by problem characteristics.

### Technology Choices

#### 1. Java + Spring Boot (Core Transactional Services)

**Services**: User, Auth, Product Catalog, Order  
**Languages & Frameworks**: Java 21 + Spring Boot 3.2 + Spring Data JPA

**Why Java?**

| Requirement | Java Advantage |
|-------------|----------------|
| **ACID Transactions** | Strong ACID guarantees, JPA/Hibernate for complex ORM patterns, distributed transaction handling |
| **Type Safety** | Compile-time type checking prevents entire classes of runtime errors in financial logic |
| **Performance** | JIT compilation, mature GC tuning, predictable latency for real-time order processing |
| **Ecosystem** | Spring framework maturity, extensive libraries, proven patterns at scale |
| **Team Skills** | Java ubiquitous in enterprise e-commerce; hiring pool deep |
| **Operational Stability** | Long-term support, backward compatibility, proven in production for 20+ years |

**Specific Patterns**:
- Spring Data JPA for complex product catalog queries (N+1 prevention, custom repositories)
- Spring Security for authentication/authorization logic
- Spring Cloud Config for distributed configuration management
- Spring Boot Actuator for health checks and metrics

**Why NOT other languages?**
- ❌ Python: Too slow for high-throughput transaction processing; GIL limits concurrency
- ❌ Node.js: Immature async/await patterns for complex transactional workflows; harder to debug memory leaks in production

---

#### 2. Python (Search & Recommendation Service)

**Service**: Search & Recommendations  
**Language & Framework**: Python 3.11 + FastAPI + Elasticsearch + MLflow

**Why Python?**

| Requirement | Python Advantage |
|-------------|-----------------|
| **Search Integration** | Elasticsearch Python client (elasticsearch-py) is battle-tested, mature, full API coverage |
| **Machine Learning** | NumPy, Pandas, scikit-learn for collaborative filtering; TensorFlow for neural recommendations |
| **Data Processing** | Vectorization and batch processing native to ML frameworks; fast prototyping |
| **Analyst Accessibility** | Data scientists can read and modify recommendation logic without backend eng overhead |
| **Flexibility** | Dynamic typing allows rapid iteration on ML models; easy experimentation |

**Specific Patterns**:
- FastAPI for async I/O-heavy search operations (full-text queries across millions of products)
- Elasticsearch for distributed full-text search, faceting, and aggregations
- Async SQLAlchemy for database queries without blocking I/O
- MLflow for model versioning and A/B testing of recommendation algorithms
- Pandas for batch processing of user-item interaction matrices

**Why NOT other languages?**
- ❌ Java: Massive overhead for simple search + ML workflows; boilerplate dominates code
- ❌ Node.js: No native ML libraries; would require calling external services, adding latency

---

#### 3. TypeScript + Node.js (Notifications & External Integrations)

**Services**: Notifications, Payment Processor  
**Language & Framework**: TypeScript 5.3 + NestJS + Bull (job queue)

**Why TypeScript/Node.js?**

| Requirement | TypeScript/Node.js Advantage |
|-------------|------------------------------|
| **High I/O Throughput** | Event-driven, non-blocking I/O native; single-threaded event loop optimized for I/O-bound tasks |
| **Third-Party Integrations** | REST client libraries (axios, node-fetch) mature and well-maintained; async/await pattern natural |
| **Real-Time Processing** | Low-latency event handling, minimal GC pauses; suitable for webhook processing |
| **Distributed Task Queuing** | Bull queue library, Redis integration, built-in retry/backoff patterns |
| **Developer Experience** | Rapid API development, hot-reloading, minimal friction for integration code |
| **AWS SDK Integration** | AWS SDK v3 for Node.js is first-class; great SQS/SNS/SES support |

**Specific Patterns**:
- NestJS for structured service architecture (decorator-based routing, middleware, guards)
- Bull for reliable job processing (payments, notification retries, webhook deliveries)
- async/await for clean, readable async workflows
- Stripe SDK for payment processing with idempotency keys
- AWS SES for email, AWS SNS for SMS, custom webhook delivery

**Why NOT other languages?**
- ❌ Java: Unnecessary overhead for mostly I/O-bound notification service; overkill for payment webhooks
- ❌ Python: Inferior async/await ergonomics compared to Node.js; requires additional libraries (Celery) for job queuing

---

### Polyglot Integration Points

Services communicate **exclusively via REST APIs** (synchronous) and **AWS EventBridge** (asynchronous). There is **no shared state**, no cross-language function calls, and **language boundaries are absolute**.

```
User Service (Java)
    ↓ REST API
Product Catalog (Java) ← receives /catalog/products
    ↓ REST API
Order Service (Java) ← receives /products/{id}
    ↓ REST API (Payment Info)
Payment Processor (TypeScript) → processes payment
    ↓ EventBridge Event
    ├→ Notifications Service (TypeScript) → sends order confirmation email
    ├→ Search Service (Python) → updates user's purchase history for recommendations
    └→ Product Catalog (Java) → updates inventory

**All communication is asynchronous or request-response.**
**No shared databases, no direct library calls across services.**
```

---

## High-Level Component Breakdown

### System Layers

```
┌─────────────────────────────────────────────────────────────┐
│                       External Clients                       │
│              (Web, Mobile, Third-Party APIs)                │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTPS
┌──────────────────────▼──────────────────────────────────────┐
│                    API Gateway                              │
│  (Routing, Rate Limiting, Auth Token Validation, Logging)  │
└──────────────────────┬──────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┬──────────────┐
        │              │              │              │
┌───────▼────┐  ┌──────▼────┐  ┌─────▼──────┐  ┌──▼──────────┐
│   Java      │  │  Python   │  │ TypeScript │  │   Auth      │
│ Services    │  │ Service   │  │ Services   │  │  Service    │
│ (ECS)       │  │ (ECS)     │  │  (ECS)     │  │  (ECS)      │
└──────┬──────┘  └──────┬────┘  └─────┬──────┘  └──┬──────────┘
       │                │             │            │
  ┌────┴────┐     ┌─────▼─────┐  ┌──▼──────────┐
  │PostgreSQL│    │Elasticsearch   DynamoDB   │
  │  (RDS)   │    │ + OpenSearch  (for logs)  │
  └─────────┘     └──────────────────────────┘

       │                          │
       └──────────────┬───────────┘
                      │ Events
                ┌─────▼──────┐
                │EventBridge │ (Pub/Sub)
                └────────────┘
                      │
          ┌───────────┼───────────┐
          │           │           │
      ┌───▼──┐  ┌────▼───┐  ┌──┬─▼─┐
      │ SQS  │  │  SNS   │  │ ││
      │      │  │        │  │ ││
      └──────┘  └────────┘  └─────┘
   (Notification (Email/SMS) (Webhooks)
    Retries)
```

### Core Services (Java + Spring Boot)

#### User Service
- **Responsibility**: User profile management, account details, preferences
- **Endpoints**: `/api/v1/users/{id}`, `/api/v1/users/{id}/profile`
- **Data**: PostgreSQL (users table)
- **Key Workflows**: User registration, profile updates, preference management
- **Integration Points**: Called by Auth Service for user details

#### Auth Service
- **Responsibility**: Token generation, OAuth2 handshakes, session management
- **Endpoints**: `/api/v1/auth/login`, `/api/v1/auth/register`, `/api/v1/auth/refresh`, `/api/v1/auth/validate`
- **Data**: PostgreSQL (credentials, sessions)
- **Key Workflows**: Login, token issuance, token validation, logout
- **Integration Points**: Called by API Gateway for every request; calls User Service for account lookup

#### Product Catalog Service
- **Responsibility**: Product metadata, inventory, pricing, categories
- **Endpoints**: `/api/v1/products`, `/api/v1/products/{id}`, `/api/v1/categories`
- **Data**: PostgreSQL (products, categories, pricing)
- **Key Workflows**: Product retrieval, inventory management, price updates
- **Integration Points**: Called by Order Service (inventory checks); called by Search Service via REST

#### Order Service
- **Responsibility**: Order lifecycle (create, update, fulfill, cancel), shopping cart
- **Endpoints**: `/api/v1/orders`, `/api/v1/orders/{id}`, `/api/v1/orders/{id}/cancel`
- **Data**: PostgreSQL (orders, order_items, shipping, returns)
- **Key Workflows**: Order creation, payment orchestration, fulfillment tracking, cancellation
- **Integration Points**: 
  - Calls Product Catalog for inventory checks
  - Calls User Service for shipping address
  - Publishes `order.created`, `order.paid`, `order.shipped` events

---

### Specialized Services

#### Search & Recommendations Service (Python)
- **Responsibility**: Full-text search, personalized recommendations
- **Endpoints**: `/api/v1/search`, `/api/v1/recommendations`
- **Data**: Elasticsearch (product index), DynamoDB (user-item interactions)
- **Key Workflows**: 
  - Full-text search with facets (category, price range, author, rating)
  - Personalized book recommendations (collaborative filtering)
  - Search ranking (popularity, relevance, user history)
- **Integration Points**: 
  - Subscribes to `order.created` events to update interaction matrix
  - Called by clients for search and recommendations

#### Notifications Service (TypeScript)
- **Responsibility**: Async notifications (email, SMS, webhooks)
- **Endpoints**: `/api/v1/notifications` (for status checks only)
- **Data**: DynamoDB (notification audit log)
- **Key Workflows**: 
  - Listen to `order.created`, `order.shipped`, `payment.failed` events
  - Send emails via AWS SES
  - Send SMS via AWS SNS
  - Retry failed notifications (exponential backoff)
  - Store audit trail (who was notified, when, status)
- **Integration Points**: 
  - Subscribes to all Order Service, Payment Service events
  - No synchronous API dependencies

#### Payment Processor Service (TypeScript)
- **Responsibility**: Payment gateway integration (Stripe/PayPal)
- **Endpoints**: `/api/v1/payments`, `/api/v1/payments/{id}/status`
- **Data**: DynamoDB (payment records), Stripe/PayPal accounts (external)
- **Key Workflows**: 
  - Process card payments via Stripe API
  - Handle payment webhooks (success, failure, refund)
  - Implement PCI compliance (never store card data locally)
  - Manage refunds and chargebacks
- **Integration Points**: 
  - Called by Order Service (synchronous payment processing)
  - Publishes `payment.succeeded`, `payment.failed` events
  - Receives Stripe webhooks (async payment confirmations)

---

### Infrastructure & Cross-Cutting Concerns

#### API Gateway
- **Role**: Single entry point for all external API calls
- **Responsibilities**:
  - Request routing to appropriate microservice
  - Rate limiting (100 req/s per user, 1000 req/s globally)
  - Authentication token validation (JWT signature verification)
  - Request/response logging and correlation IDs
  - SSL/TLS termination
- **Technology**: AWS API Gateway + Lambda authorizer

#### Service Discovery
- **Role**: Dynamic service registry as services scale up/down
- **Implementation**: AWS CloudMap (DNS-based service discovery)
- **Why**: Eliminates need for manual load balancer configuration

#### Event Bus (Pub/Sub)
- **Role**: Decoupled asynchronous communication between services
- **Implementation**: AWS EventBridge
- **Patterns**:
  - Event producer publishes structured events (JSON)
  - Event consumers subscribe to event patterns
  - Built-in retry logic (exponential backoff)
  - Dead letter queue for unprocessable messages
- **Events**:
  - `order.created` → published by Order Service, consumed by Notifications, Search, Inventory
  - `payment.succeeded` → published by Payment Service, consumed by Order Service, Notifications
  - `payment.failed` → published by Payment Service, consumed by Notifications, Order Service

#### Observability Stack

**Centralized Logging**
- **Implementation**: CloudWatch Logs with unified JSON schema
- **Coverage**: All services log all events (requests, errors, business events)
- **Format**: Correlation ID, timestamp, service name, log level, message, structured metadata

**Distributed Tracing**
- **Implementation**: AWS X-Ray
- **Coverage**: Traces every API request from API Gateway through all services
- **Use Cases**: Identify performance bottlenecks, visualize service dependencies

**Metrics & Monitoring**
- **Implementation**: CloudWatch Metrics + custom application metrics
- **Key Metrics**:
  - Request count, response time (P50, P95, P99)
  - Error rates by service and error type
  - Database connection pool utilization
  - Queue depth for async jobs
  - Business metrics (orders/min, revenue/hour)
- **Alerting**: SNS notifications to ops team on threshold breach

#### Data Persistence Strategy

| Service | Technology | Rationale |
|---------|-----------|-----------|
| User Service | PostgreSQL (RDS) | Strong ACID guarantees, complex relational queries |
| Auth Service | PostgreSQL (RDS) | Session management requires strong consistency |
| Product Catalog | PostgreSQL (RDS) | Complex inventory and pricing queries; joins with categories |
| Order Service | PostgreSQL (RDS) | Critical transactional consistency for order processing |
| Search Service | Elasticsearch | Optimized for full-text search and faceted aggregations |
| Notifications | DynamoDB | High-throughput audit logging, no complex queries |
| Payment Processor | DynamoDB + Stripe | Minimal state; primary system of record is Stripe |

**Cross-Database Consistency**
- Each service has its own database schema; no shared tables
- Data consistency maintained through **eventual consistency** patterns
- Example: Order created in Order Service → event published → Search Service updates recommendation matrix asynchronously

---

## System Architecture Diagram

### Service Topology & Communication

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          EXTERNAL CLIENTS                                │
│              (Web Frontend, Mobile App, Third-Party APIs)                │
└────────────────────────────┬─────────────────────────────────────────────┘
                             │ HTTPS/REST
                             │
        ┌────────────────────▼─────────────────────────┐
        │         AWS API GATEWAY                      │
        │  ┌────────────────────────────────────────┐  │
        │  │ Rate Limiting (100 req/s per user)     │  │
        │  │ JWT Token Validation                   │  │
        │  │ Correlation ID Assignment              │  │
        │  │ Request Logging                        │  │
        │  └────────────────────────────────────────┘  │
        └────────────────────┬─────────────────────────┘
                             │
        ┌────────────────────┴─────────────────────┐
        │                                          │
        │  ┌──────────────────────────────────┐   │
        │  │                                  │   │
    ┌───▼──────────────────────────────────┐ │   │
    │  JAVA + SPRING BOOT MICROSERVICES    │ │   │
    │  (ECS Fargate, 2 AZs, autoscaling)   │ │   │
    │                                      │ │   │
    │  ┌─────────────────────────────────┐ │ │   │
    │  │  User Service                   │ │ │   │
    │  │  GET /users/{id}                │ │ │   │
    │  │  POST /users                    │ │ │   │
    │  │  PUT /users/{id}                │ │ │   │
    │  └─────────────────────────────────┘ │ │   │
    │                                      │ │   │
    │  ┌─────────────────────────────────┐ │ │   │
    │  │  Auth Service                   │ │ │   │
    │  │  POST /auth/login               │ │ │   │
    │  │  POST /auth/refresh             │ │ │   │
    │  │  POST /auth/validate            │ │ │   │
    │  └─────────────────────────────────┘ │ │   │
    │                                      │ │   │
    │  ┌─────────────────────────────────┐ │ │   │
    │  │  Product Catalog Service        │ │ │   │
    │  │  GET /products                  │ │ │   │
    │  │  GET /products/{id}             │ │ │   │
    │  │  PATCH /products/{id}/inventory │ │ │   │
    │  └─────────────────────────────────┘ │ │   │
    │                                      │ │   │
    │  ┌─────────────────────────────────┐ │ │   │
    │  │  Order Service                  │ │ │   │
    │  │  POST /orders                   │ │ │   │
    │  │  GET /orders/{id}               │ │ │   │
    │  │  PATCH /orders/{id}/status      │ │ │   │
    │  └─────────────────────────────────┘ │ │   │
    │                                      │ │   │
    │       ↓ All services connect to     │ │   │
    │                                      │ │   │
    │  ┌─────────────────────────────────┐ │ │   │
    │  │  PostgreSQL RDS (Multi-AZ)      │ │ │   │
    │  │  - Databases: users, auth,      │ │ │   │
    │  │    catalog, orders              │ │ │   │
    │  │  - Replication: synchronous     │ │ │   │
    │  │  - Backup: automated daily      │ │ │   │
    │  │  - Connection pooling: 20 conn/ │ │ │   │
    │  │    service (HikariCP)           │ │ │   │
    │  └─────────────────────────────────┘ │ │   │
    │                                      │ │   │
    └──────────────────────────────────────┘ │   │
                                             │   │
    ┌────────────────────────────────────────┼───┤
    │  PYTHON MICROSERVICES                  │   │
    │  (ECS Fargate, 2 AZs, autoscaling)     │   │
    │                                        │   │
    │  ┌──────────────────────────────────┐  │   │
    │  │  Search & Recommendations Service │  │   │
    │  │  GET /search                      │  │   │
    │  │  GET /recommendations             │  │   │
    │  │  POST /search/filters             │  │   │
    │  │                                   │  │   │
    │  │  ↓ Connects to:                   │  │   │
    │  │  - Elasticsearch (full-text)      │  │   │
    │  │  - DynamoDB (user interactions)   │  │   │
    │  └──────────────────────────────────┘  │   │
    │                                        │   │
    └────────────────────────────────────────┘   │
                                                 │
    ┌────────────────────────────────────────┐   │
    │  TYPESCRIPT/NODE.JS MICROSERVICES      │   │
    │  (ECS Fargate, 2 AZs, autoscaling)     │   │
    │                                        │   │
    │  ┌──────────────────────────────────┐  │   │
    │  │  Notifications Service (NestJS)  │  │   │
    │  │  GET /notifications/status       │  │   │
    │  │  [Async workers]                 │  │   │
    │  │  - Email via SES                 │  │   │
    │  │  - SMS via SNS                   │  │   │
    │  │  - Webhook delivery              │  │   │
    │  │                                  │  │   │
    │  │  ↓ Connects to:                  │  │   │
    │  │  - DynamoDB (audit logs)         │  │   │
    │  │  - Bull queue (Redis)            │  │   │
    │  └──────────────────────────────────┘  │   │
    │                                        │   │
    │  ┌──────────────────────────────────┐  │   │
    │  │  Payment Processor (NestJS)      │  │   │
    │  │  POST /payments                  │  │   │
    │  │  GET /payments/{id}              │  │   │
    │  │  POST /payments/{id}/refund      │  │   │
    │  │                                  │  │   │
    │  │  ↓ Connects to:                  │  │   │
    │  │  - DynamoDB (payment records)    │  │   │
    │  │  - Stripe API (external)         │  │   │
    │  │  - Bull queue (webhooks)         │  │   │
    │  └──────────────────────────────────┘  │   │
    │                                        │   │
    └────────────────────────────────────────┘   │
                                                 │
    ┌────────────────────────────────────────┐   │
    │  CROSS-CUTTING INFRASTRUCTURE           │   │
    │                                        │   │
    │  ┌──────────────────────────────────┐  │   │
    │  │  AWS EventBridge (Event Bus)     │  │   │
    │  │  [Decoupled Pub/Sub]             │  │   │
    │  │  Events: order.*, payment.*,     │  │   │
    │  │  user.*, product.*               │  │   │
    │  │  Retry: exponential backoff      │  │   │
    │  │  DLQ: failed messages -> SNS     │  │   │
    │  └──────────────────────────────────┘  │   │
    │                                        │   │
    │  ┌──────────────────────────────────┐  │   │
    │  │  AWS CloudMap (Service Registry) │  │   │
    │  │  DNS-based service discovery     │  │   │
    │  └──────────────────────────────────┘  │   │
    │                                        │   │
    │  ┌──────────────────────────────────┐  │   │
    │  │  CloudWatch Logs (Centralized)   │  │   │
    │  │  JSON structured logs            │  │   │
    │  │  Retention: 30 days              │  │   │
    │  └──────────────────────────────────┘  │   │
    │                                        │   │
    │  ┌──────────────────────────────────┐  │   │
    │  │  AWS X-Ray (Distributed Tracing) │  │   │
    │  │  Request flow visualization      │  │   │
    │  │  Performance analysis            │  │   │
    │  └──────────────────────────────────┘  │   │
    │                                        │   │
    │  ┌──────────────────────────────────┐  │   │
    │  │  CloudWatch Metrics & Alarms     │  │   │
    │  │  Custom app metrics               │  │   │
    │  │  SNS notifications on threshold   │  │   │
    │  └──────────────────────────────────┘  │   │
    │                                        │   │
    └────────────────────────────────────────┘   │
                                                 │
    ┌────────────────────────────────────────┐   │
    │  DATA STORES (Multi-AZ)                 │   │
    │                                        │   │
    │  PostgreSQL RDS (Primary)              │   │
    │  - Transaction consistency             │   │
    │  - 95th percentile query: < 50ms      │   │
    │                                        │   │
    │  Elasticsearch OpenSearch              │   │
    │  - Full-text search index              │   │
    │  - Faceted aggregations                │   │
    │                                        │   │
    │  DynamoDB (Multi-region replica)       │   │
    │  - Notifications audit log             │   │
    │  - User-item interactions              │   │
    │  - Payment records                     │   │
    │                                        │   │
    │  Redis (ElastiCache)                   │   │
    │  - Bull job queue                      │   │
    │  - Session cache (optional)            │   │
    │                                        │   │
    └────────────────────────────────────────┘   │
                                                 │
    ┌────────────────────────────────────────┐   │
    │  EXTERNAL INTEGRATIONS                  │   │
    │                                        │   │
    │  Stripe API                            │   │
    │  - Payment processing                  │   │
    │  - Webhook callbacks                   │   │
    │                                        │   │
    │  AWS SES (Simple Email Service)        │   │
    │  - Transactional emails                │   │
    │  - Retry with exponential backoff      │   │
    │                                        │   │
    │  AWS SNS (Simple Notification Service) │   │
    │  - SMS delivery                        │   │
    │  - Mobile push (future)                │   │
    │                                        │   │
    │  AWS Secrets Manager                   │   │
    │  - Stripe API keys                     │   │
    │  - Database credentials                │   │
    │  - Rotation: every 90 days             │   │
    │                                        │   │
    └────────────────────────────────────────┘   │
                                                 │
    ┌────────────────────────────────────────┐   │
    │  DEPLOYMENT & ORCHESTRATION             │   │
    │                                        │   │
    │  AWS ECS Fargate                       │   │
    │  - Serverless container orchestration │   │
    │  - Auto-scaling based on CPU/memory   │   │
    │  - Task placement across 2+ AZs       │   │
    │                                        │   │
    │  ECR (Elastic Container Registry)      │   │
    │  - Private Docker image repository     │   │
    │  - Image tagging: semver              │   │
    │                                        │   │
    │  CodePipeline & CodeBuild              │   │
    │  - Automated CI/CD                     │   │
    │  - Unit tests, integration tests       │   │
    │  - Automated deployments               │   │
    │                                        │   │
    │  CloudFormation / Terraform            │   │
    │  - Infrastructure as Code              │   │
    │  - Reproducible deployments            │   │
    │                                        │   │
    └────────────────────────────────────────┘   │
                                                 │
    ┌────────────────────────────────────────┐   │
    │  SECURITY & COMPLIANCE                  │   │
    │                                        │   │
    │  AWS Cognito                           │   │
    │  - User pools (OAuth2/OIDC)           │   │
    │  - MFA support                         │   │
    │  - Social identity (optional)          │   │
    │                                        │   │
    │  AWS WAF + Shield                      │   │
    │  - DDoS protection                     │   │
    │  - SQL injection prevention            │   │
    │                                        │   │
    │  AWS Secrets Manager                   │   │
    │  - Zero hardcoded credentials          │   │
    │  - Automatic rotation                  │   │
    │                                        │   │
    │  VPC + Security Groups                 │   │
    │  - Private subnets for databases       │   │
    │  - Minimal port exposure               │   │
    │                                        │   │
    │  TLS 1.2+ Encryption                   │   │
    │  - All in-transit communication        │   │
    │  - AES-256 encryption at rest          │   │
    │                                        │   │
    └────────────────────────────────────────┘   │
                                                 │
└─────────────────────────────────────────────────┘
```

---

## Data Flow Patterns

### Pattern 1: Synchronous Request-Response (REST)

**Scenario**: User creates an order

```
1. Client → API Gateway (POST /orders)
   Request: { userId, cartItems, shippingAddress }
   Headers: Authorization: Bearer <JWT>

2. API Gateway → Order Service
   Validates token, extracts userId, routes to /orders endpoint

3. Order Service → Auth Service
   Verifies token scope: "write:orders"

4. Order Service → Product Catalog Service
   Checks inventory for each product in cart
   Request: GET /products/{productId}/inventory
   Response: { available: true, quantity: 100 }

5. Order Service → User Service
   Fetches user's default shipping address
   Request: GET /users/{userId}/addresses
   Response: { address }

6. Order Service → Payment Processor Service
   Processes payment synchronously
   Request: POST /payments
   Response: { paymentId: "pay_123", status: "succeeded" }

7. Order Service → Database (PostgreSQL)
   Persists order record:
   INSERT INTO orders (order_id, user_id, status, created_at)
   VALUES (uuid(), userId, "PENDING", now())

8. Order Service → EventBridge
   Publishes: order.created event
   Payload: { orderId, userId, totalAmount, items }

9. Order Service → API Gateway → Client
   Response: { orderId, status: "PENDING", estimatedDelivery: "2026-01-25" }
   Status: 201 Created
```

**Key Characteristics**:
- Request → Response within 500ms (target)
- Synchronous payment processing (blocking)
- Inventory checks prevent overselling
- Complete transaction before returning to client

---

### Pattern 2: Asynchronous Event-Driven (EventBridge)

**Scenario**: After order created, notify user and update recommendations

```
Timeline:

T+0s:  Order Service publishes event:
       {
         "source": "bookstore.order",
         "detail-type": "OrderCreated",
         "detail": {
           "orderId": "ord_abc123",
           "userId": "user_xyz789",
           "items": [
             { "productId": "book_001", "quantity": 2 }
           ],
           "timestamp": "2026-01-18T12:22:00Z"
         }
       }

T+0.5s: EventBridge routes event to subscribers:
        - Notifications Service (SQS queue)
        - Search Service (SQS queue)
        - Product Catalog Service (SQS queue)

T+1s:   Notifications Service consumes message:
        - Reads order details from SQS
        - Fetches user email from User Service
        - Creates email job in Bull queue

T+2s:   Notifications Worker picks up job:
        - Renders email template
        - Calls AWS SES to send email
        - On success: delete from queue
        - On failure: add to DLQ with retry metadata

T+5s:   Email delivered to user

Meanwhile (in parallel):

T+1s:   Search Service consumes event:
        - Parses order items
        - Updates DynamoDB interaction matrix:
          user_xyz789 + book_001 interaction
        - Updates ML model training queue

T+3s:   Search Service ML job processes interaction:
        - Recalculates recommendation scores
        - Updates in-memory recommendation cache

T+10s:  Next time user calls GET /recommendations:
        New recommendations include books similar to their purchase
```

**Key Characteristics**:
- Order returns to user immediately (T+0.5s)
- Downstream processing happens asynchronously
- Failed messages go to DLQ, not lost
- Services don't wait for each other
- Scalable to millions of events/day

---

### Pattern 3: Service-to-Service Synchronous Call

**Scenario**: Order Service needs product info

```
Order Service
  ├─ Create ORDER in PostgreSQL
  ├─ Call: GET /api/v1/products/book_001 (Product Catalog)
  │         ↓
  │    Product Catalog Service
  │    ├─ Receives request with correlation ID
  │    ├─ Query PostgreSQL:
  │    │  SELECT * FROM products WHERE product_id = 'book_001'
  │    ├─ Response: { title, price, inventory }
  │    └─ Return 200 OK
  │         ↓
  ├─ Receive response, validate stock
  ├─ Call: POST /api/v1/payments (Payment Processor)
  │         ↓
  │    Payment Processor Service
  │    ├─ Call Stripe API (external)
  │    ├─ Wait for response (2-3 seconds typical)
  │    └─ Return payment status
  │         ↓
  ├─ Receive payment status
  ├─ Update ORDER status to "PAID"
  ├─ Return 201 Created to client
  └─ Publish order.created event to EventBridge
```

**Key Characteristics**:
- Blocking call: Order Service waits for responses
- Timeout handling: 5s timeout per external call
- Circuit breaker: If Product Catalog fails 5x in a row, return cached response
- Retry logic: 3 attempts with exponential backoff for transient errors

---

## Architectural Principles & Constraints

### Principle 1: Microservices Autonomy

**Definition**: Each service is independently deployable, scalable, and maintains its own data.

**Implementation**:
- ✅ No shared databases across services
- ✅ No synchronous calls during critical operations
- ✅ Event-driven integration for cross-service workflows
- ✅ Service independently scales based on its load

**Violation Examples**:
- ❌ Shared PostgreSQL schema (forces coordinated deployments)
- ❌ Synchronous cascading calls (Service A → B → C → D, one failure = all fail)
- ❌ Shared data caches (tight coupling)

---

### Principle 2: Failure Isolation

**Definition**: Failure in one service doesn't cascade to others.

**Implementation**:
- ✅ Timeouts on all external calls (5s default)
- ✅ Circuit breakers on dependency failures
- ✅ Graceful degradation (Search unavailable → show basic catalog)
- ✅ Bulkheads: Separate thread pools per dependency

**Violation Examples**:
- ❌ Synchronous required calls without fallback
- ❌ Retry storms (hammering a failed service)
- ❌ No timeouts (hanging forever waiting for response)

---

### Principle 3: Asynchrony Where Possible

**Definition**: Use async messaging for non-critical operations; save synchronous calls for must-have data.

**Implementation**:
- ✅ Order creation (critical) = synchronous
- ✅ Notifications (nice-to-have) = asynchronous events
- ✅ Recommendations (background) = asynchronous ML jobs
- ✅ Audit logging (compliance) = asynchronous to secondary store

**Decision Tree**:
```
Does the operation block user action?
├─ YES → Synchronous (must complete before returning)
└─ NO → Asynchronous (publish event, don't wait)

Examples:
├─ "Process payment for order" → Sync (blocks order)
├─ "Send order confirmation email" → Async (doesn't block)
├─ "Check product inventory" → Sync (needed for validation)
└─ "Update recommendation scores" → Async (background)
```

---

### Principle 4: Observability First

**Definition**: Every system action is observable. Problems are identified before customers report them.

**Implementation**:
- ✅ All requests logged with correlation IDs
- ✅ Distributed tracing end-to-end
- ✅ Custom metrics for business events
- ✅ Alerts on anomalies (error rate, latency, throughput)

**Observability Checklist**:
- [ ] Can trace single request through all services? (X-Ray)
- [ ] Can identify which service is slow? (CloudWatch)
- [ ] Can see error rates by service? (Metrics)
- [ ] Can replay what happened in an incident? (Logs + traces)

---

### Principle 5: Data Consistency Model

**Definition**: Determine what "consistent" means for this system.

**Implementation**:
- **Strong consistency** (ACID): Within single service database (orders table)
- **Eventual consistency**: Across services (Order Service → Search Service recommendations)
  - Time to consistency: 10-30 seconds typical
  - Resolution: Event replay, manual reconciliation on conflict
- **Read-after-write consistency**: API always returns latest written data

**Practical Example**:
```
T+0: User purchases book_001
T+0.1s: Order Service persists order (strongly consistent)
T+0.2s: Order Service publishes event
T+1s: Search Service receives event, updates DynamoDB
T+1.5s: User calls GET /recommendations
       Results reflect new purchase? Not guaranteed
       Recommendation updated? Happens in next batch job

→ Acceptable: User doesn't expect instantaneous ML

vs.

T+0: User checks order status
T+0.1s: Order Service queries database, returns "PAID"
T+1s: User calls again
      Must get same result (or newer)
      Not older (never going backward)

→ Required: Users expect monotonic consistency
```

---

### Principle 6: Security by Default

**Definition**: Security isn't an afterthought; it's baked into architecture.

**Implementation**:
- ✅ Zero trust: Every request authenticated and authorized
- ✅ Secrets management: Never hardcode credentials
- ✅ Encryption: In transit (TLS) and at rest (AES-256)
- ✅ Audit trails: All sensitive operations logged
- ✅ Rate limiting: Prevent abuse and DoS

**Security Checkpoints**:
```
Client Request
  ↓
API Gateway [1. TLS verification]
  ↓
JWT Token Validation [2. Signature check]
  ↓
Cognito Lookup [3. User pool status]
  ↓
Authorization Check [4. Scopes/roles]
  ↓
Service Endpoint [5. Business logic access control]
  ↓
Database [6. Encrypted at rest]
  ↓
Response [7. Sanitize sensitive data]
  ↓
Logging [8. Audit trail recorded]
```

---

### Constraint 1: No Distributed Transactions

**Statement**: We do NOT use distributed transactions (2-phase commit, Saga pattern, etc.) for orchestration.

**Rationale**:
- Distributed transactions are fragile, slow, and complex
- Services are independent; cross-service coordination is eventual

**Alternative**: Event-driven workflows + compensating transactions

**Example**:
```
NOT this (❌ distributed transaction):
  BEGIN TRANSACTION
    UPDATE orders SET status = 'PAID'
    UPDATE inventory SET quantity = quantity - 1
    INSERT INTO payment_records ...
  COMMIT

This (✅ event-driven):
  1. Order Service: Create order, mark as PENDING, commit locally
  2. Order Service: Publish order.created event
  3. Product Catalog: Receives event, decrements inventory
  4. Payment Processor: Receives event, processes payment
  5. If payment fails: Publish payment.failed event
  6. Order Service: Receives payment.failed, publishes order.cancelled
  7. Product Catalog: Receives order.cancelled, increments inventory back
```

---

### Constraint 2: All Communication is REST or Events

**Statement**: No direct gRPC, GraphQL, or message queues for synchronous calls.

**Rationale**:
- REST is language-agnostic and firewall-friendly
- Easy to test, monitor, and debug
- Clear separation between sync (REST) and async (events)

**Violation Examples**:
- ❌ gRPC between services (adds protocol complexity)
- ❌ Direct Kafka consumer/producer (we use EventBridge)
- ❌ GraphQL (frontend choice, not backend)

---

### Constraint 3: No Shared State, No Global Caching

**Statement**: Services do NOT share in-memory caches, sessions, or state machines.

**Rationale**:
- Distributed state is hard to keep consistent
- Service scaling would require cache invalidation across instances
- Debugging becomes impossible

**Allowed**:
- ✅ Local caching within a service instance (expires after 5 minutes)
- ✅ External caching for reference data (Redis, kept in sync via events)
- ✅ Database as source of truth

**Disallowed**:
- ❌ Shared HashMap between services
- ❌ Session stickiness (server must work independently)
- ❌ In-memory recommendation cache shared across instances

---

### Constraint 4: No Cross-Service Database Joins

**Statement**: You cannot query User Service database from Order Service code.

**Rationale**:
- Violates service autonomy
- Couples deployment cycles
- Schema changes in one service break others

**Alternative**: Service-to-service REST call

```
NOT this (❌):
  SELECT o.*, u.* FROM orders o
  JOIN users_db.users u ON o.user_id = u.id
  [Can't; different databases]

This (✅):
  1. Order Service: SELECT * FROM orders WHERE user_id = ?
  2. For each order, call GET /api/v1/users/{userId} on User Service
  3. Merge results in application code
```

---

## Explicit Assumptions

### Assumption 1: AWS as the Cloud Provider

**Statement**: All infrastructure is deployed on AWS.

**Rationale**:
- Mature, production-grade services (RDS, ECS, EventBridge)
- Cost-effective for startup/scale-up phase
- Most hiring pool has AWS experience

**Implications**:
- Cannot use GCP Cloud Functions or Azure App Service without redesign
- Portability is NOT a goal
- We leverage AWS-specific features (EventBridge is better than Kafka for this workload)

---

### Assumption 2: Synchronous Payment Processing

**Statement**: Payment authorization happens in the order creation request (synchronous).

**Rationale**:
- Users expect immediate payment confirmation
- Reduces fraud (payment decision made before order ships)
- Payment processor (Stripe) responds within 2-3 seconds

**Implications**:
- If Stripe is slow, order creation is slow
- Circuit breaker: If Stripe unavailable, reject order (don't retry indefinitely)
- Payment completion confirmation is asynchronous (webhook from Stripe)

---

### Assumption 3: Strong Authentication; Weak Authorization Initially

**Statement**: Every request must be authenticated (who are you?). Authorization (what can you do?) starts simple.

**Rationale**:
- Authentication is non-negotiable for security
- Authorization starts with role-based (ADMIN, CUSTOMER)
- Can evolve to fine-grained scopes later

**Implications**:
- All APIs protected by Bearer JWT token
- Roles: ADMIN (full access), CUSTOMER (read own data)
- Attribute-based access control (ABAC) is a future enhancement

---

### Assumption 4: PostgreSQL Multi-AZ is Sufficient for Consistency

**Statement**: PostgreSQL with synchronous replication across 2+ AZs provides our consistency model.

**Rationale**:
- RDS Multi-AZ automatic failover (2-3 minutes)
- Synchronous replication = data never lost on primary failure
- Good balance of consistency vs. availability

**Implications**:
- If primary goes down, orders are queued (API returns 503) until failover
- Failover is automatic; no manual intervention
- We accept 503 errors during failover (rare, ~minutes/year)

---

### Assumption 5: Elasticsearch for Search Only, Not as Source of Truth

**Statement**: Elasticsearch is a read-optimized index. Product Catalog (PostgreSQL) is source of truth.

**Rationale**:
- Elasticsearch can lose data on failure (it's eventually consistent)
- But Search Service can rebuild index from PostgreSQL
- PostgreSQL guarantees no lost orders, no overselling

**Implications**:
- Search index becomes stale during outages (OK, not critical path)
- To update a product, write to PostgreSQL first
- Then publish event to re-index in Elasticsearch
- If Elasticsearch offline: Fall back to basic PostgreSQL queries

---

### Assumption 6: DynamoDB for Audit-Only Data

**Statement**: DynamoDB stores audit logs and non-critical state (notifications, payment records for reference).

**Rationale**:
- High write throughput
- Cheap for append-only audit logs
- Global tables for cross-region disaster recovery (future)

**Implications**:
- Never query DynamoDB for critical business logic
- If DynamoDB fails, Notifications still queue locally (Bull)
- Notification audit trail delayed but not lost

---

### Assumption 7: EventBridge as Primary Event Bus

**Statement**: AWS EventBridge is the canonical event broker. No Kafka, RabbitMQ, or SNS fan-out.

**Rationale**:
- Managed, serverless (no ops burden)
- Built-in retry logic (exponential backoff)
- DLQ support for poison messages
- EventBridge costs scale with event count (not infrastructure)

**Implications**:
- All inter-service async communication goes through EventBridge
- Services register event subscriptions on deployment
- If EventBridge fails: Events are retried for 24 hours
- No need for separate message broker infrastructure

---

### Assumption 8: JWT Tokens, Not Session-Based Auth

**Statement**: Authentication uses JWT tokens (stateless), not server-side sessions.

**Rationale**:
- Stateless: Any API Gateway instance can validate token
- No session affinity required (easier load balancing)
- Supports distributed deployments
- AWS Cognito issues JWTs natively

**Implications**:
- Tokens expire (1 hour), require refresh
- Token revocation requires token blacklist (future enhancement)
- Logout = client deletes token (no server state needed)

---

### Assumption 9: ECS Fargate, Not Kubernetes

**Statement**: We use AWS ECS Fargate for container orchestration, not Kubernetes.

**Rationale**:
- Managed service (AWS handles upgrades, patching)
- Simpler operational burden for small-to-medium team
- AWS-native integrations (IAM, CloudWatch, Secrets Manager)
- Cost-effective for this workload

**Implications**:
- Cannot use Helm charts or Kubernetes operators
- Auto-scaling via CloudWatch metrics
- Blue-green deployments via CloudFormation stack updates
- Portability to other cloud providers is not a feature

---

### Assumption 10: Polyglot Services Communicate ONLY via REST and Events

**Statement**: Java, Python, and TypeScript services never directly call each other's libraries or share code.

**Rationale**:
- Language boundaries are absolute
- Forces explicit contracts (REST, events)
- Allows independent version management
- Clear ownership and deployment cycles

**Implications**:
- If Java service needs Python functionality, call Python service via REST
- Shared utilities (logging, error handling) implemented per-language
- No monorepo (separate repos per service)
- Testing is contract-based, not shared

---

### Assumption 11: Idempotency Keys Prevent Duplicate Payments

**Statement**: All create operations include idempotency key; Payment Processor deduplicates.

**Rationale**:
- Network failures can cause client to retry (user clicks "Pay" twice)
- Idempotency key: UUID generated by client, sent with request
- Server checks: if key exists, return previous response

**Implications**:
- Payment Processor stores idempotency key → payment ID mapping
- Retry with same key returns same result (no double charge)
- Idempotency key expires after 24 hours

---

### Assumption 12: Pagination Required for Large Result Sets

**Statement**: API endpoints returning lists use cursor-based pagination.

**Rationale**:
- Offset-based pagination fails with deletions (gaps in results)
- Cursor-based: stable, efficient, scales to billions of rows

**Implications**:
- GET /products returns: { data: [...], cursor: "next_token", total_count: 1000000 }
- Client passes cursor in next request
- No random access ("give me page 1000") allowed

---

### Assumption 13: Correlation IDs Enable Request Tracing

**Statement**: Every request gets a correlation ID (generated by API Gateway or client).

**Rationale**:
- Single request spawns multiple service calls
- Correlation ID links all logs/traces
- Operators can see full request journey

**Implications**:
- Every log entry includes correlation_id
- Distributed tracing (X-Ray) uses correlation ID
- On error: Operator can query logs by correlation_id to see what happened

---

## Technology Stack Summary

| Category | Technology | Version | Purpose |
|----------|-----------|---------|---------|
| **Language: Java** | Java | 21 LTS | Type-safe, high performance |
| **Framework: Java** | Spring Boot | 3.2 | REST endpoints, dependency injection, data access |
| **ORM: Java** | Hibernate + JPA | 6.x | Object-relational mapping |
| **Language: Python** | Python | 3.11 | Fast prototyping, ML-ready |
| **Framework: Python** | FastAPI | 0.104+ | Async REST framework |
| **ORM: Python** | SQLAlchemy | 2.x | Async database access |
| **Language: TypeScript** | TypeScript | 5.3 | Type-safe Node.js |
| **Framework: TypeScript** | NestJS | 10.x | Structured async services |
| **Job Queue** | Bull | 4.x | Redis-backed job queue (Node.js) |
| **Database: OLTP** | PostgreSQL | 15+ | Transactional data (users, orders) |
| **Database: Search** | Elasticsearch/OpenSearch | 8.x | Full-text search and aggregations |
| **Database: KV Store** | DynamoDB | — | Audit logs, notifications |
| **Cache: Memory** | Redis (ElastiCache) | 7.x | Session cache, job queue |
| **Message Bus** | AWS EventBridge | — | Event pub/sub |
| **Orchestration** | ECS Fargate | — | Container orchestration |
| **Container Registry** | AWS ECR | — | Private Docker images |
| **Load Balancing** | AWS API Gateway | — | API routing, rate limiting |
| **Authentication** | AWS Cognito + JWT | — | User authentication |
| **Secrets Management** | AWS Secrets Manager | — | Credential storage |
| **Observability: Logs** | CloudWatch Logs | — | Centralized logging |
| **Observability: Traces** | AWS X-Ray | — | Distributed tracing |
| **Observability: Metrics** | CloudWatch Metrics | — | Custom metrics and alarms |
| **Payment Gateway** | Stripe API | — | Payment processing |
| **Email Service** | AWS SES | — | Transactional email |
| **SMS Service** | AWS SNS | — | SMS delivery |
| **Infrastructure as Code** | Terraform / CloudFormation | — | Reproducible deployments |
| **CI/CD** | AWS CodePipeline + CodeBuild | — | Automated deployment |
| **Testing Framework: Java** | JUnit 5 + Mockito | — | Unit and integration tests |
| **Testing Framework: Python** | pytest | 7.x | Unit and integration tests |
| **Testing Framework: TypeScript** | Jest | 29.x | Unit and integration tests |
| **API Documentation** | OpenAPI 3.1 + Swagger | — | REST API specification |
| **Container Runtime** | Docker | 24.x | Container images |
| **Version Control** | Git + GitHub | — | Source code management |
| **Monitoring** | CloudWatch Dashboards | — | Visual monitoring |

---

## Next Steps

This **System Overview & Architecture Specification** is the foundation. All future service specifications will:

1. **Reference this document** for consistency
2. **Follow the naming conventions** and design patterns established here
3. **Implement the architectural principles** (autonomy, failure isolation, etc.)
4. **Use the technology stack** as specified
5. **Build observability** using CloudWatch, X-Ray, and structured logging

**Ready for Iteration 2**: Specify the first service (recommended: **Auth Service**)

When you're ready, provide:
```
Auth Service Specification
Requirements:
- [Your specific requirements]
```

And I'll generate:
- OpenAPI 3.1 specification
- Spring Boot controller skeleton
- Database schema (PostgreSQL)
- Security considerations
- Deployment configuration
- Test templates
- Cursor IDE-ready code snippets

---

**Document Version**: 1.0.0  
**Status**: Foundation — Ready for service specs  
**Next Review**: After first 3 services implemented
