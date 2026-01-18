# Online Bookstore Backend - Domain Model & Data Ownership Specification

**Version:** 1.0.0  
**Last Updated:** 2026-01-18  
**Status:** Iteration 2 — Domain Model & Data Ownership  
**Audience:** Architects, Backend Engineers, Cursor IDE Development  
**Prerequisite**: System Overview & Architecture Specification v1.0.0

---

## Table of Contents

1. [Overview](#overview)
2. [Bounded Contexts & Domain Mapping](#bounded-contexts--domain-mapping)
3. [Core Domains](#core-domains)
4. [Data Ownership Rules](#data-ownership-rules)
5. [Entity Definitions](#entity-definitions)
6. [Database Schema Decisions](#database-schema-decisions)
7. [Domain Diagrams](#domain-diagrams)
8. [Entity-Relationship Diagrams](#entity-relationship-diagrams)
9. [Cross-Domain Data Flows](#cross-domain-data-flows)

---

## Overview

**Domain-Driven Design (DDD)** organizes the bookstore backend into independent **bounded contexts**, each representing a distinct area of business logic with clear data ownership.

**Key Principles**:
- ✅ Each service owns its data (no cross-service database access)
- ✅ Services communicate via REST APIs or events, not shared tables
- ✅ Entity definitions are language-agnostic but database schemas are service-specific
- ✅ Clear boundaries prevent circular dependencies and enable independent scaling

**Organizational Model**:
```
Bookstore Backend
├─ User Domain (User Service)
│  └─ Manages: User accounts, profiles, preferences
├─ Identity Domain (Auth Service)
│  └─ Manages: Credentials, JWT tokens, sessions
├─ Catalog Domain (Product Catalog Service)
│  └─ Manages: Products, inventory, pricing, categories
├─ Order Domain (Order Service)
│  └─ Manages: Orders, shopping carts, order items, fulfillment
├─ Payment Domain (Payment Processor Service)
│  └─ Manages: Payments, transactions, refunds (external Stripe as source of truth)
├─ Search Domain (Search & Recommendations Service)
│  └─ Manages: Product search index, recommendations, user interactions
└─ Notification Domain (Notifications Service)
   └─ Manages: Notification delivery, audit logs, retry tracking
```

---

## Bounded Contexts & Domain Mapping

### 1. User Domain

| Aspect | Details |
|--------|---------|
| **Service** | User Service |
| **Language** | Java 21 + Spring Boot 3.2 |
| **Database** | PostgreSQL (RDS) |
| **Primary Responsibility** | User profile management, account details, preferences |
| **Scope** | User registration, profile updates, address management, preference storage |
| **Key Entities** | User, UserProfile, Address, Preference |
| **Bounded Context** | Complete owner of user identity and profile data |

**Data Ownership**:
- ✅ Owns: `users`, `user_profiles`, `addresses`, `preferences` tables
- ❌ Does NOT own: Credentials (owned by Identity Domain), Order data, Payment info
- 🔗 Accessed by: Auth Service (via REST), Order Service (via REST)

**API Contracts**:
- `GET /api/v1/users/{userId}` → Returns User + UserProfile
- `PUT /api/v1/users/{userId}` → Updates User profile
- `GET /api/v1/users/{userId}/addresses` → Returns list of addresses
- `POST /api/v1/users/{userId}/addresses` → Creates new address

---

### 2. Identity Domain

| Aspect | Details |
|--------|---------|
| **Service** | Auth Service |
| **Language** | Java 21 + Spring Boot 3.2 |
| **Database** | PostgreSQL (RDS) |
| **Primary Responsibility** | Authentication, authorization, token management |
| **Scope** | User login, logout, token generation/refresh, session management |
| **Key Entities** | Credential, Session, TokenBlacklist, AuthAudit |
| **Bounded Context** | Exclusive owner of credentials and security tokens |

**Data Ownership**:
- ✅ Owns: `credentials`, `sessions`, `token_blacklist`, `auth_audit_log` tables
- ❌ Does NOT own: User profile (owned by User Domain)
- 🔗 Calls: User Service (fetch user details), AWS Cognito (token validation)

**API Contracts**:
- `POST /api/v1/auth/login` → Returns JWT token
- `POST /api/v1/auth/register` → Creates user + credential
- `POST /api/v1/auth/refresh` → Issues new token from refresh token
- `POST /api/v1/auth/validate` → Validates token (called by API Gateway)
- `POST /api/v1/auth/logout` → Blacklists token

**Design Notes**:
- Credentials stored hashed (bcrypt, not plaintext)
- JWT tokens include user ID and scopes
- Token expiry: 1 hour; refresh token expiry: 30 days
- Session stored server-side for revocation
- AWS Cognito integration handles OAuth2/OIDC flows

---

### 3. Catalog Domain

| Aspect | Details |
|--------|---------|
| **Service** | Product Catalog Service |
| **Language** | Java 21 + Spring Boot 3.2 |
| **Database** | PostgreSQL (RDS) |
| **Primary Responsibility** | Product metadata, inventory, pricing, categories |
| **Scope** | Product CRUD, inventory tracking, category management, price updates |
| **Key Entities** | Product, Category, Inventory, Price, Author, Publisher |
| **Bounded Context** | Authoritative source for product information and stock levels |

**Data Ownership**:
- ✅ Owns: `products`, `categories`, `inventory`, `prices`, `authors`, `publishers` tables
- ❌ Does NOT own: Order data, Payment data
- 🔗 Accessed by: Order Service (inventory checks), Search Service (product index)

**API Contracts**:
- `GET /api/v1/products` → List products with pagination
- `GET /api/v1/products/{productId}` → Fetch single product
- `GET /api/v1/products/{productId}/inventory` → Check stock level
- `PATCH /api/v1/products/{productId}/inventory` → Decrease inventory (called by Order Service)
- `POST /api/v1/products` → Create product (admin only)
- `PUT /api/v1/products/{productId}` → Update product (admin only)
- `DELETE /api/v1/products/{productId}` → Soft delete (admin only)

**Design Notes**:
- Inventory is pessimistic (decrement immediately upon order)
- Products include: ISBN, title, author, publisher, description, price, cover URL
- Categories support hierarchical structure (parent-child)
- Soft deletes for audit trail (products never hard deleted)
- Search index in Elasticsearch is derived (not source of truth)

---

### 4. Order Domain

| Aspect | Details |
|--------|---------|
| **Service** | Order Service |
| **Language** | Java 21 + Spring Boot 3.2 |
| **Database** | PostgreSQL (RDS) |
| **Primary Responsibility** | Order lifecycle, shopping carts, order fulfillment |
| **Scope** | Order creation, payment coordination, fulfillment tracking, cancellation |
| **Key Entities** | Order, OrderItem, ShoppingCart, CartItem, Fulfillment, Return |
| **Bounded Context** | Orchestrator of order workflows; calls multiple services |

**Data Ownership**:
- ✅ Owns: `orders`, `order_items`, `shopping_carts`, `cart_items`, `fulfillments`, `returns` tables
- ❌ Does NOT own: Product details (calls Catalog), User addresses (calls User Service), Payment details (calls Payment Service)
- 🔗 Calls: Product Catalog (inventory check), User Service (address), Payment Processor (charge card)
- 🔗 Publishes: `order.created`, `order.paid`, `order.shipped`, `order.cancelled` events

**API Contracts**:
- `POST /api/v1/orders` → Create order (from cart), process payment
- `GET /api/v1/orders/{orderId}` → Fetch order details
- `GET /api/v1/orders?userId={userId}` → List user's orders
- `PATCH /api/v1/orders/{orderId}/status` → Update status (fulfillment)
- `POST /api/v1/orders/{orderId}/cancel` → Cancel order, process refund
- `POST /api/v1/carts` → Create shopping cart
- `POST /api/v1/carts/{cartId}/items` → Add item to cart
- `DELETE /api/v1/carts/{cartId}/items/{itemId}` → Remove item from cart
- `GET /api/v1/carts/{cartId}` → Fetch cart contents

**Design Notes**:
- Order status: PENDING → PAID → PROCESSING → SHIPPED → DELIVERED
- Order contains multiple OrderItems (each with productId, quantity, price at time of purchase)
- Price snapshot: store price at purchase time (don't reference current catalog price)
- Inventory decremented immediately on order creation (optimistic locking)
- Cart is ephemeral (expires after 30 days of inactivity)

---

### 5. Payment Domain

| Aspect | Details |
|--------|---------|
| **Service** | Payment Processor Service |
| **Language** | TypeScript 5.3 + NestJS 10 |
| **Database** | DynamoDB (audit log) + Stripe (external source of truth) |
| **Primary Responsibility** | Payment processing, transaction management, refunds |
| **Scope** | Charge cards, handle payment webhooks, process refunds, manage PCI compliance |
| **Key Entities** | Payment, Transaction, Refund, PaymentAudit, IdempotencyKey |
| **Bounded Context** | Integration layer with Stripe; DynamoDB stores local audit trail only |

**Data Ownership**:
- ✅ Owns (local DynamoDB): `payments`, `transactions`, `refunds`, `payment_audit_log`, `idempotency_keys` (for deduplication)
- ❌ Does NOT own: Order data, User data
- 🔗 Calls: Stripe API (external; source of truth for payment data)
- 🔗 Publishes: `payment.succeeded`, `payment.failed`, `payment.refunded` events

**API Contracts**:
- `POST /api/v1/payments` → Charge card (called synchronously by Order Service)
- `GET /api/v1/payments/{paymentId}` → Fetch payment status
- `POST /api/v1/payments/{paymentId}/refund` → Issue refund
- `POST /api/v1/payments/webhook/stripe` → Receive Stripe webhooks (async confirmations)

**Design Notes**:
- PCI Compliance: Never store full card numbers (use Stripe tokens only)
- Idempotency: All payment requests include idempotency_key (client-generated UUID)
- If request retried with same idempotency_key, return previous response (no double charge)
- Stripe is source of truth; local DynamoDB is audit trail
- Payment status in Stripe: pending → succeeded/failed → refunded
- Webhook from Stripe confirms payment (asynchronous confirmation)

---

### 6. Search & Recommendations Domain

| Aspect | Details |
|--------|---------|
| **Service** | Search & Recommendations Service |
| **Language** | Python 3.11 + FastAPI |
| **Database** | Elasticsearch (product index) + DynamoDB (user interactions) |
| **Primary Responsibility** | Full-text search, product discovery, personalized recommendations |
| **Scope** | Search indexing, faceted search, collaborative filtering, ranking algorithms |
| **Key Entities** | SearchDocument, UserInteraction, RecommendationModel, SearchAggregation |
| **Bounded Context** | Read-optimized; derives data from Order and Catalog domains |

**Data Ownership**:
- ✅ Owns (Elasticsearch): `products` index (derived from Catalog)
- ✅ Owns (DynamoDB): `user_interactions`, `recommendation_models`, `search_analytics` tables
- ❌ Does NOT own: Product master data (source of truth in Catalog), Order data
- 🔗 Subscribes to: `order.created` events (to build interaction matrix), `product.updated` events (to reindex)
- 🔗 Calls: Product Catalog (to rebuild index on restart)

**API Contracts**:
- `GET /api/v1/search` → Full-text search with filters
- `GET /api/v1/search?q=python&category=fiction&max_price=50` → Faceted search
- `GET /api/v1/recommendations` → Personalized recommendations for user
- `POST /api/v1/recommendations/feedback` → Collect click/purchase feedback
- `GET /api/v1/recommendations/trending` → Trending books (no personalization)

**Design Notes**:
- Elasticsearch index documents include: id, title, author, description, price, category, rating, reviews
- User interactions tracked: view, add_to_cart, purchase
- Recommendations use collaborative filtering (user-item matrix)
- If Elasticsearch offline, fall back to PostgreSQL Catalog queries (slow but functional)
- Search index built on startup from Product Catalog; updated via events

---

### 7. Notification Domain

| Aspect | Details |
|--------|---------|
| **Service** | Notifications Service |
| **Language** | TypeScript 5.3 + NestJS 10 |
| **Database** | DynamoDB (audit log) + Redis (Bull queue) |
| **Primary Responsibility** | Async notifications (email, SMS, webhooks), retry logic, audit trail |
| **Scope** | Event-driven notification dispatch, delivery status tracking, failed notification recovery |
| **Key Entities** | Notification, NotificationTemplate, NotificationAudit, DeliveryAttempt |
| **Bounded Context** | Consumer of events; no other service calls this directly |

**Data Ownership**:
- ✅ Owns (DynamoDB): `notifications`, `notification_templates`, `notification_audit_log`, `delivery_attempts` tables
- ✅ Owns (Redis/Bull): Job queue for notification jobs (ephemeral, not persisted)
- ❌ Does NOT own: User data (calls User Service to fetch email), Order data, Payment data
- 🔗 Subscribes to: `order.created`, `order.shipped`, `payment.failed`, `payment.succeeded` events
- 🔗 Calls: AWS SES (email), AWS SNS (SMS), custom webhook endpoints

**API Contracts**:
- `GET /api/v1/notifications/status/{notificationId}` → Fetch notification delivery status
- `GET /api/v1/notifications/audit?userId={userId}` → List notifications sent to user (admin only)

**Design Notes**:
- No synchronous endpoints (notifications are fire-and-forget)
- Notifications queued in Bull (Redis-backed job queue)
- Retry logic: exponential backoff up to 5 attempts over 24 hours
- Failed notifications go to Dead Letter Queue (DLQ) for manual review
- Audit trail: all delivery attempts logged to DynamoDB
- Templates: order_confirmation, order_shipped, payment_failed, etc.
- Multi-channel: same notification may send email + SMS + webhook

---

## Data Ownership Rules

### Rule 1: Single Service Ownership

**Statement**: Every data entity is owned by exactly one service. No shared tables, no replicas.

| Domain | Owned Data | Access Pattern | Source of Truth |
|--------|-----------|-----------------|-----------------|
| User | User profiles, addresses | Direct DB queries within service | PostgreSQL users table |
| Identity | Credentials, sessions | Direct DB queries within service | PostgreSQL credentials table |
| Catalog | Products, inventory | Direct DB queries + external calls | PostgreSQL products table |
| Order | Orders, carts | Direct DB queries + external calls | PostgreSQL orders table |
| Payment | Payment records (local) | DynamoDB + Stripe API | Stripe API (master); DynamoDB (audit) |
| Search | Product index, interactions | Elasticsearch + DynamoDB | Elasticsearch (derived from Catalog) |
| Notification | Audit logs | DynamoDB + Redis queue | DynamoDB (audit); Redis (jobs) |

---

### Rule 2: Access Pattern Hierarchy

**Tier 1 - Direct Database Access** (within service boundaries)
- Service queries its own database directly
- No network latency, no serialization/deserialization
- Used for: read-heavy operations, real-time queries
- **Services**: User, Identity, Catalog, Order

**Tier 2 - REST API Calls** (cross-service synchronous)
- Service calls another service's REST endpoint
- Network latency (typical: 50-100ms), JSON serialization
- Used for: validation, enrichment, lookups
- **Examples**: Order Service → Product Catalog (inventory check), Order Service → User Service (address lookup)
- **Timeout**: 5 seconds per call

**Tier 3 - Event Subscriptions** (cross-service asynchronous)
- Service subscribes to events published by other services
- EventBridge handles routing, retry, DLQ
- No guaranteed delivery time (typically < 1 second), but eventual consistency
- Used for: notifications, audit trails, derived data updates
- **Examples**: Order Service publishes `order.created` → Notifications Service consumes it

**Tier 4 - External APIs** (third-party systems)
- Service calls external system (Stripe, AWS SES, AWS SNS)
- No control over latency or availability
- Used for: payment processing, email/SMS delivery
- **Circuit breaker**: Fail fast if external service unavailable

---

### Rule 3: Eventual Consistency Across Domains

**Definition**: Data consistency is strong within a service, eventual across services.

**Strong Consistency (within service)**:
- Order created in Order Service DB
- Immediately returned to client with status PENDING
- Readers see consistent state (no stale reads)

**Eventual Consistency (across services)**:
```
T+0:   Order Service creates order, publishes order.created event
T+0.5s: EventBridge routes event to subscribers
T+1s:   Search Service receives event, updates DynamoDB interaction matrix
T+1.5s: Notifications Service receives event, queues email job
T+2s:   Notifications job executed, SES called
T+5s:   Email delivered

→ If user queries order status at T+0.1s: Gets PENDING (consistent)
→ If user queries recommendations at T+0.5s: May not include new purchase yet (eventual)
→ If user queries order status at T+2s: Still PENDING (strong consistency maintained)
```

---

### Rule 4: No Circular Dependencies

**Dependency Graph** (allowed edges only):
```
User Domain
└─ (no dependencies)

Identity Domain
├─ → User Domain (REST call to fetch user details)
└─ ← Called by API Gateway (token validation)

Catalog Domain
└─ (no dependencies; read-only source)

Order Domain
├─ → Catalog Domain (inventory check, pricing)
├─ → User Domain (address lookup)
├─ → Payment Domain (payment processing)
└─ → Publishes events (order.created, order.paid, order.cancelled)

Payment Domain
└─ → Publishes events (payment.succeeded, payment.failed)

Search Domain
├─ ← Subscribes to Order events (order.created, for interactions)
├─ ← Subscribes to Catalog events (product updates, for reindexing)
└─ → Calls Catalog Domain (product data on startup)

Notification Domain
└─ ← Subscribes to all events (order.*, payment.*, etc.)
```

**Forbidden Patterns**:
- ❌ Order → Identity (would require identity to know about orders)
- ❌ Search → Order (would create circular: Order → Search → Order)
- ❌ Notification → Order (would create required synchronous dependency)

---

### Rule 5: Event Publishing Contract

**Format**: All events follow strict JSON schema for clarity

```json
{
  "source": "bookstore.[domain]",
  "detail-type": "[EntityName][Action]",
  "detail": {
    "id": "uuid",
    "timestamp": "2026-01-18T12:27:00Z",
    "userId": "uuid (if applicable)",
    "data": { /* domain-specific payload */ }
  }
}
```

**Examples**:
```json
/* order.created */
{
  "source": "bookstore.order",
  "detail-type": "OrderCreated",
  "detail": {
    "orderId": "ord_abc123",
    "userId": "user_xyz789",
    "timestamp": "2026-01-18T12:27:00Z",
    "items": [
      { "productId": "book_001", "quantity": 2, "price": 19.99 }
    ],
    "totalAmount": 39.98,
    "shippingAddress": { "street": "...", "city": "..." }
  }
}

/* payment.succeeded */
{
  "source": "bookstore.payment",
  "detail-type": "PaymentSucceeded",
  "detail": {
    "paymentId": "pay_stripe_12345",
    "orderId": "ord_abc123",
    "amount": 39.98,
    "timestamp": "2026-01-18T12:27:05Z",
    "stripeChargeId": "ch_123456"
  }
}

/* order.shipped */
{
  "source": "bookstore.order",
  "detail-type": "OrderShipped",
  "detail": {
    "orderId": "ord_abc123",
    "userId": "user_xyz789",
    "timestamp": "2026-01-18T12:30:00Z",
    "trackingNumber": "1Z999AA10123456784",
    "carrier": "UPS"
  }
}
```

---

## Entity Definitions

### User Domain Entities

#### User
```
id: UUID (primary key)
email: String (unique, max 255 chars)
first_name: String (max 100 chars)
last_name: String (max 100 chars)
phone: String (optional, max 20 chars)
created_at: Timestamp
updated_at: Timestamp
is_active: Boolean (soft delete flag)
```

#### UserProfile
```
id: UUID (primary key)
user_id: UUID (foreign key to users)
bio: String (optional, max 500 chars)
profile_picture_url: String (optional)
date_of_birth: Date (optional)
nationality: String (optional)
preferences: JSONB (user-specific settings)
created_at: Timestamp
updated_at: Timestamp
```

#### Address
```
id: UUID (primary key)
user_id: UUID (foreign key to users)
type: Enum (home, work, other)
street: String (max 255 chars)
city: String (max 100 chars)
state: String (max 100 chars)
postal_code: String (max 20 chars)
country: String (max 100 chars)
is_default: Boolean (one default per user)
created_at: Timestamp
updated_at: Timestamp
```

#### Preference
```
id: UUID (primary key)
user_id: UUID (foreign key to users)
key: String (e.g., "email_frequency", "newsletter_opt_in")
value: String
created_at: Timestamp
updated_at: Timestamp
```

---

### Identity Domain Entities

#### Credential
```
id: UUID (primary key)
user_id: UUID (foreign key to users)
password_hash: String (bcrypt, never plaintext)
salt: String (random salt for hashing)
created_at: Timestamp
updated_at: Timestamp
last_password_change: Timestamp
failed_login_attempts: Integer (reset after successful login)
locked_until: Timestamp (optional, for failed login lockout)
```

#### Session
```
id: UUID (primary key)
user_id: UUID (foreign key to users)
jwt_token_id: String (unique identifier in JWT payload)
created_at: Timestamp
expires_at: Timestamp
ip_address: String (for audit trail)
user_agent: String (for audit trail)
is_active: Boolean
revoked_at: Timestamp (optional, null if active)
```

#### TokenBlacklist
```
id: UUID (primary key)
jwt_token_id: String (identifier from JWT)
user_id: UUID (for filtering)
blacklisted_at: Timestamp
expires_at: Timestamp (token expiry; entries auto-deleted after)
reason: String (logout, security incident, etc.)
```

#### AuthAudit
```
id: UUID (primary key)
user_id: UUID (optional, null for failed login)
event: String (login_success, login_failure, logout, token_refresh, token_revoke)
timestamp: Timestamp
ip_address: String
user_agent: String
metadata: JSONB (additional context)
```

---

### Catalog Domain Entities

#### Product
```
id: UUID (primary key)
isbn: String (unique, max 20 chars)
title: String (max 255 chars)
description: Text
category_id: UUID (foreign key to categories)
author_id: UUID (foreign key to authors)
publisher_id: UUID (foreign key to publishers)
publication_date: Date
language: String (e.g., "en", "fr", default "en")
pages: Integer
format: Enum (hardcover, paperback, ebook, audiobook)
cover_image_url: String
price: Decimal (10,2) — current price
original_price: Decimal (10,2, optional) — for discounts
rating: Decimal (1,1) — 0-5 stars (derived from reviews)
review_count: Integer (derived)
is_available: Boolean (soft delete; false if discontinued)
created_at: Timestamp
updated_at: Timestamp
```

#### Category
```
id: UUID (primary key)
name: String (max 100 chars, unique)
parent_category_id: UUID (optional, for hierarchy)
description: String (optional, max 500 chars)
display_order: Integer (for sorting in UI)
created_at: Timestamp
updated_at: Timestamp
```

#### Author
```
id: UUID (primary key)
first_name: String (max 100 chars)
last_name: String (max 100 chars)
bio: Text (optional)
birth_date: Date (optional)
nationality: String (optional)
website: String (optional)
created_at: Timestamp
updated_at: Timestamp
```

#### Publisher
```
id: UUID (primary key)
name: String (max 255 chars, unique)
country: String (optional)
website: String (optional)
created_at: Timestamp
updated_at: Timestamp
```

#### Inventory
```
id: UUID (primary key)
product_id: UUID (foreign key to products, unique)
quantity_available: Integer (currently in stock)
quantity_reserved: Integer (ordered but not yet shipped)
quantity_damaged: Integer (for accounting)
reorder_level: Integer (threshold for alerting)
reorder_quantity: Integer (batch size for restocking)
last_restocked: Timestamp
created_at: Timestamp
updated_at: Timestamp
```

#### Price
```
id: UUID (primary key)
product_id: UUID (foreign key to products)
price: Decimal (10,2)
effective_from: Timestamp
effective_until: Timestamp (null if current)
reason: String (standard, promotion, seasonal)
created_at: Timestamp
```

---

### Order Domain Entities

#### Order
```
id: UUID (primary key)
user_id: UUID (foreign key to users)
status: Enum (pending, paid, processing, shipped, delivered, cancelled, returned)
total_amount: Decimal (10,2)
tax_amount: Decimal (10,2)
shipping_cost: Decimal (10,2)
discount_amount: Decimal (10,2, optional)
promo_code: String (optional, max 50 chars)
shipping_address_id: UUID (snapshot of address at order time)
billing_address_id: UUID (snapshot of address at order time)
payment_id: UUID (foreign key to payments, optional initially)
created_at: Timestamp
paid_at: Timestamp (optional)
shipped_at: Timestamp (optional)
delivered_at: Timestamp (optional)
cancelled_at: Timestamp (optional)
updated_at: Timestamp
```

#### OrderItem
```
id: UUID (primary key)
order_id: UUID (foreign key to orders)
product_id: UUID (snapshot reference to product)
product_title: String (snapshot of title at purchase)
product_isbn: String (snapshot of ISBN)
quantity: Integer
unit_price: Decimal (10,2) — price at time of purchase
total_price: Decimal (10,2)
created_at: Timestamp
```

#### ShoppingCart
```
id: UUID (primary key)
user_id: UUID (foreign key to users, unique)
created_at: Timestamp
expires_at: Timestamp (30 days from last update)
updated_at: Timestamp
abandoned_at: Timestamp (optional, for analytics)
```

#### CartItem
```
id: UUID (primary key)
cart_id: UUID (foreign key to shopping_carts)
product_id: UUID (reference to current product)
quantity: Integer
added_at: Timestamp
```

#### Fulfillment
```
id: UUID (primary key)
order_id: UUID (foreign key to orders)
status: Enum (pending, picked, packed, shipped, delivered, lost, returned)
warehouse_location: String (optional)
tracking_number: String (optional)
carrier: String (optional, e.g., "UPS", "FedEx")
estimated_delivery: Date (optional)
shipped_at: Timestamp (optional)
delivered_at: Timestamp (optional)
created_at: Timestamp
updated_at: Timestamp
```

#### Return
```
id: UUID (primary key)
order_id: UUID (foreign key to orders)
reason: String (defective, wrong_item, changed_mind, etc.)
status: Enum (initiated, approved, rejected, shipped_to_warehouse, received, refunded)
requested_at: Timestamp
approved_at: Timestamp (optional)
rejected_at: Timestamp (optional)
refund_amount: Decimal (10,2)
refund_processed_at: Timestamp (optional)
created_at: Timestamp
updated_at: Timestamp
```

---

### Payment Domain Entities

#### Payment
```
id: UUID (primary key, local DynamoDB)
order_id: UUID
user_id: UUID
amount: Decimal (10,2)
currency: String (default "USD")
status: String (pending, succeeded, failed, refunded)
stripe_charge_id: String (external Stripe ID)
stripe_customer_id: String (external Stripe ID)
payment_method: String (card, etc.)
card_last_four: String (last 4 digits only, for display)
created_at: Timestamp
updated_at: Timestamp
```

#### Transaction
```
id: UUID (primary key, local DynamoDB)
payment_id: UUID
order_id: UUID
type: String (charge, refund, adjustment)
amount: Decimal (10,2)
status: String (pending, completed, failed)
external_transaction_id: String (Stripe ID)
created_at: Timestamp
updated_at: Timestamp
```

#### Refund
```
id: UUID (primary key, local DynamoDB)
payment_id: UUID
order_id: UUID
return_id: UUID (reference to order return)
amount: Decimal (10,2)
reason: String
stripe_refund_id: String (external Stripe ID)
status: String (initiated, succeeded, failed)
created_at: Timestamp
completed_at: Timestamp (optional)
```

#### IdempotencyKey
```
id: UUID (primary key, local DynamoDB)
idempotency_key: String (unique, client-provided UUID)
user_id: UUID
endpoint: String (e.g., "POST /api/v1/payments")
request_hash: String (hash of request body)
response_json: String (cached response)
created_at: Timestamp
expires_at: Timestamp (24 hours from creation)
```

#### PaymentAudit
```
id: UUID (primary key, local DynamoDB)
payment_id: UUID
user_id: UUID
event: String (payment_initiated, payment_succeeded, payment_failed, refund_initiated, etc.)
timestamp: Timestamp
details: JSONB (amount, error message, etc.)
```

---

### Search Domain Entities

#### SearchDocument (Elasticsearch)
```
id: String (product_id)
title: String (analyzed, searchable)
author: String (analyzed)
publisher: String
description: Text (analyzed)
isbn: String (exact match)
category: String (keyword, not analyzed)
tags: String[] (keyword array)
price: Float (for range queries)
rating: Float (0-5, for filtering/sorting)
review_count: Integer
available: Boolean (product is_available)
created_at: DateTime (for recent filter)
popularity_score: Float (for ranking, derived from sales count)
```

#### UserInteraction (DynamoDB)
```
id: UUID (primary key)
user_id: UUID (sort key for querying user's interactions)
product_id: UUID
interaction_type: String (view, add_to_cart, purchase)
timestamp: Timestamp (for recency weighting)
metadata: JSONB (e.g., time_spent_viewing for views)
```

#### RecommendationModel (DynamoDB)
```
id: UUID (primary key)
user_id: UUID
model_version: String (for A/B testing)
recommended_product_ids: String[] (list of product IDs, ordered)
score: Float (model confidence)
generated_at: Timestamp
expires_at: Timestamp (refresh every 7 days)
algorithm: String (collaborative_filtering, content_based, etc.)
```

#### SearchAnalytics (DynamoDB)
```
id: UUID (primary key)
query: String
result_count: Integer
click_through_rate: Float
timestamp: Timestamp
user_id: UUID (optional, for tracking)
```

---

### Notification Domain Entities

#### Notification (DynamoDB)
```
id: UUID (primary key)
user_id: UUID (sort key for user's notifications)
type: String (order_confirmation, order_shipped, payment_failed, etc.)
template_id: String (template used)
status: String (queued, sent, failed, delivered)
channels: String[] (email, sms, webhook)
created_at: Timestamp
sent_at: Timestamp (optional)
delivered_at: Timestamp (optional)
failed_at: Timestamp (optional)
retry_count: Integer (0 initially)
next_retry_at: Timestamp (optional)
error_message: String (optional)
```

#### NotificationTemplate (DynamoDB)
```
id: String (primary key, e.g., "order_confirmation")
name: String
subject: String (for email)
body_template: String (with placeholders: {{order_id}}, {{user_name}})
channels_enabled: String[] (email, sms, webhook)
created_at: Timestamp
updated_at: Timestamp
```

#### NotificationAudit (DynamoDB)
```
id: UUID (primary key)
notification_id: UUID
user_id: UUID
event: String (queued, sent, failed, delivered, bounced)
channel: String (email, sms, webhook)
timestamp: Timestamp
metadata: JSONB (delivery details)
```

#### DeliveryAttempt (DynamoDB)
```
id: UUID (primary key)
notification_id: UUID
attempt_number: Integer
timestamp: Timestamp
status: String (success, failed, retrying)
error_code: String (optional)
error_message: String (optional)
external_message_id: String (optional, from SES/SNS)
retry_at: Timestamp (optional)
```

---

## Database Schema Decisions

### PostgreSQL (User, Identity, Catalog, Order Services)

**Why PostgreSQL for these services?**

| Service | Reason |
|---------|--------|
| User | Complex user profile with nested addresses; consistent reads; ACID for data integrity |
| Identity | Credentials require encrypted storage; sessions need consistent updates; audit logs need strong consistency |
| Catalog | Complex product metadata (authors, publishers, categories); hierarchical categories; inventory tracking; full ACID compliance |
| Order | Complex order state machine; inventory coordination; price snapshots; refund logic; strong consistency required |

**PostgreSQL Configuration**:
- **Version**: 15+
- **Instance Type**: db.r6g.xlarge (RDS, Multi-AZ)
- **Storage**: General Purpose SSD, 500 GB, auto-scaling
- **Backup**: Automated daily, 30-day retention, point-in-time recovery
- **Replication**: Synchronous Multi-AZ (RDS automatic failover)
- **Connection Pooling**: PgBouncer or RDS Proxy (20 connections per service)
- **Encryption**: At rest (AES-256), in transit (SSL)

**Schema Isolation**:
- Single PostgreSQL instance with separate schemas per service
- User Domain: `user_schema`
- Identity Domain: `identity_schema`
- Catalog Domain: `catalog_schema`
- Order Domain: `order_schema`
- No cross-schema queries (enforced at application layer)

**Indexing Strategy**:
```sql
/* User Domain */
CREATE INDEX idx_users_email ON user_schema.users(email);
CREATE INDEX idx_users_created ON user_schema.users(created_at DESC);
CREATE INDEX idx_addresses_user ON user_schema.addresses(user_id);

/* Catalog Domain */
CREATE INDEX idx_products_category ON catalog_schema.products(category_id);
CREATE INDEX idx_products_isbn ON catalog_schema.products(isbn);
CREATE INDEX idx_inventory_product ON catalog_schema.inventory(product_id);
CREATE INDEX idx_prices_product ON catalog_schema.prices(product_id, effective_from DESC);

/* Order Domain */
CREATE INDEX idx_orders_user ON order_schema.orders(user_id);
CREATE INDEX idx_orders_status ON order_schema.orders(status);
CREATE INDEX idx_orders_created ON order_schema.orders(created_at DESC);
CREATE INDEX idx_order_items_order ON order_schema.order_items(order_id);
CREATE INDEX idx_carts_user ON order_schema.shopping_carts(user_id);
```

---

### Elasticsearch (Search Service)

**Why Elasticsearch?**

| Feature | Benefit |
|---------|---------|
| Full-text search | Tokenization, stemming, synonyms, phrase queries across product titles/descriptions |
| Faceted search | Fast aggregations on category, price range, rating, author |
| Relevance ranking | TF-IDF scoring, boost titles over descriptions |
| Scale | Horizontal scaling for 10M products; queries < 300ms at P95 |

**Elasticsearch Configuration**:
- **Version**: 8.x (or OpenSearch 2.x)
- **Nodes**: 3-node cluster (multi-AZ)
- **Shard allocation**: 3 primary shards, 1 replica per index
- **Storage**: 500 GB total
- **Backup**: Daily snapshot to S3

**Index Design**:
```json
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "default": {
          "type": "standard",
          "stopwords": "_english_"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "title": { "type": "text", "analyzer": "standard", "boost": 2 },
      "author": { "type": "text" },
      "description": { "type": "text" },
      "isbn": { "type": "keyword" },
      "category": { "type": "keyword" },
      "price": { "type": "float" },
      "rating": { "type": "float" },
      "available": { "type": "boolean" },
      "created_at": { "type": "date" }
    }
  }
}
```

**Index Lifecycle**:
1. **On startup**: Search Service fetches all products from Catalog, builds index
2. **On product update**: Catalog publishes `product.updated` event → Search Service reindexes that product
3. **Daily**: Rebuild entire index (full re-sync from Catalog)
4. **Disaster recovery**: Delete index and rebuild from Catalog

---

### DynamoDB (Payment, Search Interactions, Notifications)

**Why DynamoDB?**

| Service | Use Case | Reason |
|---------|----------|--------|
| Payment | Payment audit log, idempotency keys | High write throughput, expire old records via TTL |
| Search | User interactions (views, purchases) | Append-only, eventually consistent, cheap at scale |
| Notification | Notification audit log, delivery attempts | High write throughput, query by user_id and timestamp |

**DynamoDB Configuration**:
- **Billing Mode**: Pay-per-request (variable workload)
- **Encryption**: At rest (AWS managed) and in transit (TLS)
- **Backup**: Point-in-time recovery enabled
- **TTL**: Enabled on `expires_at` for automatic cleanup

**Table Designs**:

#### Payments Table
```
Primary Key: id (String)
Attributes:
  - order_id (String, GSI sort key)
  - user_id (String, GSI partition key)
  - amount (Number)
  - status (String)
  - created_at (Number, Unix timestamp)
  - expires_at (Number, for TTL)
  
Global Secondary Index (GSI):
  - Partition Key: user_id
  - Sort Key: created_at (DESC, for recent first)
```

#### UserInteractions Table
```
Primary Key: id (String)
Attributes:
  - user_id (String, GSI partition key)
  - product_id (String)
  - interaction_type (String)
  - timestamp (Number, GSI sort key)
  - metadata (String, JSON-serialized)

Global Secondary Index (GSI):
  - Partition Key: user_id
  - Sort Key: timestamp (DESC)
  - Projection: All
```

#### Notifications Table
```
Primary Key: id (String)
Attributes:
  - user_id (String, GSI partition key)
  - type (String)
  - status (String)
  - created_at (Number, GSI sort key)
  - channels (String array)
  - retry_count (Number)
  - next_retry_at (Number)

Global Secondary Index (GSI):
  - Partition Key: user_id
  - Sort Key: created_at (DESC)
```

---

## Domain Diagrams

### Domain Model Map

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          ONLINE BOOKSTORE DOMAINS                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  USER DOMAIN (User Service)                                        │    │
│  │  ┌─────────────────────────────────────────────────────────────┐   │    │
│  │  │ Entities:                                                   │   │    │
│  │  │ • User (id, email, name, phone)                            │   │    │
│  │  │ • UserProfile (bio, picture, preferences)                 │   │    │
│  │  │ • Address (street, city, postal_code, type)               │   │    │
│  │  │ • Preference (user settings)                              │   │    │
│  │  └─────────────────────────────────────────────────────────────┘   │    │
│  │  Database: PostgreSQL                                             │    │
│  │  Access: User Service only; Catalog Domain for user lookup      │    │
│  │  Events: user.registered, user.updated                          │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  IDENTITY DOMAIN (Auth Service)                                    │    │
│  │  ┌─────────────────────────────────────────────────────────────┐   │    │
│  │  │ Entities:                                                   │   │    │
│  │  │ • Credential (password_hash, salt, failed_attempts)        │   │    │
│  │  │ • Session (jwt_token_id, created_at, expires_at)          │   │    │
│  │  │ • TokenBlacklist (jwt_token_id, blacklisted_at)           │   │    │
│  │  │ • AuthAudit (event, timestamp, ip_address, user_agent)    │   │    │
│  │  └─────────────────────────────────────────────────────────────┘   │    │
│  │  Database: PostgreSQL                                             │    │
│  │  Calls: User Service (fetch user details)                         │    │
│  │  Integrates: AWS Cognito (OAuth2/OIDC)                           │    │
│  │  Events: token.issued, token.revoked                             │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  CATALOG DOMAIN (Product Catalog Service)                          │    │
│  │  ┌─────────────────────────────────────────────────────────────┐   │    │
│  │  │ Entities:                                                   │   │    │
│  │  │ • Product (title, isbn, price, description)               │   │    │
│  │  │ • Category (name, parent_id, display_order)               │   │    │
│  │  │ • Author (name, bio, birth_date)                          │   │    │
│  │  │ • Publisher (name, country)                               │   │    │
│  │  │ • Inventory (quantity_available, reserved, reorder_level) │   │    │
│  │  │ • Price (price, effective_from, reason)                   │   │    │
│  │  └─────────────────────────────────────────────────────────────┘   │    │
│  │  Database: PostgreSQL                                             │    │
│  │  Access: Read-heavy; source of truth for product data             │    │
│  │  Called by: Order Service (inventory), Search Service (index)     │    │
│  │  Events: product.created, product.updated, product.deleted       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  ORDER DOMAIN (Order Service)                                      │    │
│  │  ┌─────────────────────────────────────────────────────────────┐   │    │
│  │  │ Entities:                                                   │   │    │
│  │  │ • Order (status, total_amount, payment_id)                 │   │    │
│  │  │ • OrderItem (product_id, quantity, unit_price)             │   │    │
│  │  │ • ShoppingCart (user_id, expires_at)                       │   │    │
│  │  │ • CartItem (product_id, quantity)                          │   │    │
│  │  │ • Fulfillment (status, tracking_number, estimated_deliv)   │   │    │
│  │  │ • Return (reason, status, refund_amount)                   │   │    │
│  │  └─────────────────────────────────────────────────────────────┘   │    │
│  │  Database: PostgreSQL                                             │    │
│  │  Calls: Catalog (inventory), User (addresses), Payment (charges)  │    │
│  │  Events: order.created, order.paid, order.shipped, order.cancel  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  PAYMENT DOMAIN (Payment Processor Service)                        │    │
│  │  ┌─────────────────────────────────────────────────────────────┐   │    │
│  │  │ Entities (local DynamoDB):                                  │   │    │
│  │  │ • Payment (order_id, user_id, amount, status)              │   │    │
│  │  │ • Transaction (payment_id, type, amount)                   │   │    │
│  │  │ • Refund (payment_id, return_id, amount, status)           │   │    │
│  │  │ • IdempotencyKey (idempotency_key, request_hash, response) │   │    │
│  │  │ • PaymentAudit (event, timestamp, details)                 │   │    │
│  │  └─────────────────────────────────────────────────────────────┘   │    │
│  │  Database: DynamoDB (local) + Stripe API (source of truth)        │    │
│  │  Called by: Order Service (synchronous payment)                    │    │
│  │  External: Stripe API for card processing                          │    │
│  │  Events: payment.succeeded, payment.failed, payment.refunded      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  SEARCH & RECOMMENDATIONS DOMAIN (Search Service)                  │    │
│  │  ┌─────────────────────────────────────────────────────────────┐   │    │
│  │  │ Entities:                                                   │   │    │
│  │  │ • SearchDocument (Elasticsearch: title, author, price)     │   │    │
│  │  │ • UserInteraction (DynamoDB: user_id, product_id, type)    │   │    │
│  │  │ • RecommendationModel (user_id, recommended_product_ids)   │   │    │
│  │  │ • SearchAnalytics (query, result_count, timestamp)         │   │    │
│  │  └─────────────────────────────────────────────────────────────┘   │    │
│  │  Databases: Elasticsearch (index) + DynamoDB (interactions)        │    │
│  │  Subscribes: order.created (for interactions), product.* (reindex) │    │
│  │  Calls: Catalog Service (on startup for initial index)             │    │
│  │  Events: (none published; consumer only)                           │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  NOTIFICATION DOMAIN (Notifications Service)                       │    │
│  │  ┌─────────────────────────────────────────────────────────────┐   │    │
│  │  │ Entities (DynamoDB):                                        │   │    │
│  │  │ • Notification (user_id, type, status, channels)           │   │    │
│  │  │ • NotificationTemplate (name, subject, body_template)      │   │    │
│  │  │ • NotificationAudit (notification_id, event, channel)       │   │    │
│  │  │ • DeliveryAttempt (notification_id, attempt_number, status) │   │    │
│  │  └─────────────────────────────────────────────────────────────┘   │    │
│  │  Databases: DynamoDB (audit) + Redis/Bull (job queue)              │    │
│  │  Subscribes: order.*, payment.*, user.* events                     │    │
│  │  Calls: User Service (email), AWS SES (email), AWS SNS (SMS)       │    │
│  │  Events: (none published; consumer only)                           │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Entity-Relationship Diagrams

### PostgreSQL (User + Identity + Catalog + Order Domains)

```
USER SCHEMA
═══════════

users (PK: id)
├─ id: UUID
├─ email: String (UNIQUE)
├─ first_name: String
├─ last_name: String
├─ phone: String
├─ created_at: Timestamp
├─ updated_at: Timestamp
└─ is_active: Boolean
    │
    ├──→ user_profiles (1-to-1)
    │    ├─ id: UUID
    │    ├─ user_id: UUID (FK)
    │    ├─ bio: String
    │    ├─ profile_picture_url: String
    │    └─ preferences: JSONB
    │
    ├──→ addresses (1-to-many)
    │    ├─ id: UUID
    │    ├─ user_id: UUID (FK)
    │    ├─ type: Enum
    │    ├─ street: String
    │    ├─ city: String
    │    ├─ postal_code: String
    │    ├─ is_default: Boolean
    │    └─ [indexed on user_id]
    │
    └──→ preferences (1-to-many)
         ├─ id: UUID
         ├─ user_id: UUID (FK)
         ├─ key: String
         └─ value: String


IDENTITY SCHEMA
═══════════════

credentials (PK: id)
├─ id: UUID
├─ user_id: UUID (FK to users)
├─ password_hash: String
├─ salt: String
├─ created_at: Timestamp
├─ updated_at: Timestamp
└─ failed_login_attempts: Integer
    └─ [indexed on user_id]

sessions (PK: id)
├─ id: UUID
├─ user_id: UUID (FK to users)
├─ jwt_token_id: String (UNIQUE)
├─ created_at: Timestamp
├─ expires_at: Timestamp
├─ is_active: Boolean
└─ [indexed on user_id, expires_at]

token_blacklist (PK: id)
├─ id: UUID
├─ jwt_token_id: String (UNIQUE)
├─ user_id: UUID (FK to users)
├─ blacklisted_at: Timestamp
└─ [indexed on expires_at for TTL cleanup]

auth_audit_log (PK: id)
├─ id: UUID
├─ user_id: UUID (FK to users, nullable)
├─ event: String
├─ timestamp: Timestamp
├─ ip_address: String
└─ [indexed on user_id, timestamp]


CATALOG SCHEMA
══════════════

products (PK: id)
├─ id: UUID
├─ isbn: String (UNIQUE)
├─ title: String
├─ description: Text
├─ category_id: UUID (FK)
├─ author_id: UUID (FK)
├─ publisher_id: UUID (FK)
├─ price: Decimal
├─ rating: Decimal
├─ is_available: Boolean
├─ created_at: Timestamp
├─ [indexed on category_id, author_id, isbn]
│
├──→ categories (many-to-1)
│    ├─ id: UUID
│    ├─ name: String (UNIQUE)
│    ├─ parent_category_id: UUID (FK, self-referential)
│    ├─ description: String
│    └─ display_order: Integer
│
├──→ authors (many-to-1)
│    ├─ id: UUID
│    ├─ first_name: String
│    ├─ last_name: String
│    ├─ bio: Text
│    └─ [indexed on last_name]
│
├──→ publishers (many-to-1)
│    ├─ id: UUID
│    ├─ name: String (UNIQUE)
│    └─ country: String
│
├──→ inventory (1-to-1)
│    ├─ id: UUID
│    ├─ product_id: UUID (FK, UNIQUE)
│    ├─ quantity_available: Integer
│    ├─ quantity_reserved: Integer
│    ├─ reorder_level: Integer
│    └─ [indexed on product_id]
│
└──→ prices (1-to-many)
     ├─ id: UUID
     ├─ product_id: UUID (FK)
     ├─ price: Decimal
     ├─ effective_from: Timestamp
     ├─ effective_until: Timestamp
     └─ [indexed on product_id, effective_from DESC]


ORDER SCHEMA
════════════

shopping_carts (PK: id)
├─ id: UUID
├─ user_id: UUID (FK to users, UNIQUE)
├─ created_at: Timestamp
├─ expires_at: Timestamp
└─ [indexed on user_id, expires_at]
    │
    └──→ cart_items (1-to-many)
         ├─ id: UUID
         ├─ cart_id: UUID (FK)
         ├─ product_id: UUID (reference to catalog.products)
         ├─ quantity: Integer
         └─ [indexed on cart_id]

orders (PK: id)
├─ id: UUID
├─ user_id: UUID (FK to users)
├─ status: Enum (pending, paid, processing, shipped, delivered, cancelled)
├─ total_amount: Decimal
├─ tax_amount: Decimal
├─ shipping_cost: Decimal
├─ payment_id: UUID (FK to payments, nullable)
├─ created_at: Timestamp
├─ paid_at: Timestamp
├─ shipped_at: Timestamp
├─ delivered_at: Timestamp
├─ [indexed on user_id, status, created_at DESC]
│
├──→ order_items (1-to-many)
│    ├─ id: UUID
│    ├─ order_id: UUID (FK)
│    ├─ product_id: UUID (snapshot reference)
│    ├─ product_title: String (snapshot)
│    ├─ quantity: Integer
│    ├─ unit_price: Decimal
│    └─ [indexed on order_id]
│
└──→ fulfillments (1-to-many)
     ├─ id: UUID
     ├─ order_id: UUID (FK)
     ├─ status: Enum (pending, packed, shipped, delivered)
     ├─ tracking_number: String
     ├─ carrier: String
     ├─ estimated_delivery: Date
     ├─ shipped_at: Timestamp
     └─ [indexed on order_id]

returns (PK: id)
├─ id: UUID
├─ order_id: UUID (FK to orders)
├─ reason: String
├─ status: Enum (initiated, approved, refunded)
├─ requested_at: Timestamp
├─ refund_amount: Decimal
└─ [indexed on order_id, status]
```

---

### DynamoDB (Payment + Search + Notification Domains)

```
PAYMENT DOMAIN (DynamoDB)
═════════════════════════

payments
├─ id (String, PK)
├─ order_id (String, GSI-PK)
├─ user_id (String, GSI-PK)
├─ amount (Number)
├─ currency (String)
├─ status (String)
├─ stripe_charge_id (String)
├─ stripe_customer_id (String)
├─ created_at (Number)
├─ TTL: expires_at (for records older than 7 years)
│
├─ Global Secondary Index:
│  └─ GSI: user_id (PK) + created_at (SK, DESC)
│     → Query: all payments for user, ordered by recency
│
└─ transactions (child items, denormalized)
   ├─ transaction_id (String, ULID)
   ├─ payment_id (String, GSI-PK)
   ├─ type (String)
   ├─ amount (Number)
   ├─ status (String)
   └─ external_transaction_id (String)

idempotency_keys
├─ id (String, PK)
├─ idempotency_key (String, UNIQUE)
├─ user_id (String, GSI-PK)
├─ endpoint (String)
├─ request_hash (String)
├─ response_json (String)
├─ created_at (Number)
├─ TTL: expires_at (24 hours)
│
└─ Global Secondary Index:
   └─ GSI: user_id (PK) + endpoint (SK)
      → Query: recent payment attempts by user


SEARCH DOMAIN (DynamoDB)
════════════════════════

user_interactions
├─ id (String, PK)
├─ user_id (String, GSI-PK)
├─ product_id (String)
├─ interaction_type (String) [view, add_to_cart, purchase]
├─ timestamp (Number, GSI-SK)
└─ Global Secondary Index:
   └─ GSI: user_id (PK) + timestamp (SK, DESC)
      → Query: get all interactions for user, recency ordered


NOTIFICATION DOMAIN (DynamoDB)
══════════════════════════════

notifications
├─ id (String, PK)
├─ user_id (String, GSI-PK)
├─ type (String) [order_confirmation, order_shipped, etc.]
├─ template_id (String)
├─ status (String) [queued, sent, failed, delivered]
├─ channels (String array) [email, sms, webhook]
├─ created_at (Number, GSI-SK)
├─ sent_at (Number)
├─ retry_count (Number)
├─ next_retry_at (Number)
└─ Global Secondary Index:
   └─ GSI: user_id (PK) + created_at (SK, DESC)
      → Query: get notifications for user, ordered by recency

delivery_attempts
├─ id (String, PK)
├─ notification_id (String, GSI-PK)
├─ attempt_number (Number)
├─ timestamp (Number, GSI-SK)
├─ status (String)
├─ error_code (String)
├─ external_message_id (String)
└─ Global Secondary Index:
   └─ GSI: notification_id (PK) + timestamp (SK)
      → Query: get all delivery attempts for notification
```

---

### Elasticsearch (Search Domain)

```
SEARCH INDEX STRUCTURE
══════════════════════

Index Name: products
Document Type: _doc (default)
Sharding: 3 primary, 1 replica
Refresh Interval: 1s

Mapping:
{
  "properties": {
    "id": {
      "type": "keyword",
      "index": true
    },
    "title": {
      "type": "text",
      "analyzer": "standard",
      "boost": 2.0,
      "fields": {
        "keyword": { "type": "keyword" }
      }
    },
    "author": {
      "type": "text",
      "analyzer": "standard"
    },
    "publisher": {
      "type": "keyword"
    },
    "description": {
      "type": "text",
      "analyzer": "standard"
    },
    "isbn": {
      "type": "keyword"
    },
    "category": {
      "type": "keyword"
    },
    "tags": {
      "type": "keyword"
    },
    "price": {
      "type": "float"
    },
    "rating": {
      "type": "float"
    },
    "review_count": {
      "type": "integer"
    },
    "available": {
      "type": "boolean"
    },
    "created_at": {
      "type": "date"
    },
    "popularity_score": {
      "type": "float"
    }
  }
}

Sample Query:
  GET /products/_search
  {
    "query": {
      "bool": {
        "must": [
          { "match": { "title": "python programming" } }
        ],
        "filter": [
          { "term": { "category": "technology" } },
          { "range": { "price": { "lte": 50 } } },
          { "term": { "available": true } }
        ]
      }
    },
    "aggs": {
      "by_category": { "terms": { "field": "category" } },
      "price_ranges": {
        "range": {
          "field": "price",
          "ranges": [
            { "to": 20 },
            { "from": 20, "to": 50 },
            { "from": 50 }
          ]
        }
      }
    }
  }
```

---

## Cross-Domain Data Flows

### Flow 1: User Registration

```
1. Client → API Gateway: POST /auth/register
   Payload: { email, password, firstName, lastName }

2. API Gateway → Auth Service (Identity Domain)
   Validates request, creates new transaction

3. Auth Service → User Service (User Domain)
   Synchronous: POST /api/v1/users
   Payload: { email, firstName, lastName }
   Response: { userId }

4. User Service → PostgreSQL (users table)
   INSERT INTO user_schema.users (...)
   → Returns created user with id

5. Auth Service → PostgreSQL (credentials table)
   INSERT INTO identity_schema.credentials (user_id, password_hash, salt)

6. Auth Service → AWS Cognito
   CREATE_USER in user pool

7. Auth Service → EventBridge
   Publish: user.registered
   Event: { userId, email, timestamp }

8. EventBridge → Search Service (async subscription)
   Search Service initializes user interaction matrix

9. EventBridge → Notifications Service (async subscription)
   Notifications Service queues welcome email

10. Auth Service → API Gateway → Client
    Response: { userId, token, refreshToken }
    Status: 201 Created
```

---

### Flow 2: Create Order (Complex Multi-Service)

```
1. Client → API Gateway: POST /orders
   Payload: { userId, shippingAddressId, items: [{ productId, quantity }] }
   Headers: Authorization: Bearer <JWT>

2. API Gateway → Auth Service (Identity Domain)
   Validate token, extract userId, verify scopes ("write:orders")

3. API Gateway → Order Service (Order Domain)
   Forward request with correlation ID

4. Order Service → PostgreSQL (orders table)
   INSERT INTO order_schema.orders (user_id, status=PENDING, created_at)
   → Returns orderId

5. For each item in cart:
   Order Service → Product Catalog (Catalog Domain)
   Synchronous: GET /api/v1/products/{productId}/inventory
   Response: { quantity_available, price }
   
   [Validation: quantity_available >= requested quantity]

6. Order Service → User Service (User Domain)
   Synchronous: GET /api/v1/users/{userId}/addresses/{addressId}
   Response: { street, city, postal_code, etc. }

7. Order Service → PostgreSQL (order_items table)
   For each item:
   INSERT INTO order_schema.order_items (order_id, product_id, quantity, unit_price)

8. Order Service → Product Catalog (Catalog Domain)
   Synchronous: PATCH /api/v1/products/{productId}/inventory
   Payload: { quantityDecrement: requested_qty }
   
   [Pessimistic: inventory immediately reserved]

9. Order Service → PostgreSQL (orders table)
   UPDATE orders SET status=PROCESSING

10. Order Service → Payment Processor (Payment Domain)
    Synchronous: POST /api/v1/payments
    Payload: {
      orderId,
      amount: total_price,
      idempotencyKey: uuid(),
      paymentMethod: card_token
    }
    Timeout: 5 seconds
    
    [Payment Processor calls Stripe API, returns charge_id]

11. Payment Processor → DynamoDB (payments table)
    INSERT payments record with status=SUCCEEDED

12. Order Service → PostgreSQL (orders table)
    UPDATE orders SET status=PAID, payment_id=<payment_id>

13. Order Service → EventBridge
    Publish: order.created
    Event: {
      orderId,
      userId,
      items,
      totalAmount,
      timestamp
    }

14. EventBridge routes event to 3 subscribers:
    
    a) Notifications Service receives order.created
       → Queues "order_confirmation" email job in Bull
       → Email sent within 1-2 seconds
    
    b) Search Service receives order.created
       → Updates DynamoDB user_interactions for each product
       → Triggers ML job to recalculate recommendations
    
    c) (Optional) Product Catalog receives order.created
       → Updates product popularity metrics

15. Order Service → API Gateway → Client
    Response: {
      orderId,
      status: PAID,
      estimatedDelivery: "2026-01-25",
      items,
      totalAmount
    }
    Status: 201 Created
    Timing: T+0 to T+0.5s (synchronous operations)

Eventual Consistency Window:
- T+0.5s: Order visible if queried
- T+1s: Email queued and being sent
- T+2s: Recommendations being recalculated
- T+5s: Email delivered to user
```

---

### Flow 3: Search with Recommendations

```
1. Client → API Gateway: GET /search?q=python&category=fiction&max_price=50
   Headers: Authorization: Bearer <JWT>

2. API Gateway → Search Service (Search Domain)
   Forward request with correlation ID

3. Search Service → Elasticsearch
   Query: {
     "bool": {
       "must": [ { "match": { "title": "python" } } ],
       "filter": [
         { "term": { "category": "fiction" } },
         { "range": { "price": { "lte": 50 } } }
       ]
     }
   }
   Response: [ list of product documents ], timing: < 300ms

4. Fetch recommendations (parallel):
   Search Service → DynamoDB (user_interactions table)
   Query: user_id={userId}, ordered by recency
   Response: [ list of recent interactions ]

5. Search Service → DynamoDB (recommendation_models table)
   Query: user_id={userId}
   Response: { recommended_product_ids, model_version }

6. Enrich results:
   Search Service merges:
   - Search results (high relevance to query)
   - Recommendations (high relevance to user profile)
   
   Ranking: query relevance (60%) + user preference (40%)

7. Response → API Gateway → Client
   {
     "results": [
       {
         "id": "book_001",
         "title": "Python Secrets",
         "author": "Jane Doe",
         "price": 35.99,
         "rating": 4.5,
         "why_recommended": "search_match" | "user_preference"
       },
       ...
     ],
     "total": 234,
     "facets": {
       "categories": { "fiction": 150, "non-fiction": 84 },
       "price_ranges": { "0-20": 50, "20-50": 100, "50+": 84 }
     }
   }
   Timing: T+0 to T+0.5s (search < 300ms + recommendation lookup < 100ms)
```

---

## Summary: Data Ownership Matrix

| Entity Type | Domain | Service | Database | Owner | Consumers |
|-------------|--------|---------|----------|-------|-----------|
| User | User | User Service | PostgreSQL | ✅ | Auth, Order |
| Credential | Identity | Auth Service | PostgreSQL | ✅ | Auth only |
| Session | Identity | Auth Service | PostgreSQL | ✅ | Auth only |
| Product | Catalog | Catalog Service | PostgreSQL | ✅ | Order, Search, Notifications |
| Inventory | Catalog | Catalog Service | PostgreSQL | ✅ | Order |
| Order | Order | Order Service | PostgreSQL | ✅ | Notifications, Search, Payment |
| Payment | Payment | Payment Service | DynamoDB + Stripe | ✅ | Order, Notifications |
| SearchDocument | Search | Search Service | Elasticsearch | ✅ | Search only |
| UserInteraction | Search | Search Service | DynamoDB | ✅ | Search only |
| Notification | Notification | Notifications Service | DynamoDB | ✅ | Notifications only |

---

**Document Version**: 1.0.0  
**Status**: Iteration 2 — Complete  
**Next**: Service API Specifications (Auth Service first)
