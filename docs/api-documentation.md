# API Documentation

This document provides comprehensive API documentation for the Online Bookstore Microservices system, including OpenAPI specifications, request/response examples, and integration guides.

## Table of Contents

1. [API Overview](#api-overview)
2. [Authentication](#authentication)
3. [Common Patterns](#common-patterns)
4. [Auth Service API](#auth-service-api)
5. [User Service API](#user-service-api)
6. [Product Catalog API](#product-catalog-api)
7. [Order Service API](#order-service-api)
8. [Search Service API](#search-service-api)
9. [Payment Service API](#payment-service-api)
10. [Notifications API](#notifications-api)
11. [Error Handling](#error-handling)
12. [Rate Limiting](#rate-limiting)
13. [API Versioning](#api-versioning)

---

## API Overview

### Base URLs

| Environment | Base URL | Description |
|-------------|----------|-------------|
| **Development** | `http://localhost:8080` | Local development |
| **Staging** | `https://api-staging.bookstore.com` | Staging environment |
| **Production** | `https://api.bookstore.com` | Production environment |

### Service Endpoints

| Service | Base Path | Port (Local) | Description |
|---------|-----------|--------------|-------------|
| **API Gateway** | `/` | 8080 | Main entry point |
| **Auth Service** | `/api/v1/auth` | 8081 | Authentication & authorization |
| **User Service** | `/api/v1/users` | 8082 | User management |
| **Product Catalog** | `/api/v1/products` | 8083 | Product inventory |
| **Order Service** | `/api/v1/orders` | 8084 | Order processing |
| **Search Service** | `/api/v1/search` | 8085 | Full-text search |
| **Payment Service** | `/api/v1/payments` | 8086 | Payment processing |
| **Notifications** | `/api/v1/notifications` | 8087 | Notification management |

### Content Types

- **Request Content-Type**: `application/json`
- **Response Content-Type**: `application/json`
- **Character Encoding**: `UTF-8`

### Response Envelope

All API responses follow a consistent envelope structure:

```json
{
  "status": "success|error|warning",
  "data": { /* actual response data */ },
  "meta": {
    "timestamp": "2026-01-18T12:27:00Z",
    "correlationId": "req_abc123def456",
    "version": "1.0.0",
    "requestId": "req_abc123def456"
  },
  "errors": [
    {
      "code": "VALIDATION_ERROR",
      "message": "Field is required",
      "field": "email",
      "details": { /* optional context */ }
    }
  ],
  "pagination": { /* only for list endpoints */ }
}
```

### Pagination

List endpoints support cursor-based pagination:

```json
{
  "status": "success",
  "data": [ /* array of items */ ],
  "meta": { /* standard meta */ },
  "pagination": {
    "totalCount": 1250,
    "pageSize": 20,
    "nextCursor": "eyJpZCI6ICJwcm9kXzIyMiJ9",
    "prevCursor": "eyJpZCI6ICJwcm9kXzAwMSJ9",
    "hasMore": true
  }
}
```

---

## Authentication

### JWT Bearer Token Authentication

All API requests (except authentication endpoints) require a valid JWT token:

```
Authorization: Bearer <jwt_token>
```

### Token Structure

JWT tokens contain the following claims:

```json
{
  "sub": "user_abc123",
  "email": "user@example.com",
  "roles": ["CUSTOMER"],
  "permissions": ["read:products", "write:orders"],
  "iss": "auth-service",
  "aud": "bookstore-api",
  "iat": 1642512000,
  "exp": 1642515600,
  "jti": "token_abc123"
}
```

### Token Expiration

- **Access Token**: 1 hour (3600 seconds)
- **Refresh Token**: 7 days (604800 seconds)

### Obtaining Tokens

#### Login Request

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "rememberMe": false
}
```

#### Login Response

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "status": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": "user_abc123",
      "email": "user@example.com",
      "firstName": "John",
      "lastName": "Doe",
      "roles": ["CUSTOMER"]
    }
  },
  "meta": {
    "timestamp": "2026-01-18T12:27:00Z",
    "correlationId": "req_123456",
    "version": "1.0.0"
  }
}
```

### Refreshing Tokens

```http
POST /api/v1/auth/refresh
Authorization: Bearer <expired_access_token>
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### Using Tokens

```http
GET /api/v1/users/profile
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

## Common Patterns

### Request Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes* | Bearer token (*except auth endpoints) |
| `Content-Type` | Yes | `application/json` |
| `X-Correlation-ID` | No | Request correlation ID |
| `X-API-Key` | No | API key for external integrations |
| `Accept-Language` | No | Language preference (en, es, fr) |

### Query Parameters

#### Filtering
```
GET /api/v1/products?category=books&price_min=10&price_max=50&in_stock=true
```

#### Sorting
```
GET /api/v1/products?sort=price&order=asc
GET /api/v1/products?sort=created_at&order=desc
```

#### Field Selection
```
GET /api/v1/products?fields=id,title,price,inventory
```

### Idempotency

For non-idempotent operations, include an idempotency key:

```http
POST /api/v1/orders
Authorization: Bearer <token>
Content-Type: application/json
Idempotency-Key: ord_abc123def456

{
  "userId": "user_xyz789",
  "items": [
    {
      "productId": "prod_001",
      "quantity": 2
    }
  ]
}
```

### Conditional Requests

```http
GET /api/v1/products/prod_001
Authorization: Bearer <token>
If-None-Match: "etag_value"
```

---

## Auth Service API

### POST /register

Register a new user account.

**Request:**
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe",
  "acceptTerms": true,
  "marketingConsent": false
}
```

**Response (201 Created):**
```json
{
  "status": "success",
  "data": {
    "userId": "user_abc123",
    "email": "user@example.com",
    "emailVerified": false,
    "verificationToken": "email_verify_123",
    "createdAt": "2026-01-18T12:27:00Z"
  },
  "meta": { /* standard meta */ }
}
```

### POST /login

Authenticate user and return tokens.

**Request:**
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "rememberMe": false
}
```

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": "user_abc123",
      "email": "user@example.com",
      "firstName": "John",
      "lastName": "Doe",
      "roles": ["CUSTOMER"]
    }
  },
  "meta": { /* standard meta */ }
}
```

### POST /verify-email

Verify user email address.

**Request:**
```http
POST /api/v1/auth/verify-email
Content-Type: application/json

{
  "token": "email_verify_123"
}
```

### POST /forgot-password

Initiate password reset.

**Request:**
```http
POST /api/v1/auth/forgot-password
Content-Type: application/json

{
  "email": "user@example.com"
}
```

### POST /reset-password

Reset password using reset token.

**Request:**
```http
POST /api/v1/auth/reset-password
Content-Type: application/json

{
  "resetToken": "reset_123",
  "newPassword": "NewSecurePass123!",
  "confirmPassword": "NewSecurePass123!"
}
```

### POST /refresh

Refresh access token.

**Request:**
```http
POST /api/v1/auth/refresh
Authorization: Bearer <expired_token>
Content-Type: application/json

{
  "refreshToken": "refresh_token_here"
}
```

### POST /validate

Validate JWT token.

**Request:**
```http
POST /api/v1/auth/validate
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "valid": true,
    "user": {
      "id": "user_abc123",
      "email": "user@example.com",
      "roles": ["CUSTOMER"],
      "permissions": ["read:products", "write:orders"]
    },
    "expiresAt": "2026-01-18T13:27:00Z"
  },
  "meta": { /* standard meta */ }
}
```

### POST /logout

Logout and invalidate refresh token.

**Request:**
```http
POST /api/v1/auth/logout
Authorization: Bearer <token>
Content-Type: application/json

{
  "refreshToken": "refresh_token_here"
}
```

---

## User Service API

### GET /users/profile

Get current user profile.

**Request:**
```http
GET /api/v1/users/profile
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "status": "success",
  "data": {
    "id": "user_abc123",
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "dateOfBirth": "1990-01-15",
    "phoneNumber": "+1234567890",
    "preferences": {
      "newsletter": true,
      "notifications": {
        "email": true,
        "sms": false,
        "push": true
      }
    },
    "addresses": [
      {
        "id": "addr_123",
        "type": "shipping",
        "firstName": "John",
        "lastName": "Doe",
        "street": "123 Main St",
        "city": "New York",
        "state": "NY",
        "zipCode": "10001",
        "country": "US",
        "isDefault": true
      }
    ],
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-18T12:00:00Z"
  },
  "meta": { /* standard meta */ }
}
```

### PUT /users/profile

Update user profile.

**Request:**
```http
PUT /api/v1/users/profile
Authorization: Bearer <token>
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Smith",
  "phoneNumber": "+1234567890",
  "preferences": {
    "newsletter": false,
    "notifications": {
      "email": true,
      "sms": true
    }
  }
}
```

### POST /users/addresses

Add a new address.

**Request:**
```http
POST /api/v1/users/addresses
Authorization: Bearer <token>
Content-Type: application/json

{
  "type": "shipping",
  "firstName": "John",
  "lastName": "Doe",
  "street": "456 Oak Ave",
  "city": "Los Angeles",
  "state": "CA",
  "zipCode": "90210",
  "country": "US",
  "isDefault": false
}
```

### PUT /users/addresses/{addressId}

Update an address.

**Request:**
```http
PUT /api/v1/users/addresses/addr_123
Authorization: Bearer <token>
Content-Type: application/json

{
  "street": "789 Pine St",
  "city": "Chicago",
  "state": "IL",
  "zipCode": "60601"
}
```

### DELETE /users/addresses/{addressId}

Delete an address.

**Request:**
```http
DELETE /api/v1/users/addresses/addr_123
Authorization: Bearer <token>
```

---

## Product Catalog API

### GET /products

Get products with filtering and pagination.

**Request:**
```http
GET /api/v1/products?category=books&price_min=10&price_max=50&in_stock=true&sort=price&order=asc&page_size=20
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "status": "success",
  "data": [
    {
      "id": "prod_001",
      "title": "Clean Code",
      "author": "Robert C. Martin",
      "isbn": "978-0132350884",
      "description": "A handbook of agile software craftsmanship...",
      "price": {
        "amount": 29.99,
        "currency": "USD"
      },
      "category": "books",
      "tags": ["programming", "software-engineering", "agile"],
      "images": [
        {
          "url": "https://images.bookstore.com/prod_001_main.jpg",
          "alt": "Clean Code book cover",
          "type": "main"
        }
      ],
      "inventory": {
        "available": 150,
        "reserved": 5,
        "status": "in_stock"
      },
      "rating": {
        "average": 4.7,
        "count": 2847
      },
      "createdAt": "2026-01-01T00:00:00Z",
      "updatedAt": "2026-01-18T10:00:00Z"
    }
  ],
  "meta": { /* standard meta */ },
  "pagination": {
    "totalCount": 1250,
    "pageSize": 20,
    "nextCursor": "eyJpZCI6ICJwcm9kXzIyMiJ9",
    "hasMore": true
  }
}
```

### GET /products/{productId}

Get product details.

**Request:**
```http
GET /api/v1/products/prod_001
Authorization: Bearer <token>
```

### GET /categories

Get product categories.

**Request:**
```http
GET /api/v1/categories
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "data": [
    {
      "id": "cat_001",
      "name": "Programming",
      "slug": "programming",
      "description": "Books about software development",
      "parentId": null,
      "subcategories": [
        {
          "id": "cat_002",
          "name": "Java",
          "slug": "java",
          "parentId": "cat_001"
        }
      ]
    }
  ],
  "meta": { /* standard meta */ }
}
```

### GET /products/search

Search products (redirects to Search Service).

**Request:**
```http
GET /api/v1/products/search?q=clean+code&category=programming
Authorization: Bearer <token>
```

---

## Order Service API

### POST /orders

Create a new order.

**Request:**
```http
POST /api/v1/orders
Authorization: Bearer <token>
Content-Type: application/json
Idempotency-Key: ord_abc123

{
  "items": [
    {
      "productId": "prod_001",
      "quantity": 2,
      "price": 29.99
    },
    {
      "productId": "prod_002",
      "quantity": 1,
      "price": 39.99
    }
  ],
  "shippingAddressId": "addr_123",
  "billingAddressId": "addr_123",
  "paymentMethodId": "pm_456"
}
```

**Response (201 Created):**
```json
{
  "status": "success",
  "data": {
    "id": "order_abc123",
    "status": "pending",
    "items": [
      {
        "id": "item_001",
        "productId": "prod_001",
        "productTitle": "Clean Code",
        "quantity": 2,
        "unitPrice": 29.99,
        "totalPrice": 59.98
      }
    ],
    "subtotal": 99.97,
    "tax": 8.00,
    "shipping": 5.99,
    "total": 113.96,
    "currency": "USD",
    "shippingAddress": { /* address object */ },
    "createdAt": "2026-01-18T12:30:00Z",
    "estimatedDelivery": "2026-01-25T00:00:00Z"
  },
  "meta": { /* standard meta */ }
}
```

### GET /orders

Get user's orders.

**Request:**
```http
GET /api/v1/orders?status=completed&page_size=10
Authorization: Bearer <token>
```

### GET /orders/{orderId}

Get order details.

**Request:**
```http
GET /api/v1/orders/order_abc123
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "id": "order_abc123",
    "status": "shipped",
    "items": [ /* order items */ ],
    "shippingAddress": { /* address */ },
    "tracking": {
      "carrier": "UPS",
      "trackingNumber": "1Z999AA1234567890",
      "status": "in_transit",
      "estimatedDelivery": "2026-01-25T00:00:00Z",
      "updates": [
        {
          "timestamp": "2026-01-20T14:30:00Z",
          "status": "shipped",
          "location": "New York, NY",
          "message": "Package has left the facility"
        }
      ]
    },
    "payment": {
      "status": "paid",
      "method": "credit_card",
      "last4": "4242",
      "brand": "visa"
    },
    "createdAt": "2026-01-18T12:30:00Z",
    "updatedAt": "2026-01-20T14:30:00Z"
  },
  "meta": { /* standard meta */ }
}
```

### PATCH /orders/{orderId}/cancel

Cancel an order.

**Request:**
```http
PATCH /api/v1/orders/order_abc123/cancel
Authorization: Bearer <token>
Content-Type: application/json

