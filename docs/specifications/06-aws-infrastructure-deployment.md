# AWS Infrastructure & Deployment Specification

**Version:** 1.0.0  
**Last Updated:** 2026-01-18  
**Status:** Iteration 6 — AWS Infrastructure & Deployment  
**Audience:** DevOps Engineers, Cloud Architects, Backend Engineers (Cursor IDE)  
**Prerequisite**: End-to-End Flow Specification v1.0.0

---

## Table of Contents

1. [Overview](#overview)
2. [Compute Strategy](#compute-strategy)
3. [Containerization & Runtime](#containerization--runtime)
4. [API Ingress & Routing](#api-ingress--routing)
5. [Messaging Infrastructure](#messaging-infrastructure)
6. [Database Infrastructure](#database-infrastructure)
7. [Search Infrastructure](#search-infrastructure)
8. [Storage & CDN](#storage--cdn)
9. [Environment Separation](#environment-separation)
10. [Networking & Security](#networking--security)
11. [Observability & Monitoring](#observability--monitoring)
12. [Deployment Pipeline](#deployment-pipeline)
13. [Disaster Recovery](#disaster-recovery)
14. [Cost Optimization](#cost-optimization)
15. [Implementation Checklist](#implementation-checklist)

---

## Overview

### Architecture Principles

- ✅ **Polyglot services**: Java (Spring Boot) on ECS, Python (FastAPI) on ECS, TypeScript (NestJS) on ECS
- ✅ **Containerized**: Docker images in ECR, orchestrated by ECS (not Kubernetes - overkill for this scale)
- ✅ **Managed services**: RDS (PostgreSQL), Elasticsearch (managed), DynamoDB (serverless)
- ✅ **Event-driven**: EventBridge + SQS for async workflows
- ✅ **High availability**: Multi-AZ deployment, auto-scaling, circuit breakers
- ✅ **Cost-conscious**: Spot instances for non-critical services, reserved capacity for baseline
- ✅ **Production-ready**: TLS 1.3+, mTLS for service-to-service, secrets management via Secrets Manager

### Target Scale

| Metric | Value |
|--------|-------|
| Concurrent Users | 10,000 |
| Requests/sec (peak) | 1,000 RPS |
| Order Volume/day | 50,000 orders |
| Search QPS | 500 QPS |
| Data Growth/year | 200 GB |
| RTO | 1 hour |
| RPO | 15 minutes |

### AWS Regions & Availability

**Primary Region:** eu-west-1 (Ireland)  
**Backup Region:** eu-west-2 (London)  
**Deployment Pattern:** Active-active in primary, warm standby in backup

---

## Compute Strategy

### Decision Matrix: ECS vs Lambda vs EC2

| Service | Choice | Reasoning |
|---------|--------|-----------|
| **Order Service (Java)** | ECS + Fargate | Stateful, long-lived connections to DB, predictable load |
| **User Service (Java)** | ECS + Fargate | Stateful, Redis caching, profile pictures → S3 |
| **Catalog Service (Java)** | ECS + Fargate | Stateful, inventory caching, EventBridge pub/sub |
| **Payment Service (TS)** | ECS + Fargate | Synchronous RPC from Order Service, strict SLA (30s) |
| **Search Service (Python)** | ECS + Fargate | CPU-intensive (Elasticsearch queries), long-lived ES connections |
| **Notifications Service (TS)** | ECS + Fargate + Bull | Message queue workers, long-running email jobs |
| **API Gateway** | ALB | Cost-effective layer 7 routing, native ECS integration |
| **Event Processing** | Lambda (SQS→Lambda) | Transient workloads, auto-scaling to zero, pay-per-invoke |
| **Scheduled Jobs** | EventBridge + Lambda | DynamoDB cleanup, reconciliation, reporting |

### ECS Cluster Architecture

AWS Region: eu-west-1
├── ECS Cluster: bookstore-prod
│   ├── Availability Zone: eu-west-1a
│   │   ├── EC2 Instance (t3.2xlarge)
│   │   │   ├── CPU: 8 vCPU (reservable, on-demand fallback)
│   │   │   ├── Memory: 32 GB
│   │   │   ├── EBS: 100 GB gp3
│   │   │   └── Security Group: cluster-node-sg
│   │   └── Capacity: 4 tasks (shared)
│   │
│   ├── Availability Zone: eu-west-1b
│   │   ├── EC2 Instance (t3.2xlarge)
│   │   └── Capacity: 4 tasks (shared)
│   │
│   └── Availability Zone: eu-west-1c
│       ├── EC2 Instance (t3.2xlarge)
│       └── Capacity: 4 tasks (shared)
│
├── ECS Services:
│   ├── order-service (3 tasks, min 3, max 10)
│   ├── user-service (2 tasks, min 2, max 8)
│   ├── catalog-service (2 tasks, min 2, max 8)
│   ├── payment-service (2 tasks, min 2, max 6)
│   ├── search-service (3 tasks, min 3, max 12)
│   └── notifications-service (2 tasks, min 2, max 8)
│
└── Service Discovery: AWS Cloud Map (service-to-service DNS)

### Compute Specifications per Service

#### Order Service

**Task Definition:**
- Image: `bookstore/order-service:latest` (ECR)
- CPU: 1024 (1 vCPU)
- Memory: 2048 MB (2 GB)
- Logging: CloudWatch `/aws/ecs/order-service`
- Environment:
  SPRING_PROFILES_ACTIVE=prod
  DB_HOST=postgres.bookstore.local
  DB_PORT=5432
  DB_NAME=bookstore
  REDIS_URL=redis://redis-cluster.bookstore.local:6379
  SERVICE_JWT_SECRET=*** (from Secrets Manager)

**Service Definition:**
- Desired Count: 3 (high availability)
- Min: 3, Max: 10 (scale up on high load)
- Placement: Spread across AZs
- Health Check: HTTP /health → 200 OK (5s interval, 2 failures = unhealthy)
- Circuit Breaker: Enabled (stops deployment if new task fails health check)
- Auto-scaling Policy:
  - Scale up: CPU > 70% or Memory > 80% for 2 minutes
  - Scale down: CPU < 30% for 5 minutes
  - Cooldown: 3 minutes

**Resource Isolation:**
- Network: VPC (bookstore-prod-vpc)
- Security Group: order-service-sg (inbound: 8080 from ALB)
- CPU reservation: 1024 (guaranteed)
- Memory reservation: 2048 MB (guaranteed)

#### Search Service (Higher Resources)

**Task Definition:**
- Image: `bookstore/search-service:latest` (ECR)
- CPU: 2048 (2 vCPU) — CPU-intensive search queries
- Memory: 4096 MB (4 GB)
- Elasticsearch Connection Pool: 20 connections
- Logging: CloudWatch `/aws/ecs/search-service`

**Service Definition:**
- Desired Count: 3
- Min: 3, Max: 12 (search is scalable)
- Auto-scaling Policy:
  - Scale up: CPU > 60% or Search Latency p99 > 500ms
  - Scale down: CPU < 20% for 10 minutes

Why higher resources?
- Elasticsearch queries CPU-intensive
- Aggregations (facets) need memory
- Concurrent search requests
- Scoring algorithms expensive

#### Notifications Service (Bursty)

**Task Definition:**
- Image: `bookstore/notifications-service:latest` (ECR)
- CPU: 512 (0.5 vCPU)
- Memory: 1024 MB (1 GB)
- Bull Queue Workers: 5 (process jobs in parallel)
- Logging: CloudWatch `/aws/ecs/notifications-service`

**Service Definition:**
- Desired Count: 2 (minimum for redundancy)
- Min: 2, Max: 8
- Scaling Policy:
  - Queue Depth metric: If backlog_messages > 100, scale up
  - Cooldown: 1 minute

Why lower baseline?
- Notifications are async (queued in Bull)
- Bursty load (spikes after order creation)
- Can handle 100s of jobs per task
- Scale quickly on queue buildup

### Scaling Policies (Terraform/CDK)

# Auto Scaling Target
resource "aws_appautoscaling_target" "order_service" {
  max_capacity       = 10
  min_capacity       = 3
  resource_id        = "service/bookstore-prod/order-service"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

# Scale Up: CPU > 70%
resource "aws_appautoscaling_policy" "order_service_scale_up_cpu" {
  name               = "order-service-scale-up-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.order_service.resource_id
  scalable_dimension = aws_appautoscaling_target.order_service.scalable_dimension
  service_namespace  = aws_appautoscaling_target.order_service.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 70.0
    scale_out_cooldown = 180  # 3 minutes
    scale_in_cooldown  = 300  # 5 minutes
  }
}

# Scale Up: Memory > 80%
resource "aws_appautoscaling_policy" "order_service_scale_up_memory" {
  name               = "order-service-scale-up-memory"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.order_service.resource_id
  scalable_dimension = aws_appautoscaling_target.order_service.scalable_dimension
  service_namespace  = aws_appautoscaling_target.order_service.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
    target_value       = 80.0
    scale_out_cooldown = 180
    scale_in_cooldown  = 300
  }
}

# Scale Down: CPU < 30% for 10 minutes
resource "aws_appautoscaling_policy" "order_service_scale_down_cpu" {
  name               = "order-service-scale-down-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.order_service.resource_id
  scalable_dimension = aws_appautoscaling_target.order_service.scalable_dimension
  service_namespace  = aws_appautoscaling_target.order_service.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 30.0
    scale_out_cooldown = 60
    scale_in_cooldown  = 600  # 10 minutes
  }
}

---

## Containerization & Runtime

### Docker Image Strategy

#### Base Images (Multi-Stage Builds)

**Order Service (Java):**

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build
COPY . .

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Build application
RUN mvn clean package -DskipTests -Dspring.profiles.active=prod

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Non-root user
RUN adduser -D -u 1000 appuser
USER appuser

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /build/target/order-service-*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Java options for container (low memory footprint)
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled -XX:+DisableExplicitGC -Xms512m -Xmx2048m"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

**Search Service (Python):**

# Stage 1: Build
FROM python:3.11-slim AS builder

WORKDIR /build
COPY requirements.txt .

# Create virtual environment
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

RUN pip install --no-cache-dir -r requirements.txt

# Stage 2: Runtime
FROM python:3.11-slim

# Install curl
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Non-root user
RUN useradd -m -u 1000 appuser
USER appuser

WORKDIR /app

# Copy venv from builder
COPY --from=builder /opt/venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Copy application
COPY . .

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8000/health || exit 1

# Run FastAPI with Uvicorn workers
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "4"]

**Notifications Service (TypeScript):**

# Stage 1: Build
FROM node:21-alpine AS builder

WORKDIR /build
COPY package*.json ./

# Install dependencies
RUN npm ci --only=production

# Build (if using TypeScript)
COPY . .
RUN npm run build

# Stage 2: Runtime
FROM node:21-alpine

# Install curl
RUN apk add --no-cache curl

# Non-root user
RUN addgroup -S appuser && adduser -S appuser -G appuser
USER appuser

WORKDIR /app

# Copy node_modules from builder
COPY --from=builder /build/node_modules ./node_modules

# Copy built application
COPY --from=builder /build/dist ./dist
COPY package.json ./

# Expose port
EXPOSE 3000

# Health check
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:3000/health || exit 1

# Run application
CMD ["node", "dist/main.js"]

**Why Multi-Stage?**
- Smaller final images (no build tools)
- Faster deployment
- Reduced attack surface (no build dependencies)
- Faster CI/CD pipelines

### ECR Repository Configuration

# ECR Repositories (one per service)
resource "aws_ecr_repository" "order_service" {
  name                 = "bookstore/order-service"
  image_tag_mutability = "MUTABLE"  # Allow :latest to be updated
  
  image_scanning_configuration {
    scan_on_push = true  # Scan for vulnerabilities
  }
  
  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = aws_kms_key.ecr.arn
  }
}

# Lifecycle policy: Keep last 10 images, delete untagged after 7 days
resource "aws_ecr_lifecycle_policy" "order_service" {
  repository = aws_ecr_repository.order_service.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last 10 images"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["v"]
          countType     = "imageCountMoreThan"
          countNumber   = 10
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 2
        description  = "Remove untagged after 7 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 7
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

### Runtime Environment Variables

**Injected via ECS Task Definition:**

[
  {
    "name": "SPRING_PROFILES_ACTIVE",
    "value": "prod"
  },
  {
    "name": "AWS_REGION",
    "value": "eu-west-1"
  },
  {
    "name": "SERVICE_NAME",
    "value": "order-service"
  },
  {
    "name": "LOG_LEVEL",
    "value": "INFO"
  },
  {
    "name": "CORRELATION_ID_HEADER",
    "value": "X-Correlation-ID"
  }
]

**Secrets (from AWS Secrets Manager):**

[
  {
    "name": "DB_PASSWORD",
    "valueFrom": "arn:aws:secretsmanager:eu-west-1:ACCOUNT:secret:bookstore/db/password:password::"
  },
  {
    "name": "REDIS_PASSWORD",
    "valueFrom": "arn:aws:secretsmanager:eu-west-1:ACCOUNT:secret:bookstore/redis/password:password::"
  },
  {
    "name": "SERVICE_JWT_SECRET",
    "valueFrom": "arn:aws:secretsmanager:eu-west-1:ACCOUNT:secret:bookstore/jwt/secret:secret::"
  },
  {
    "name": "STRIPE_API_KEY",
    "valueFrom": "arn:aws:secretsmanager:eu-west-1:ACCOUNT:secret:bookstore/stripe/api_key:api_key::"
  }
]

Why Secrets Manager?
- Automatic rotation
- Encryption at rest (KMS)
- Audit trail (CloudTrail)
- Fine-grained IAM policies
- No hardcoded secrets in images

---

## API Ingress & Routing

### Application Load Balancer (ALB)

**Architecture:**

Internet (TLS 1.3+)
     ↓
ALB: bookstore-alb (eu-west-1)
├── Port: 443 (HTTPS) ← Certificate: *.bookstore.local (ACM)
├── Port: 80 (HTTP) ← Redirect to 443
│
├── Target Group 1: order-service-tg
│   └── Path: /api/v1/orders*
│       ├── Targets: 3 × Order Service (ECS)
│       ├── Port: 8080
│       └── Health Check: /health
│
├── Target Group 2: user-service-tg
│   └── Path: /api/v1/users*
│       └── Targets: 2 × User Service
│
├── Target Group 3: catalog-service-tg
│   └── Path: /api/v1/products*
│       └── Targets: 2 × Catalog Service
│
├── Target Group 4: search-service-tg
│   └── Path: /api/v1/search*
│       └── Targets: 3 × Search Service
│
└── Target Group 5: payment-service-tg
    └── Path: /api/v1/payments*
        └── Targets: 2 × Payment Service
        └── Special: Webhook endpoints (no auth required)

### ALB Listener Rules (Terraform)

# HTTPS Listener (primary)
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = "443"
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS-1-2-2017-01"  # TLS 1.2+
  certificate_arn   = aws_acm_certificate.bookstore.arn

  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "application/json"
      message_body = jsonencode({ error = "No matching rule" })
      status_code  = "404"
    }
  }
}

# HTTP Listener (redirect to HTTPS)
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# Listener Rule: /api/v1/orders* → Order Service
resource "aws_lb_listener_rule" "orders" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 1

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.order_service.arn
  }

  condition {
    path_pattern {
      values = ["/api/v1/orders*"]
    }
  }
}

# Listener Rule: /api/v1/search* → Search Service
resource "aws_lb_listener_rule" "search" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 2

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.search_service.arn
  }

  condition {
    path_pattern {
      values = ["/api/v1/search*"]
    }
  }
}

# Target Group: Order Service
resource "aws_lb_target_group" "order_service" {
  name        = "order-service-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"  # For Fargate

  health_check {
    healthy_threshold   = 2
    unhealthy_threshold = 2
    timeout             = 3
    interval            = 10
    path                = "/health"
    matcher             = "200"
  }

  stickiness {
    type            = "lb_cookie"
    enabled         = true
    cookie_duration = 86400  # 1 day
  }
}

### Rate Limiting & DDoS Protection

**AWS WAF (Web Application Firewall):**

resource "aws_wafv2_web_acl" "bookstore" {
  name  = "bookstore-waf"
  scope = "REGIONAL"

  default_action {
    allow {}
  }

  rule {
    name     = "RateLimitRule"
    priority = 1

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = 2000  # 2000 requests per 5 minutes
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "RateLimitRule"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 2

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "AWSManagedRulesCommonRuleSetMetric"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "bookstore-waf"
    sampled_requests_enabled   = true
  }
}

resource "aws_wafv2_web_acl_association" "alb" {
  resource_arn = aws_lb.main.arn
  web_acl_arn  = aws_wafv2_web_acl.bookstore.arn
}

**DDoS Protection (AWS Shield Standard = free, Shield Advanced = paid):**

- Shield Standard: Basic DDoS protection (included)
- Shield Advanced: Enhanced protection + real-time attack notifications ($3000/month)
- CloudFront: CDN edge protection (for static assets)

For this scale (1000 RPS peak), Shield Standard is sufficient.

---

## Messaging Infrastructure

### EventBridge Configuration

**Event Bus:**

resource "aws_cloudwatch_event_bus" "bookstore" {
  name = "bookstore-events"
}

# Event Bus Policy (allow services to publish)
resource "aws_cloudwatch_event_bus_policy" "bookstore" {
  event_bus_name = aws_cloudwatch_event_bus.bookstore.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
        Action   = "events:PutEvents"
        Resource = aws_cloudwatch_event_bus.bookstore.arn
      }
    ]
  })
}

### SQS Queues (Event Subscribers)

# Fulfillment Service Queue
resource "aws_sqs_queue" "fulfillment_dlq" {
  name                      = "fulfillment-dlq"
  message_retention_seconds = 1209600  # 14 days
}

resource "aws_sqs_queue" "fulfillment" {
  name                      = "fulfillment-queue"
  visibility_timeout_seconds = 300    # 5 minutes
  message_retention_seconds = 345600  # 4 days
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.fulfillment_dlq.arn
    maxReceiveCount     = 2
  })
}

