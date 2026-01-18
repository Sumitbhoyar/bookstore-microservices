# Online Bookstore Backend - Authentication & Authorization Specification

**Version:** 1.0.0  
**Last Updated:** 2026-01-18  
**Status:** Iteration 3 — Authentication & Authorization  
**Audience:** Backend Engineers, Security Engineers, Cursor IDE Development  
**Prerequisites**: System Overview v1.0.0, Domain Model v1.0.0

---

## Table of Contents

1. [Overview](#overview)
2. [Authentication Mechanisms](#authentication-mechanisms)
3. [JWT Token Specification](#jwt-token-specification)
4. [Authorization Model](#authorization-model)
5. [API Access Control Rules](#api-access-control-rules)
6. [Service-to-Service Authentication](#service-to-service-authentication)
7. [Security Constraints & Invariants](#security-constraints--invariants)
8. [Implementation Patterns](#implementation-patterns)
9. [Error Handling](#error-handling)
10. [Audit & Compliance](#audit--compliance)

---

## Overview

**Authentication** answers: "Who are you?"  
**Authorization** answers: "What can you do?"

This specification defines:
- How users prove identity (OAuth2 via AWS Cognito + local credentials)
- How services verify tokens (JWT signature validation)
- What operations users are allowed (role-based access control)
- How services authenticate to each other (mTLS + signed requests)
- Security guardrails (rate limiting, token expiry, refresh logic)

**Security Model**: Zero trust — every request must be authenticated and authorized.

---

## Authentication Mechanisms

### Mechanism 1: User Login (OAuth2 via AWS Cognito + Local Fallback)

#### Primary Flow: AWS Cognito

**When to use**: Production deployments, federated identity, OAuth2/OIDC integration

**Flow**:
```
1. Client sends credentials to Auth Service
   POST /api/v1/auth/login
   {
     "email": "user@example.com",
     "password": "secure_password_123"
   }

2. Auth Service → AWS Cognito InitiateAuth (AdminUserPasswordAuth flow)
   - Cognito validates credential against user pool
   - On success: Cognito returns:
     {
       "AuthenticationResult": {
         "AccessToken": "eyJkZXRhaWxzIn0.eyJjbGllbn....",
         "IdToken": "eyJkZXRhaWxzIn0.eyJzdWIiOiJ...",
         "RefreshToken": "AYABe....",
         "ExpiresIn": 3600,
         "TokenType": "Bearer"
       }
     }

3. Auth Service → Local Session Store (PostgreSQL)
   INSERT INTO sessions (user_id, jwt_token_id, created_at, expires_at, is_active)
   - Extract user_id from Cognito response
   - Create local session record for tracking

4. Auth Service → Custom JWT Creation (Spring Boot Security)
   Generate internal JWT with:
   {
     "sub": "user_id",
     "email": "user@example.com",
     "roles": ["CUSTOMER"],
     "scopes": ["read:products", "write:orders"],
     "iat": 1705585860,
     "exp": 1705589460,
     "iss": "bookstore.auth.service"
   }
   - Sign with RS256 (private key stored in AWS Secrets Manager)

5. Auth Service → API Gateway → Client
   Response: {
     "userId": "uuid-user-id",
     "token": "eyJhbGciOiJSUzI1NiI...",
     "refreshToken": "refresh_uuid_token",
     "expiresIn": 3600,
     "tokenType": "Bearer"
   }
   Status: 200 OK
```

#### Fallback Flow: Local Authentication (for testing/development)

**When to use**: Local development without AWS Cognito dependency

**Flow**:
```
1. Client sends credentials to Auth Service
   POST /api/v1/auth/login
   {
     "email": "user@example.com",
     "password": "secure_password_123"
   }

2. Auth Service → PostgreSQL (credentials table)
   SELECT password_hash, salt FROM credentials WHERE user_id = (SELECT id FROM users WHERE email = ?)
   → Retrieve stored bcrypt hash

3. Auth Service → Verify Password
   bcrypt.verify(password, password_hash)
   - If password_hash != computed_hash: return 401 Unauthorized
   - If failed_login_attempts >= 5:
     - Lock account: UPDATE credentials SET locked_until = now() + 15 minutes
     - Return 423 Locked (account temporarily locked)
   - Reset failed_login_attempts counter

4. Auth Service → Local Session Store
   INSERT INTO sessions (user_id, jwt_token_id, created_at, expires_at, is_active)

5. Auth Service → Create JWT (same as Cognito flow)
   Generate and sign JWT with internal private key

6. Auth Service → API Gateway → Client
   Same response format as Cognito flow
```

**Security Notes**:
- Passwords hashed with bcrypt (cost factor 12, salted)
- Failed login tracking: locked after 5 failed attempts for 15 minutes
- Never log plaintext passwords
- Constant-time comparison for password verification

---

### Mechanism 2: User Registration

**Endpoint**: `POST /api/v1/auth/register`

**Flow**:
```
1. Client sends registration data
   POST /api/v1/auth/register
   {
     "email": "newuser@example.com",
     "password": "secure_password_123",
     "firstName": "John",
     "lastName": "Doe"
   }

2. Auth Service → Validation
   - Email format: RFC 5322 compliant
   - Email uniqueness: SELECT COUNT(*) FROM users WHERE email = ? → must be 0
   - Password strength: min 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special char
   - If validation fails: return 400 Bad Request with details

3. Auth Service → User Service (REST call)
   POST /api/v1/users
   {
     "email": "newuser@example.com",
     "firstName": "John",
     "lastName": "Doe"
   }
   Response: { "userId": "uuid-user-id" }

4. Auth Service → PostgreSQL (credentials table)
   - Generate random salt: salt = bcrypt.generateSalt(12)
   - Hash password: password_hash = bcrypt.hash(password, salt)
   INSERT INTO credentials (user_id, password_hash, salt, created_at)
   VALUES (?, ?, ?, now())

5. Auth Service → AWS Cognito (if enabled)
   AdminCreateUser with temporary password
   AdminSetUserPassword (permanent password)

6. Auth Service → EventBridge
   Publish: user.registered
   Event: {
     "source": "bookstore.user",
     "detail-type": "UserRegistered",
     "detail": {
       "userId": "uuid-user-id",
       "email": "newuser@example.com",
       "timestamp": "2026-01-18T12:31:00Z"
     }
   }

7. Auth Service → API Gateway → Client
   Response: { "userId": "uuid-user-id", "email": "newuser@example.com" }
   Status: 201 Created
```

---

### Mechanism 3: Token Refresh

**Endpoint**: `POST /api/v1/auth/refresh`

**Flow**:
```
1. Client sends refresh request
   POST /api/v1/auth/refresh
   {
     "refreshToken": "refresh_uuid_token_original"
   }

2. Auth Service → PostgreSQL (sessions table)
   SELECT * FROM sessions 
   WHERE jwt_token_id = (SELECT jwt_token_id FROM sessions WHERE user_id = ? AND is_active = true)
   - Verify refresh_token is valid
   - Verify session not revoked
   - Verify refresh_token not expired (30-day lifetime)

3. Auth Service → PostgreSQL (token_blacklist table)
   SELECT * FROM token_blacklist WHERE jwt_token_id = ?
   - If found: return 401 Unauthorized (token revoked)

4. Auth Service → Create new JWT
   - Same payload structure as original token
   - New exp = now() + 1 hour
   - New iat = now()

5. Auth Service → Generate new refresh token
   - Generate cryptographically secure UUID
   - Store in sessions table

6. Auth Service → API Gateway → Client
   Response: {
     "token": "new_jwt_token",
     "refreshToken": "new_refresh_token",
     "expiresIn": 3600,
     "tokenType": "Bearer"
   }
   Status: 200 OK
```

**Constraints**:
- Refresh token lifetime: 30 days (non-sliding)
- Access token lifetime: 1 hour (non-sliding)
- Each refresh generates new tokens (old ones remain valid until expiry)
- Cannot refresh expired refresh token (must re-authenticate)

---

### Mechanism 4: Logout

**Endpoint**: `POST /api/v1/auth/logout`

**Flow**:
```
1. Client sends logout request
   POST /api/v1/auth/logout
   Headers: Authorization: Bearer <JWT>

2. API Gateway → Auth Service
   - Extract JWT from Authorization header
   - Verify signature before passing to service

3. Auth Service → PostgreSQL (token_blacklist table)
   INSERT INTO token_blacklist (jwt_token_id, user_id, blacklisted_at, expires_at, reason)
   VALUES (?, ?, now(), jwt_exp_timestamp, 'logout')
   - jwt_token_id extracted from JWT payload
   - expires_at = JWT expiry timestamp (automatic cleanup after expiry)

4. Auth Service → PostgreSQL (sessions table)
   UPDATE sessions SET is_active = false, revoked_at = now()
   WHERE jwt_token_id = ? AND user_id = ?

5. Auth Service → API Gateway → Client
   Response: { "message": "Successfully logged out" }
   Status: 200 OK
```

**Invariants**:
- All tokens (access + refresh) blacklisted on logout
- Session marked inactive
- Cannot use token after logout (checked on every API call)

---

## JWT Token Specification

### Token Format

**Standard JWT Structure**: `header.payload.signature`

#### Header
```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "2026-01-key-id-001"
}
```

**Signing Algorithm**: RS256 (RSA with SHA-256)  
**Signature verification**: Public key from `/.well-known/jwks.json` endpoint

#### Payload
```json
{
  "sub": "user-uuid-1234-5678",
  "email": "user@example.com",
  "roles": ["CUSTOMER"],
  "scopes": ["read:products", "read:orders", "write:orders", "write:cart"],
  "iat": 1705585860,
  "exp": 1705589460,
  "nbf": 1705585860,
  "iss": "bookstore.auth.service",
  "aud": ["bookstore.api"],
  "jti": "jwt-unique-id-uuid"
}
```

**Field Definitions**:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `sub` | String | ✅ | Subject (user UUID) — identifies the user |
| `email` | String | ✅ | User email address (for quick lookup) |
| `roles` | String[] | ✅ | List of roles: ADMIN, CUSTOMER, GUEST |
| `scopes` | String[] | ✅ | OAuth2 scopes defining permissions |
| `iat` | Integer | ✅ | Issued at (Unix timestamp) |
| `exp` | Integer | ✅ | Expiry (Unix timestamp) — 1 hour from iat |
| `nbf` | Integer | ✅ | Not before (Unix timestamp) — usually equals iat |
| `iss` | String | ✅ | Issuer — always "bookstore.auth.service" |
| `aud` | String[] | ✅ | Audience — always ["bookstore.api"] |
| `jti` | String | ✅ | JWT ID — unique token identifier for revocation |

**Token Lifetime**:
```
Access Token (JWT):
  - Issued at (iat): T+0
  - Expires at (exp): T+3600 (1 hour)
  - Refresh window: Last 5 minutes of token lifetime
  - After expiry: 401 Unauthorized (request new token)

Refresh Token:
  - Lifetime: 30 days (non-sliding)
  - Cannot be renewed (must re-authenticate after 30 days)
  - Blacklisting on revocation is permanent
```

#### Signature

**Signing Process** (Spring Boot):
```java
// Pseudocode
Key privateKey = loadPrivateKeyFromSecretsManager("bookstore-jwt-private-key");
String signature = Jwts.builder()
  .setHeader(header)
  .setClaims(payload)
  .signWith(privateKey, SignatureAlgorithm.RS256)
  .compact();
```

**Verification Process** (all services):
```java
// Pseudocode
PublicKey publicKey = loadPublicKeyFromJwksEndpoint();
Jws<Claims> jws = Jwts.parserBuilder()
  .setSigningKey(publicKey)
  .build()
  .parseClaimsJws(token);

Claims claims = jws.getBody();
// Check: exp > now(), nbf <= now(), iss == "bookstore.auth.service"
```

---

### Token Validation Rules

**Performed on every API request** (API Gateway + Service):

#### Step 1: Format Validation
```
- Token present in Authorization header: "Bearer <token>"
- Format: exactly 3 dot-separated base64url-encoded strings
- If invalid: return 400 Bad Request
```

#### Step 2: Signature Verification
```
- Decode header to get "kid" (key ID)
- Fetch public key from /.well-known/jwks.json using kid
- Verify signature: HMAC-SHA256(header.payload) == signature
- If invalid: return 401 Unauthorized
```

#### Step 3: Payload Validation
```
- exp > now() (not expired)
- nbf <= now() (not before time satisfied)
- iat <= now() (issued in past)
- iss == "bookstore.auth.service" (correct issuer)
- aud contains "bookstore.api" (correct audience)
- If any check fails: return 401 Unauthorized
```

#### Step 4: Revocation Check
```
- Check PostgreSQL token_blacklist table:
  SELECT * FROM token_blacklist WHERE jwt_token_id = ?
- If found and expires_at > now(): return 401 Unauthorized
- If expires_at <= now(): allow (entry will be cleaned up by background job)
```

#### Step 5: Scope Validation
```
- Extract requested scopes from request context
- Verify all requested scopes in token.scopes array
- If permission denied: return 403 Forbidden
```

**Implementation**: Performed in this order:
1. API Gateway Lambda authorizer (format, signature, basic payload)
2. Service controller/middleware (revocation, scope validation)

---

### Public Key Distribution

**Endpoint**: `GET /.well-known/jwks.json`

**Purpose**: Allow services to verify JWT signatures without contacting Auth Service

**Response Format** (JWKS - JSON Web Key Set):
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "2026-01-key-id-001",
      "n": "xjlCRBqkQRmx4...",
      "e": "AQAB",
      "alg": "RS256"
    },
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "2025-12-key-id-001",
      "n": "ykmtDFBCTNfg...",
      "e": "AQAB",
      "alg": "RS256"
    }
  ]
}
```

**Key Rotation**:
- New key generated quarterly (every 3 months)
- Old key kept for 30 days (for tokens still in flight)
- Kids format: "YYYY-MM-key-id-NNN"
- Latest key always has kid without version suffix

**Caching**:
- Consumers cache JWKS for 24 hours
- Cache invalidation on any service restart
- Services refresh on signature verification failure (cache miss)

---

## Authorization Model

### Role-Based Access Control (RBAC)

**Roles** (mutually exclusive, one per user):

| Role | Description | Typical User | Permissions |
|------|-------------|--------------|-------------|
| **ADMIN** | Full system access | Internal staff | All operations (create/read/update/delete products, view orders, manage users) |
| **CUSTOMER** | Regular user | End customer | Read products, create/read/manage own orders, read own profile, search |
| **GUEST** | Unauthenticated user | No account | Read products, search (limited), cannot order |

**Role Assignment**:
- Default role on registration: CUSTOMER
- Admin role: manually assigned via database (no self-registration)
- Guest role: no JWT token (passed as query parameter or session cookie)

### Scopes (OAuth2)

**Scopes define granular permissions** within roles:

#### Product Scopes
```
read:products        — Read product details, list products
write:products       — Create/update/delete products (admin only)
read:categories      — Read product categories
write:categories     — Create/update categories (admin only)
```

#### Order Scopes
```
read:orders          — Read order history
write:orders         — Create orders
read:own_orders      — Read only own orders
write:own_orders     — Create/modify only own orders
read:all_orders      — Read all orders (admin)
write:order_status   — Update order status (admin/fulfillment staff)
```

#### User Scopes
```
read:profile         — Read user profile
write:profile        — Update own profile
read:all_users       — Read all users (admin)
write:all_users      — Update any user (admin)
write:user_roles     — Change user roles (admin)
```

#### Payment Scopes
```
read:payments        — Read own payment history
write:payments       — Create payment (for own orders)
read:all_payments    — Read all payments (admin/finance)
write:refunds        — Process refunds (admin/finance)
```

#### Admin Scopes
```
admin:all            — All administrative operations
audit:logs           — Read audit logs
manage:system        — System configuration
```

### Scope-to-Permission Mapping

**Product Service Example**:
```
GET  /api/v1/products              → requires: read:products
GET  /api/v1/products/{id}         → requires: read:products
POST /api/v1/products              → requires: write:products (admin)
PUT  /api/v1/products/{id}         → requires: write:products (admin)
DELETE /api/v1/products/{id}       → requires: write:products (admin)
PATCH /api/v1/products/{id}/inventory → requires: write:products (admin)
```

**Order Service Example**:
```
POST /api/v1/orders                → requires: write:orders OR write:own_orders
GET  /api/v1/orders/{id}           → requires: read:orders OR (read:own_orders + owner verification)
GET  /api/v1/orders                → requires: read:all_orders (admin) OR read:own_orders (filters by userId)
PATCH /api/v1/orders/{id}/status   → requires: write:order_status (admin/fulfillment)
```

### Permission Hierarchy

**Role-based defaults**:

| Role | Default Scopes |
|------|-----------------|
| ADMIN | admin:all, read:products, write:products, read:categories, write:categories, read:all_orders, write:order_status, read:all_users, write:refunds, audit:logs, manage:system |
| CUSTOMER | read:products, read:categories, read:orders, write:orders, read:own_orders, write:own_orders, read:payments, write:payments, read:profile, write:profile |
| GUEST | read:products, read:categories (no auth required — passed via API key) |

**Scope override** (advanced):
- Customer can be granted write:products for seller marketplace (future enhancement)
- Seller role (future): subset of admin permissions for managing own products

---

## API Access Control Rules

### Request Validation Pipeline

**Applied to every API request** (in order):

```
1. Format Check
   ├─ Method: GET, POST, PUT, PATCH, DELETE only
   ├─ Path: matches defined routes
   └─ Headers: Content-Type, Authorization (if required)

2. Authentication Check
   ├─ Extract token from Authorization: Bearer <token>
   ├─ Verify format (3 JWT parts)
   ├─ Verify signature
   ├─ Verify payload claims (exp, nbf, iss, aud)
   └─ Check token_blacklist table for revocation

3. Rate Limiting Check
   ├─ Per-user rate limit: 100 req/s
   ├─ Per-IP rate limit: 1000 req/s (global)
   └─ Per-endpoint burst limit (search: 10 req/s)

4. Authorization Check
   ├─ Extract scopes from JWT
   ├─ Verify scopes match endpoint requirements
   └─ Check resource ownership (user can only read own orders)

5. Business Logic Validation
   ├─ Domain-specific rules (inventory checks, order state machine)
   └─ Service returns 403 if business rules violated
```

### Rate Limiting Configuration

**Implemented at API Gateway + service level**:

```
Global Rate Limits:
  - Per IP: 1000 requests/second (hard limit, 429 Too Many Requests)
  - Per user (authenticated): 100 requests/second
  - Per endpoint:
    * Search: 10 req/s (resource-intensive)
    * Login/Register: 5 req/s (prevent brute force)
    * Payments: 2 req/s (prevent accidental duplication)

Burst Allowance:
  - Burst window: 5 seconds
  - Token bucket algorithm: refill at 20% of limit per second
  - Example: 100 req/s limit allows burst of 500 over 5 seconds

Behavior on Rate Limit Exceeded:
  - Response: 429 Too Many Requests
  - Headers:
      X-RateLimit-Limit: 100
      X-RateLimit-Remaining: 0
      X-RateLimit-Reset: 1705589460 (Unix timestamp when limit resets)
      Retry-After: 60 (seconds)
```

### Cross-Origin Resource Sharing (CORS)

**CORS Policy** (API Gateway configuration):

```
Allowed Origins:
  - Development: http://localhost:3000, http://localhost:3001
  - Production: https://bookstore.example.com, https://api.bookstore.example.com

Allowed Methods:
  - GET, POST, PUT, PATCH, DELETE, OPTIONS

Allowed Headers:
  - Content-Type
  - Authorization
  - X-Correlation-ID
  - X-Idempotency-Key

Exposed Headers:
  - X-RateLimit-Limit
  - X-RateLimit-Remaining
  - X-RateLimit-Reset
  - X-Correlation-ID

Max Age:
  - 3600 seconds (1 hour) for preflight caching

Credentials:
  - Allow credentials: true (cookies sent with requests)
```

### Endpoint Access Matrix

**Comprehensive access control rules**:

```
PRODUCT SERVICE
═════════════════════════════════════════════════════════════════

GET  /api/v1/products
  Auth Required: NO (public endpoint)
  Scopes: (none)
  Rate Limit: 10 req/s per IP
  Action: List products with pagination, filters, facets
  Response: [Product], status 200
  Errors: 400 (invalid filter), 429 (rate limit)

GET  /api/v1/products/{productId}
  Auth Required: NO
  Scopes: (none)
  Action: Fetch single product
  Response: Product, status 200
  Errors: 404 (not found)

GET  /api/v1/products/{productId}/inventory
  Auth Required: YES
  Scopes: read:products, write:orders
  Caller: Order Service (service-to-service)
  Action: Check inventory (internal call)
  Response: { quantity_available, reserved }, status 200

POST /api/v1/products
  Auth Required: YES
  Scopes: write:products
  Roles: ADMIN only
  Action: Create product
  Body: { title, isbn, author_id, description, price }
  Response: Product, status 201
  Errors: 400 (validation), 401 (unauthorized), 403 (forbidden)

PUT  /api/v1/products/{productId}
  Auth Required: YES
  Scopes: write:products
  Roles: ADMIN only
  Action: Update entire product
  Body: { title, isbn, description, price, category_id }
  Response: Product, status 200

PATCH /api/v1/products/{productId}
  Auth Required: YES
  Scopes: write:products
  Roles: ADMIN only
  Action: Partial update
  Body: { price } or { description } (any subset)
  Response: Product, status 200

DELETE /api/v1/products/{productId}
  Auth Required: YES
  Scopes: write:products
  Roles: ADMIN only
  Action: Soft delete (mark is_available = false)
  Response: 204 No Content
  Errors: 404 (not found)

PATCH /api/v1/products/{productId}/inventory
  Auth Required: YES
  Scopes: write:products
  Caller: Order Service (service-to-service)
  Action: Decrement inventory (pessimistic)
  Body: { quantityDecrement: 2 }
  Response: { quantity_available }, status 200
  Errors: 409 (insufficient stock), 400 (invalid quantity)


ORDER SERVICE
═════════════════════════════════════════════════════════════════

POST /api/v1/orders
  Auth Required: YES
  Scopes: write:orders OR write:own_orders
  Roles: CUSTOMER only
  Action: Create order
  Body: { shippingAddressId, items: [{ productId, quantity }], idempotencyKey }
  Response: Order, status 201
  Errors: 400 (validation), 401 (unauthorized), 409 (conflict - duplicate order)

GET  /api/v1/orders/{orderId}
  Auth Required: YES
  Scopes: read:orders OR read:own_orders
  Ownership: User can read own order OR admin reads any
  Action: Fetch order details
  Response: Order, status 200
  Errors: 403 (not owner), 404 (not found)

GET  /api/v1/orders
  Auth Required: YES
  Scopes: read:orders OR read:own_orders
  Query Params:
    - If ADMIN with read:all_orders: returns all orders
    - If CUSTOMER with read:own_orders: filters by userId automatically
  Response: [Order], status 200
  Pagination: cursor-based, 50 items default

PATCH /api/v1/orders/{orderId}/status
  Auth Required: YES
  Scopes: write:order_status
  Roles: ADMIN or fulfillment staff only
  Action: Update order status (PENDING → PAID → PROCESSING → SHIPPED)
  Body: { status }
  Response: Order, status 200
  Errors: 400 (invalid status), 409 (invalid state transition)

POST /api/v1/orders/{orderId}/cancel
  Auth Required: YES
  Scopes: write:orders OR write:own_orders
  Ownership: User can cancel own order (within time window) OR admin cancels any
  Action: Cancel order, process refund
  Body: { reason }
  Response: Order (status=CANCELLED), status 200
  Errors: 409 (cannot cancel - already shipped), 403 (not owner)


AUTH SERVICE
═════════════════════════════════════════════════════════════════

POST /api/v1/auth/login
  Auth Required: NO
  Rate Limit: 5 req/s per IP (prevent brute force)
  Action: Authenticate user
  Body: { email, password }
  Response: { userId, token, refreshToken, expiresIn }, status 200
  Errors: 400 (validation), 401 (invalid credentials), 423 (account locked)

POST /api/v1/auth/register
  Auth Required: NO
  Rate Limit: 5 req/s per IP
  Action: Create new user account
  Body: { email, password, firstName, lastName }
  Response: { userId, email }, status 201
  Errors: 400 (validation), 409 (email exists)

POST /api/v1/auth/refresh
  Auth Required: NO
  Body: { refreshToken }
  Action: Issue new access token
  Response: { token, refreshToken, expiresIn }, status 200
  Errors: 401 (invalid refresh token), 410 (refresh token expired)

POST /api/v1/auth/logout
  Auth Required: YES
  Scopes: (none)
  Action: Revoke tokens
  Response: { message: "Successfully logged out" }, status 200

POST /api/v1/auth/validate
  Auth Required: NO
  Header: Authorization: Bearer <token>
  Caller: API Gateway Lambda authorizer
  Action: Validate token (called on every request)
  Response: { sub, email, roles, scopes }, status 200
  Errors: 401 (invalid token)

GET /.well-known/jwks.json
  Auth Required: NO
  Cache-Control: public, max-age=86400
  Action: Get public keys for token verification
  Response: { keys: [{ kty, use, kid, n, e, alg }] }, status 200


SEARCH SERVICE (PUBLIC)
═════════════════════════════════════════════════════════════════

GET  /api/v1/search
  Auth Required: NO
  Rate Limit: 10 req/s per IP
  Query Params:
    - q: search query (required, 1-255 chars)
    - category: optional filter
    - max_price: optional range
    - sort: relevance|price|rating
  Response: { results, facets, total }, status 200

GET  /api/v1/recommendations
  Auth Required: YES (if personalized)
  Auth Required: NO (if trending)
  Scopes: read:products (if auth required)
  Query Params:
    - type: personalized|trending
  Response: [Product], status 200
```

---

## Service-to-Service Authentication

### Architecture

**Service-to-service calls** (Order Service → Product Catalog, etc.) require authentication distinct from user authentication.

**Methods** (in priority order):

#### Method 1: mTLS (Mutual TLS) — Preferred

**When to use**: Critical services (Order ↔ Payment), high security requirement

**Configuration**:
```
1. Each service issued certificate + private key by internal CA
2. Service certificate stored in AWS Secrets Manager
3. Service loads cert at startup
4. Certificate rotation: every 90 days (automatic)

2. Outbound HTTP Client Configuration (Java/Python/TypeScript):
   - Load client cert from Secrets Manager
   - Load CA cert for server verification
   - Enable hostname verification
   - TLS version: 1.2+ only
   - Cipher suites: TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384 only

3. Inbound Server Configuration (ECS task):
   - Load server cert + key
   - Require client cert verification
   - Verify client cert chain
   - Reject if client cert not from internal CA

4. Validation:
   - Check certificate expiry on startup (warn if < 7 days)
   - Reject expired certificates
   - Verify CN/SAN matches service hostname
```

**Java Example** (Spring Boot):
```java
RestTemplate template = new RestTemplateBuilder()
  .setConnectTimeout(Duration.ofSeconds(5))
  .setReadTimeout(Duration.ofSeconds(5))
  .requestFactory(() -> {
    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
    
    // Load client cert
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(
      secretsManagerClient.getSecret("bookstore-order-service-cert"),
      secretsManagerClient.getSecret("bookstore-cert-password").toCharArray()
    );
    
    // Load CA cert
    KeyStore trustStore = KeyStore.getInstance("JKS");
    trustStore.load(
      secretsManagerClient.getSecret("bookstore-ca-cert"),
      secretsManagerClient.getSecret("bookstore-ca-password").toCharArray()
    );
    
    factory.setHttpClient(HttpClients.custom()
      .setSSLContext(createSSLContext(keyStore, trustStore))
      .build());
    return factory;
  })
  .build();
```

#### Method 2: Service Token (JWT with Service Identity)

**When to use**: Lower-security calls, cross-domain communication, public cloud scenarios

**Flow**:
```
1. Service obtains service JWT from Auth Service
   - Once at startup (or on expiry)
   - Cached in memory

2. Service makes HTTP request with service JWT
   GET /api/v1/products/book_001/inventory
   Headers: Authorization: Bearer <service_jwt>

3. Product Service validates:
   - JWT signature (same as user JWT)
   - JWT "sub" claim = service identity (e.g., "order-service")
   - JWT "aud" claim = "bookstore.api"
   - Verify this is a service token (not user token)
     Check: "service_type": "order-service" claim

4. Product Service authorizes:
   - Extract requested operation from request context
   - Verify service has scope for operation
   - Example scopes for Order Service:
     * read:catalog
     * write:inventory (to decrement stock)
     * read:pricing
```

**Service JWT Payload**:
```json
{
  "sub": "order-service",
  "service_type": "order-service",
  "service_version": "1.2.3",
  "roles": ["SERVICE"],
  "scopes": ["read:catalog", "write:inventory", "read:pricing"],
  "iat": 1705585860,
  "exp": 1705589460,
  "nbf": 1705585860,
  "iss": "bookstore.auth.service",
  "aud": ["bookstore.api"]
}
```

**Service Token Endpoint** (Auth Service):

```
POST /api/v1/auth/service-token

Headers:
  X-Service-Id: order-service
  X-Service-Secret: (shared secret from environment)
  
Response:
{
  "token": "eyJhbGciOiJSUzI1NiI...",
  "expiresIn": 3600
}

Note: This endpoint NOT public; called internally by services only
Only authenticated via shared secrets (not OAuth2 flow)
```

#### Method 3: API Key (for non-critical, read-only operations)

**When to use**: Search service → Elasticsearch, Notifications → SES (AWS SDK), etc.

**Configuration**:
```
1. Generate API key per service
   Key format: sk_live_<random_64_chars>
   
2. Store in AWS Secrets Manager:
   bookstore-search-api-key
   bookstore-notifications-api-key
   
3. Service loads on startup
   Validate format, set in environment
   
4. Send with requests:
   Headers: X-API-Key: sk_live_<key>
   OR Query param: ?api_key=sk_live_<key>

5. Receiving service validates:
   - Key format correct
   - Key matches known service
   - Key not revoked
   - API key expiry (optional)
```

**Do NOT use for**:
- ❌ Critical business operations (payments, orders)
- ❌ Authenticated user context
- ❌ Cross-boundary security

---

### Service Dependency Graph with Auth Methods

```
User (Browser)
  └─ HTTPS + JWT Token
     └─ API Gateway (validate JWT)
        └─ Order Service

Order Service (needs catalog data)
  ├─ mTLS (prod: Product Catalog Service)
  ├─ mTLS (prod: Payment Processor Service)
  ├─ mTLS (prod: User Service)
  └─ JWT Service Token (dev: fallback if mTLS unavailable)

Search Service (needs product data)
  ├─ mTLS or Service JWT (Product Catalog Service)
  └─ API Key (Elasticsearch)

Notification Service (needs user email)
  ├─ mTLS or Service JWT (User Service)
  ├─ API Key (AWS SES)
  └─ API Key (AWS SNS)
```

---

## Security Constraints & Invariants

### Constraint 1: No Hardcoded Secrets

**Requirement**: Every credential stored in AWS Secrets Manager, never in code/config files

**Verification**:
```bash
# Pre-commit hook to prevent secrets
grep -r "password" . --include="*.java" --include="*.py" --include="*.ts" \
  | grep -v "SecretManager\|Secrets\|config_secret" \
  && exit 1

# Environment variable validation
if [ -z "$SECRETS_MANAGER_ARN" ]; then
  echo "ERROR: SECRETS_MANAGER_ARN not set"
  exit 1
fi
```

**Secrets Managed**:
- Database passwords (one per service)
- JWT signing keys (private key for RS256)
- API keys (Stripe, AWS SES, AWS SNS)
- SSL/TLS certificates and keys
- OAuth2 client secrets (for Cognito)

**Rotation Policy**:
- Database passwords: every 90 days
- JWT signing keys: every 90 days (keep old key for 30 days)
- API keys: every 180 days
- SSL/TLS certs: every 90 days
- OAuth2 secrets: every 180 days

---

### Constraint 2: Never Store Plaintext Passwords

**Requirement**: All passwords hashed with bcrypt, salted, before storage

**Implementation**:
```
Bcrypt Configuration:
  - Algorithm: bcrypt (not MD5, SHA1, SHA256)
  - Cost factor: 12 (increases with Moore's law every ~2 years)
  - Salt: generated per password (bcrypt handles automatically)
  
Verification Process:
  1. User submits password in HTTPS POST request
  2. Server computes bcrypt(password, stored_hash) in constant time
  3. If match: authentication success
  4. If no match: increment failed_login_attempts counter
  
Failed Login Handling:
  - Attempt 1-4: allow retry
  - Attempt 5: lock account for 15 minutes
  - Attempt 10: lock account for 1 hour + admin alert
  - Reset counter: after successful login
```

---

### Constraint 3: Token Expiry and Refresh

**Requirement**: Short-lived tokens + refresh token mechanism prevents token hijacking

**Invariants**:
```
Access Token:
  - Lifetime: exactly 1 hour (3600 seconds)
  - Non-renewable (must refresh)
  - Revocable via blacklist
  
Refresh Token:
  - Lifetime: exactly 30 days (non-sliding, doesn't extend)
  - Cannot be renewed (must re-authenticate)
  - Single-use? No (can be reused multiple times, RFC 6749 allows)
  
Revocation:
  - Logout = add token to blacklist
  - Blacklist checked on every request
  - Expired entries auto-deleted after token expiry
  
Token Refresh Window:
  - Client can refresh up to 5 minutes before expiry
  - Recommended: client refreshes at 50% of lifetime (30 minutes)
  - If refresh fails: user must re-authenticate (no fallback)
```

---

### Constraint 4: Scope Validation on Every Request

**Requirement**: Authorization enforced at request time, not cached

**Validation Flow**:
```
1. Extract token from request
2. Verify signature + payload validity
3. Extract scopes from token.scopes
4. Determine required scopes for endpoint
5. If (required scopes) ⊄ (token scopes) → return 403 Forbidden

Example:
  Request: PATCH /api/v1/products/book_001
  Required scope: write:products
  Token scopes: [read:products, read:categories]
  Result: 403 (scope write:products not in token scopes)
```

**Scopes NOT cached or pre-evaluated**:
- Always fetched from token payload (immutable during token lifetime)
- If permissions need to change mid-session: must refresh token (not possible; user must re-auth)

---

### Constraint 5: Ownership Verification

**Requirement**: Users can only access/modify their own resources (unless admin)

**Implementation Pattern**:
```java
// Example: GET /api/v1/orders/{orderId}
@GetMapping("/orders/{orderId}")
public ResponseEntity<OrderDto> getOrder(
    @PathVariable String orderId,
    @AuthenticationPrincipal UserPrincipal principal) {
  
  Order order = orderService.findById(orderId);
  
  if (order == null) {
    return ResponseEntity.notFound().build(); // 404
  }
  
  // Ownership check
  if (!principal.getUserId().equals(order.getUserId()) && !principal.isAdmin()) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // 403
  }
  
  return ResponseEntity.ok(orderToDto(order));
}

// Query-level filtering for list operations
@GetMapping("/orders")
public ResponseEntity<List<OrderDto>> listOrders(
    @AuthenticationPrincipal UserPrincipal principal) {
  
  List<Order> orders;
  
  if (principal.isAdmin()) {
    // Admin sees all orders
    orders = orderService.findAll();
  } else {
    // Regular user sees only own orders
    orders = orderService.findByUserId(principal.getUserId());
  }
  
  return ResponseEntity.ok(orders.stream().map(this::orderToDto).toList());
}
```

**Rules**:
- Non-admin users: silently filter query results (don't reveal existence of other resources)
- Admin users: no filtering applied
- Detail view: return 404 (not 403) to avoid leaking existence

---

### Constraint 6: Idempotency for Unsafe Operations

**Requirement**: POST/PUT/PATCH/DELETE must be idempotent using idempotency keys

**Implementation**:
```
1. Client generates UUID: uuid_v4()
2. Client includes in request header or body:
   Headers: X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
   OR
   Body: { "idempotencyKey": "550e8400..." }

3. Server checks for previous request:
   SELECT * FROM request_log 
   WHERE idempotency_key = ? AND user_id = ?
   
4. If found (same user, same operation):
   - Return cached response (status 200)
   - Do NOT re-execute operation
   - Return same response body as original
   
5. If not found:
   - Execute operation
   - Store request + response in request_log
   - Entries expire after 24 hours

6. Idempotency-Key Mismatch:
   - Same operation, different key: process as new request
   - Different operations, same key: process as new request
   - Only matches if (user_id, idempotency_key, method, path) all same
```

**Critical for Payments**:
```
POST /api/v1/payments
Headers: X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Body: { amount: 99.99, orderId: "ord_123" }

Request 1:
  - No previous match
  - Call Stripe API
  - Stripe returns charge_id: ch_12345
  - Store in DynamoDB idempotency_keys table
  - Return: { paymentId, status: "succeeded", charge_id: "ch_12345" }

Request 2 (duplicate, same key):
  - Match found in idempotency_keys
  - Retrieve cached response
  - Return: { paymentId, status: "succeeded", charge_id: "ch_12345" }
  - No second Stripe call (prevents double-charging)
```

---

### Constraint 7: HTTPS Only (TLS 1.2+)

**Requirement**: All HTTP communication encrypted, no plaintext

**Configuration**:
```
API Gateway:
  - Enforce HTTPS redirect (HTTP 301 to HTTPS)
  - TLS minimum version: 1.2
  - Supported cipher suites:
    * TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
    * TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
  - Disable weak ciphers (RC4, DES, MD5)
  - HSTS header: Strict-Transport-Security: max-age=31536000
  
Service-to-Service:
  - TLS 1.2+ enforced
  - Certificate pinning (verify CN/SAN)
  - Hostname verification required
  
JWT Transmission:
  - Always in Authorization header (not query param)
  - Never in cookies or custom headers (unless HTTPS)
  - Header format: Authorization: Bearer <token>
```

---

### Constraint 8: No Token Leakage

**Requirement**: Tokens never logged, stored in plain text, or transmitted insecurely

**Violations (Prohibited)**:
```
❌ Logging token:
   log.info("User authenticated with token: " + token)

❌ Storing in config file:
   jwt.token=eyJhbGciOiJSUzI1NiI...

❌ Passing in query parameter:
   /api/v1/orders?token=eyJhbGciOiJSUzI1NiI...

❌ Storing in database (plaintext):
   INSERT INTO audit_log (token) VALUES (?)

❌ Passing in HTTP instead of HTTPS:
   GET http://api.bookstore.example.com/api/v1/products

✅ Correct Usage:
   Headers: Authorization: Bearer eyJhbGciOiJSUzI1NiI...
   Log only: user_id, timestamp, operation (never token)
   HTTPS only
```

**Logging Best Practices**:
```
✅ Log this:
{
  "timestamp": "2026-01-18T12:31:00Z",
  "user_id": "user_uuid_123",
  "event": "auth_success",
  "ip_address": "203.0.113.42",
  "operation": "POST /api/v1/orders"
}

❌ Never log this:
{
  "timestamp": "...",
  "user_id": "...",
  "token": "eyJhbGciOiJSUzI1NiI...",  // NO
  "password": "secure_pass_123"       // NO
}
```

---

### Constraint 9: Rate Limiting Prevents Brute Force and DoS

**Rate Limits** (enforced at API Gateway):

```
Per-User Limits:
  - Login endpoint: 5 requests/sec → 423 Locked (after 5 failed in 60 sec)
  - Register endpoint: 5 requests/sec
  - Search endpoint: 10 requests/sec
  - Payment endpoint: 2 requests/sec
  - General endpoint: 100 requests/sec

Per-IP Limits (global):
  - 1000 requests/sec → 429 Too Many Requests
  - Burst: 5-second window allows 5000 requests

Behavior:
  - First hit: allow, decrement token bucket
  - Tokens refill over time (leaky bucket algorithm)
  - When bucket empty: return 429 status
  - Headers: X-RateLimit-Remaining, X-RateLimit-Reset
```

---

### Constraint 10: Admin Access Logged and Audited

**Requirement**: All admin operations logged with user identity and timestamp

**Audit Log Table Schema**:
```sql
CREATE TABLE auth_audit_log (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  admin_user_id UUID NOT NULL (who performed action),
  event STRING NOT NULL (login_success, login_failure, token_refresh, logout, admin_action),
  resource_type STRING (user, product, order, payment),
  resource_id UUID,
  operation STRING (create, read, update, delete),
  old_value JSONB (what changed from),
  new_value JSONB (what changed to),
  ip_address STRING,
  user_agent STRING,
  status STRING (success, failure),
  error_message STRING (if failure),
  timestamp TIMESTAMP NOT NULL,
  
  INDEX (user_id, timestamp),
  INDEX (admin_user_id, timestamp),
  INDEX (resource_type, resource_id, timestamp)
);
```

**Audited Events**:
```
User Domain:
  - user.created (registration)
  - user.profile_updated
  - user.role_changed (by admin)
  - user.deleted (soft delete)

Product Domain:
  - product.created (by admin)
  - product.updated (by admin)
  - product.price_changed (by admin)
  - product.deleted (by admin)

Order Domain:
  - order.created
  - order.status_changed (by admin/fulfillment)
  - order.cancelled (by user or admin)
  - order.refunded (by admin)

Payment Domain:
  - payment.attempted
  - payment.succeeded
  - payment.failed
  - refund.processed (by admin)

Auth Domain:
  - login_success
  - login_failure (with reason: invalid_credentials, account_locked)
  - token_refresh
  - token_revoked (logout)
  - token_blacklisted (security incident)
```

**Retention Policy**:
- Retain: minimum 7 years (regulatory/PCI-DSS)
- Archive: move to cold storage after 1 year
- Deletion: after 7 years (GDPR/compliance)

---

## Implementation Patterns

### Pattern 1: JWT Validation in Spring Boot (Java)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
  
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      List<String> roles = jwt.getClaimAsStringList("roles");
      List<String> scopes = jwt.getClaimAsStringList("scopes");
      
      List<GrantedAuthority> authorities = new ArrayList<>();
      roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
      scopes.forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
      
      return authorities;
    });
    return converter;
  }
  
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(authorize -> authorize
        .antMatchers("POST", "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
        .antMatchers("GET", "/api/v1/products", "/api/v1/products/**").permitAll()
        .antMatchers("GET", "/api/v1/search").permitAll()
        .antMatchers("GET", "/.well-known/jwks.json").permitAll()
        .antMatchers("POST", "/api/v1/orders").hasAuthority("SCOPE_write:orders")
        .antMatchers("GET", "/api/v1/orders/**").hasAuthority("SCOPE_read:orders")
        .antMatchers("POST", "/api/v1/products").hasAuthority("SCOPE_write:products")
        .anyRequest().authenticated()
      )
      .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
      )
      .exceptionHandling(handling -> handling
        .authenticationEntryPoint(new JwtAuthenticationEntryPoint())
        .accessDeniedHandler(new JwtAccessDeniedHandler())
      );
    
    return http.build();
  }
}

@RestControllerAdvice
public class JwtExceptionHandler {
  
  @ExceptionHandler(JwtException.class)
  public ResponseEntity<ErrorResponse> handleJwtException(JwtException e) {
    return ResponseEntity.status(401).body(new ErrorResponse(
      "UNAUTHORIZED",
      "Invalid or expired token: " + e.getMessage()
    ));
  }
  
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
    return ResponseEntity.status(403).body(new ErrorResponse(
      "FORBIDDEN",
      "Insufficient permissions"
    ));
  }
}
```

### Pattern 2: JWT Validation in Python (FastAPI)

```python
from fastapi import FastAPI, Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthCredentials
from pydantic import BaseModel
import jwt
from typing import List, Optional
import httpx

app = FastAPI()
security = HTTPBearer()

class TokenPayload(BaseModel):
    sub: str
    email: str
    roles: List[str]
    scopes: List[str]
    exp: int
    iat: int
    nbf: int
    iss: str
    aud: List[str]
    jti: str

async def get_jwks():
    """Fetch JWKS from Auth Service"""
    async with httpx.AsyncClient() as client:
        response = await client.get("https://auth-service/.well-known/jwks.json")
        return response.json()

async def verify_token(credentials: HTTPAuthCredentials = Depends(security)) -> TokenPayload:
    token = credentials.credentials
    
    try:
        # Fetch JWKS
        jwks = await get_jwks()
        
        # Decode header to get kid
        unverified_header = jwt.get_unverified_header(token)
        kid = unverified_header.get("kid")
        
        # Find matching key
        rsa_key = None
        for key in jwks["keys"]:
            if key["kid"] == kid:
                rsa_key = jwt.algorithms.RSAAlgorithm.from_jwk(json.dumps(key))
                break
        
        if not rsa_key:
            raise HTTPException(status_code=401, detail="Invalid token key")
        
        # Verify and decode
        payload = jwt.decode(
            token,
            rsa_key,
            algorithms=["RS256"],
            audience="bookstore.api",
            issuer="bookstore.auth.service"
        )
        
        # Check blacklist
        blacklist_entry = db.query(TokenBlacklist).filter(
            TokenBlacklist.jwt_token_id == payload["jti"]
        ).first()
        
        if blacklist_entry:
            raise HTTPException(status_code=401, detail="Token revoked")
        
        return TokenPayload(**payload)
        
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token expired")
    except jwt.InvalidSignatureError:
        raise HTTPException(status_code=401, detail="Invalid token signature")
    except Exception as e:
        raise HTTPException(status_code=401, detail=f"Token validation failed: {str(e)}")

@app.get("/api/v1/products")
async def list_products(token: Optional[TokenPayload] = Depends(verify_token)):
    # Public endpoint (token optional)
    return {"products": []}

@app.post("/api/v1/orders")
async def create_order(
    order_data: dict,
    token: TokenPayload = Depends(verify_token)
):
    # Protected endpoint
    if "write:orders" not in token.scopes:
        raise HTTPException(status_code=403, detail="Insufficient permissions")
    
    if token.roles != ["CUSTOMER"]:
        raise HTTPException(status_code=403, detail="Only customers can create orders")
    
    # Create order (token.sub = user_id)
    return {"orderId": "ord_123", "userId": token.sub}
```

### Pattern 3: JWT Validation in TypeScript (NestJS)

```typescript
import { Injectable } from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import { Strategy, ExtractJwt } from 'passport-jwt';
import { PassportStrategy } from '@nestjs/passport';
import * as axios from 'axios';

interface TokenPayload {
  sub: string;
  email: string;
  roles: string[];
  scopes: string[];
  exp: number;
  iat: number;
  nbf: number;
  iss: string;
  aud: string[];
  jti: string;
}

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  constructor() {
    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      ignoreExpiration: false,
      algorithms: ['RS256'],
      issuer: 'bookstore.auth.service',
      audience: 'bookstore.api',
      secretOrKeyProvider: async (request, rawJwtToken, done) => {
        try {
          // Fetch JWKS
          const response = await axios.default.get(
            'https://auth-service/.well-known/jwks.json'
          );
          const jwks = response.data;
          
          // Get kid from token header
          const decodedToken = JSON.parse(
            Buffer.from(rawJwtToken.split('.')[0], 'base64').toString()
          );
          const kid = decodedToken.kid;
          
          // Find matching key
          const key = jwks.keys.find(k => k.kid === kid);
          if (!key) {
            return done(new Error('Key not found'));
          }
          
          // Convert JWKS to PEM
          const publicKey = await this.jwksToPem(key);
          done(null, publicKey);
        } catch (error) {
          done(error);
        }
      },
    });
  }
  
  private async jwksToPem(jwk: any): Promise<string> {
    // Implementation to convert JWK to PEM format
    // Using jose library: npm install jose
  }
  
  async validate(payload: TokenPayload): Promise<any> {
    // Check token blacklist
    const blacklistEntry = await this.tokenBlacklistService.findOne(payload.jti);
    if (blacklistEntry) {
      throw new UnauthorizedException('Token revoked');
    }
    
    return {
      userId: payload.sub,
      email: payload.email,
      roles: payload.roles,
      scopes: payload.scopes,
    };
  }
}

@Injectable()
export class JwtAuthGuard extends AuthGuard('jwt') {
  handleRequest(err, user, info) {
    if (err || !user) {
      throw err || new UnauthorizedException(info?.message);
    }
    return user;
  }
}

@Controller('api/v1')
export class OrderController {
  @Post('orders')
  @UseGuards(JwtAuthGuard)
  async createOrder(
    @Body() orderData: CreateOrderDto,
    @Request() req
  ) {
    const user = req.user;
    
    // Check scopes
    if (!user.scopes.includes('write:orders')) {
      throw new ForbiddenException('Insufficient permissions');
    }
    
    // Check role
    if (!user.roles.includes('CUSTOMER')) {
      throw new ForbiddenException('Only customers can create orders');
    }
    
    return this.orderService.create(user.userId, orderData);
  }
  
  @Get('orders/:orderId')
  @UseGuards(JwtAuthGuard)
  async getOrder(
    @Param('orderId') orderId: string,
    @Request() req
  ) {
    const user = req.user;
    
    // Check scopes
    if (!user.scopes.includes('read:orders')) {
      throw new ForbiddenException('Insufficient permissions');
    }
    
    const order = await this.orderService.findById(orderId);
    
    // Ownership check
    if (order.userId !== user.userId && !user.roles.includes('ADMIN')) {
      throw new ForbiddenException('Not authorized to view this order');
    }
    
    return order;
  }
}
```

---

## Error Handling

### Standard Error Response Format

**All endpoints return consistent error responses**:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable description",
    "details": {
      "field": "error about this field"
    },
    "timestamp": "2026-01-18T12:31:00Z",
    "path": "/api/v1/orders",
    "traceId": "correlation-id-uuid"
  }
}
```

### Common Authentication Errors

| HTTP Status | Error Code | Reason | Resolution |
|-------------|-----------|--------|-----------|
| 400 | INVALID_REQUEST | Malformed request (missing Authorization header) | Add Authorization: Bearer <token> |
| 400 | INVALID_TOKEN_FORMAT | Token not in "Bearer <token>" format | Use correct format |
| 401 | UNAUTHORIZED | Token missing/invalid/expired | Provide valid token or refresh |
| 401 | INVALID_SIGNATURE | Token signature verification failed | Token corrupted or signed with wrong key |
| 401 | TOKEN_EXPIRED | Token exp claim is past current time | Call /auth/refresh with refreshToken |
| 401 | TOKEN_REVOKED | Token in blacklist table | User logged out or token revoked |
| 401 | INVALID_CREDENTIALS | Email/password incorrect during login | Verify credentials |
| 401 | INVALID_REFRESH_TOKEN | Refresh token invalid or expired | Re-authenticate (POST /auth/login) |
| 403 | INSUFFICIENT_PERMISSIONS | Token scopes don't match endpoint requirement | Use account with required scopes |
| 403 | RESOURCE_FORBIDDEN | User doesn't own resource (e.g., reading other's order) | Only access own resources |
| 423 | ACCOUNT_LOCKED | Account locked after 5 failed login attempts | Try again after 15 minutes |
| 429 | RATE_LIMIT_EXCEEDED | Too many requests from user/IP | Wait X seconds (see Retry-After header) |

### Rate Limit Headers

**Returned on every response**:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 87
X-RateLimit-Reset: 1705589460
```

**When limit exceeded**:
```
HTTP/1.1 429 Too Many Requests
Retry-After: 60
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1705589520

{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests. Please retry after 60 seconds.",
    "timestamp": "2026-01-18T12:31:00Z"
  }
}
```

---

## Audit & Compliance

### Audit Trail Requirements

**Every authentication/authorization event logged**:

```
Fields to capture:
  - timestamp: when event occurred
  - user_id: who performed action
  - admin_id: if action by admin (who authorized it)
  - event_type: login_success, login_failure, logout, token_refresh, etc.
  - resource_type: user, product, order, payment
  - resource_id: UUID of affected resource
  - operation: create, read, update, delete
  - old_value: what changed from (for updates)
  - new_value: what changed to (for updates)
  - ip_address: source IP of request
  - user_agent: browser/client making request
  - status: success or failure
  - error_message: why operation failed
  
Never capture:
  - plaintext passwords
  - full credit card numbers
  - API keys or secrets
```

### PCI-DSS Compliance

**Bookstore processes payments; must comply with PCI Data Security Standard**:

```
PCI Requirement 1: Network security
  ✅ API Gateway in public subnet, services in private subnet
  ✅ All inter-service traffic encrypted (mTLS)
  ✅ Database only accessible from service VPC

PCI Requirement 2: Secure defaults
  ✅ No hardcoded passwords/API keys
  ✅ Secrets rotated regularly
  ✅ Default deny policy on security groups

PCI Requirement 3: Data protection
  ✅ Never store full PAN (card number) — use Stripe tokens only
  ✅ Encrypt card data in transit (TLS 1.2+)
  ✅ Encrypt sensitive data at rest (AES-256)

PCI Requirement 6: Secure development
  ✅ Code reviewed before deployment
  ✅ SAST tools scan code for vulnerabilities
  ✅ Secrets scanning on commits

PCI Requirement 8: Access control
  ✅ JWT-based authentication for all APIs
  ✅ Multi-factor authentication for admin access (future)
  ✅ Access logging for all admin operations

PCI Requirement 10: Logging
  ✅ All authentication attempts logged
  ✅ All data access by admin logged
  ✅ Log retention: 7 years
  ✅ Logs encrypted and tamper-proof
```

### GDPR Compliance (Right to Erasure)

```
User Deletion Process:
  1. User requests account deletion via GDPR form
  2. Auth Service verifies user identity
  3. Soft delete user: UPDATE users SET is_active = false
  4. Cascade soft delete:
     - Addresses: soft delete
     - Payment records: anonymize (remove full name, keep last 4 of card)
     - Order history: anonymize (remove personal details, keep order facts)
     - Audit logs: kept for 7 years (regulatory requirement)
  5. Schedule hard delete after 30-day grace period
  6. Confirm deletion to user via email

What NOT to delete:
  - Audit logs (keep 7 years)
  - Financial records (keep 7 years)
  - Fraud indicators (keep indefinitely)
  - Payment settlement records (keep 7 years)
```

---

**Document Version**: 1.0.0  
**Status**: Iteration 3 — Complete  
**Next**: API Gateway & Rate Limiting Specification