{
  "reason": "Changed my mind",
  "notes": "Customer requested cancellation"
}
```

### GET /orders/{orderId}/returns

Get return options for an order.

**Request:**
```http
GET /api/v1/orders/order_abc123/returns
Authorization: Bearer <token>
```

### POST /orders/{orderId}/returns

Create a return request.

**Request:**
```http
POST /api/v1/orders/order_abc123/returns
Authorization: Bearer <token>
Content-Type: application/json

{
  "items": [
    {
      "orderItemId": "item_001",
      "quantity": 1,
      "reason": "defective"
    }
  ],
  "returnMethod": "mail_back",
  "notes": "Book pages were stuck together"
}
```

---

## Search Service API

### GET /search

Full-text search across products.

**Request:**
```http
GET /api/v1/search?q=clean+code&category=programming&price_min=10&price_max=100&sort=relevance&order=desc&page_size=20
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "query": "clean code",
    "totalResults": 47,
    "facets": {
      "category": [
        {"value": "programming", "count": 32},
        {"value": "software-engineering", "count": 15}
      ],
      "price_range": [
        {"value": "0-20", "count": 12},
        {"value": "20-50", "count": 25},
        {"value": "50-100", "count": 10}
      ],
      "rating": [
        {"value": "4-5", "count": 35},
        {"value": "3-4", "count": 10},
        {"value": "0-3", "count": 2}
      ]
    },
    "results": [
      {
        "id": "prod_001",
        "title": "Clean Code",
        "author": "Robert C. Martin",
        "price": 29.99,
        "rating": 4.7,
        "category": "programming",
        "highlights": {
          "title": "<em>Clean</em> <em>Code</em>",
          "description": "...writing <em>clean</em>, maintainable <em>code</em>..."
        },
        "score": 0.95
      }
    ]
  },
  "meta": { /* standard meta */ },
  "pagination": {
    "totalCount": 47,
    "pageSize": 20,
    "nextCursor": "eyJzZWFyY2hBZnRlciI6IFsiMC45NSJdfQ==",
    "hasMore": true
  }
}
```

### GET /recommendations

Get personalized recommendations.

**Request:**
```http
GET /api/v1/recommendations?type=similar&product_id=prod_001&limit=10
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "type": "similar_products",
    "productId": "prod_001",
    "recommendations": [
      {
        "id": "prod_002",
        "title": "Clean Architecture",
        "author": "Robert C. Martin",
        "price": 39.99,
        "rating": 4.8,
        "reason": "Same author, similar topic",
        "confidence": 0.92
      }
    ]
  },
  "meta": { /* standard meta */ }
}
```

### GET /autocomplete

Get search suggestions.

**Request:**
```http
GET /api/v1/autocomplete?q=clean&limit=5
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "query": "clean",
    "suggestions": [
      {"text": "clean code", "type": "query"},
      {"text": "clean architecture", "type": "product_title"},
      {"text": "Clean Code", "type": "product_title"}
    ]
  },
  "meta": { /* standard meta */ }
}
```

---

## Payment Service API

### POST /payments

Process a payment.

**Request:**
```http
POST /api/v1/payments
Authorization: Bearer <token>
Content-Type: application/json
Idempotency-Key: pay_abc123