# Notifications Service Queue
resource "aws_sqs_queue" "notifications_dlq" {
  name                      = "notifications-dlq"
  message_retention_seconds = 604800  # 7 days
}

resource "aws_sqs_queue" "notifications" {
  name                      = "notifications-queue"
  visibility_timeout_seconds = 300
  message_retention_seconds = 345600
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.notifications_dlq.arn
    maxReceiveCount     = 3  # More retries for notifications
  })
}

### EventBridge Rules → SQS Routes

# Route: order.created → fulfillment-queue
resource "aws_cloudwatch_event_rule" "order_created_to_fulfillment" {
  name           = "order-created-to-fulfillment"
  event_bus_name = aws_cloudwatch_event_bus.bookstore.name
  
  event_pattern = jsonencode({
    source      = ["bookstore.order"]
    detail-type = ["OrderCreated"]
  })
}

resource "aws_cloudwatch_event_target" "fulfillment_queue" {
  rule           = aws_cloudwatch_event_rule.order_created_to_fulfillment.name
  event_bus_name = aws_cloudwatch_event_bus.bookstore.name
  arn            = aws_sqs_queue.fulfillment.arn
  
  dead_letter_config {
    arn = aws_sqs_queue.fulfillment_dlq.arn
  }
  
  # Retry policy
  retry_policy {
    maximum_event_age       = 7200   # 2 hours
    maximum_retry_attempts  = 2
  }
}

