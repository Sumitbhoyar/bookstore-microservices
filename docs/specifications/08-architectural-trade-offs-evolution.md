# Architectural Trade-offs & Evolution Specification

**Version:** 1.0.0  
**Last Updated:** 2026-01-18  
**Status:** Iteration 8 — Final Architecture  
**Audience:** CTOs, Principal Engineers, Tech Leads, Interview Candidates  
**Prerequisite:** All Previous Specifications v1.0.0

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Polyglot Service Architecture Justification](#polyglot-service-architecture-justification)
3. [Technology Choices & Alternatives](#technology-choices--alternatives)
4. [Trade-offs Analysis](#trade-offs-analysis)
5. [Cost-Complexity Matrix](#cost-complexity-matrix)
6. [Security & Compliance](#security--compliance)
7. [Scalability Limits & Bottlenecks](#scalability-limits--bottlenecks)
8. [Evolution Paths (1-3 Years)](#evolution-paths-1-3-years)
9. [Decision Framework](#decision-framework)
10. [Architectural Debt & Mitigation](#architectural-debt--mitigation)

---

## Executive Summary

### The Bookstore Platform Architecture

**What:** Polyglot, event-driven microservices platform for e-commerce  
**Scale:** 10,000 concurrent users, 1,000 RPS peak, 50K orders/day  
**Cost:** ~$15K-20K/month (AWS infrastructure)  
**Availability:** 99.9% - 99.99% (service-dependent)  
**Latency:** p99 < 1 second (API requests)

### Why This Architecture?

**Strategic Decision:** Optimize for team scalability and developer autonomy, not for absolute performance or minimal complexity.

This is NOT:
- A single-language monolith (Java only)
- A Kubernetes-based platform (too complex for this scale)
- A serverless-first design (wrong for long-lived connections)

This IS:
- A pragmatic polyglot platform using best-in-class tools per problem domain
- Managed services (RDS, ElastiCache, Elasticsearch) to reduce operational overhead
- Container-based with ECS (not Kubernetes) for simplicity at this scale
- Event-driven to enable team independence and asynchronous workflows

### Key Principles

| Principle | Rationale | Trade-off |
|-----------|-----------|-----------|
| **Use the right tool** | Java for business logic, Python for ML, TS for APIs | Higher operational complexity |
| **Managed > Self-Managed** | RDS > Self-hosted PostgreSQL | Less control, higher cost |
| **Eventual consistency** | Event-driven architecture | Complex distributed debugging |
| **Automation first** | CI/CD, infrastructure-as-code | Requires discipline & tooling |
| **Observability > Complexity** | Comprehensive logging/tracing | Increases operational cost |

---

## Polyglot Service Architecture Justification

### Service Breakdown & Language Choices

#### 1. Order Service → Java Spring Boot

**Why Java?**
- **Business logic complexity:** Order creation involves multi-step saga (reserve inventory → charge payment → create order)
- **Type safety:** Reduces runtime bugs in complex workflows
- **Spring ecosystem:** Proven transaction management, test frameworks
- **Scalability:** JVM GC tuning for predictable latency
- **Team expertise:** Most common enterprise language

**Considered Alternatives:**
- ❌ Python: Too slow for high-RPS request handler (1,000 RPS, 450ms avg)
- ❌ Go: Less mature dependency management; team less familiar
- ❌ .NET: AWS-native tooling weaker than Java

**Risk:** JVM startup time (30s+), memory overhead (2GB base). Mitigated via Fargate always-on tasks.

---

#### 2. User Service → Java Spring Boot

**Why Java?**
- Same reasons as Order Service
- Profile data read-heavy (Redis caches 80%+ of reads)
- S3 integration for picture uploads (well-supported)

**Alternative Considered:** Go
- **Pros:** 10x faster startup, 1/4 memory
- **Cons:** Team skill gap, less mature HTTP middleware
- **Decision:** Team productivity > resource efficiency at this scale

---

#### 3. Catalog Service → Java Spring Boot

**Why Java?**
- Complex inventory management (pessimistic locking via SELECT FOR UPDATE)
- EventBridge event publishing (native AWS SDK support)
- Spring Data for ORM & query complexity

---

#### 4. Payment Service → TypeScript NestJS

**Why NOT Java here?**
- **Simplicity matters:** Payment is a thin wrapper around Stripe API
- **JSON-native:** Request/response payloads are 100% JSON
- **Rapid iteration:** FinTech changes frequently
- **Team skill:** Backend team has Node expertise
- **Webhook handling:** Express/NestJS middleware simpler than Spring

**Considered Alternatives:**
- ❌ Java: Overkill for thin integration layer
- ❌ Python: Not WebSocket-native; slower for concurrent requests
- ✅ Go: Would be equally valid; team chose Node

**Trade-off:** Less mature error handling ecosystem. Mitigated via comprehensive error boundaries & testing.

---

#### 5. Search Service → Python FastAPI

**Why Python?**
- **Elasticsearch client:** Python library superior to Java for DSL queries
- **ML-ready:** Recommendation engine uses scikit-learn, pandas
- **Data science team:** Existing Python expertise
- **Rapid prototyping:** Collaborative filtering algorithms in 2 weeks
- **Async-first:** FastAPI ASGI handles 500 concurrent search requests efficiently

**Considered Alternatives:**
- ❌ Java: Slower to iterate on ML features
- ❌ TypeScript: NumPy/SciKit equivalents inferior

**Risk:** Python GIL limits true parallelism. Mitigated via: (1) Async I/O for network (GIL-friendly), (2) Multi-worker Uvicorn, (3) Numpy-heavy operations release GIL.

---

#### 6. Notifications Service → TypeScript NestJS + Bull

**Why TypeScript + Node?**
- **Job queues native:** Bull (Redis-backed) is Node's best queue library
- **Concurrency:** Non-blocking I/O for 1000s of parallel email jobs
- **Slim footprint:** Stateless, quick to scale up/down
- **Integration:** Nodemailer, SES SDK well-supported

**Considered Alternatives:**
- ❌ Java: Over-engineered for fire-and-forget notifications
- ❌ Python: Celery/RQ less polished than Bull

**Trade-off:** Notifications failure rate 1% vs 0.1% (acceptable for non-critical feature).

---

### Polyglot Tradeoffs Summary

| Advantage | Disadvantage |
|-----------|--------------|
| **Best tool per job** | Operational complexity (3 runtimes) |
| **Team autonomy** (each team picks tools) | Onboarding steeper |
| **Fast iteration** (Python data team) | Harder to move code between services |
| **Type safety where it matters** (Java) | Learning curve for new hires |
| **Proven technologies** | No single "standard" to point to |

**Cost Impact:**
- +20% operational overhead vs monolithic Java
- -30% feature development time (right tool per team)
- Net: Justified for 30-50 person engineering org

---

## Technology Choices & Alternatives

### Compute: ECS vs Lambda vs Kubernetes

| Aspect | ECS Fargate | AWS Lambda | Kubernetes |
|--------|------------|-----------|-----------|
| **Startup Time** | 30-60s | 300-500ms | 5-30s |
| **Cold Start Penalty** | Low (always warm) | High (for Java) | Low |
| **Operational Overhead** | 4 hours/month | Near-zero | 40+ hours/month |
| **Cost @ 1000 RPS** | $2.5K/month | $8K+/month | $5K+/month |
| **Service-to-Service Latency** | <10ms | 100-200ms (cold start) | <5ms |
| **Team Skill** | Moderate | High | Very High |

**Decision: ECS Fargate**

**Why not Lambda?**
1. **Java cold start**: 5-10 seconds for Order Service (unacceptable)
2. **Always-on services**: Search Service needs persistent Elasticsearch connections
3. **Cost at scale**: Payment Service (synchronous, low latency SLA) better on Fargate
4. **Simplicity**: ECS is "EC2 without the ops" — managed container orchestration sufficient for this scale

**Why not Kubernetes?**
1. **Over-engineered**: K8s solves problems we don't have (multi-region, multi-cloud, 1000+ node clusters)
2. **Team cost**: Would require 1 dedicated SRE (not justified)
3. **Operational burden**: Network policies, resource management, debugging is harder
4. **AWS-native benefits**: Lose VPC integration, IAM, native observability

**Verdict:** ECS is the Goldilocks choice—just enough orchestration, maximum AWS integration, manageable ops.

---

### Database: RDS Aurora vs DynamoDB vs Self-Managed

| Aspect | RDS Aurora | DynamoDB | Self-Managed PostgreSQL |
|--------|-----------|----------|------------------------|
| **Consistency** | Strong (ACID) | Eventual (per item) | Strong (ACID) |
| **Query Flexibility** | SQL (complex joins) | Key-value only | SQL |
| **Cost (100GB)** | $400/month | $600+/month (write-heavy) | $150/month + $8/hour ops |
| **Backups** | Automatic (30 days) | Automatic (35 days) | Manual/custom |
| **HA/Failover** | Automatic (10-30s) | Built-in | Custom + expensive |
| **Operational Effort** | 1 hour/month | 30 minutes/month | 20+ hours/month |

**Decision: RDS Aurora PostgreSQL (Primary) + DynamoDB (Specific Use Cases)**

**Why RDS for core data?**
1. **Complex transactions:** Order creation requires ACID guarantees (inventory, payment, order atomic)
2. **Operational leverage:** AWS-managed backups, automatic failover, performance tuning
3. **Query complexity:** JOIN user data, inventory, pricing — DynamoDB insufficient
4. **Cost efficiency:** Calculated: RDS saves $500/month vs self-managed + 2 person-years ops effort

**Why DynamoDB for specific tables?**
- Payment idempotency cache (key-value, TTL, high throughput)
- User interactions (append-only, no complex queries)
- Event processing log (deduplication, eventual consistency fine)

**Why not self-managed?**
1. **Uptime**: 99.95% RDS SLA vs 95% self-managed (typical)
2. **Scaling complexity**: Add read replicas (1 click) vs manual PostgreSQL replication config
3. **Security**: AWS handles OS patches, backups encrypted, automated snapshots

---

### Search: Elasticsearch vs Algolia vs OpenSearch vs Solr

| Aspect | OpenSearch | Algolia | Elasticsearch | Solr |
|--------|-----------|---------|---------------|------|
| **Cost (100GB)** | $500/month | $3K+/month | $1K+/month | $400/month |
| **Setup Time** | 2 hours | 1 hour | 2 hours | 6 hours |
| **Query Language** | Lucene DSL | Limited | Lucene DSL | Lucene |
| **Operational Overhead** | Moderate (AWS-managed) | None (SaaS) | High | High |
| **Customization** | Excellent | Limited | Excellent | Good |
| **Recommendation Engine Support** | Good (Python DSL) | None | Good | Fair |

**Decision: Amazon OpenSearch (AWS-managed Elasticsearch fork)**

**Why OpenSearch?**
1. **AWS-native:** VPC integration, IAM auth, CloudWatch monitoring without extra config
2. **Cost:** 40% cheaper than Algolia, 50% of self-hosted Elasticsearch after ops cost
3. **Customization:** Need to implement hybrid recommendations (CF + content-based) — impossible in Algolia
4. **Scalability:** Can handle 500 QPS search + trending analytics; Algolia overkill

**Why not self-managed Elasticsearch?**
1. **Operational cost:** Requires 0.5 FTE for cluster management, tuning, backups
2. **AWS OpenSearch**: Same features, managed by AWS
3. **Break-even:** Managed costs amortized quickly with team productivity gains

---

### Messaging: EventBridge + SQS vs Kafka vs SNS

| Aspect | EventBridge+SQS | Apache Kafka | AWS SNS |
|--------|-----------------|--------------|---------|
| **Setup Complexity** | 1 hour | 40 hours | 2 hours |
| **Operations** | Minimal | 1+ FTE for cluster | Minimal |
| **Message Retention** | 4 days (SQS) | 7+ days (configurable) | None (SNS fan-out) |
| **Ordering Guarantees** | Per-shard (SQS FIFO) | Per-topic | None |
| **Cost (1000 msg/sec)** | $800/month | $2K+/month (ops) | $300/month |
| **Scalability** | Automatic | Manual | Automatic |

**Decision: EventBridge + SQS (Event Bus Pattern)**

**Why not Kafka?**
1. **Overkill for current scale:** 1000 RPS peak = 100K messages/day. Kafka designed for 10B+/day.
2. **Operational complexity:** Requires dedicated ops. EventBridge is 90% of functionality with 1% ops cost.
3. **Ordering:** Don't need total ordering across all events; per-service eventual consistency OK.

**Why not pure SNS?**
1. **No retries/DLQ:** SNS delivers once; SQS provides retry + DLQ for robustness.
2. **Timing:** SNS is "now or never"; SQS decouples timing.

**Verdict:** EventBridge (event rules/routing) + SQS (queue/retry/DLQ) = perfect for event-driven at this scale. Kafka is future work (if 100x scale happens).

---

### Caching: Redis vs Memcached vs DynamoDB Accelerator (DAX)

| Aspect | Redis | Memcached | DAX |
|--------|-------|-----------|-----|
| **Persistence** | Yes (RDB/AOF) | No | No |
| **Data Types** | Rich (sets, lists, streams) | Strings only | N/A (DynamoDB view) |
| **Consistency** | Eventually consistent | Eventually consistent | Strongly consistent |
| **Replication** | Sentinel/Cluster | Manual | Built-in |
| **Cost (32GB)** | $200/month | $150/month | $300/month |
| **Ops Complexity** | Moderate | Low | Very low |

**Decision: Redis (with Multi-AZ Sentinel)**

**Why Redis?**
1. **Session data:** Need persistence (JWT blacklist)
2. **Data structures:** Bull queues need Redis (list/hash/zset)
3. **Reliability:** Sentinel auto-failover works well at this scale
4. **Community:** Best-in-class Python/Node/Java clients

**Why not Memcached?**
1. **Persistence needed:** Session tokens must survive cache failure
2. **No ordered sets:** Can't implement trending products efficiently

**Why not DAX?**
1. **Over-engineered:** Only adds value if exclusively DynamoDB-backed (we're not)
2. **Higher cost:** More expensive than Redis without extra benefits

---

## Trade-offs Analysis

### Architecture Decision 1: Synchronous Payment Service

**Decision:** Order Service calls Payment Service synchronously (no queue).

**Rationale:**
- Payment must succeed before order is created (business requirement)
- Can't defer: Customer needs confirmation immediately
- Latency acceptable: 2-second SLA with circuit breaker

**Trade-off:**

| Advantage | Disadvantage |
|-----------|--------------|
| Simple model (request-response) | Tight coupling (Order depends on Payment availability) |
| Immediate feedback to customer | Higher latency P99 (payment RTT included) |
| Strong consistency (order ↔ payment atomic) | Payment Service becomes critical path |
| | If Payment fails, order fails (not degraded) |

**Mitigation:**
1. Circuit breaker (fail-fast, don't retry indefinitely)
2. 30-second timeout (don't wait forever)
3. Fallback: Return "payment pending" to customer (ask to retry)
4. Async reconciliation: 24-hour job to fix orphaned payments

**Alternative Considered:** Async with saga pattern
- **Benefit:** Decoupling, resilience
- **Cost:** 10x operational complexity, distributed debugging nightmare
- **Why rejected:** Not justified for 99.9% uptime target at current scale

---

### Architecture Decision 2: Single-Region (Active-Active Backup Only)

**Decision:** Primary in eu-west-1 (Ireland), warm standby in eu-west-2 (London).

**Rationale:**
- Cost: Multi-region active-active = 2x infrastructure cost
- Scale: 1-hour RTO acceptable (not mission-critical)
- Risk: Unlikely region failure vs likely transient issues

**Trade-off:**

| Advantage | Disadvantage |
|-----------|--------------|
| 50% cost savings | 1-hour downtime if region fails |
| Simpler operations | Manual failover (requires on-call action) |
| Easier debugging (single region) | Reputational damage ($100K/hour revenue loss estimate) |
| | Data consistency more complex (backups, not real-time sync) |

**Mitigation:**
1. Daily backup restore tests (verify backup integrity)
2. Terraform for rapid recreation (can spin up new region in 30 minutes)
3. DNS failover (manual, but quick)
4. Incident runbook: "Region Failure" (step-by-step)

**Alternative Considered:** Active-active multi-region
- **Benefit:** Zero-downtime failover, no data loss
- **Cost:** $500K+ extra/year infrastructure, 2x operations
- **Why rejected:** Not justified unless SLA requirement > 99.99%

---

### Architecture Decision 3: Event-Driven for Order Fulfillment

**Decision:** Order Service publishes event → Fulfillment Service consumes (SQS queue).

**Rationale:**
- Decoupling: Fulfillment can be down without affecting order creation
- Scalability: Can scale fulfillment independently
- Resilience: Retries built-in (SQS DLQ)

**Trade-off:**

| Advantage | Disadvantage |
|-----------|--------------|
| Independent scaling | Eventual consistency (order confirmation before fulfillment started) |
| Fault isolation (Fulfillment down ≠ Orders fail) | Complex distributed debugging |
| Operational flexibility (can pause fulfillment) | Harder to guarantee total ordering |
| | Queue management overhead (DLQs, monitoring) |

**Mitigation:**
1. CloudWatch alarms on SQS queue depth
2. Runbooks for queue backup scenarios
3. Comprehensive logging with correlation IDs
4. Acceptance: Eventual consistency is feature, not bug

**Alternative Considered:** Synchronous (Order → Fulfillment RPC)
- **Benefit:** Simpler model, immediate feedback
- **Cost:** Fulfillment availability becomes order availability (tight coupling)
- **Why rejected:** Defeats purpose of microservices

---

## Cost-Complexity Matrix

### Operating Expense (Monthly)

| Component | Cost | % of Total | Justification |
|-----------|------|-----------|---------------|
| **ECS Fargate** | $3.5K | 18% | 3 services × 3 tasks, 2 vCPU, 4GB RAM |
| **RDS Aurora** | $3.2K | 16% | 3 instances (r6g.xlarge), 100GB storage, backups |
| **ElastiCache Redis** | $1.8K | 9% | 3 nodes (r7g.xlarge), multi-AZ, snapshots |
| **OpenSearch** | $1.2K | 6% | 3 nodes (m6g.xlarge), 100GB EBS, backups |
| **S3 + CloudFront** | $0.6K | 3% | 1TB storage, 10TB bandwidth/month |
| **RDS Proxy** | $0.2K | 1% | Connection pooling |
| **EventBridge + SQS** | $0.4K | 2% | Event routing + queues |
| **DynamoDB** | $0.3K | 2% | Pay-per-request pricing |
| **Data Transfer** | $1.2K | 6% | NAT gateway, inter-region backup |
| **CloudWatch + Logs** | $0.8K | 4% | Metrics, alarms, log retention |
| **Secrets Manager + KMS** | $0.3K | 2% | Secrets + encryption keys |
| **NAT Gateways** | $0.8K | 4% | 1 per AZ (3 AZs) |
| **Other (miscellaneous)** | $0.9K | 5% | VPN, DNS, SSL certs |
| **TOTAL** | **$16.2K** | **100%** | Per month |

### Annual Expense: ~$194K

**Scaling Model:**
- At 2x traffic: +$5K (RDS read replicas, more ECS tasks)
- At 10x traffic: +$30K (multi-region, dedicated ops)

**Cost Optimization Opportunities (Next 6 Months):**
1. Reserved Instances (1-year): -$3K/month (RDS, ECS, ElastiCache)
2. Spot instances for non-critical: -$800/month (notifications, off-peak search)
3. Data transfer optimization: -$400/month (CloudFront caching)
4. Total potential savings: -$4.2K/month = **-26%**

---

### Operational Complexity (FTE/Month)

| Role | Effort | Time Allocation | Responsibilities |
|------|--------|-----------------|------------------|
| **DevOps Engineer** | 1.0 FTE | 25h/month | Terraform, CI/CD, monitoring, incidents |
| **On-Call Engineer** | 0.5 FTE | 12h/month | Incident response, runbooks, escalation |
| **SRE (Part-time)** | 0.3 FTE | 6h/month | Capacity planning, chaos engineering, postmortems |
| **Developer (Ops-aware)** | 0.2 FTE | 5h/month | Service-specific deployment, debugging |
| **Total** | **2.0 FTE** | **48h/month** | Platform operations |

**Complexity by Layer:**

| Layer | Complexity | Reason |
|-------|-----------|--------|
| **Application** | Medium | 3 languages, polyglot debugging harder |
| **Orchestration** | Low | ECS simpler than Kubernetes |
| **Database** | Low | RDS managed, minimal tuning needed |
| **Messaging** | Low | EventBridge + SQS straightforward |
| **Observability** | High | 3 runtimes × custom logging, tracing overhead |
| **Networking** | Medium | VPC, security groups, NACLs, mTLS |
| **Security** | Medium | IAM policies, secrets rotation, compliance |
| **TOTAL** | **Medium-High** | Justified by operational leverage (2 FTE for 30-50 person eng org) |

---

## Security & Compliance

### Threat Model & Mitigations

#### Threat 1: SQL Injection (RDS)

**Risk:** Attacker injects malicious SQL, compromises data

**Mitigations:**
- ✅ **Parameterized queries:** All services use ORM (Hibernate/SQLAlchemy/TypeORM) with parameterized queries
- ✅ **Input validation:** Request DTOs with @Valid, @Pattern annotations
- ✅ **Database user permissions:** Application user has only SELECT/INSERT/UPDATE/DELETE, no DROP/ALTER
- ✅ **Audit logging:** All queries logged to CloudTrail

**Residual Risk:** Medium (relies on developers using ORM correctly)

---

#### Threat 2: Payment Card Data Breach

**Risk:** Attacker gains access to Stripe API keys, exfiltrates payment data

**Mitigations:**
- ✅ **Never store PCI data:** Stripe tokenizes cards; we store tokens only
- ✅ **Secrets Manager:** API keys encrypted, rotated quarterly
- ✅ **mTLS:** Service-to-service encryption
- ✅ **Secrets rotation:** Automated via Lambda trigger
- ✅ **Least privilege:** Payment Service only calls Stripe (no DB access)

**Residual Risk:** Low

---

#### Threat 3: Distributed Denial of Service (DDoS)

**Risk:** Attacker sends 10M requests/sec, brings down platform

**Mitigations:**
- ✅ **AWS Shield Standard:** Free Layer 3/4 DDoS protection
- ✅ **AWS WAF:** Rate limiting (2000 req/5 min per IP)
- ✅ **CloudFront:** Global edge caching, traffic filtering
- ✅ **Application-level:** Service circuit breakers, graceful degradation
- ⚠️ **Shield Advanced:** Not justified ($3K/month) for current revenue

**Residual Risk:** Medium (sustained attack could impact; Shield Standard less effective for large botnets)

---

#### Threat 4: Account Takeover (ATO)

**Risk:** Attacker compromises user password, places fraudulent orders

**Mitigations:**
- ✅ **MFA:** Mandatory for sensitive operations (via Cognito)
- ✅ **Bcrypt:** Passwords hashed with workfactor=12 (0.3 seconds per hash)
- ✅ **Session expiry:** JWTs expire in 15 minutes (short-lived)
- ✅ **Token blacklist:** Logout revokes token immediately
- ✅ **Anomaly detection:** Fraud rules (new IP + large order = manual review)

**Residual Risk:** Low (except phishing, which is user-level)

---

#### Threat 5: Infrastructure Compromise (EC2/RDS)

**Risk:** Attacker gains shell access to ECS instance, pivots to RDS

**Mitigations:**
- ✅ **No SSH:** ECS instances not SSH-accessible; use AWS Systems Manager Session Manager
- ✅ **IAM roles:** Each service has minimal permissions (least privilege)
- ✅ **Network isolation:** RDS in private subnets (no internet access)
- ✅ **Security groups:** RDS accepts connections only from ECS tasks
- ✅ **Encryption in transit:** TLS 1.3 for all connections

**Residual Risk:** Low (defense-in-depth architecture)

---

### Compliance Framework

**Applicable Standards:**
- GDPR (EU customers): Data retention, right to deletion, data portability
- PCI-DSS (Stripe): Payment data handling
- SOC 2 (future requirement for enterprise contracts)

**Current Status:**

| Standard | Requirement | Status | Gap |
|----------|-------------|--------|-----|
| **GDPR** | 30-day data deletion | ✅ Implemented | Email customer to confirm |
| **GDPR** | Data access export | ✅ Implemented | Job exports user data as JSON |
| **GDPR** | Data breach notification | ✅ Planned | Process TBD, 72-hour response |
| **PCI-DSS** | Payment data isolation | ✅ Implemented | Stripe handles; we store tokens only |
| **SOC 2** | Change management | ⚠️ Partial | GitHub-based, needs formal approval process |
| **SOC 2** | Access controls | ⚠️ Partial | IAM in place; need audit trail |
| **SOC 2** | Incident response | ⚠️ Partial | Runbooks in place; need postmortem template |

**Roadmap (3 Months):**
- Implement SOC 2 audit trail (CloudTrail + CloudWatch Logs)
- Formalize change approval process
- Document incident response procedures

---

## Scalability Limits & Bottlenecks

### Current Architecture Limits (10K CCU, 1K RPS Peak)

| Component | Limit | Current Usage | Headroom |
|-----------|-------|----------------|----------|
| **RDS Aurora** | 256 connections | 45 connections | 82% |
| **RDS Storage** | 64 TB | 100 GB | 99.8% |
| **ElastiCache Nodes** | 300 GB (3 nodes) | 45 GB | 85% |
| **OpenSearch Nodes** | 300 GB (3 nodes) | 120 GB | 60% |
| **ALB Target Throughput** | Unlimited* | 1K RPS | Excellent |
| **ECS Task Density** | 30 tasks per instance | 12 tasks | 60% |
| **EventBridge Event Rate** | 100K/sec* | 500/sec | 99.5% |
| **SQS Throughput** | 300K msg/sec* | 100 msg/sec | 99.9% |

*Soft limits; can increase via support ticket

### Predicted Bottlenecks @ 10x Scale (10K RPS)

#### Bottleneck 1: RDS CPU (Most Likely)

**Symptom:** Order creation latency p99 > 3 seconds

**Root Cause:** Complex queries (inventory check + order creation) CPU-bound

**Mitigation:**
1. **Read replicas:** Add 2 read-only instances for read-heavy queries
2. **Query optimization:** Index on (order_id, user_id, status) tuple
3. **Caching:** Cache product prices, tax rates (hits reduce queries 40%)
4. **Scaling:** Upgrade to db.r7g.2xlarge (2x CPU)

**Cost Impact:** +$2K/month for read replicas + larger instances

---

#### Bottleneck 2: ElastiCache Memory @ 10x

**Symptom:** Cache eviction rate > 30%, hit ratio drops to 60%

**Root Cause:** User profiles + product details cached; at 10x scale, exceeds memory

**Mitigation:**
1. **Eviction policy change:** LRU → LFU (frequency-based)
2. **TTL reduction:** Profiles 1h → 30m
3. **Selective caching:** Cache only top 1K products (80/20 rule)
4. **Scale up:** 3 × r7g.xlarge → 3 × r7g.2xlarge

**Cost Impact:** +$300/month

---

#### Bottleneck 3: Elasticsearch Query Latency @ 10x

**Symptom:** Search p99 latency > 1000ms (above SLO)

**Root Cause:** Index size grows; aggregations (facets) become expensive

**Mitigation:**
1. **Shard count increase:** 3 → 5 shards (parallelize queries)
2. **Aggregation optimization:** Cache facets (5-minute TTL)
3. **Index rotation:** products-2026-01 → products-2026-02 (time-based)
4. **Auto-scaling:** OpenSearch node count 3 → 5

**Cost Impact:** +$800/month

---

#### Bottleneck 4: Network Bandwidth @ 10x

**Symptom:** Data transfer costs spike from $1.2K to $8K/month

**Root Cause:** More traffic through NAT gateways + inter-AZ communication

**Mitigation:**
1. **CloudFront aggressive caching:** Static assets, product images CDN
2. **VPC endpoints:** S3, DynamoDB gateway endpoints (free egress)
3. **Data compression:** Gzip API responses (50% reduction)
4. **Geographic expansion:** Content delivery network (if multi-region)

**Cost Impact:** -$4K/month (savings from efficiency)

---

### Path to 100x Scale (100K RPS = 8B Orders/Day)

**Architecture would break at 10x. Requires redesign:**

| Change | Rationale | Cost |
|--------|-----------|------|
| **Multi-region active-active** | Single region RTO unacceptable | +$500K/year infra |
| **Sharded database** | Monolithic RDS cannot scale to 10B rows | +2 engineers, 6 months |
| **Kafka/Pulsar** | EventBridge insufficient for high-throughput streaming | +$500K/year ops |
| **Kubernetes** | ECS cannot manage 1000+ tasks efficiently | +1 SRE full-time |
| **Data warehouse** | Analytics queries too expensive on operational DB | +$200K/year Redshift |

**Verdict:** At 100x scale, would rebuild from scratch with event-streaming architecture (Kafka, Flink, distributed databases). Current architecture is local maximum for 1K-10K RPS range.

---

## Evolution Paths (1-3 Years)

### Year 1: Scale to 5K RPS (500M Orders/Year)

**Initiatives:**

1. **Database read replicas**
   - Add 2 read-only Aurora instances
   - Offload report queries, analytics
   - Cost: +$1.5K/month

2. **Search improvement**
   - Implement ML-based personalized ranking
   - A/B test recommendations (Python data team)
   - Cost: +$100K (engineering)

3. **Payment optimization**
   - Implement 3D Secure 2.0 (fraud prevention)
   - Offer multiple payment methods (PayPal, Apple Pay)
   - Cost: +$50K (engineering + integration)

4. **Compliance**
   - Achieve SOC 2 Type II certification
   - GDPR compliance audit
   - Cost: +$80K (consulting + engineering)

**Total Cost Impact:** +$10K/month infrastructure + $230K engineering

---

### Year 2: Scale to 10K RPS (1B Orders/Year)

**Initiatives:**

1. **Multi-region active-active**
   - Secondary region (us-east-1) with real-time replication
   - Global load balancing (Route 53)
   - Cost: +$500K/year infrastructure

2. **Messaging upgrade**
   - Evaluate Kafka for real-time analytics (not operational)
   - Keep EventBridge for event routing
   - Kafka cluster in separate environment (analytics-only)
   - Cost: +$200K/year

3. **GraphQL gateway**
   - Consider BFF (Backend-for-Frontend) pattern
   - GraphQL layer for mobile team efficiency
   - Keep REST APIs operational
   - Cost: +$100K (engineering)

4. **Data warehouse**
   - Amazon Redshift for BI/analytics queries
   - Separate from operational database
   - Cost: +$150K/year

**Total Cost Impact:** +$700K infrastructure + $100K engineering

---

### Year 3+: Enterprise Scale (100K+ RPS)

**Architectural Pivot Required:**

1. **Event-streaming backbone**
   - Replace EventBridge with Kafka (or AWS MSK)
   - Implement Kappa architecture (streaming compute)
   - Real-time analytics on all events
   - Cost: +$1.2M/year

2. **Polyglot persistence**
   - Sharded databases per business domain
   - Temporal database for event sourcing
   - Feature store for ML
   - Cost: +$800K/year

3. **Service mesh**
   - AWS App Mesh or Istio
   - Better observability, canary deployments
   - Mutual TLS between all services
   - Cost: +$300K (engineering + infra)

4. **Global expansion**
   - Multi-region active-active worldwide
   - Local payment methods per region
   - Data residency compliance (GDPR, CCPA)
   - Cost: +$2M/year infrastructure

**Verdict:** This is fundamentally different architecture. At this scale, you've earned the right to rebuild.

---

## Decision Framework

### How We Made These Choices

#### Principle 1: "Choose Boring Technology"

**Bias:** Pick proven, boring solutions over innovative, shiny ones.

**Application:**
- Spring Boot (2015, battle-tested) over Quarkus (2019, emerging)
- PostgreSQL (30+ years) over NewSQL (Cockroach DB, TiDB)
- ECS (2014) over Kubernetes (2014, but 10x more complex)

**Outcome:** Less exciting, more reliable. Team can focus on product, not infrastructure surprises.

---

#### Principle 2: "Optimize for Team Scale"

**Bias:** Decisions that scale with organization, not just workload.

**Application:**
- Polyglot architecture: Each team picks tools they're expert in (Java, Python, Node)
- Managed services: Reduce operational burden (no DBAs needed)
- ECS over K8s: 1 DevOps engineer can operate; K8s needs 5+

**Outcome:** 30-person team can own this architecture. Clear responsibility boundaries.

---

#### Principle 3: "Cost of Complexity vs Benefit"

**Bias:** Accept higher operational cost if engineering velocity > operations cost.

**Calculation:**
- Polyglot: +20% ops overhead
- Monolith: +30% engineering time (context-switching, shared release cycles)
- Outcome: Use polyglot (net positive for this team structure)

---

#### Principle 4: "Observability > Simplicity"

**Bias:** Add logging/tracing/metrics even if it increases operational cost.

**Rationale:**
- Distributed systems are hard to debug
- Comprehensive observability reduces MTTR (Mean Time To Recovery)
- Every on-call incident saved = $10K+ value
- Cost: +$3K/month observability vs +$50K incident cost

**Outcome:** Extensive instrumentation justified.

---

#### Principle 5: "Premature Optimization Is Evil"

**Bias:** Don't optimize for 100x scale until you hit 10x.

**Application:**
- Not implementing Kafka yet (EventBridge sufficient)
- Not sharding databases yet (RDS read replicas sufficient)
- Not multi-region active-active yet (RPO/RTO targets allow)

**Outcome:** Reduces complexity today; explicit upgrade path when needed.

---

## Architectural Debt & Mitigation

### Debt Item 1: Polyglot Operational Complexity

**Debt:** Operating 3 different runtimes (Java, Python, Node) requires more operational tooling.

**Interest Paid:**
- +$2K/month in CloudWatch, logging costs
- +40 hours/month DevOps time (vs monolithic Java would be -20 hours/month)
- +2 weeks onboarding time for new engineers

**Mitigation Strategy:**
1. **Comprehensive documentation:** Runbooks for each language's failure modes
2. **Automation:** Identical deployment pipeline for all languages (CI/CD hides differences)
3. **Staging environment:** Test changes in all three languages before prod
4. **Team rotation:** Developers spend 20% time in unfamiliar languages (build empathy)

**Decision Point (Year 2):** If operational burden exceeds 1 FTE, consider migrating non-critical services to primary language (Java).

---

### Debt Item 2: Event-Driven Eventual Consistency

**Debt:** Order status updates are eventual (not immediate). Creates complexity.

**Interest Paid:**
- +15% more debugging (where's the event?)
- +5% operational incidents (queue backup, message loss)
- +$2K/month monitoring/alerting for queues

**Mitigation Strategy:**
1. **Runbooks:** Top 3 incidents are queue-related (documented solutions)
2. **Alarming:** Alert on queue depth; auto-scale SQS processing
3. **Tracing:** Correlation IDs enable end-to-end event tracing
4. **Acceptance:** Eventual consistency is deliberate tradeoff for resilience

**When to Reconsider:** If customers complain about order status delay > 5 minutes, reconsider synchronous model.

---

### Debt Item 3: RDS Monolithic Datastore

**Debt:** All data (users, orders, inventory, analytics) in single database.

**Interest Paid:**
- Query contention (reporting queries slow down transactional workloads)
- Scaling limitation (can't shard without major refactoring)
- Backup/restore challenges (100GB dump gets large)

**Mitigation Strategy (Proactive, not yet implemented):**
1. **Read replicas for reporting:** Offload analytics queries (Year 1)
2. **Separate analytics database:** Redshift for BI queries (Year 2)
3. **Event sourcing:** Audit log of all state changes (Year 2)
4. **Sharding plan:** Document how to split by tenant/region (Year 3)

**When to Refactor:** At 10K RPS (1B rows), single DB becomes bottleneck. Estimated: 6-month refactoring project.

---

### Debt Item 4: Single-Region Availability

**Debt:** Region failure = full outage. 1-hour RTO acceptable today but may not be tomorrow.

**Interest Paid:**
- +$50K annual revenue loss per hour of downtime
- Reputational damage (customer trust)
- Not HIPAA/FedRAMP compliant (future enterprise customers may need multi-region)

**Mitigation Strategy (Reactive, triggered by incident or customer demand):**
1. **Year 1:** Test failover to secondary region (manual, documented)
2. **Year 2:** Automate failover (DNS, infrastructure as code ready)
3. **Year 3+:** Active-active multi-region (both regions serving traffic)

**When to Implement:** If enterprise customer requires < 15-minute RTO, prioritize immediate failover automation (6-week project).

---

### Debt Item 5: Limited Rollback Capability

**Debt:** Failed deployments might not rollback cleanly (schema migrations, data transforms).

**Interest Paid:**
- Deployment fear (can't quickly rollback if things break)
- Longer incident resolution (can't just revert)
- Slower iteration (more validation before deploy)

**Mitigation Strategy:**
1. **Schema versioning:** Support N and N-1 schema versions simultaneously
2. **Feature flags:** Decouple deployment from feature activation
3. **Canary deployments:** 5% → 25% → 50% → 100% traffic ramp-up
4. **Blue-green:** Always have previous version running as fallback

**Implementation Timeline:** 6 weeks to implement across all 6 services.

---

## Interview Questions & Answers

### Q1: "Why didn't you use Kubernetes?"

**Answer:**

Kubernetes is amazing for:
- 100+ node clusters (scheduling complexity)
- Multi-cloud deployments (vendor lock-in avoidance)
- Strict resource guarantees (high-frequency trading, real-time systems)

But for our case:
- **Scale:** 18 total ECS tasks (easily managed). K8s overhead unjustified.
- **AWS-native:** Why abstract away AWS services? We're committed to AWS.
- **Team cost:** K8s requires dedicated SRE team. Current 2-person ops can manage ECS.
- **Learning curve:** 6-month ramp-up for K8s; 1-week for ECS.

**Trade-off:** We accepted less flexibility for 80% cost savings and simpler operations.

**When we'd reconsider:** If hiring remote/globally distributed teams with different cloud preferences, K8s abstraction becomes valuable. At 100x scale, the operational overhead amortizes.

---

### Q2: "Isn't polyglot architecture a nightmare?"

**Answer:**

Yes and no.

**Challenges:**
- Can't move code between services (language-specific)
- Onboarding takes longer (3 languages to learn)
- Operational tooling must support 3 runtimes
- Debugging distributed system is hard; multiply by 3

**But:**
- **Right tool wins:** Python for ML, Java for transactions, Node for queues. Forced monolith would be wrong tool for some jobs.
- **Team autonomy:** Data team can iterate on recommendations independently from transaction team.
- **Competitive advantage:** Fast iteration on feature (each team expert in their language).

**Lever we'd pull:** If operational burden exceeds 1 FTE by Year 2, migrate to primary language (Java). Current pain is acceptable because engineering velocity benefit > operational cost.

---

### Q3: "Why EventBridge + SQS instead of Kafka?"

**Answer:**

**Kafka shines when:**
- Message volume: 100K+ events/sec (ours: 500/sec)
- Need durable event log (replay, time-travel queries)
- Streaming computation required (Flink topology)
- Organization already has Kafka expertise

**EventBridge + SQS wins when:**
- Moderate throughput (100-10K events/sec)
- Don't need replay (events consumed once)
- Operational simplicity matters (managed service)
- AWS-native team

**Our math:**
- EventBridge + SQS: $400/month, 0.2 FTE ops
- Kafka cluster: $2K/month (self-managed) or $5K/month (AWS MSK), 1 FTE ops
- Break-even: ~1800 events/sec, neither architecture clearly wins

**Future:** If we hit 10K events/sec AND need replay/streaming, migrate to Kafka. Explicit upgrade path documented.

---

### Q4: "How do you handle distributed transactions?"

**Answer:**

**The setup:**
- Order creation involves: Reserve inventory (Catalog DB) → Charge payment (Stripe) → Create order (Order DB)
- Three independent systems; how to ensure atomicity?

**Our solution: Saga pattern with compensations**

1. **Happy path:** Inventory reserved → Payment charged → Order created ✅
2. **Failure at step 2:** Inventory reserved, but payment declined
   - Compensation: Release inventory reservation
   - Customer sees: "Card declined, try another" (retryable)
3. **Failure at step 3:** Inventory reserved, payment charged, but order DB fails
   - Compensation: Refund payment, release inventory
   - BUT: Payment already charged in Stripe (irreversible)
   - Recovery: Async job detects "payment without order", creates order retroactively

**Trade-off:** Consistency is *eventual*, not immediate. Order ≠ Payment for milliseconds, but guaranteed to reconcile within 5 minutes (retry job).

**When this breaks down:** If we needed distributed transactions across 10+ services (complex failure modes), we'd reconsider monolithic architecture. At 3 services, saga is manageable.

---

### Q5: "What's the biggest architectural risk?"

**Answer:**

**Risk 1: RDS becomes bottleneck (Probability: 60% by Year 2)**
- At 10K RPS, query load exceeds single RDS cluster capacity
- Mitigation: Read replicas, query optimization, caching
- Explicit upgrade path: Sharding (6-month project)

**Risk 2: Operational complexity at multi-region (Probability: 40% by Year 2)**
- Multi-region requires deployment to 2 places, double monitoring
- Mitigation: Infrastructure-as-code (Terraform) reduces manual work
- Explicit upgrade path: GitOps pipeline automates multi-region deployment

**Risk 3: Polyglot debugging nightmare (Probability: 30% in Year 1)**
- Tracing across 3 languages is hard
- Mitigation: Comprehensive observability (X-Ray, correlation IDs)
- Explicit upgrade path: Standardize on primary language if exceeds 1 FTE pain

**Most likely failure mode:** RDS CPU exhaustion causes order latency spikes. Have read replicas + query optimization plan ready.

---

## Conclusion: Why This Architecture

### Core Thesis

> **Optimize for team productivity and operational clarity, not for absolute performance or minimal complexity.**

This architecture is NOT:
- Cutting-edge (ECS vs Kubernetes, EventBridge vs Kafka)
- Minimal complexity (3 languages, distributed events)
- Globally scalable at day one (single region)

This architecture IS:
- **Pragmatic:** Right tool per job, managed services reduce ops burden
- **Autonomous:** Teams pick their languages, deploy independently
- **Observable:** Comprehensive logging/tracing aids debugging
- **Evolvable:** Explicit upgrade paths for each component (read replicas, multi-region, sharding)

### The Wager

We're betting that:

1. **Team productivity** (engineers shipping features) > operational cost
2. **Eventual consistency** is acceptable for 99.9% of use cases
3. **Managed services** save more ops time than they cost money
4. **Event-driven** architecture scales gracefully to 10x before major refactoring needed
5. **Boring technology** lets us focus on product, not infrastructure fires

### The Escape Hatches

We've left ourselves explicit outs:
- **Bottleneck:** RDS hits CPU limit → Add read replicas (2 weeks)
- **Complexity:** Polyglot ops exceeds tolerance → Standardize on Java (6 weeks)
- **Scale:** 10K RPS approaching limits → Plan sharding + Kafka (3 months planning)
- **Availability:** Customer demands < 15 min RTO → Automate failover (6 weeks)

### Final Thought

> Perfect architecture is the enemy of good. This architecture is good for the next 18 months and has explicit upgrade paths. We'll revisit at 5K RPS (Year 1 review) and 10K RPS (Year 2 redesign decision).

---

**Document Version:** 1.0.0  
**Status:** Iteration 8 — Final, Production-Ready  
**Prepared for:** Technical interviews, architecture reviews, team onboarding  
**Next:** Operational runbooks, incident response procedures