{
  "orderId": "order_abc123",
  "amount": {
    "value": 11396,
    "currency": "USD"
  },
  "paymentMethod": {
    "type": "card",
    "card": {
      "number": "4242424242424242",
      "expMonth": 12,
      "expYear": 2026,
      "cvc": "123",
      "holderName": "John Doe"
    }
  },
  "billingAddress": {
    "firstName": "John",
    "lastName": "Doe",
    "street": "123 Main St",
    "city": "New York",
    "state": "NY",
    "zipCode": "10001",
    "country": "US"
  }
}
```

**Response (201 Created):**
```json
{
  "status": "success",
  "data": {
    "id": "pay_abc123",
    "status": "succeeded",
    "amount": {
      "value": 11396,
      "currency": "USD"
    },
    "paymentMethod": {
      "type": "card",
      "last4": "4242",
      "brand": "visa"
    },
    "processingFee": 42,
    "netAmount": 11354,
    "createdAt": "2026-01-18T12:30:00Z",
    "capturedAt": "2026-01-18T12:30:05Z"
  },
  "meta": { /* standard meta */ }
}
```

### GET /payments/{paymentId}

Get payment details.

**Request:**
```http
GET /api/v1/payments/pay_abc123
Authorization: Bearer <token>
```

### POST /payments/{paymentId}/refund

Refund a payment.

**Request:**
```http
POST /api/v1/payments/pay_abc123/refund
Authorization: Bearer <token>
Content-Type: application/json
Idempotency-Key: ref_abc123