# Route: order.created → notifications-queue
resource "aws_cloudwatch_event_rule" "order_created_to_notifications" {
  name           = "order-created-to-notifications"
  event_bus_name = aws_cloudwatch_event_bus.bookstore.name
  
  event_pattern = jsonencode({
    source      = ["bookstore.order"]
    detail-type = ["OrderCreated"]
  })
}

resource "aws_cloudwatch_event_target" "notifications_queue" {
  rule           = aws_cloudwatch_event_rule.order_created_to_notifications.name
  event_bus_name = aws_cloudwatch_event_bus.bookstore.name
  arn            = aws_sqs_queue.notifications.arn
  
  dead_letter_config {
    arn = aws_sqs_queue.notifications_dlq.arn
  }
  
  retry_policy {
    maximum_event_age       = 3600   # 1 hour
    maximum_retry_attempts  = 3
  }
}

### SNS Topics (Alerts & Notifications)

# DLQ Alerts
resource "aws_sns_topic" "dlq_alerts" {
  name = "bookstore-dlq-alerts"
}

resource "aws_sns_topic_subscription" "dlq_to_slack" {
  topic_arn = aws_sns_topic.dlq_alerts.arn
  protocol  = "https"
  endpoint  = "https://hooks.slack.com/services/T00000000/B00000000/..."  # Slack webhook
}

# Subscribe DLQ to SNS
resource "aws_sqs_queue_policy" "dlq_publish" {
  queue_url = aws_sqs_queue.fulfillment_dlq.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Principal = "*"
        Action   = "sqs:SendMessage"
        Resource = aws_sqs_queue.fulfillment_dlq.arn
        Condition = {
          ArnEquals = {
            "aws:SourceArn" = aws_sns_topic.dlq_alerts.arn
          }
        }
      }
    ]
  })
}

---

## Database Infrastructure

### RDS PostgreSQL (Primary Datastore)

**Instance Configuration:**

resource "aws_rds_cluster" "bookstore" {
  cluster_identifier      = "bookstore-postgres"
  engine                  = "aurora-postgresql"
  engine_version          = "15.3"  # Latest stable
  database_name           = "bookstore"
  master_username         = "admin"
  master_password         = random_password.db_password.result  # From Secrets Manager
  db_subnet_group_name    = aws_db_subnet_group.rds.name
  db_cluster_subnet_group_name = aws_db_cluster_subnet_group.rds.name
  
  # Availability
  availability_zones = ["eu-west-1a", "eu-west-1b", "eu-west-1c"]
  backup_retention_period = 30                      # 30 days backup
  preferred_backup_window = "03:00-04:00"          # 3-4 AM UTC
  preferred_maintenance_window = "sun:04:00-sun:05:00"
  
  # Performance
  storage_encrypted           = true
  storage_type                = "io1"
  iops                        = 3000                # 3000 provisioned IOPS
  allocated_storage            = 100               # 100 GB initial
  multi_az                    = true
  
  # Security
  vpc_security_group_ids = [aws_security_group.rds.id]
  iam_database_authentication_enabled = true
  
  # Monitoring
  enabled_cloudwatch_logs_exports = ["postgresql"]
  monitoring_interval             = 60  # CloudWatch metrics every minute
  monitoring_role_arn             = aws_iam_role.rds_monitoring.arn
  
  # Backtrack (Aurora feature: rewind to previous state)
  backtrack_window = 24  # 24-hour backtrack window
  
  # Parameter group (tune for application)
  db_cluster_parameter_group_name = aws_rds_cluster_parameter_group.bookstore.name
}

# RDS Instances (cluster members)
resource "aws_rds_cluster_instance" "bookstore" {
  count              = 3
  cluster_identifier = aws_rds_cluster.bookstore.id
  instance_class     = "db.r6g.xlarge"  # 4 vCPU, 32 GB (ARM-based, cost-effective)
  engine              = "aurora-postgresql"
  engine_version      = "15.3"
  publicly_accessible = false
  
  # Instance-level monitoring
  monitoring_interval = 60
  
  # Performance Insights
  performance_insights_enabled          = true
  performance_insights_retention_period = 7  # 7 days free tier
}

