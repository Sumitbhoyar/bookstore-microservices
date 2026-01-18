# End-to-End Flow Specification

**Version:** 1.0.0  
**Last Updated:** 2026-01-18  
**Status:** Iteration 5 — Order, Search & Event Flow  
**Audience:** Backend Engineers, System Architects, Cursor IDE Development  
**Prerequisite**: Service Design & API Contracts Specification v1.0.0

---

## Table of Contents

1. [Overview](#overview)
2. [Order Placement Flow (Orchestrated Saga)](#order-placement-flow-orchestrated-saga)
3. [Search & Recommendation Flow](#search--recommendation-flow)
4. [Event Publishing & Consumption](#event-publishing--consumption)
5. [Failure Handling & Compensation](#failure-handling--compensation)
6. [Idempotency Rules](#idempotency-rules)
7. [Transaction Boundaries](#transaction-boundaries)
8. [Consistency Model](#consistency-model)
9. [Mental Model Reference](#mental-model-reference)

---

## Overview

This specification defines end-to-end distributed flows for the Online Bookstore backend using:

- **Choreography-based events** for asynchronous updates (cross-domain notifications)
- **Orchestrated saga patterns** for order workflows (coordinated multi-step transactions)
- **Eventual consistency** across services with strong consistency within bounded contexts
- **Idempotency guarantees** for safe retries and duplicate prevention
- **Clear transaction boundaries** for atomic operations and rollback

### Key Principles

✅ **Each service owns its transaction** — No distributed 2-phase commit; instead, local transactions + compensation  
✅ **Events are the source of truth** — State changes published as immutable facts  
✅ **Idempotency by design** — All operations safe to retry with same request ID  
✅ **Eventual consistency** — Temporary inconsistencies are acceptable; final state guaranteed correct  
✅ **Compensation always available** — Failure reversals planned for each step  
✅ **Clear ownership** — One service owns each domain; others consume events

---

## Order Placement Flow (Orchestrated Saga)

### High-Level Summary

```
User initiates checkout
    ↓
Order Service creates order (PENDING)
    ↓
Payment Service charges card (async call, 5s timeout)
    ├─ Success → Order becomes PAID
    ├─ Decline → Order cancelled, inventory released, refund triggered
    └─ Timeout → Retry or manual review
    ↓
Notifications Service sends confirmation (async event)
    ↓
Search Service updates interaction matrix (async event)
    ↓
Fulfillment team ships order
    ↓
Order becomes DELIVERED
```

### Detailed Step-by-Step Flow

#### PHASE 1: Request Validation & Order Creation

**Step 1.1: Client sends POST /api/v1/orders**

```
Request:
  POST https://api.bookstore.example.com/api/v1/orders
  Authorization: Bearer {access_token}
  X-Idempotency-Key: req_20260118_user_abc123_001
  Content-Type: application/json
  
  {
    "shipping_address_id": "addr_001",
    "items": [
      { "product_id": "prod_001", "quantity": 2 },
      { "product_id": "prod_002", "quantity": 1 }
    ],
    "promo_code": "SAVE10"
  }

Timing: T+0ms
Service: API Gateway
```

**Transaction Boundary 1: Idempotency Check**

```
API Gateway / Order Service receives request
  ├─ Extract X-Idempotency-Key: req_20260118_user_abc123_001
  ├─ Query DynamoDB: idempotency_keys table
  ├─ If exists:
  │   └─ Return cached response (same order_id)
  └─ If not exists:
      └─ Continue to validation
      
Timing: T+5ms
Database: DynamoDB (1 read)
Consistency: Strong (local transaction)
```

**Step 1.2: Validate Request & Fetch Dependencies**

```
Order Service validates:
  1. User exists and is active
  2. Shipping address exists and belongs to user
  3. All product IDs valid
  4. Items not empty (1-100 range)
  
  Call User Service (HTTP):
    GET https://user-service.bookstore.local:8443/api/v1/users/{userId}/addresses/{addressId}
    Authorization: Bearer {service_jwt}
    
    Response: { address, tax_rate, shipping_cost }
    
  Timing: T+15ms
  Timeout: 5s
  Retry: No (fail-fast)
```

**Step 1.3: Check Inventory Availability**

```
Order Service calls Catalog Service (HTTP):
  For each item in order:
    GET https://catalog-service.bookstore.local:8443/api/v1/products/{productId}/inventory
    Authorization: Bearer {service_jwt}
    
    Response: { quantity_available, quantity_reserved, price }
    
  Validate:
    FOR EACH item:
      IF item.quantity > inventory.quantity_available:
        THROW InsufficientInventoryException
        
Timing: T+25ms (3 items × 5ms per call + network latency)
Timeout: 5s per call
Retry: No (fail-fast)
Consistency: Eventual (inventory state may change between check and reserve)
```

**Transaction Boundary 2: Order Creation & Inventory Reservation**

```
Order Service (PostgreSQL, ACID):
  BEGIN TRANSACTION
  
  1. INSERT INTO orders (
       order_id, user_id, status, total_amount, 
       shipping_address_snapshot, created_at
     )
     VALUES (ord_20260118_abc123, user_abc123, 'PENDING', 154.97, {...}, NOW())
     
  2. FOR EACH item:
       INSERT INTO order_items (
         order_item_id, order_id, product_id, 
         product_title_snapshot, quantity, unit_price, total_price
       )
       VALUES (oi_001, ord_20260118_abc123, prod_001, "Effective Java", 2, 49.99, 99.98)
       
  3. Cache for idempotency:
       INSERT INTO idempotency_keys (
         idempotency_key, response_json, created_at, expires_at
       )
       VALUES (req_20260118_user_abc123_001, {...}, NOW(), NOW() + 24h)
       
  COMMIT
  
  Timing: T+30ms
  Database: PostgreSQL (orders schema)
  Consistency: STRONG (ACID transaction)
  Status: Order now PENDING

Response to Order Service internal code:
  {
    "order_id": "ord_20260118_abc123",
    "status": "pending",
    "items": [...],
    "total_amount": 154.97,
    "subtotal": 144.97,
    "tax": 15.00,
    "shipping_cost": 10.00,
    "discount": -15.00  (SAVE10 promo)
  }
```

**Step 1.4: Reserve Inventory (Pessimistic Locking)**

```
Order Service calls Catalog Service (HTTP):
  FOR EACH item in order:
    PATCH https://catalog-service.bookstore.local:8443/api/v1/products/{productId}/inventory
    Authorization: Bearer {service_jwt}
    Content-Type: application/json
    
    {
      "quantity_decrement": 2,
      "reason": "order_created",
      "order_id": "ord_20260118_abc123"
    }
    
  Response: {
    "quantity_available": 43,
    "quantity_reserved": 7,
    "status": "success"
  }
  
  OR on failure: {
    "status": "error",
    "code": "INSUFFICIENT_INVENTORY",
    "message": "Only 2 available, requested 3"
  }
  
  Timeout: 5s
  Retry: Automatic (3 exponential backoff: 100ms, 200ms, 400ms)
  
  Transaction Boundary 3: Catalog Service (PostgreSQL, ACID)
    BEGIN TRANSACTION (serializable isolation)
    1. SELECT quantity_available FROM inventory WHERE product_id = prod_001 FOR UPDATE
    2. IF quantity_available >= quantity_decrement:
         UPDATE inventory SET quantity_available = quantity_available - 2
         COMMIT
         Success ✓
       ELSE:
         ROLLBACK
         Error: Insufficient inventory
    END TRANSACTION
    
  Consistency: STRONG within Catalog Service
  Timing: T+40ms (per item, ~120ms total for 3 items)
```

---

#### PHASE 2: Payment Processing (Synchronous, Critical Path)

**Step 2.1: Call Payment Service**

```
Order Service calls Payment Service (HTTP, synchronous):
  POST https://payment-service.bookstore.local:8443/api/v1/payments
  Authorization: Bearer {service_jwt}
  Idempotency-Key: req_20260118_user_abc123_001  (← SAME KEY)
  X-Correlation-ID: corr_20260118_abc123
  Content-Type: application/json
  
  {
    "order_id": "ord_20260118_abc123",
    "amount": 15497,  (in cents)
    "currency": "USD",
    "payment_method": "stripe_pm_1234567890",
    "metadata": {
      "user_id": "user_abc123",
      "order_id": "ord_20260118_abc123"
    }
  }
  
  Timeout: 5 seconds
  Retries: 1 (on timeout or 5xx errors)
  
Timing: T+140ms (after inventory checks)
```

**Transaction Boundary 4: Payment Service (DynamoDB + Stripe API)**

```
Payment Service receives request:
  
  1. Check idempotency cache (DynamoDB):
     Query idempotency_keys table with key=req_20260118_user_abc123_001
     If found AND response_status != "processing":
       Return cached response immediately
       Timing: ~5ms
     Else:
       Continue to step 2
       
  2. Call Stripe API (external):
     POST https://api.stripe.com/v1/charges
     Authorization: Bearer stripe_sk_...
     
     {
       "amount": 15497,
       "currency": "usd",
       "payment_method": "stripe_pm_1234567890",
       "idempotency_key": "req_20260118_user_abc123_001",  ← Stripe also checks
       "metadata": { "order_id": "ord_20260118_abc123" }
     }
     
     Timeout: 5s
     Retry: No (Stripe call only once)
     
  3. Stripe responses (3 scenarios):
  
     SCENARIO A: Success (status 200)
       Response: {
         "charge_id": "ch_1234567890",
         "amount": 15497,
         "currency": "usd",
         "status": "succeeded",
         "payment_method": "card_visa_4242"
       }
       
     SCENARIO B: Card Declined (status 402)
       Response: {
         "error": {
           "code": "card_declined",
           "decline_code": "insufficient_funds",
           "message": "Your card has insufficient funds"
         }
       }
       Action: → PHASE 3A (Compensation)
       
     SCENARIO C: Network Timeout (no response after 5s)
       Action: → PHASE 3B (Retry or Compensate)
       
  4. Store payment locally (DynamoDB):
     PUT payments table:
       {
         "payment_id": "pay_stripe_ch_1234567890",
         "order_id": "ord_20260118_abc123",
         "amount": 15497,
         "status": "succeeded",
         "stripe_charge_id": "ch_1234567890",
         "created_at": "2026-01-18T12:27:30Z"
       }
       
     Timing: ~10ms
     
  5. Cache response for idempotency:
     PUT idempotency_keys table:
       {
         "idempotency_key": "req_20260118_user_abc123_001",
         "response_json": {...},
         "response_status": "succeeded",
         "expires_at": NOW() + 24h
       }
       
     Timing: ~5ms
     
  Total Payment Service transaction: T+160ms
  Consistency: Strong (local DynamoDB + external Stripe)
```

---

#### PHASE 3: Order Confirmation & Event Publishing

**Scenario A: Payment Succeeded**

```
Order Service receives payment success response:
  
  Transaction Boundary 5: Update Order Status (PostgreSQL, ACID)
    BEGIN TRANSACTION
    
    UPDATE orders 
    SET status = 'PAID', 
        payment_id = 'pay_stripe_ch_1234567890',
        paid_at = NOW()
    WHERE order_id = 'ord_20260118_abc123'
    AND status = 'PENDING'
    
    IF rows_affected == 0:
      THROW OptimisticLockException()
      ROLLBACK
    ELSE:
      COMMIT
      
    Timing: ~5ms
    Consistency: STRONG
    
  2. Publish event to EventBridge (AWS SDK, SigV4 signed):
     
     PUT-EVENTS call:
       {
         "Entries": [
           {
             "Source": "bookstore.order",
             "DetailType": "OrderCreated",
             "Detail": {
               "order_id": "ord_20260118_abc123",
               "user_id": "user_abc123",
               "items": [...],
               "total_amount": 154.97,
               "status": "paid",
               "timestamp": "2026-01-18T12:27:35Z"
             }
           }
         ]
       }
       
     Note: This is a separate call AFTER order status update
     
     Timing: ~20ms (asynchronous from Order Service perspective)
     Consistency: EVENTUAL (published to event bus)
     
  3. Return success response to client:
  
     HTTP 201 Created
     Location: /api/v1/orders/ord_20260118_abc123
     
     {
       "status": "success",
       "data": {
         "order_id": "ord_20260118_abc123",
         "user_id": "user_abc123",
         "status": "paid",
         "items": [...],
         "total_amount": 154.97,
         "created_at": "2026-01-18T12:27:00Z",
         "paid_at": "2026-01-18T12:27:35Z",
         "estimated_delivery": "2026-01-25T00:00:00Z"
       }
     }
     
  Total time to client: ~T+180ms
  Client-perceived success: Order PAID
```

**Event: OrderCreated (Published to EventBridge)**

```
EventBridge routes to 3 subscribers:

ROUTE 1: Notifications Service
  Lambda triggered: Send order confirmation email
  
  Queue job in Bull (Redis):
    {
      "notification_id": "notif_20260118_xyz789",
      "user_id": "user_abc123",
      "template_id": "order_confirmation",
      "variables": {
        "order_id": "ord_20260118_abc123",
        "total_amount": "154.97 USD",
        "estimated_delivery": "Jan 25, 2026"
      },
      "channels": ["email", "sms"]
    }
    
  Email sent asynchronously:
    To: john@example.com
    Subject: Order Confirmation #ord_20260118_abc123
    Body: Your order has been confirmed...
    
  Timing: T+500ms (async)
  Consistency: EVENTUAL

ROUTE 2: Search Service
  Lambda triggered: Update recommendation model
  
  Insert into DynamoDB user_interactions table:
    {
      "id": "interaction_20260118_user_abc123_order",
      "user_id": "user_abc123",
      "product_id": ["prod_001", "prod_002"],
      "interaction_type": "purchase",
      "timestamp": 1705589255,
      "metadata": { "order_id": "ord_20260118_abc123" }
    }
    
  Trigger ML job to retrain recommendation model:
    Input: User's purchase history (now updated)
    Output: New personalized recommendations
    
  Timing: T+1000ms (async, 5-10 minute model retraining)
  Consistency: EVENTUAL

ROUTE 3: Warehouse System (via webhook)
  POST https://warehouse.bookstore.local/api/v1/orders
  
  {
    "order_id": "ord_20260118_abc123",
    "items": [...],
    "shipping_address": {...},
    "priority": "standard",
    "created_at": "2026-01-18T12:27:00Z"
  }
  
  Warehouse marks order as "Ready to Pick"
  Fulfillment team picks books from shelves
  
  Timing: T+2000ms (async)
  Consistency: EVENTUAL
```

---

### Scenario B: Payment Declined (Compensation Required)

```
Payment Service returns 402 Payment Required:

Order Service receives error:
  
  Transaction Boundary 6: Compensation - Release Inventory
    
    FOR EACH item in order:
      PATCH https://catalog-service.bookstore.local:8443/api/v1/products/{productId}/inventory
      
      {
        "quantity_increment": 2,  (reverse of decrement)
        "reason": "order_cancelled_payment_declined",
        "order_id": "ord_20260118_abc123"
      }
      
      Response: { "quantity_available": 45, "quantity_reserved": 5 }
      
    Timing: ~120ms (3 items)
    
  Transaction Boundary 7: Cancel Order (PostgreSQL, ACID)
    BEGIN TRANSACTION
    
    UPDATE orders 
    SET status = 'CANCELLED',
        cancelled_at = NOW(),
        cancellation_reason = 'payment_declined'
    WHERE order_id = 'ord_20260118_abc123'
    
    COMMIT
    
    Timing: ~5ms
    
  3. Publish OrderCancelled event:
    {
      "Source": "bookstore.order",
      "DetailType": "OrderCancelled",
      "Detail": {
        "order_id": "ord_20260118_abc123",
        "reason": "payment_declined",
        "timestamp": "2026-01-18T12:27:40Z"
      }
    }
    
  4. Return error to client:
  
    HTTP 402 Payment Required
    
    {
      "status": "error",
      "data": null,
      "errors": [
        {
          "code": "PAYMENT_DECLINED",
          "message": "Your card was declined due to insufficient funds",
          "details": {
            "decline_code": "insufficient_funds"
          }
        }
      ]
    }
    
  Total time: ~T+160ms (faster than success path because no compensation events)
```

---

### Scenario C: Network Timeout (Payment Service Unresponsive)

```
Order Service timeout after 5 seconds:

  1. First retry (automatic, exponential backoff):
     Wait 100ms
     Retry POST /api/v1/payments with SAME Idempotency-Key
     
     Outcome A: Success (within retry window) ✓
       Continue to PHASE 3A (confirmation)
     
     Outcome B: Still timeout
       Continue to step 2
       
  2. Second retry:
     Wait 200ms
     Retry POST /api/v1/payments with SAME Idempotency-Key
     
     Outcome A: Success ✓
       Continue to PHASE 3A
     
     Outcome B: Still timeout
       Continue to step 3
       
  3. Third retry limit reached:
     Total elapsed: 5s + 100ms + 5s + 200ms + 5s = ~15s
     
     Decision: Check payment status asynchronously
     
     Background task:
       Wait 2 seconds
       Query Payment Service: GET /api/v1/payments/{payment_id}?order_id=ord_20260118_abc123
       
       Response A: Payment succeeded
         Update order status to PAID
         Publish OrderCreated event
         
       Response B: Payment failed
         Release inventory
         Update order status to CANCELLED
         Publish OrderCancelled event
         
       Response C: Payment status unknown
         Mark order as PENDING_PAYMENT_VERIFICATION
         Send alert to operations team for manual review
         
  Timing: T+15-20s (depends on retry timing)
  Consistency: EVENTUAL (resolved within a few seconds)
  Idempotency: Payment Service's Idempotency-Key prevents double charges
```

---

## Search & Recommendation Flow

### Flow: User Searches for Products

#### Step 1: Search Request Received

```
User in SPA:
  GET https://api.bookstore.example.com/api/v1/search
  Query parameters:
    ?q=python&category=technology&price_max=50&sort=rating&page_size=20
  
  No Authorization header required (public endpoint)

Timing: T+0ms
Service: API Gateway (no JWT validation)
```

#### Step 2: Elasticsearch Query Execution

```
Search Service receives request (Python FastAPI):
  
  Build Elasticsearch query:
    {
      "query": {
        "bool": {
          "must": [
            {
              "multi_match": {
                "query": "python",
                "fields": ["title^2", "author", "description"],
                "fuzziness": "AUTO"
              }
            }
          ],
          "filter": [
            { "term": { "category": "technology" } },
            { "range": { "price": { "lte": 50 } } },
            { "term": { "available": true } }
          ]
        }
      },
      "sort": [{ "rating": { "order": "desc" } }],
      "size": 20,
      "track_scores": true,
      "aggs": {
        "by_category": { "terms": { "field": "category", "size": 10 } },
        "price_ranges": {
          "range": {
            "field": "price",
            "ranges": [
              { "to": 20 },
              { "from": 20, "to": 50 },
              { "from": 50 }
            ]
          }
        },
        "by_rating": {
          "range": {
            "field": "rating",
            "ranges": [
              { "from": 4.5 },
              { "from": 4.0, "to": 4.5 },
              { "to": 4.0 }
            ]
          }
        }
      }
    }
  
  Execute against Elasticsearch cluster:
    es_client.search(index="products", body=query)
    
  Response:
    {
      "hits": {
        "total": { "value": 342, "relation": "eq" },
        "hits": [
          {
            "_id": "prod_001",
            "_score": 8.5,
            "_source": {
              "product_id": "prod_001",
              "title": "Effective Python",
              "author": "Brett Slatkin",
              "price": 45.99,
              "rating": 4.7,
              "available": true
            },
            "sort": [4.7]  ← For pagination cursor
          },
          ...
        ]
      },
      "aggregations": {
        "by_category": {
          "buckets": [
            { "key": "technology", "doc_count": 200 },
            { "key": "fiction", "doc_count": 50 }
          ]
        },
        "price_ranges": {
          "buckets": [
            { "key": "0-20", "doc_count": 80 },
            { "key": "20-50", "doc_count": 150 },
            { "key": "50+", "doc_count": 112 }
          ]
        }
      }
    }
    
  Timing: ~100-200ms (typical Elasticsearch query time)
  Consistency: EVENTUAL (index may be 1-2s behind Catalog Service)
```

#### Step 3: Track User Interaction & Return Results

```
Search Service:
  
  1. If user authenticated (Authorization header present):
     Extract user_id from JWT
     
     Queue async job (Fire-and-forget):
       Track user search interaction in DynamoDB:
         PUT user_interactions table:
           {
             "id": "interaction_20260118_user_abc123_search",
             "user_id": "user_abc123",
             "interaction_type": "search",
             "query": "python",
             "result_count": 342,
             "timestamp": 1705589240,
             "metadata": { "category": "technology", "price_max": 50 }
           }
           
       Timing: ~5ms (async, doesn't block response)
       Consistency: EVENTUAL
       
  2. Format and return response:
  
    HTTP 200 OK
    
    {
      "status": "success",
      "data": [
        {
          "product_id": "prod_001",
          "title": "Effective Python",
          "author": "Brett Slatkin",
          "price": 45.99,
          "rating": 4.7,
          "relevance_score": 8.5
        },
        ...
      ],
      "meta": {
        "pagination": {
          "total_count": 342,
          "page_size": 20,
          "next_cursor": "eyJzZWFyY2hfYWZ0ZXIiOiBbNC43LCAicHJvZF8wMDEiXX0",
          "has_more": true
        },
        "facets": {
          "categories": [...],
          "price_ranges": [...]
        }
      }
    }
    
    Timing: T+220ms (search + aggregations + formatting)
```

### Flow: User Gets Personalized Recommendations

#### Step 1: Recommendations Request

```
User authenticated in SPA:
  GET https://api.bookstore.example.com/api/v1/recommendations
  Authorization: Bearer {access_token}
  Query parameters:
    ?limit=10
    
Timing: T+0ms
Service: Search Service
```

#### Step 2: Fetch User Interaction History

```
Search Service receives request:
  
  Extract user_id from JWT:
    user_id = "user_abc123"
    
  Query DynamoDB user_interactions table:
    Query by user_id, limit 100, sort by timestamp DESC
    
    SELECT * FROM user_interactions
    WHERE user_id = 'user_abc123'
    ORDER BY timestamp DESC
    LIMIT 100
    
    Returns:
      [
        { product_id: "prod_001", type: "purchase", timestamp: T-1 },
        { product_id: "prod_002", type: "view", timestamp: T-2 },
        { product_id: "prod_003", type: "add_to_cart", timestamp: T-3 },
        ...
      ]
      
    Timing: ~20ms (DynamoDB GSI query)
    Consistency: EVENTUAL (interactions added asynchronously)
```

#### Step 3: Generate Recommendations (Hybrid Algorithm)

```
Search Service:
  
  Algorithm: Hybrid Collaborative Filtering + Content-Based
  
  PART 1: Collaborative Filtering
    Input: User's purchase/view history
    Matrix factorization: Find users with similar taste
    Find products liked by similar users
    
    Score matrix:
      user_abc123's interactions:
        prod_001 (purchased, 100 points)
        prod_002 (viewed, 50 points)
        prod_003 (add_to_cart, 75 points)
      
      Similar users:
        user_xyz789 (similarity score: 0.92)
          Liked: prod_005, prod_008, prod_010
        
      CF Score = similarity_score × product_score
    
    Result: prod_005 (CF score: 0.92 × 85 = 77.2)
    
  PART 2: Content-Based Filtering
    Input: Products user interacted with
    Features: category, author, rating, price_range
    
    User likes:
      - Technology books (70%)
      - Authors: Joshua Bloch, Robert Martin (high rating)
      - Price range: $40-60
      
    Find similar:
      prod_008: Design Patterns
        - Category: Technology (match)
        - Author: Gang of Four (match)
        - Price: $54.99 (match)
      - CB Score: 0.85
      
  HYBRID: Combine scores
    Final Score = 0.6 × CF_score + 0.4 × CB_score
    
    prod_005: (0.6 × 0.95) + (0.4 × 0.80) = 0.89
    prod_008: (0.6 × 0.70) + (0.4 × 0.85) = 0.82
    
  Query recommendations_models table:
    SELECT * FROM recommendations_models
    WHERE user_id = 'user_abc123'
    AND expires_at > NOW()
    
    If model exists and fresh:
      Use cached model (generated 1-2 hours ago)
      Timing: ~10ms
    Else:
      Generate new model (CPU intensive)
      Store in DynamoDB
      Timing: ~500-1000ms (async, return previous or trending)
      
  Timing: 10-100ms (varies with cache hit/miss)
  Consistency: EVENTUAL (model may be hours old)
```

#### Step 4: Return Recommendations

```
HTTP 200 OK

{
  "status": "success",
  "data": [
    {
      "product_id": "prod_005",
      "title": "Design Patterns",
      "author": "Gang of Four",
      "price": 54.99,
      "rating": 4.8,
      "reason": "collaborative_filtering",
      "score": 0.89
    },
    {
      "product_id": "prod_008",
      "title": "Refactoring",
      "author": "Martin Fowler",
      "price": 52.99,
      "rating": 4.6,
      "reason": "content_based",
      "score": 0.82
    },
    ...
  ],
  "meta": {
    "algorithm": "hybrid_cf_content",
    "model_version": "v2.1",
    "generated_at": "2026-01-18T11:00:00Z"
  }
}

Timing: T+100-300ms (depending on model cache)
```

---

## Event Publishing & Consumption

### Event Format (Standard Across All Services)

```json
{
  "source": "bookstore.{domain}",
  "detail-type": "{EntityName}{Action}",
  "account": "123456789012",
  "time": "2026-01-18T12:27:35.000Z",
  "region": "us-east-1",
  "resources": [],
  "detail": {
    "id": "{entity_id}",
    "timestamp": "2026-01-18T12:27:35Z",
    "user_id": "user_abc123 (if applicable)",
    "data": { /* domain-specific payload */ }
  }
}
```

### Order Domain Events

#### Event 1: order.created

```json
{
  "source": "bookstore.order",
  "detail-type": "OrderCreated",
  "time": "2026-01-18T12:27:35.000Z",
  "detail": {
    "order_id": "ord_20260118_abc123",
    "user_id": "user_abc123",
    "timestamp": "2026-01-18T12:27:35Z",
    "items": [
      {
        "order_item_id": "oi_001",
        "product_id": "prod_001",
        "product_title": "Effective Java",
        "quantity": 2,
        "unit_price": 4999,
        "total_price": 9998
      }
    ],
    "subtotal": 15497,
    "tax": 1549,
    "shipping": 1000,
    "discount": -1500,
    "total_amount": 15497,
    "currency": "USD",
    "status": "paid",
    "shipping_address": {
      "street": "123 Rue de Rivoli",
      "city": "Paris",
      "postal_code": "75001",
      "country": "FR"
    },
    "payment_id": "pay_stripe_ch_1234567890"
  }
}

Publishing service: Order Service
Published to: AWS EventBridge
Subscribers:
  1. Notifications Service (send email/SMS)
  2. Search Service (update user interactions)
  3. Warehouse System (webhook)
  
Guaranteed delivery: Yes (EventBridge retries up to 24 hours)
Ordering: FIFO within same order ID (not guaranteed across orders)
```

#### Event 2: order.paid

```json
{
  "source": "bookstore.order",
  "detail-type": "OrderPaid",
  "time": "2026-01-18T12:27:40.000Z",
  "detail": {
    "order_id": "ord_20260118_abc123",
    "user_id": "user_abc123",
    "timestamp": "2026-01-18T12:27:40Z",
    "payment_id": "pay_stripe_ch_1234567890",
    "amount": 15497,
    "currency": "USD"
  }
}

Publishing service: Order Service
Subscribers: Fulfillment system, Analytics
```

#### Event 3: order.cancelled

```json
{
  "source": "bookstore.order",
  "detail-type": "OrderCancelled",
  "time": "2026-01-18T12:27:45.000Z",
  "detail": {
    "order_id": "ord_20260118_abc123",
    "user_id": "user_abc123",
    "timestamp": "2026-01-18T12:27:45Z",
    "reason": "payment_declined | customer_request | inventory_unavailable",
    "refund_initiated": true,
    "refund_id": "ref_xyz789"
  }
}

Publishing service: Order Service
Subscribers:
  1. Notifications Service (send cancellation notice)
  2. Payment Service (trigger refund if applicable)
  3. Warehouse System (cancel pick)
```

### Payment Domain Events

#### Event: payment.succeeded

```json
{
  "source": "bookstore.payment",
  "detail-type": "PaymentSucceeded",
  "time": "2026-01-18T12:27:35.000Z",
  "detail": {
    "payment_id": "pay_stripe_ch_1234567890",
    "order_id": "ord_20260118_abc123",
    "user_id": "user_abc123",
    "timestamp": "2026-01-18T12:27:35Z",
    "amount": 15497,
    "currency": "USD",
    "stripe_charge_id": "ch_1234567890"
  }
}

Publishing service: Payment Service (via webhook from Stripe)
Subscribers: Order Service (confirm payment), Analytics
```

#### Event: payment.refunded

```json
{
  "source": "bookstore.payment",
  "detail-type": "PaymentRefunded",
  "time": "2026-01-18T13:00:00.000Z",
  "detail": {
    "refund_id": "ref_xyz789",
    "payment_id": "pay_stripe_ch_1234567890",
    "order_id": "ord_20260118_abc123",
    "user_id": "user_abc123",
    "timestamp": "2026-01-18T13:00:00Z",
    "amount": 15497,
    "reason": "customer_request | order_cancelled"
  }
}

Publishing service: Payment Service
Subscribers: Notifications Service (confirm refund)
```

### Catalog Domain Events

#### Event: product.created

```json
{
  "source": "bookstore.catalog",
  "detail-type": "ProductCreated",
  "time": "2026-01-18T10:00:00.000Z",
  "detail": {
    "product_id": "prod_002",
    "timestamp": "2026-01-18T10:00:00Z",
    "isbn": "978-0136298151",
    "title": "Clean Code",
    "price": 5499,
    "category_id": "cat_tech"
  }
}

Publishing service: Catalog Service
Subscribers: Search Service (add to index)
```

---

## Failure Handling & Compensation

### Failure Type 1: Inventory Insufficient During Order Creation

```
Failure Point: Catalog Service inventory check fails
  Catalog Service PATCH inventory returns HTTP 409 Conflict

Order Service detects failure:
  
  1. Compensation: Release already-reserved inventory
     FOR EACH previously-reserved item:
       PATCH https://catalog-service.bookstore.local:8443/...
       {
         "quantity_increment": reserved_qty,
         "reason": "order_creation_failed_inventory_unavailable"
       }
       
       Retry logic: 3 attempts with exponential backoff
       
  2. Cleanup: Cancel order in database
     UPDATE orders SET status = 'CANCELLED'
     WHERE order_id = 'ord_20260118_abc123'
     
  3. Response to client:
     HTTP 409 Conflict
     {
       "status": "error",
       "code": "INSUFFICIENT_INVENTORY",
       "message": "Product 'prod_002' has only 2 available (requested 3)"
     }
     
  State after failure:
    - Order: CANCELLED
    - Inventory: Released (back to original available count)
    - Client: Informed (can retry or choose different quantity)
    - Payment: Not initiated (clean failure, no compensation needed)
```

### Failure Type 2: Payment Declined

```
Failure Point: Payment Service returns 402 Payment Required

Order Service detects failure:
  
  Compensation Phase (Saga Rollback):
    
    Step 1: Release inventory
      FOR EACH item in order:
        PATCH Catalog Service with quantity_increment
        Timeout: 5s per call
        Retry: 3 exponential backoff
        
        If all fail after 3 retries:
          Alert operations team
          Inventory remains reserved (manual reconciliation needed)
          
    Step 2: Cancel order
      UPDATE orders SET status = 'CANCELLED' WHERE order_id = ...
      
      If this fails (database down):
        Alert operations team
        Manual recovery: Query order history to determine state
        
    Step 3: Publish OrderCancelled event
      EventBridge will retry for 24 hours if fails
      
  Response to client:
    HTTP 402 Payment Required
    {
      "status": "error",
      "code": "PAYMENT_DECLINED",
      "message": "Your card was declined"
    }
    
  State after failure:
    - Order: CANCELLED
    - Inventory: Released
    - Client: Can retry with different payment method
    - No financial impact: No charge, no refund needed
```

### Failure Type 3: Payment Service Timeout

```
Failure Point: Order Service timeout calling Payment Service after 5 seconds

Scenario A: Retry succeeds within grace period

  Order Service:
    1. Wait 100ms (exponential backoff)
    2. Retry with SAME Idempotency-Key
    3. Payment Service idempotency check:
         Query idempotency_keys table with key='req_20260118_...'
         If found:
           Return SAME response as first attempt
         Else:
           Process payment again (but Stripe also has idempotency!)
           
    4. Stripe receives same idempotency_key
         Stripe query: SELECT * FROM idempotency_keys WHERE key='req_20260118_...'
         If found:
           Return SAME response (charge NOT duplicated)
         Else:
           New charge attempt
           
    Result: SAFE (double-idempotency prevents double charge)
    
Scenario B: Timeout persists through 3 retries

  Order Service:
    1. Attempt 1: Timeout after 5s
    2. Wait 100ms
    3. Attempt 2: Timeout after 5s
    4. Wait 200ms
    5. Attempt 3: Timeout after 5s
    
    Total elapsed: ~15s
    
    Decision: Async check + manual review
    
    Background task (scheduled for T+2s):
      GET /api/v1/payments/{payment_id}?order_id=ord_...
      
      Response A: Payment succeeded (status: "succeeded")
        Update order status: PENDING → PAID
        Publish OrderCreated event
        Send confirmation email
        
      Response B: Payment failed (status: "failed")
        Release inventory
        Update order status: PENDING → CANCELLED
        Send cancellation email
        
      Response C: Payment unknown (404 or error)
        Keep order: PENDING (timeout state)
        Alert operations team
        Escalate to manual review
        
  State during timeout:
    - Order: PENDING
    - Inventory: RESERVED (locked, can't be sold)
    - Client: Waiting
    - Payment: Unknown state
    
  State after async check:
    - Depends on payment status
    - If payment succeeded: Order PAID (eventual success)
    - If payment failed: Compensation triggers
    - If unknown: Manual intervention required
```

### Failure Type 4: Order Service Database Down

```
Failure Point: PostgreSQL connection fails during order creation

Failure detection:
  Exception: Connection timeout
  Service: Order Service
  
Automatic recovery:
  
  1. Connection pool retries (automatic):
     - Wait 200ms, reconnect
     - Retry operation
     - If success: Continue normally
     
  2. If persists (database truly down):
     HTTP 503 Service Unavailable
     
     {
       "status": "error",
       "code": "SERVICE_UNAVAILABLE",
       "message": "Order service temporarily unavailable. Please try again."
     }
     
  3. Circuit breaker opens:
     - Fail all new requests immediately (don't attempt connection)
     - Wait 30 seconds
     - Try single probe request
     - If succeeds: Close circuit
     - If fails: Reopen circuit
     
  State during outage:
    - Order: NOT created (atomically failed)
    - Inventory: NOT reserved (no call made to Catalog Service)
    - Payment: NOT initiated (sync call never reached)
    - Client: 503 error (can retry later)
    
  Recovery:
    - DBA restores database from backup
    - Clients retry requests
    - Orders created successfully on retry
    - Idempotency key ensures no duplicates
```

### Failure Type 5: Event Publishing Fails (EventBridge Down)

```
Failure Point: AWS EventBridge is down or reachable

Scenario: Order Service successfully creates order but EventBridge fails

Order Service code:
  
  Step 1: Update order status (committed to DB) ✓
    UPDATE orders SET status = 'PAID' WHERE order_id = ...
    COMMIT
    
  Step 2: Publish event to EventBridge
    try:
      eventBridgeClient.putEvents(OrderCreatedEvent)
    catch EventBridgeException:
      LOG error with order_id
      QUEUE for retry in SQS Dead Letter Queue
      Continue (don't fail order creation)
      
  Step 3: Return success to client ✓
    HTTP 201 Created
    Order details
    
  State immediately after:
    - Order: PAID (in database)
    - Event: NOT published
    - Notifications: NOT sent
    - Client: Believes order successful (correct)
    
  Recovery (automatic):
    1. Order Service background job:
       Query EventBridge DLQ for failed events
       Try republish every 30 seconds
       
    2. EventBridge retries (if message queued):
       Automatic retry for 24 hours
       Exponential backoff: 1s, 2s, 4s, 8s, ..., 300s
       
    3. After 24 hours:
       Event moved to Dead Letter Queue
       Operator manually investigates
       
  Final state:
    - Order: PAID (consistent)
    - Notifications: Delivered (eventually)
    - Delay: Depends on recovery speed (typically < 5 minutes)
    - User impact: Minimal (email delayed)
```

---

## Idempotency Rules

### Rule 1: Every write operation must be idempotent

```
Definition: Same request (same idempotency key) produces same result,
            regardless of how many times it's executed.

Implementation:

  Client generates: X-Idempotency-Key: req_20260118_user_abc123_001
  
  Server behavior:
    1. On first receipt:
       - Process normally
       - Store (idempotency_key → response) in cache
       - Return response
       
    2. On second receipt (same key):
       - Query cache: Is this key processed?
       - If yes: Return cached response (no side effects)
       - If no: Repeat step 1
       
  Example:
    Request 1: POST /orders with key='req_001'
      → Creates order ord_001
      → Stores: idempotency_keys['req_001'] = { order_id: 'ord_001', ... }
      → Returns: { order_id: 'ord_001', status: 'pending' }
      
    Request 2 (retry): POST /orders with same key='req_001'
      → Looks up: idempotency_keys['req_001']
      → Found! Returns cached response
      → No new order created
      → Client receives same response as request 1
```

### Rule 2: Idempotency scope per operation

```
Scope: User + Operation + Time

  Format:
    X-Idempotency-Key: {timestamp}_{user_id}_{operation_id}_{sequence}
    
  Example:
    req_20260118_user_abc123_order_001
    
  Components:
    - Timestamp: 2026-01-18 (date)
    - User ID: user_abc123 (who initiated)
    - Operation: order (what action)
    - Sequence: 001 (first attempt)
    
  Uniqueness:
    - Globally unique within 24 hours
    - Same user cannot generate two identical keys within timeframe
```

### Rule 3: Cache retention period

```
Idempotency cache retention:

  DynamoDB table: idempotency_keys
  
  TTL: 24 hours from creation
  Reasoning:
    - HTTP max-age: Clients shouldn't cache > 24h
    - Retry windows: Typical retries complete within 5 minutes
    - Garbage collection: Prevents unbounded table growth
    
  DELETE operation:
    SELECT * FROM idempotency_keys WHERE created_at < NOW() - 24h
    DELETE matching rows
    
  Timing: Automated daily cleanup job (3am UTC)
  Performance: < 1 second (TTL index scans)
```

### Rule 4: Idempotency with service-to-service calls

```
Order Service → Payment Service

Order Service generates: X-Idempotency-Key = req_20260118_user_abc123_001

Order Service sends to Payment Service:
  POST /api/v1/payments
  X-Idempotency-Key: req_20260118_user_abc123_001
  
Payment Service:
  1. Check DynamoDB: Is this key processed?
  2. If yes: Return cached response
  3. If no: Forward to Stripe with SAME key
  
Stripe API:
  1. Check Stripe database: Is this key processed?
  2. If yes: Return cached charge
  3. If no: Create new charge
  
Triple-layer idempotency:
  Order Service cache → Payment Service cache → Stripe cache
  
  Result: Impossible to double-charge even with massive retries
```

### Rule 5: Non-idempotent operations forbidden

```
Forbidden patterns:

  ❌ GET /api/v1/orders (can be called multiple times safely, no idempotency key needed)
  ❌ DELETE /api/v1/orders/{id} (idempotent by nature, second delete returns 404 or 204)
  ✓ POST /api/v1/orders (requires idempotency key)
  ✓ PATCH /api/v1/products/{id}/inventory (requires idempotency key)
  ✓ POST /api/v1/payments (requires idempotency key)
  
  Rule: If operation has side effects (write to DB), requires X-Idempotency-Key
```

---

## Transaction Boundaries

### Definition: Strong vs. Eventual Consistency

```
STRONG CONSISTENCY (within transaction boundary):

  Example: Order creation
  
    BEGIN TRANSACTION (PostgreSQL, serializable isolation)
      1. INSERT into orders table
      2. INSERT into order_items table (3 items)
      3. INSERT into idempotency_keys cache
    COMMIT
    
    Atomicity: All 3 succeed or all 3 fail
    Isolation: No other transaction sees partial state
    Durability: Once committed, persists forever
    Consistency: Database constraints enforced
    
  Timing: Single ACID transaction
  Scope: Single database (PostgreSQL orders schema)
  
EVENTUAL CONSISTENCY (across transaction boundaries):

  Example: Order creation + Event publishing + Notifications
  
    T+0ms: Order created (strong consistency ✓)
    T+20ms: Event published to EventBridge
    T+500ms: Notifications Service receives event
    T+1000ms: Email queued in Bull
    T+2000ms: Email sent by SES
    
    Between T+0 and T+2000:
      - Order is PAID (strong consistency)
      - But email not yet sent (eventual consistency)
      - If user checks email immediately: Not there yet
      - If user checks email 1 minute later: There (consistent)
```

### Transaction Boundary 1: Order Creation

```
PostgreSQL transaction (serializable):

  Input: CreateOrderRequest { items, address, promo_code }
  
  Actions:
    1. INSERT orders (status='PENDING')
    2. INSERT order_items (3 items)
    3. INSERT idempotency_keys
    
  Guarantees:
    - Atomicity: All succeed or rollback
    - Isolation: Reads see no uncommitted changes
    - Durability: Committed data survives crashes
    - Consistency: Foreign key constraints checked
    
  Outcome:
    Success: Order exists with status='PENDING'
    Failure: Order doesn't exist (rollback)
    
  No other service involved yet
```

### Transaction Boundary 2: Inventory Reservation

```
PostgreSQL transaction (serializable):

  Input: PATCH /inventory { quantity_decrement: 2 }
  
  Actions:
    BEGIN TRANSACTION
      1. SELECT quantity_available FROM inventory 
         WHERE product_id='prod_001' 
         FOR UPDATE  ← Pessimistic lock prevents race condition
         
      2. IF quantity_available >= 2:
           UPDATE inventory SET quantity_available -= 2
           COMMIT
           Success ✓
         ELSE:
           ROLLBACK
           Error: Insufficient inventory
           
  Guarantees:
    - No double-reservation: Lock held until commit
    - No race condition: Only one transaction at a time per product
    - Atomicity: Quantity check + update together
    
  Isolation level: SERIALIZABLE
    - Prevents phantom reads
    - Prevents dirty reads
    - Prevents non-repeatable reads
    
  Outcome:
    Success: Inventory decreased atomically
    Failure: No change (rollback)
```

### Transaction Boundary 3: Payment Processing

```
DynamoDB transaction (eventual consistency internally):

  Input: POST /payments { amount, payment_method }
  
  Actions:
    1. Check idempotency cache (DynamoDB query)
    2. If miss: Call Stripe API
    3. Store result in payments table
    4. Store idempotency result (for future retries)
    
  Guarantees:
    - Atomicity: Within DynamoDB (per item)
    - NOT atomic across Stripe + DynamoDB
    - If Stripe succeeds but DynamoDB fails:
        Payment charged but not logged
        Recovery: Stripe webhook confirms payment
        
  Timeout: 5 seconds
  
  Outcome:
    Success: Payment in DynamoDB + charged via Stripe
    Failure: No charge + DynamoDB unchanged
```

### Transaction Boundary 4: Order Status Update

```
PostgreSQL transaction (serializable):

  Input: Update order status from PENDING → PAID
  
  Actions:
    BEGIN TRANSACTION
      1. SELECT * FROM orders WHERE order_id='ord_...' FOR UPDATE
      2. IF current_status == 'PENDING':
           UPDATE orders SET status='PAID', paid_at=NOW()
           COMMIT
           Success ✓
         ELSE:
           ROLLBACK  (order already PAID or CANCELLED)
           
  Guarantees:
    - Optimistic locking: Prevents concurrent updates
    - Atomicity: Status change + timestamp together
    
  Outcome:
    Success: Order PAID
    Failure: Order remains in previous state
```

### Transaction Boundary 5: Event Publishing

```
EventBridge (AWS managed service):

  Action: Put event to EventBridge
  
  Guarantees:
    - Reliable delivery: Yes (retries for 24 hours)
    - Ordering: Per event source + detail-type
    - Atomicity: Event published or not (binary)
    - NOT atomic with Order Service transaction
    
  Timing:
    - Order Service: Order committed to DB ✓
    - Event Service: Event queued ✓
    - Subscribers: Receive event (1-2 second delay typical)
    
  If Order Service crashes after commit but before event publish:
    - Order: Saved to DB
    - Event: Not published
    - Recovery: Background job republishes
    
  Risk of data loss: Very low (EventBridge durability)
```

---

## Consistency Model

### CAP Theorem Application

```
System: Online Bookstore

PARTITION TOLERANCE: P = Required
  Multiple services across regions
  Network failures expected
  Cannot avoid partitions
  
CONSISTENCY vs. AVAILABILITY tradeoff:

  Choice: Eventual Consistency + High Availability
  
  Rationale:
    - E-commerce values availability (downtime = lost sales)
    - Users tolerate eventual consistency (notifications delayed 1-2s)
    - Strong consistency would require 2PC (not scalable)
    
  Sacrifice: Immediate consistency
    - Order created but notifications delayed
    - Inventory updated asynchronously
    - Search index lags by 1-2 seconds
    
  Guarantee: Final consistency
    - After 5 minutes: All services agree on state
    - After 24 hours: All systems converged
```

### Consistency Guarantees by Operation

```
STRONG CONSISTENCY (within service boundaries):

  1. Order creation
     - User creates order → Saved to DB immediately
     - User queries order 1ms later → Sees created order
     - Consistency: Strong (read-after-write)
     
  2. Inventory reservation
     - Catalog reserves inventory → SQL UPDATE committed
     - Another user queries inventory 1ms later → Sees reduced count
     - Consistency: Strong (read-after-write)
     
EVENTUAL CONSISTENCY (across service boundaries):

  1. Email notification
     - Order created at T+0
     - Email sent at T+500ms
     - If user checks email at T+100ms: Not there
     - If user checks at T+1s: Email received
     - Consistency: Eventual (lag = 500-1000ms)
     
  2. Search index update
     - Product created in Catalog at T+0
     - Search index updated at T+1000ms
     - User searches immediately: Product not found
     - User searches after 5s: Product found
     - Consistency: Eventual (lag = 1000-2000ms)
     
  3. Recommendation model update
     - User purchase at T+0
     - Recommendation model trained at T+1h
     - Recommendations based on latest interactions: 1 hour old
     - Consistency: Eventual (lag = 1 hour)
```

### Handling Consistency Gaps

```
Problem: User's perceived consistency gap

Scenario:
  User places order (T+0)
  User sees: "Order successful! #ord_123"
  User checks email (T+100ms)
  Email not there
  User confused: "Where's my confirmation?"
  
Solution: Manage expectations

  1. UI shows local state immediately
     "Order placed! Check your email in 1-2 minutes"
     
  2. Event-driven updates refresh UI
     Once email sent, notification bubbles up to UI
     
  3. Status polling fallback
     Client polls: GET /api/v1/orders/{orderId}/status
     Shows: "Confirmation email: sent" / "pending"
     
  Result: User understands eventual delivery
```

---

## Mental Model Reference

### Execution Model: Distributed Saga Pattern

```
REQUEST ARRIVES
    ↓
┌─ Transaction Boundary 1: Validate & Create
│  └─ PostgreSQL ACID transaction (order + items + cache)
│     Result: Order PENDING
│
├─ Transaction Boundary 2: Reserve Inventory
│  └─ Catalog Service (PostgreSQL ACID)
│     Result: Inventory reserved
│
├─ Transaction Boundary 3: Payment Processing
│  └─ Payment Service → Stripe (eventual consistency)
│     Result: Payment succeeded or failed
│
├─ IF Payment succeeded:
│  │
│  ├─ Transaction Boundary 4: Confirm Order
│  │  └─ PostgreSQL ACID (status: PENDING → PAID)
│  │     Result: Order PAID
│  │
│  ├─ Transaction Boundary 5: Publish Event
│  │  └─ EventBridge put-events (async)
│  │     Result: Event queued
│  │
│  └─ SUBSCRIBERS (asynchronous):
│     ├─ Notifications Service (email/SMS)
│     ├─ Search Service (interaction matrix)
│     └─ Warehouse System (fulfillment)
│
├─ IF Payment failed:
│  │
│  ├─ Compensation: Release Inventory
│  │  └─ Catalog Service (quantity_increment)
│  │
│  ├─ Compensation: Cancel Order
│  │  └─ PostgreSQL ACID (status: PENDING → CANCELLED)
│  │
│  └─ Publish OrderCancelled Event
│     └─ Notify user of failure
│
└─ RETURN to Client (HTTP response)
```

### Retry & Idempotency Model

```
Request arrives with X-Idempotency-Key

Step 1: Check Cache
  ├─ Found in cache?
  │  ├─ Yes → Return cached response immediately
  │  └─ No → Continue
  
Step 2: Execute Operation
  ├─ Success?
  │  ├─ Yes → Cache result, return
  │  └─ No (error or timeout) → Continue
  
Step 3: Retry Logic
  ├─ Is retriable error?
  │  ├─ Yes → Retry with backoff
  │  │  ├─ Attempt 1 (100ms wait)
  │  │  ├─ Attempt 2 (200ms wait)
  │  │  └─ Attempt 3 (400ms wait)
  │  ├─ No → Fail immediately
  │  
  └─ Max retries exceeded?
     ├─ Yes → Check async status
     └─ No → Retry again

Key insight:
  Retries use SAME Idempotency-Key
  → Downstream services recognize retries
  → No duplicate side effects
```

### Event Flow Model

```
Service A publishes event:
  Event: OrderCreated { order_id, user_id, items, ... }
  Published at: T+0ms
  
EventBridge queue:
  Event queued for routing
  Timing: T+5ms
  
EventBridge routes to subscribers:
  Subscriber 1 (Notifications Service)
    ├─ Receives at T+100ms
    ├─ Queues email job
    ├─ Email sent at T+500ms
    └─ User receives at T+2000ms (external)
    
  Subscriber 2 (Search Service)
    ├─ Receives at T+105ms
    ├─ Updates DynamoDB
    ├─ Retrains model at T+1h
    └─ New recommendations available at T+1h30m
    
  Subscriber 3 (Warehouse)
    ├─ Receives at T+110ms
    ├─ Creates pick list
    ├─ Physical fulfillment at T+6 hours
    └─ Shipment ready at T+24 hours

Timeline:
  T+0: Order placed (user perceives success)
  T+0.5s: All subscribers have received event
  T+2: Email sent (user sees confirmation)
  T+1h: Recommendations updated
  T+24h: Order delivered
  
Total consistency window: 1-24 hours
  - Core consistency (inventory, payment): < 1 second
  - Notifications: < 5 seconds
  - Search/analytics: 1-2 hours
```

---

## Summary Tables

### Transaction Boundaries by Operation

| Operation | Database | Isolation | Atomicity | Scope |
|-----------|----------|-----------|-----------|-------|
| Create Order | PostgreSQL | SERIALIZABLE | ✓ Atomic | Single service |
| Reserve Inventory | PostgreSQL | SERIALIZABLE | ✓ Atomic | Catalog Service |
| Process Payment | DynamoDB + Stripe | EVENTUAL | ⚠ Eventual | Cross-service |
| Confirm Order | PostgreSQL | SERIALIZABLE | ✓ Atomic | Order Service |
| Publish Event | EventBridge | N/A | ⚠ Best-effort | Async |

### Consistency Model by Component

| Component | Consistency | Lag | Acceptable? |
|-----------|-------------|-----|-------------|
| Order Creation | Strong | Immediate | ✓ Yes |
| Inventory | Strong | Immediate | ✓ Yes |
| Payment | Strong | < 1s | ✓ Yes |
| Email Notification | Eventual | < 5s | ✓ Yes |
| Search Index | Eventual | 1-2s | ✓ Yes |
| Recommendations | Eventual | 1 hour | ✓ Yes |
| Analytics | Eventual | 5 minutes | ✓ Yes |

### Failure Recovery Time

| Failure | Detection | Recovery | User Impact |
|---------|-----------|----------|-------------|
| Inventory Low | Immediate | Retry with less qty | Request denied |
| Payment Declined | Immediate | Retry payment method | Retry or cancel |
| Payment Timeout | 5 seconds | Async check + manual | Waiting 1-2 min |
| Email Failure | 10 seconds | Automatic retry (24h) | Email delayed |
| Catalog Down | Immediate | Circuit breaker opens | 503 error |

---

**Document Version**: 1.0.0  
**Status**: Iteration 5 — Complete  
**Next**: Database Migrations & Seeding Specification