{
  "amount": {
    "value": 2999,
    "currency": "USD"
  },
  "reason": "customer_request",
  "notes": "Customer returned the book"
}
```

### GET /payment-methods

Get user's saved payment methods.

**Request:**
```http
GET /api/v1/payment-methods
Authorization: Bearer <token>
```

### POST /payment-methods

Save a payment method.

**Request:**
```http
POST /api/v1/payment-methods
Authorization: Bearer <token>
Content-Type: application/json

{
  "type": "card",
  "card": {
    "number": "4242424242424242",
    "expMonth": 12,
    "expYear": 2026,
    "cvc": "123",
    "holderName": "John Doe"
  },
  "billingAddress": { /* address */ },
  "isDefault": true
}
```

---

## Notifications API

### GET /notifications

Get user notifications.

**Request:**
```http
GET /api/v1/notifications?status=unread&type=order&page_size=20
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "data": [
    {
      "id": "notif_001",
      "type": "order_shipped",
      "title": "Your order has shipped!",
      "message": "Your order #12345 has been shipped and is on its way.",
      "data": {
        "orderId": "order_abc123",
        "trackingNumber": "1Z999AA1234567890",
        "carrier": "UPS"
      },
      "channels": ["email", "push"],
      "status": "unread",
      "createdAt": "2026-01-20T14:30:00Z",
      "readAt": null
    }
  ],
  "meta": { /* standard meta */ },
  "pagination": { /* pagination */ }
}
```

### PATCH /notifications/{notificationId}/read

Mark notification as read.

**Request:**
```http
PATCH /api/v1/notifications/notif_001/read
Authorization: Bearer <token>
```

### POST /notifications/test

Send a test notification (admin only).

**Request:**
```http
POST /api/v1/notifications/test
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "userId": "user_abc123",
  "channels": ["email", "sms"],
  "title": "Test Notification",
  "message": "This is a test notification"
}
```

### GET /notifications/preferences

Get notification preferences.

**Request:**
```http
GET /api/v1/notifications/preferences
Authorization: Bearer <token>
```

### PUT /notifications/preferences

Update notification preferences.

**Request:**
```http
PUT /api/v1/notifications/preferences
Authorization: Bearer <token>
Content-Type: application/json