# Parameter Group (tune PostgreSQL)
resource "aws_rds_cluster_parameter_group" "bookstore" {
  name        = "bookstore-pg-params"
  family      = "aurora-postgresql15"
  description = "Parameter group for Bookstore"

  # Connection pooling
  parameter {
    name  = "max_connections"
    value = "256"
  }

  # Shared buffers
  parameter {
    name  = "shared_buffers"
    value = "{DBInstanceClassMemory/32768}"  # 1/4 of RAM
  }

  # Work memory
  parameter {
    name  = "work_mem"
    value = "16384"  # 16 MB (for sorts, hash tables)
  }

  # Maintenance memory
  parameter {
    name  = "maintenance_work_mem"
    value = "262144"  # 256 MB (for VACUUM, CREATE INDEX)
  }

  # Write ahead log (durability)
  parameter {
    name  = "wal_buffers"
    value = "16384"
  }

  parameter {
    name  = "synchronous_commit"
    value = "remote_write"  # Wait for replica write (balanced)
  }

  # Query planning
  parameter {
    name  = "random_page_cost"
    value = "1.1"  # gp3 is fast (SSD)
  }

  # Logging
  parameter {
    name  = "log_min_duration_statement"
    value = "1000"  # Log slow queries > 1 second
  }

  parameter {
    name  = "log_connections"
    value = "1"
  }
}

# Automatic failover
resource "aws_rds_cluster" "bookstore" {
  # ... (previous config)
  
  enable_http_endpoint = false  # Only for serverless
}

**Connection Pooling (PgBouncer in Transit):**

Since ECS tasks need connection pooling for performance:

# RDS Proxy (AWS-managed connection pooling)
resource "aws_db_proxy" "bookstore" {
  name                   = "bookstore-proxy"
  engine_family          = "POSTGRESQL"
  role_arn               = aws_iam_role.rds_proxy.arn
  vpc_subnet_ids         = aws_subnet.private[*].id
  vpc_security_group_ids = [aws_security_group.rds_proxy.id]
  
  # Authentication
  auth {
    auth_scheme = "SECRETS"
    secret_arn  = aws_secretsmanager_secret.db_password.arn
  }
  
  # Connection pooling
  max_connections             = 100
  session_pinning_filters     = ["EXCLUDE_VARIABLE_SETS"]  # Avoid pinning on variable changes
  init_query                  = ""
  
  # Idle timeout
  session_idle_timeout_seconds = 900  # 15 minutes
  
  # Maximum idle connections
  max_idle_connections_percent = 50
}

# Proxy Target Group
resource "aws_db_proxy_target_group" "bookstore" {
  db_proxy_name           = aws_db_proxy.bookstore.name
  target_group_name       = "default"
  db_cluster_identifiers  = [aws_rds_cluster.bookstore.cluster_identifier]
}

**Why RDS Proxy?**
- Connection pooling (each task doesn't need direct connection)
- Cost savings (fewer DB connections)
- Automatic failover to replica
- IAM authentication support
- Built-in DDoS protection

### Redis Cluster (Caching)

resource "aws_elasticache_replication_group" "bookstore" {
  replication_group_description = "Redis cluster for Bookstore"
  engine                       = "redis"
  engine_version               = "7.2"
  node_type                    = "cache.r7g.xlarge"  # 32 GB, ARM
  num_cache_clusters           = 3                   # Multi-AZ
  parameter_group_name         = aws_elasticache_parameter_group.bookstore.name
  port                         = 6379
  security_group_ids           = [aws_security_group.redis.id]
  subnet_group_name            = aws_elasticache_subnet_group.redis.name
  
  # Replication
  automatic_failover_enabled   = true
  multi_az_enabled             = true
  
  # Encryption
  at_rest_encryption_enabled   = true
  kms_key_id                   = aws_kms_key.redis.arn
  transit_encryption_enabled   = true
  auth_token                   = random_password.redis_auth_token.result
  
  # Snapshots (backup)
  snapshot_retention_limit     = 5
  snapshot_window              = "03:00-05:00"
  
  # Monitoring
  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.redis_slow_log.name
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "slow-log"
  }
  
  # Auto-scaling policy
  automatic_cache_failover_enabled = true
}

# Parameter group
resource "aws_elasticache_parameter_group" "bookstore" {
  name   = "bookstore-redis-params"
  family = "redis7"

  # Eviction policy
  parameter {
    name  = "maxmemory-policy"
    value = "allkeys-lru"  # Evict least-recently-used keys
  }

  # Persistence
  parameter {
    name  = "save"
    value = "900 1 300 10 60 10000"  # BGSAVE at intervals
  }
}

# Sentinel (automatic monitoring)
resource "aws_elasticache_replication_group" "bookstore" {
  # ... (previous config)
  automatic_failover_enabled = true
}

**Cache Strategy:**

- User profiles: TTL 1 hour
- Product details: TTL 1 hour (invalidated on update)
- Search facets: TTL 30 minutes
- Session tokens: TTL = token expiry
- Idempotency cache: TTL 24 hours

### DynamoDB (Serverless for Specific Use Cases)

# Payment Idempotency Cache
resource "aws_dynamodb_table" "payment_idempotency" {
  name           = "payment-idempotency-cache"
  billing_mode   = "PAY_PER_REQUEST"  # Serverless
  hash_key       = "idempotency_key"
  
  attribute {
    name = "idempotency_key"
    type = "S"  # String
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  point_in_time_recovery_specification {
    point_in_time_recovery_enabled = true
  }

  stream_specification {
    stream_view_type = "NEW_AND_OLD_IMAGES"
  }
}

# Event Processing Log (deduplication)
resource "aws_dynamodb_table" "event_processing_log" {
  name           = "event-processing-log"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "event_id"
  range_key      = "service_name"
  
  attribute {
    name = "event_id"
    type = "S"
  }

  attribute {
    name = "service_name"
    type = "S"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true  # Auto-delete after processing
  }

  global_secondary_index {
    name            = "ServiceNameIndex"
    hash_key        = "service_name"
    projection_type = "ALL"
  }
}

# User Interactions (for recommendations)
resource "aws_dynamodb_table" "user_interactions" {
  name           = "user-interactions"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "user_id"
  range_key      = "timestamp"
  
  attribute {
    name = "user_id"
    type = "S"
  }

  attribute {
    name = "timestamp"
    type = "N"  # Unix timestamp
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true  # Delete after 90 days
  }

  stream_specification {
    stream_view_type = "NEW_IMAGE"
  }

  global_secondary_index {
    name            = "ProductIdIndex"
    hash_key        = "product_id"
    projection_type = "KEYS_ONLY"
    write_capacity_units = 10
    read_capacity_units  = 10
  }
}

**Why DynamoDB for these?**
- Serverless (pay per request, no capacity planning)
- TTL support (auto-cleanup)
- Stream support (real-time events)
- High availability (multi-AZ automatic)
- Simple access patterns (key-value lookups)

---

## Search Infrastructure

### Elasticsearch Managed Service (Amazon OpenSearch)

resource "aws_opensearch_domain" "bookstore" {
  domain_name            = "bookstore"
  engine_version         = "OpenSearch_2.11"
  
  # Cluster configuration
  cluster_config {
    instance_type            = "m6g.xlarge.search"  # ARM-based, 4 vCPU, 16 GB
    instance_count           = 3
    dedicated_master_enabled = false
    zone_awareness_enabled   = true
    zone_awareness_config {
      availability_zone_count = 3
    }
  }
  
  # Storage
  ebs_options {
    ebs_enabled = true
    volume_type = "gp3"
    volume_size = 100  # 100 GB per node
    iops        = 3000
  }
  
  # Encryption
  encryption_at_rest_options {
    enabled            = true
    kms_key_arn        = aws_kms_key.opensearch.arn
  }
  
  node_to_node_encryption_options {
    enabled = true
  }
  
  # Access control
  vpc_options {
    subnet_ids         = aws_subnet.private[*].id
    security_group_ids = [aws_security_group.opensearch.id]
  }
  
  domain_endpoint_options {
    enforce_https       = true
    tls_security_policy = "Policy-Min-TLS-1-2-2019-07"
  }
  
  # IAM authentication
  access_policies = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          AWS = aws_iam_role.search_service.arn
        }
        Action   = "es:*"
        Resource = "${aws_opensearch_domain.bookstore.arn}/*"
      }
    ]
  })
  
  # Monitoring
  log_publishing_options {
    cloudwatch_log_group_arn = aws_cloudwatch_log_group.opensearch_app.arn
    enabled                  = true
    log_type                 = "ES_APPLICATION_LOGS"
  }
  
  # Backups
  snapshot_options {
    automated_snapshot_start_hour = 3
  }
  
  # Advanced options
  advanced_options = {
    "rest.action.multi.allow_explicit_index" = "true"
    "indices.fielddata.cache.size"           = "25"
  }
}

# Index template (all product indices)
resource "aws_opensearch_domain_template" "products" {
  domain_name = aws_opensearch_domain.bookstore.domain_name
  name        = "products_template"

  body = jsonencode({
    index_patterns = ["products-*"]
    template = {
      settings = {
        index = {
          number_of_shards             = 3
          number_of_replicas           = 2
          refresh_interval             = "30s"
          analysis = {
            analyzer = {
              default = {
                type      = "standard"
                stopwords = "_english_"
              }
            }
          }
        }
      }
      mappings = {
        properties = {
          product_id = {
            type = "keyword"
          }
          title = {
            type   = "text"
            fields = {
              keyword = {
                type = "keyword"
              }
            }
          }
          author = {
            type   = "text"
            fields = {
              keyword = {
                type = "keyword"
              }
            }
          }
          description = {
            type = "text"
          }
          price = {
            type = "float"
          }
          rating = {
            type = "float"
          }
          category = {
            type = "keyword"
          }
          is_available = {
            type = "boolean"
          }
          quantity_available = {
            type = "integer"
          }
          created_at = {
            type = "date"
          }
          updated_at = {
            type = "date"
          }
        }
      }
    }
  })
}

### Search Service Integration (Python)

# elasticsearch_client.py
from opensearchpy import OpenSearch, AWSV4SignerAuth
import boto3
from urllib.parse import urlparse