{
  "email": {
    "orderUpdates": true,
    "promotions": false,
    "newsletter": true
  },
  "sms": {
    "orderUpdates": true,
    "securityAlerts": true
  },
  "push": {
    "orderUpdates": true,
    "promotions": false
  }
}
```

---

## Error Handling

### HTTP Status Codes

| Status Code | Meaning | Description |
|-------------|---------|-------------|
| **200** | OK | Request successful |
| **201** | Created | Resource created successfully |
| **204** | No Content | Request successful, no content returned |
| **400** | Bad Request | Invalid request data or parameters |
| **401** | Unauthorized | Authentication required or failed |
| **403** | Forbidden | Authentication succeeded but authorization failed |
| **404** | Not Found | Resource not found |
| **409** | Conflict | Resource conflict (duplicate, etc.) |
| **422** | Unprocessable Entity | Validation failed |
| **429** | Too Many Requests | Rate limit exceeded |
| **500** | Internal Server Error | Server error |
| **502** | Bad Gateway | Upstream service error |
| **503** | Service Unavailable | Service temporarily unavailable |
| **504** | Gateway Timeout | Request timeout |

### Error Response Format

```json
{
  "status": "error",
  "data": null,
  "errors": [
    {
      "code": "VALIDATION_ERROR",
      "message": "Email address is required",
      "field": "email",
      "details": {
        "constraint": "not_null",
        "value": null
      }
    },
    {
      "code": "VALIDATION_ERROR",
      "message": "Password must be at least 8 characters",
      "field": "password",
      "details": {
        "constraint": "min_length",
        "value": "Pass1",
        "minLength": 8
      }
    }
  ],
  "meta": {
    "timestamp": "2026-01-18T12:27:00Z",
    "correlationId": "req_123456",
    "version": "1.0.0"
  }
}
```

### Common Error Codes

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Input validation failed |
| `AUTHENTICATION_FAILED` | 401 | Invalid credentials |
| `TOKEN_EXPIRED` | 401 | JWT token expired |
| `TOKEN_INVALID` | 401 | JWT token malformed |
| `INSUFFICIENT_PERMISSIONS` | 403 | User lacks required permissions |
| `RESOURCE_NOT_FOUND` | 404 | Requested resource doesn't exist |
| `RESOURCE_CONFLICT` | 409 | Resource already exists |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily down |
| `EXTERNAL_SERVICE_ERROR` | 502 | Third-party service error |
| `PAYMENT_DECLINED` | 400 | Payment processing failed |
| `INVENTORY_INSUFFICIENT` | 409 | Not enough stock available |

---

## Rate Limiting

### Rate Limits

| Endpoint Pattern | Limit | Window | Scope |
|------------------|-------|--------|-------|
| `POST /api/v1/auth/*` | 5 | 1 minute | IP address |
| `POST /api/v1/orders` | 10 | 1 hour | User |
| `GET /api/v1/search` | 100 | 1 minute | User |
| `POST /api/v1/payments` | 5 | 1 hour | User |
| All other endpoints | 1000 | 1 minute | User |

### Rate Limit Headers

```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1642512000
X-RateLimit-Retry-After: 30
```

### Rate Limit Exceeded Response

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1642512000
Retry-After: 60

{
  "status": "error",
  "errors": [
    {
      "code": "RATE_LIMIT_EXCEEDED",
      "message": "Too many requests. Please try again later.",
      "details": {
        "limit": 5,
        "window": "1 minute",
        "resetAt": "2026-01-18T12:28:00Z"
      }
    }
  ],
  "meta": { /* standard meta */ }
}
```

---

## API Versioning

### Versioning Strategy

API versioning uses URL path versioning:

```
/api/v1/auth/login
/api/v1/users/profile
/api/v2/auth/login  (future version)
```

### Version Compatibility

- **v1**: Current stable version
- Versions are backward compatible within major version
- Breaking changes require new major version
- Deprecation warnings provided 6 months before removal

### Version Headers

```http
Accept-Version: v1
API-Version: v1
```

### Version Response

```json
{
  "status": "success",
  "meta": {
    "version": "1.2.3",
    "apiVersion": "v1"
  }
}
```

---

This API documentation provides comprehensive coverage of all endpoints, request/response formats, authentication, error handling, and integration patterns for the Online Bookstore microservices system. Use this documentation as your primary reference for API integration and development.