class ElasticsearchClient:
    def __init__(self):
        # AWS authentication
        credentials = boto3.Session().get_credentials()
        auth = AWSV4SignerAuth(credentials, 'eu-west-1', 'es')
        
        self.client = OpenSearch(
            hosts=[os.getenv('OPENSEARCH_ENDPOINT')],
            http_auth=auth,
            use_ssl=True,
            verify_certs=True,
            ca_certs='/etc/ssl/certs/ca-bundle.crt',
            connection_class=RequestsHttpConnection
        )
    
    def search(self, query: dict):
        """Execute search with retry logic"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                return self.client.search(index='products-*', body=query)
            except Exception as e:
                if attempt == max_retries - 1:
                    raise
                time.sleep(2 ** attempt)  # Exponential backoff
    
    def bulk_index(self, actions: list):
        """Bulk index documents"""
        from opensearchpy import helpers
        
        try:
            helpers.bulk(self.client, actions)
        except Exception as e:
            logger.error(f"Bulk indexing failed: {e}")
            raise

---

## Storage & CDN

### S3 Buckets (Profile Pictures, Covers)

# Profile Pictures
resource "aws_s3_bucket" "profile_pictures" {
  bucket = "bookstore-profile-pictures"
}

resource "aws_s3_bucket_versioning" "profile_pictures" {
  bucket = aws_s3_bucket.profile_pictures.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "profile_pictures" {
  bucket = aws_s3_bucket.profile_pictures.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.s3.arn
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "profile_pictures" {
  bucket = aws_s3_bucket.profile_pictures.id

  rule {
    id     = "delete-old-versions"
    status = "Enabled"

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}

# Block public access (signed URLs only)
resource "aws_s3_bucket_public_access_block" "profile_pictures" {
  bucket = aws_s3_bucket.profile_pictures.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

### CloudFront Distribution (CDN)

resource "aws_cloudfront_distribution" "bookstore" {
  origin {
    domain_name = aws_s3_bucket.profile_pictures.bucket_regional_domain_name
    origin_id   = "s3-profile-pictures"

    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.bookstore.cloudfront_access_identity_path
    }
  }

  origin {
    domain_name = aws_lb.main.dns_name
    origin_id   = "alb-api"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }

    origin_custom_header {
      name  = "X-Origin-Verify"
      value = aws_secretsmanager_secret.cloudfront_origin_header.arn
    }
  }

  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"

  # Cache behavior for S3 (static assets)
  cache_behavior {
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "s3-profile-pictures"
    viewer_protocol_policy = "redirect-to-https"

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    min_ttl     = 0
    default_ttl = 3600      # 1 hour
    max_ttl     = 86400     # 1 day
  }

  # Cache behavior for API (ALB)
  cache_behavior {
    path_pattern    = "/api/*"
    allowed_methods = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods  = ["GET", "HEAD"]
    target_origin_id = "alb-api"
    viewer_protocol_policy = "https-only"

    forwarded_values {
      query_string = true
      headers {
        header_names = [
          "Authorization",
          "Host",
          "User-Agent",
          "X-Correlation-ID",
          "X-Idempotency-Key"
        ]
      }
      cookies {
        forward = "all"
      }
    }

    min_ttl     = 0
    default_ttl = 0  # Don't cache API responses
    max_ttl     = 0
  }

  # Default cache behavior
  default_cache_behavior {
    allowed_methods  = ["GET", "HEAD"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "s3-profile-pictures"
    viewer_protocol_policy = "redirect-to-https"

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    min_ttl     = 0
    default_ttl = 3600
    max_ttl     = 86400
  }

  # WAF association
  web_acl_id = aws_wafv2_web_acl.bookstore.arn

  # HTTPS
  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate.bookstore.arn
    minimum_protocol_version = "TLSv1.2_2021"
    ssl_support_method       = "sni-only"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # Logging
  logging_config {
    include_cookies = false
    bucket          = aws_s3_bucket.cloudfront_logs.bucket_regional_domain_name
    prefix          = "cloudfront-logs/"
  }
}

---

## Environment Separation

### Development, Staging, Production

# Variables
variable "environment" {
  type = string
  enum = ["dev", "staging", "prod"]
}

variable "aws_region" {
  type = string
  default = "eu-west-1"
}

# Locals (derived from environment)
locals {
  env_config = {
    dev = {
      instance_type          = "t3.large"       # Smaller
      ecs_desired_count      = 1                # Minimal
      rds_instance_class     = "db.t4g.small"   # Tiny
      redis_node_type        = "cache.t4g.small"
      elasticsearch_instance = "t3.small.elasticsearch"
      multi_az               = false
      backup_retention       = 7                # 7 days
      enable_monitoring      = false
      cost_focus             = true
    }
    staging = {
      instance_type          = "t3.xlarge"
      ecs_desired_count      = 2                # HA
      rds_instance_class     = "db.r6g.large"
      redis_node_type        = "cache.r6g.large"
      elasticsearch_instance = "r6g.large.elasticsearch"
      multi_az               = true
      backup_retention       = 14               # 2 weeks
      enable_monitoring      = true
      cost_focus             = false
    }
    prod = {
      instance_type          = "t3.2xlarge"     # Large
      ecs_desired_count      = 3                # Full HA
      rds_instance_class     = "db.r6g.xlarge"  # Larger
      redis_node_type        = "cache.r7g.xlarge"
      elasticsearch_instance = "m6g.xlarge.elasticsearch"
      multi_az               = true
      backup_retention       = 30               # 30 days
      enable_monitoring      = true
      cost_focus             = false
    }
  }

  config = local.env_config[var.environment]

  tags_common = {
    Environment = var.environment
    Project     = "bookstore"
    ManagedBy   = "Terraform"
    CreatedAt   = timestamp()
  }
}

# Resource naming
locals {
  resource_prefix = "bookstore-${var.environment}"
  cluster_name    = "${local.resource_prefix}-cluster"
  db_name         = "bookstore_${replace(var.environment, "-", "_")}"
}

# Environment-specific endpoints
output "api_endpoint" {
  value = var.environment == "prod" ? aws_cloudfront_distribution.bookstore.domain_name : aws_lb.main.dns_name
}

### Terraform Workspaces

# Create workspaces
terraform workspace new dev
terraform workspace new staging
terraform workspace new prod

# Deploy to specific environment
terraform workspace select dev
terraform apply -var-file="environments/dev.tfvars"

terraform workspace select staging
terraform apply -var-file="environments/staging.tfvars"

terraform workspace select prod
terraform apply -var-file="environments/prod.tfvars"

### Environment-Specific Configuration

**environments/dev.tfvars:**
environment = "dev"
aws_region  = "eu-west-1"
enable_nat_gateway = false  # Use NAT instances (cheaper)
enable_rds_backup  = false
log_retention_days = 7

**environments/staging.tfvars:**
environment = "staging"
aws_region  = "eu-west-1"
enable_nat_gateway = true
enable_rds_backup  = true
log_retention_days = 30

**environments/prod.tfvars:**
environment = "prod"
aws_region  = "eu-west-1"
enable_nat_gateway = true
enable_rds_backup  = true
enable_multi_region = true    # Cross-region failover
log_retention_days = 90
enable_enhanced_monitoring = true

---

## Networking & Security

### VPC Architecture

VPC: bookstore-prod-vpc (10.0.0.0/16)
├── Public Subnets (NAT gateways, ALB)
│   ├── eu-west-1a: 10.0.1.0/24
│   ├── eu-west-1b: 10.0.2.0/24
│   └── eu-west-1c: 10.0.3.0/24
│
├── Private Subnets (ECS, RDS, ElastiCache)
│   ├── eu-west-1a: 10.0.11.0/24
│   ├── eu-west-1b: 10.0.12.0/24
│   └── eu-west-1c: 10.0.13.0/24
│
├── Database Subnets (RDS replicas)
│   ├── eu-west-1a: 10.0.21.0/24
│   ├── eu-west-1b: 10.0.22.0/24
│   └── eu-west-1c: 10.0.23.0/24
│
└── VPC Endpoints (AWS services)
    ├── S3 Gateway Endpoint
    ├── DynamoDB Gateway Endpoint
    ├── ECR Interface Endpoint
    ├── CloudWatch Interface Endpoint
    └── Secrets Manager Interface Endpoint

### Security Groups

# ALB Security Group
resource "aws_security_group" "alb" {
  name        = "bookstore-alb-sg"
  description = "ALB inbound from internet"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ECS Task Security Group
resource "aws_security_group" "ecs_tasks" {
  name        = "bookstore-ecs-tasks-sg"
  description = "ECS task inbound from ALB"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 8000
    to_port         = 9000
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # Service-to-service communication (mTLS)
  ingress {
    from_port = 0
    to_port   = 65535
    protocol  = "tcp"
    self      = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# RDS Security Group
resource "aws_security_group" "rds" {
  name        = "bookstore-rds-sg"
  description = "RDS inbound from ECS"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# Redis Security Group
resource "aws_security_group" "redis" {
  name        = "bookstore-redis-sg"
  description = "Redis inbound from ECS"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

### mTLS for Service-to-Service Communication

# AWS Certificate Manager (ACM) for mTLS
resource "aws_acm_certificate" "service_mtls" {
  domain_name       = "*.bookstore.local"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

# Service mesh (AWS App Mesh) for mTLS enforcement
resource "aws_appmesh_mesh" "bookstore" {
  name = "bookstore-mesh"

  spec {
    egress_filter {
      type = "ALLOW_ALL"
    }
  }
}

resource "aws_appmesh_virtual_node" "order_service" {
  name      = "order-service"
  mesh_name = aws_appmesh_mesh.bookstore.name

  spec {
    backend {
      virtual_service {
        virtual_service_name = "catalog-service.bookstore.local"

        client_policy {
          tls {
            enforce = true
            validation {
              trust {
                acm {
                  certificate_authority_arn = aws_acm_certificate.service_mtls.arn
                }
              }
            }
          }
        }
      }
    }

    listener {
      port_mapping {
        port     = 8080
        protocol = "http"
      }

      tls {
        mode = "STRICT"
        certificate {
          acm {
            certificate_arn = aws_acm_certificate.service_mtls.arn
          }
        }
      }
    }
  }
}

### Network Access Control Lists (NACLs)

# Public NACL (ALB)
resource "aws_network_acl" "public" {
  vpc_id = aws_vpc.main.id

  # Inbound: HTTP/HTTPS from anywhere
  ingress {
    protocol   = "tcp"
    rule_no    = 100
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 80
    to_port    = 80
  }

  ingress {
    protocol   = "tcp"
    rule_no    = 110
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 443
    to_port    = 443
  }

  # Ephemeral ports (responses)
  ingress {
    protocol   = "tcp"
    rule_no    = 120
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 1024
    to_port    = 65535
  }

  # Outbound: All
  egress {
    protocol   = "-1"
    rule_no    = 100
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 0
    to_port    = 0
  }
}

# Private NACL (ECS, RDS)
resource "aws_network_acl" "private" {
  vpc_id = aws_vpc.main.id

  # Inbound: From VPC CIDR (ECS, RDS, Redis)
  ingress {
    protocol   = "-1"
    rule_no    = 100
    action     = "allow"
    cidr_block = aws_vpc.main.cidr_block
    from_port  = 0
    to_port    = 0
  }

  # Inbound: Ephemeral (responses from internet)
  ingress {
    protocol   = "tcp"
    rule_no    = 110
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 1024
    to_port    = 65535
  }

  # Outbound: All
  egress {
    protocol   = "-1"
    rule_no    = 100
    action     = "allow"
    cidr_block = "0.0.0.0/0"
    from_port  = 0
    to_port    = 0
  }
}

---

## Observability & Monitoring

### CloudWatch Dashboards

resource "aws_cloudwatch_dashboard" "bookstore" {
  dashboard_name = "bookstore-operations"

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ServiceName", "order-service"],
            ["AWS/ECS", "MemoryUtilization", "ServiceName", "order-service"],
            ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", aws_lb.main.arn_suffix],
            ["AWS/RDS", "DatabaseConnections", "DBClusterIdentifier", aws_rds_cluster.bookstore.cluster_identifier],
            ["AWS/ElastiCache", "CPUUtilization", "CacheClusterId", aws_elasticache_replication_group.bookstore.id],
          ]
          period = 60
          stat   = "Average"
          region = var.aws_region
          title  = "Infrastructure Health"
        }
      },
      {
        type = "log"
        properties = {
          query = "fields @timestamp, @message, @duration | stats sum(@duration) by bin(5m)"
          region = var.aws_region
          title  = "API Latency"
        }
      }
    ]
  })
}

### CloudWatch Alarms

# Order Service High CPU
resource "aws_cloudwatch_metric_alarm" "order_service_cpu" {
  alarm_name          = "order-service-high-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    ServiceName = "order-service"
  }
}

# RDS Connection Pool Exhaustion
resource "aws_cloudwatch_metric_alarm" "rds_connections" {
  alarm_name          = "rds-connections-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Maximum"
  threshold           = 200  # Out of 256
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    DBClusterIdentifier = aws_rds_cluster.bookstore.cluster_identifier
  }
}

# SQS Dead Letter Queue
resource "aws_cloudwatch_metric_alarm" "fulfillment_dlq" {
  alarm_name          = "fulfillment-dlq-messages"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300
  statistic           = "Average"
  threshold           = 1  # Alert immediately
  alarm_actions       = [aws_sns_topic.critical_alerts.arn]

  dimensions = {
    QueueName = aws_sqs_queue.fulfillment_dlq.name
  }
}

### CloudWatch Logs

# Log groups per service
resource "aws_cloudwatch_log_group" "order_service" {
  name              = "/aws/ecs/order-service"
  retention_in_days = var.environment == "prod" ? 90 : 30

  tags = local.tags_common
}

# Structured logging (JSON format)
# Example log entry:
# {
#   "timestamp": "2026-01-18T12:27:00Z",
#   "level": "INFO",
#   "service": "order-service",
#   "correlation_id": "corr_abc123",
#   "message": "Order created",
#   "order_id": "ord_abc123",
#   "duration_ms": 450,
#   "tags": ["api", "order"]
# }

# Log group subscriptions (forward to S3)
resource "aws_cloudwatch_log_group_subscription_filter" "order_service" {
  name            = "order-service-logs-to-s3"
  log_group_name  = aws_cloudwatch_log_group.order_service.name
  filter_pattern  = ""  # All logs
  destination_arn = aws_kinesis_firehose_delivery_stream.logs_to_s3.arn
}

### X-Ray (Distributed Tracing)

# X-Ray sampling rule
resource "aws_xray_sampling_rule" "bookstore" {
  rule_name      = "bookstore"
  priority       = 1000
  version        = 1
  reservoir_size = 1
  fixed_rate     = 0.05  # Sample 5% of requests (for cost)
  url_path       = "*"
  host           = "*"
  http_method    = "*"
  service_type   = "*"
  service_name   = "*"
}

**Java Integration (Order Service):**

@Configuration
public class XRayConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setClientHttpRequestFactory(new BufferingClientHttpRequestFactory(
            new HttpComponentsClientHttpRequestFactory()
        ));
        
        // Register X-Ray interceptor
        restTemplate.getInterceptors().add(
            new XRayClientHttpRequestInterceptor()
        );
        
        return restTemplate;
    }
}

---

## Deployment Pipeline

### GitHub Actions CI/CD

# .github/workflows/deploy.yml
name: Deploy to AWS

on:
  push:
    branches:
      - main      # Production
      - staging   # Staging
      - develop   # Development

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    
    permissions:
      id-token: write
      contents: read
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::ACCOUNT:role/github-actions-role
          aws-region: eu-west-1
      
      - name: Login to ECR
        uses: aws-actions/amazon-ecr-login@v2
      
      - name: Build Docker images
        run: |
          for service in order user catalog payment search notifications; do
            docker build -t bookstore/$service:${{ github.sha }} ./services/$service
            docker tag bookstore/$service:${{ github.sha }} bookstore/$service:latest
          done
      
      - name: Push to ECR
        run: |
          for service in order user catalog payment search notifications; do
            docker push bookstore/$service:${{ github.sha }}
            docker push bookstore/$service:latest
          done
      
      - name: Update ECS services
        run: |
          ENVIRONMENT=$(echo ${{ github.ref }} | sed 's|refs/heads/||')
          
          for service in order user catalog payment search notifications; do
            aws ecs update-service \
              --cluster bookstore-$ENVIRONMENT \
              --service $service-service \
              --force-new-deployment
          done
      
      - name: Wait for deployment
        run: |
          ENVIRONMENT=$(echo ${{ github.ref }} | sed 's|refs/heads/||')
          
          for service in order user catalog payment search notifications; do
            aws ecs wait services-stable \
              --cluster bookstore-$ENVIRONMENT \
              --services $service-service
          done
      
      - name: Run smoke tests
        run: |
          curl -f https://api.bookstore.local/health || exit 1
          curl -f https://api.bookstore.local/api/v1/products?page_size=1 || exit 1

### Blue-Green Deployment Strategy

For zero-downtime production deployments:

# Target Group 1 (Blue)
resource "aws_lb_target_group" "order_service_blue" {
  name        = "order-service-blue"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
}

# Target Group 2 (Green)
resource "aws_lb_target_group" "order_service_green" {
  name        = "order-service-green"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
}

# ALB initially routes to Blue
resource "aws_lb_listener_rule" "orders" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 1

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.order_service_blue.arn
  }

  condition {
    path_pattern {
      values = ["/api/v1/orders*"]
    }
  }
}

# Deployment process:
# 1. Deploy new tasks to Green target group
# 2. Run health checks on Green
# 3. Update ALB listener to point to Green
# 4. Monitor Green for errors (5 minutes)
# 5. If errors: Rollback to Blue
# 6. If successful: Drain Blue, prepare for next deployment

---

## Disaster Recovery

### RDS Backup & Restore

# Automated backups (already configured in RDS cluster)
resource "aws_rds_cluster" "bookstore" {
  # ...
  backup_retention_period = 30
  preferred_backup_window = "03:00-04:00"
}

# Manual snapshot for major changes
resource "aws_db_cluster_snapshot" "pre_migration" {
  db_cluster_identifier              = aws_rds_cluster.bookstore.id
  db_cluster_snapshot_identifier     = "bookstore-pre-migration-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"
}

# Cross-region backup (for DR)
resource "aws_db_cluster_snapshot" "cross_region" {
  db_cluster_snapshot_identifier = aws_db_cluster_snapshot.pre_migration.id
  target_db_cluster_snapshot_identifier = "bookstore-backup-${formatdate("YYYY-MM-DD", timestamp())}"
  source_region = "eu-west-1"
  source_db_cluster_snapshot_identifier = aws_db_cluster_snapshot.pre_migration.db_cluster_snapshot_arn
}

# Restore procedure
resource "aws_rds_cluster" "restored" {
  cluster_identifier              = "bookstore-restored"
  db_cluster_snapshot_identifier  = aws_db_cluster_snapshot.pre_migration.id
  engine                          = "aurora-postgresql"
  skip_final_snapshot             = true
}

### RTO & RPO Targets

| Component | RTO | RPO | Method |
|-----------|-----|-----|--------|
| **RDS** | 15 minutes | 5 minutes | Automated backups + Multi-AZ failover |
| **ElastiCache** | 5 minutes | 0 (in-memory) | Multi-AZ automatic failover |
| **ECS Services** | 2 minutes | 0 (stateless) | Auto-scaling + service restart |
| **S3** | 1 hour | 1 hour | Cross-region replication |

### Backup Verification

# Weekly restore test to separate environment
resource "aws_cloudwatch_event_rule" "backup_test" {
  name = "weekly-backup-restore-test"
  schedule_expression = "cron(0 6 ? * MON *)"  # Every Monday 6 AM
}

resource "aws_cloudwatch_event_target" "backup_test_lambda" {
  rule = aws_cloudwatch_event_rule.backup_test.name
  arn = aws_lambda_function.backup_test.arn
}

# Lambda function tests restore
def lambda_handler(event, context):
    rds = boto3.client('rds')
    
    # Find latest backup
    snapshots = rds.describe_db_cluster_snapshots(
        DBClusterIdentifier='bookstore-postgres',
        SnapshotType='automated'
    )
    
    latest = max(snapshots['DBClusterSnapshots'], key=lambda x: x['SnapshotCreateTime'])
    
    # Restore to test cluster
    rds.restore_db_cluster_from_snapshot(
        DBClusterIdentifier='bookstore-backup-test',
        SnapshotIdentifier=latest['DBClusterSnapshotIdentifier'],
        Engine='aurora-postgresql'
    )
    
    # Wait for restore
    waiter = rds.get_waiter('db_cluster_available')
    waiter.wait(DBClusterIdentifier='bookstore-backup-test')
    
    # Run integrity checks
    check_data_integrity()
    
    # Clean up
    rds.delete_db_cluster(
        DBClusterIdentifier='bookstore-backup-test',
        SkipFinalSnapshot=True
    )

---

## Cost Optimization

### Resource Sizing & Right-Sizing

**ECS Task Sizing (Target Utilization 60-70%):**

Order Service:
  - Desired: 3 tasks × 1024 CPU, 2048 MB
  - Cost: $0.02525/hour per task = $75.75/month × 3 = $227.25

Search Service:
  - Desired: 3 tasks × 2048 CPU, 4096 MB
  - Cost: $0.05050/hour per task = $151.50/month × 3 = $454.50

Total ECS: ~$682/month

**Reserved Capacity Discount (vs On-Demand):**

Standard Reserved Instance (1-year):
  - t3.2xlarge: $265/month (vs $400/month on-demand) = 34% savings
  
Compute Savings Plan (Flexible):
  - 1-year commitment: $0.0189/hour (vs $0.0313/hour on-demand) = 40% savings

### Spot Instances for Non-Critical Workloads

# Notifications Service on Spot (bursty, tolerant of interruptions)
resource "aws_ec2_fleet" "notifications_spot" {
  launch_template_config {
    launch_template_specification {
      id      = aws_launch_template.ecs_notifications.id
      version = "$Latest"
    }

    overrides {
      instance_type = "t3.large"
      weighted_capacity = 1
    }
  }

  target_capacity_specification {
    total_target_capacity = 2
    on_demand_target_capacity = 0
    spot_target_capacity = 2
  }

  spot_options {
    allocation_strategy = "capacity-optimized"
  }

  type = "maintain"
}

**Cost Savings:** Spot instances up to 70% cheaper than on-demand

### Data Transfer Optimization

**VPC Endpoints (avoid NAT gateway charges):**

NAT Gateway: $32/month + $0.045 per GB
VPC Endpoint (S3 Gateway): Free

Annual Savings with 100 GB/month S3 transfer:
  NAT: $32 × 12 + $0.045 × 100 × 12 = $438
  VPC Endpoint: $0
  Savings: $438/year

---

## Implementation Checklist

### Phase 1: Infrastructure Setup (Week 1-2)

- [ ] **VPC & Networking**
  - [ ] Create VPC (10.0.0.0/16)
  - [ ] Create public/private subnets across 3 AZs
  - [ ] Setup NAT gateways (or instances for dev)
  - [ ] Create VPC endpoints (S3, DynamoDB, ECR, CloudWatch, Secrets Manager)
  - [ ] Configure NACLs and security groups

- [ ] **RDS Setup**
  - [ ] Create Aurora PostgreSQL cluster (3 instances, multi-AZ)
  - [ ] Configure parameter groups (connection pooling, slow query logging)
  - [ ] Setup RDS Proxy for connection pooling
  - [ ] Enable automated backups (30 days)
  - [ ] Test backup/restore procedure
  - [ ] Create initial databases and schemas

- [ ] **ElastiCache Redis**
  - [ ] Create Redis cluster (3 nodes, multi-AZ)
  - [ ] Configure parameter groups
  - [ ] Enable encryption at rest and in transit
  - [ ] Setup snapshot automation
  - [ ] Test failover

- [ ] **Elasticsearch (OpenSearch)**
  - [ ] Create OpenSearch domain (3 nodes)
  - [ ] Configure index templates for products
  - [ ] Setup automatic snapshots
  - [ ] Create initial indices
  - [ ] Test search and aggregations

### Phase 2: Container & Orchestration (Week 2-3)

- [ ] **ECR Repositories**
  - [ ] Create ECR repositories (one per service)
  - [ ] Configure lifecycle policies
  - [ ] Enable image scanning

- [ ] **ECS Cluster**
  - [ ] Create ECS cluster (bookstore-prod)
  - [ ] Launch EC2 instances (t3.2xlarge × 3 AZs)
  - [ ] Register instances with cluster
  - [ ] Configure CloudWatch Container Insights

- [ ] **ECS Task Definitions**
  - [ ] Create task definitions for each service
  - [ ] Configure CPU/memory resources
  - [ ] Setup environment variables and secrets
  - [ ] Configure logging (CloudWatch)
  - [ ] Setup health checks

- [ ] **ECS Services**
  - [ ] Create services (order, user, catalog, payment, search, notifications)
  - [ ] Configure desired count and auto-scaling
  - [ ] Setup load balancing (ALB target groups)
  - [ ] Configure service discovery (Cloud Map)

### Phase 3: Load Balancing & Ingress (Week 3)

- [ ] **Application Load Balancer**
  - [ ] Create ALB (bookstore-alb)
  - [ ] Configure listener (HTTPS, port 443)
  - [ ] Create target groups per service
  - [ ] Setup listener rules (path-based routing)
  - [ ] Enable access logging (S3)

- [ ] **SSL/TLS Certificate**
  - [ ] Request ACM certificate (*.bookstore.local)
  - [ ] Configure ALB listener with certificate
  - [ ] Setup HTTP→HTTPS redirect

- [ ] **WAF & DDoS**
  - [ ] Create WAF web ACL
  - [ ] Configure rate limiting (2000 req/5min)
  - [ ] Associate WAF with ALB
  - [ ] Enable AWS Shield Standard (automatic)

### Phase 4: Messaging & Events (Week 4)

- [ ] **EventBridge**
  - [ ] Create EventBridge bus (bookstore-events)
  - [ ] Define event rules (order.created → SQS)
  - [ ] Configure dead-letter queues

- [ ] **SQS Queues**
  - [ ] Create main queues (fulfillment, notifications, catalog)
  - [ ] Create DLQ for each main queue
  - [ ] Configure queue policies
  - [ ] Setup retry policies

- [ ] **SNS Topics**
  - [ ] Create alert topics
  - [ ] Setup Slack webhook subscriptions
  - [ ] Configure email subscriptions for critical alerts

### Phase 5: Storage & CDN (Week 4-5)

- [ ] **S3 Buckets**
  - [ ] Create profile pictures bucket
  - [ ] Enable versioning and encryption
  - [ ] Configure lifecycle policies
  - [ ] Block public access

- [ ] **CloudFront**
  - [ ] Create distribution
  - [ ] Configure origin (S3 + ALB)
  - [ ] Setup caching behaviors
  - [ ] Associate WAF
  - [ ] Setup logging to S3

### Phase 6: Monitoring & Observability (Week 5)

- [ ] **CloudWatch**
  - [ ] Create log groups (per service)
  - [ ] Setup dashboards
  - [ ] Configure alarms (CPU, memory, RDS connections)
  - [ ] Setup log subscriptions (CloudWatch → S3)

- [ ] **X-Ray**
  - [ ] Configure X-Ray sampling rules
  - [ ] Integrate services with X-Ray SDK
  - [ ] Setup service maps

- [ ] **Logging**
  - [ ] Configure structured logging (JSON)
  - [ ] Setup log retention policies
  - [ ] Test log aggregation

### Phase 7: Security & Secrets (Week 5-6)

- [ ] **AWS Secrets Manager**
  - [ ] Store DB password
  - [ ] Store Redis password
  - [ ] Store JWT secrets
  - [ ] Store Stripe API keys
  - [ ] Configure rotation policies

- [ ] **IAM Policies**
  - [ ] Create role for ECS tasks
  - [ ] Create role for Lambda functions
  - [ ] Create role for RDS Proxy
  - [ ] Implement least-privilege access

- [ ] **Encryption**
  - [ ] Configure KMS keys (RDS, Redis, S3, EBS)
  - [ ] Enable encryption in transit (TLS 1.3)
  - [ ] Setup mTLS for service-to-service

### Phase 8: CI/CD Pipeline (Week 6)

- [ ] **GitHub Actions**
  - [ ] Create build workflow
  - [ ] Configure ECR push
  - [ ] Setup ECS deployment
  - [ ] Configure smoke tests

- [ ] **Blue-Green Deployment**
  - [ ] Create dual target groups
  - [ ] Implement traffic switching logic
  - [ ] Setup rollback procedure

### Phase 9: Testing & Validation (Week 6-7)

- [ ] **Load Testing**
  - [ ] Test 1000 RPS peak load
  - [ ] Monitor latency (p50, p95, p99)
  - [ ] Verify auto-scaling triggers

- [ ] **Failover Testing**
  - [ ] Test RDS multi-AZ failover
  - [ ] Test Redis failover
  - [ ] Test ECS task replacement

- [ ] **Backup Testing**
  - [ ] Test RDS snapshot restore
  - [ ] Test data integrity after restore
  - [ ] Verify RPO/RTO targets

### Phase 10: Go-Live (Week 7-8)

- [ ] **Pre-Launch**
  - [ ] Final security audit
  - [ ] DNS cutover testing
  - [ ] Load testing in prod-like environment
  - [ ] Runbook documentation

- [ ] **Launch**
  - [ ] Enable CloudTrail (audit logging)
  - [ ] Switch traffic (gradual, 5% → 25% → 50% → 100%)
  - [ ] Monitor error rates and latency
  - [ ] Verify all services healthy

- [ ] **Post-Launch**
  - [ ] Monitor for 24 hours (24×7 on-call)
  - [ ] Collect metrics and logs
  - [ ] Document learnings
  - [ ] Plan for scale-up/optimization

---

**Document Version**: 1.0.0  
**Status**: Iteration 6 — Complete  
**Next**: Performance Benchmarks & Capacity Planning