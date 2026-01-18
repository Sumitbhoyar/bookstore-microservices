# Observability, Reliability & Operations Specification

**Version:** 1.0.0  
**Last Updated:** 2026-01-18  
**Status:** Iteration 7 — Observability & Operations  
**Audience:** DevOps Engineers, SREs, Backend Engineers (Cursor IDE)  
**Prerequisite**: AWS Infrastructure & Deployment Specification v1.0.0

---

## Table of Contents

1. [Overview](#overview)
2. [Logging Architecture](#logging-architecture)
3. [Metrics & Instrumentation](#metrics--instrumentation)
4. [Service Level Indicators (SLIs)](#service-level-indicators-slis)
5. [Distributed Tracing](#distributed-tracing)
6. [Alerting Rules & On-Call](#alerting-rules--on-call)
7. [Dashboards & Visualization](#dashboards--visualization)
8. [Incident Response](#incident-response)
9. [Operational Runbooks](#operational-runbooks)
10. [Observability Testing](#observability-testing)
11. [Implementation Checklist](#implementation-checklist)

---

## Overview

### Design Principles

- ✅ **Structured logging**: JSON format, correlation IDs, standardized fields
- ✅ **Comprehensive metrics**: RED (Request Rate, Errors, Duration) + USE (Utilization, Saturation, Errors)
- ✅ **Distributed tracing**: End-to-end request flows with service spans
- ✅ **Alerting on outcomes**: Alert on SLIs not metrics (e.g., "P99 latency > 1s" not "CPU > 80%")
- ✅ **Observability-driven**: Every deploy includes observability changes
- ✅ **Runbook-first**: Every alert has documented resolution steps

### Observability Stack

| Component | Tool | Purpose |
|-----------|------|---------|
| **Logs** | CloudWatch Logs, ELK (Elasticsearch) | Structured event logging |
| **Metrics** | CloudWatch + Prometheus | System and application metrics |
| **Traces** | AWS X-Ray + Jaeger (optional) | Distributed request tracing |
| **Alerts** | CloudWatch Alarms + PagerDuty | Incident escalation |
| **Dashboards** | CloudWatch, Grafana | Real-time visibility |

### Observability Maturity Model

| Level | Logging | Metrics | Tracing | Alerting |
|-------|---------|---------|---------|----------|
| **L1: Basic** | Unstructured logs | System metrics only | None | On error logs |
| **L2: Intermediate** | Structured logs, correlation IDs | RED metrics | Sample tracing | SLI-based |
| **L3: Advanced** | Logs + context injection | RED + USE metrics | Full tracing | Root cause detection |
| **L4: Expert** | Logs + ML anomaly | Custom business metrics | Trace sampling | Auto-remediation |

**Target: Level 3 (Advanced)** — This specification aims for comprehensive observability with full alerting on SLIs.

---

## Logging Architecture

### Logging Standards

#### Log Entry Format (JSON)

Every log must be structured JSON with these required fields:

{
  "timestamp": "2026-01-18T12:27:00.123Z",
  "level": "INFO",
  "service": "order-service",
  "version": "1.0.0",
  "environment": "prod",
  "correlation_id": "corr_abc123",
  "trace_id": "1-5e1be6a7-acde48db7f6da47d12345678",
  "span_id": "5e1be6a7acde48db",
  "user_id": "user_abc123",
  "request_id": "req_xyz789",
  "message": "Order created successfully",
  "context": {
    "order_id": "ord_abc123",
    "amount": 18047,
    "items_count": 2,
    "status": "pending"
  },
  "performance": {
    "duration_ms": 450,
    "db_time_ms": 120,
    "external_api_time_ms": 250
  },
  "tags": ["order", "payment", "api"],
  "errors": null
}

**Field Definitions:**

| Field | Type | Description | Required |
|-------|------|-------------|----------|
| `timestamp` | ISO 8601 | When log was created (UTC) | ✓ |
| `level` | String | DEBUG, INFO, WARN, ERROR, FATAL | ✓ |
| `service` | String | Service name (order-service, search-service) | ✓ |
| `version` | String | Service version (semantic) | ✓ |
| `environment` | String | dev, staging, prod | ✓ |
| `correlation_id` | UUID | Trace entire request chain | ✓ |
| `trace_id` | String | X-Ray trace ID (if distributed tracing) | ✗ |
| `span_id` | String | X-Ray span ID (if distributed tracing) | ✗ |
| `user_id` | UUID | User context (if authenticated) | ✗ |
| `request_id` | String | HTTP request ID | ✗ |
| `message` | String | Human-readable message | ✓ |
| `context` | Object | Business context (order_id, product_id, etc.) | ✓ |
| `performance` | Object | Timing metrics (duration_ms, db_time_ms) | ✗ |
| `tags` | Array | Keywords for filtering (payment, api, cache) | ✓ |
| `errors` | Object or null | Error details (code, message, stack trace) | ✗ |

#### Log Levels

**DEBUG** — Development only, verbose tracing
{
  "level": "DEBUG",
  "message": "Cache miss for product_id",
  "context": {
    "product_id": "prod_001",
    "cache_key": "product:prod_001:details"
  }
}

**INFO** — Normal operational events
{
  "level": "INFO",
  "message": "Order created successfully",
  "context": {
    "order_id": "ord_abc123",
    "status": "pending"
  }
}

**WARN** — Degraded but recoverable
{
  "level": "WARN",
  "message": "Slow database query detected",
  "context": {
    "query": "SELECT * FROM products WHERE...",
    "duration_ms": 2500
  },
  "tags": ["performance", "database"]
}

**ERROR** — Service error, client retry may help
{
  "level": "ERROR",
  "message": "Payment processing failed",
  "context": {
    "order_id": "ord_abc123",
    "payment_id": "pay_xyz789"
  },
  "errors": {
    "code": "PAYMENT_DECLINED",
    "message": "Card declined by issuer",
    "retry_attempt": 1,
    "max_retries": 3
  }
}

**FATAL** — Service cannot recover, requires intervention
{
  "level": "FATAL",
  "message": "Database connection pool exhausted",
  "context": {
    "current_connections": 256,
    "max_connections": 256
  },
  "errors": {
    "code": "DB_POOL_EXHAUSTED",
    "message": "No available connections after 30s wait",
    "stack_trace": "..."
  }
}

### Logging Implementation (per language)

#### Java (Spring Boot) — Order Service

**Dependency:**
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-core</artifactId>
    <version>1.4.11</version>
</dependency>
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-contrib</artifactId>
    <version>0.1.5</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.9</version>
</dependency>
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>

**logback-spring.xml (JSON logging):**
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <conversionRule conversionWord="clr" converterClass="org.springframework.boot.logging.logback.ColorConverter"/>

  <!-- CloudWatch JSON appender -->
  <appender name="CLOUDWATCH_JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <includeContext>true</includeContext>
      <includeMdcKeyName>correlation_id</includeMdcKeyName>
      <includeMdcKeyName>trace_id</includeMdcKeyName>
      <customFields>
        {
          "service": "order-service",
          "version": "${project.version}",
          "environment": "${SPRING_PROFILES_ACTIVE}"
        }
      </customFields>
    </encoder>
  </appender>

  <!-- Async appender for performance -->
  <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
    <appender-ref ref="CLOUDWATCH_JSON"/>
    <queueSize>512</queueSize>
    <discardingThreshold>0</discardingThreshold>
  </appender>

  <!-- Spring Boot defaults filtered for clarity -->
  <logger name="org.springframework" level="WARN"/>
  <logger name="org.springframework.web" level="INFO"/>

  <root level="INFO">
    <appender-ref ref="ASYNC"/>
  </root>
</configuration>

**Usage in code:**
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public Order createOrder(CreateOrderRequest req, String correlationId) {
        // Populate MDC (Mapped Diagnostic Context)
        MDC.put("correlation_id", correlationId);
        MDC.put("trace_id", tracing.getTraceId());
        MDC.put("user_id", req.userId.toString());
        MDC.put("request_id", req.requestId);

        try {
            logger.info("Order creation started", Map.of(
                "context", Map.of(
                    "items_count", req.items.size(),
                    "total_amount", req.totalAmount
                ),
                "tags", List.of("order", "api")
            ));

            // Business logic
            Order order = persistOrder(req);

            logger.info("Order created successfully", Map.of(
                "context", Map.of(
                    "order_id", order.getId(),
                    "status", order.getStatus()
                ),
                "performance", Map.of(
                    "duration_ms", System.currentTimeMillis() - startTime,
                    "db_time_ms", dbTimeMs
                ),
                "tags", List.of("order", "success")
            ));

            return order;
        } catch (PaymentException e) {
            logger.error("Payment processing failed", Map.of(
                "context", Map.of(
                    "order_id", req.orderId,
                    "payment_method", req.paymentMethod
                ),
                "errors", Map.of(
                    "code", e.getCode(),
                    "message", e.getMessage(),
                    "retry_attempt", retryCount
                ),
                "tags", List.of("error", "payment")
            ));
            throw e;
        } finally {
            MDC.clear();
        }
    }

    private Map<String, Object> buildLogEntry(
        String level,
        String message,
        Map<String, Object> context,
        Map<String, Object> performance,
        List<String> tags,
        Map<String, Object> errors
    ) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", Instant.now());
        entry.put("level", level);
        entry.put("service", "order-service");
        entry.put("version", "1.0.0");
        entry.put("environment", System.getenv("SPRING_PROFILES_ACTIVE"));
        entry.put("correlation_id", MDC.get("correlation_id"));
        entry.put("trace_id", MDC.get("trace_id"));
        entry.put("user_id", MDC.get("user_id"));
        entry.put("request_id", MDC.get("request_id"));
        entry.put("message", message);
        entry.put("context", context);
        if (performance != null) entry.put("performance", performance);
        entry.put("tags", tags);
        entry.put("errors", errors);
        return entry;
    }
}

#### Python (FastAPI) — Search Service

**Dependency:**
python-json-logger==2.0.7
pythonjsonlogger==2.0.7

**logging_config.py:**
import json
import logging
from logging.config import dictConfig
from pythonjsonlogger import jsonlogger
from contextvars import ContextVar
import uuid
from datetime import datetime

# Context variables (thread-safe)
correlation_id: ContextVar[str] = ContextVar('correlation_id', default=str(uuid.uuid4()))
trace_id: ContextVar[str] = ContextVar('trace_id', default='')
user_id: ContextVar[str] = ContextVar('user_id', default='')

class JsonFormatter(jsonlogger.JsonFormatter):
    def add_fields(self, log_record, record, message_dict):
        super().add_fields(log_record, record, message_dict)
        
        # Add fixed fields
        log_record['service'] = 'search-service'
        log_record['version'] = '1.0.0'
        log_record['environment'] = os.getenv('ENVIRONMENT', 'dev')
        log_record['timestamp'] = datetime.utcnow().isoformat() + 'Z'
        
        # Add context variables
        log_record['correlation_id'] = correlation_id.get()
        log_record['trace_id'] = trace_id.get()
        log_record['user_id'] = user_id.get()
        
        # Add tags
        if 'tags' in message_dict:
            log_record['tags'] = message_dict.pop('tags')

LOGGING_CONFIG = {
    'version': 1,
    'disable_existing_loggers': False,
    'formatters': {
        'json': {
            '()': JsonFormatter,
            'format': '%(timestamp)s %(level)s %(service)s %(message)s'
        }
    },
    'handlers': {
        'console': {
            'class': 'logging.StreamHandler',
            'formatter': 'json',
            'stream': 'ext://sys.stdout'
        }
    },
    'root': {
        'level': 'INFO',
        'handlers': ['console']
    }
}

dictConfig(LOGGING_CONFIG)
logger = logging.getLogger(__name__)

**Usage in code:**
from fastapi import FastAPI, Request
import logging

app = FastAPI()
logger = logging.getLogger(__name__)

@app.middleware("http")
async def add_correlation_id(request: Request, call_next):
    """Add correlation ID to all requests"""
    corr_id = request.headers.get('X-Correlation-ID', str(uuid.uuid4()))
    correlation_id.set(corr_id)
    
    response = await call_next(request)
    response.headers['X-Correlation-ID'] = corr_id
    return response

@app.get("/api/v1/search")
async def search(q: str, page_size: int = 20, current_user: JwtToken = Depends(verify_jwt)):
    user_id.set(str(current_user.user_id))
    
    start_time = time.time()
    
    logger.info(
        "Search query started",
        extra={
            "context": {
                "query": q,
                "page_size": page_size
            },
            "tags": ["search", "api"]
        }
    )
    
    try:
        results = await elasticsearch_client.search(
            query=q,
            page_size=page_size
        )
        
        duration_ms = (time.time() - start_time) * 1000
        
        logger.info(
            "Search completed successfully",
            extra={
                "context": {
                    "query": q,
                    "results_count": len(results),
                    "total_count": results['total']
                },
                "performance": {
                    "duration_ms": duration_ms,
                    "es_query_time_ms": results.get('query_time_ms', 0)
                },
                "tags": ["search", "success"]
            }
        )
        
        return results
    
    except Exception as e:
        duration_ms = (time.time() - start_time) * 1000
        
        logger.error(
            "Search failed",
            extra={
                "context": {
                    "query": q
                },
                "errors": {
                    "code": e.__class__.__name__,
                    "message": str(e),
                    "retry_attempt": retry_count
                },
                "performance": {
                    "duration_ms": duration_ms
                },
                "tags": ["error", "search"]
            }
        )
        raise

#### TypeScript (NestJS) — Payment Service

**Dependency:**
npm install winston winston-elasticsearch @aws-sdk/client-cloudwatch-logs

**logger.ts:**
import * as winston from 'winston';
import { createLogger, format, transports } from 'winston';
import { ElasticsearchTransport } from 'winston-elasticsearch';
import { v4 as uuidv4 } from 'uuid';

// Async storage for context
import { AsyncLocalStorage } from 'async_hooks';

interface LogContext {
  correlationId: string;
  traceId: string;
  userId?: string;
  requestId?: string;
}

const asyncLocalStorage = new AsyncLocalStorage<LogContext>();

export class Logger {
  private logger: winston.Logger;

  constructor() {
    const isProduction = process.env.NODE_ENV === 'production';

    this.logger = createLogger({
      format: format.combine(
        format.timestamp({ format: 'YYYY-MM-DDTHH:mm:ss.SSSZ' }),
        format.printf((info) => {
          const context = asyncLocalStorage.getStore() || {};

          return JSON.stringify({
            timestamp: info.timestamp,
            level: info.level.toUpperCase(),
            service: 'payment-service',
            version: '1.0.0',
            environment: process.env.ENVIRONMENT || 'dev',
            correlation_id: context.correlationId,
            trace_id: context.traceId,
            user_id: context.userId,
            request_id: context.requestId,
            message: info.message,
            context: info.context || {},
            performance: info.performance || {},
            tags: info.tags || [],
            errors: info.errors || null,
          });
        })
      ),
      transports: [
        new transports.Console({
          format: format.combine(
            format.colorize(),
            format.printf((info) => `${info.timestamp} [${info.level}] ${info.message}`)
          )
        }),
        ...(isProduction ? [
          new ElasticsearchTransport({
            level: 'info',
            clientOpts: {
              node: process.env.ELASTICSEARCH_URL,
            },
            index: 'logs-payment-service',
          })
        ] : [])
      ]
    });
  }

  setContext(context: Partial<LogContext>) {
    const current = asyncLocalStorage.getStore() || {
      correlationId: uuidv4(),
      traceId: '',
    };
    asyncLocalStorage.enterWith({ ...current, ...context });
  }

  info(message: string, data?: any) {
    this.logger.info(message, { context: data?.context, tags: data?.tags });
  }

  error(message: string, data?: any) {
    this.logger.error(message, {
      context: data?.context,
      errors: data?.errors,
      tags: data?.tags,
    });
  }

  warn(message: string, data?: any) {
    this.logger.warn(message, {
      context: data?.context,
      tags: data?.tags,
    });
  }

  debug(message: string, data?: any) {
    this.logger.debug(message, { context: data?.context });
  }
}

export const logger = new Logger();

### Log Aggregation & Retention

**CloudWatch Log Group Configuration:**

# Per-service log groups with retention
resource "aws_cloudwatch_log_group" "order_service" {
  name              = "/aws/ecs/order-service"
  retention_in_days = var.environment == "prod" ? 90 : 30
  kms_key_id        = aws_kms_key.logs.arn

  tags = local.tags_common
}

# Log subscription filter (export to S3 for long-term storage)
resource "aws_cloudwatch_log_group_subscription_filter" "order_service_to_s3" {
  name            = "order-service-logs-to-s3"
  log_group_name  = aws_cloudwatch_log_group.order_service.name
  filter_pattern  = ""  # All logs
  destination_arn = aws_kinesis_firehose_delivery_stream.logs_to_s3.arn
}

# Kinesis Firehose (deliver logs to S3)
resource "aws_kinesis_firehose_delivery_stream" "logs_to_s3" {
  name            = "bookstore-logs-to-s3"
  destination     = "extended_s3"
  s3_destination_config {
    role_arn   = aws_iam_role.firehose.arn
    bucket_arn = aws_s3_bucket.logs.arn
    prefix     = "logs/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/hour=!{timestamp:HH}/"
    
    # Partition by date for querying
    cloudwatch_logging_options {
      enabled         = true
      log_group_name  = aws_cloudwatch_log_group.firehose.name
      log_stream_name = "S3Delivery"
    }
  }
}

**Log Search Queries (CloudWatch Logs Insights):**

Find all errors in last 1 hour:
fields @timestamp, @message, service, error_code
| filter level = "ERROR"
| stats count() by error_code

Find slow database queries (>2 seconds):
fields @timestamp, @duration, query
| filter performance.db_time_ms > 2000
| sort performance.db_time_ms desc

Find orders by user:
fields @timestamp, order_id, user_id, status
| filter user_id = "user_abc123"
| sort @timestamp desc

---

## Metrics & Instrumentation

### Metrics Categories

#### RED Metrics (Request Rate, Errors, Duration)

**Request Rate (RPS):**
- What: Requests per second
- How: Increment counter on each request
- Alert: N/A (informational)

// Micrometer (Spring Boot)
Counter requestCounter = registry.counter(
    "http.requests.total",
    "service", "order-service",
    "method", request.getMethod(),
    "endpoint", request.getPath(),
    "status", response.getStatus()
);
requestCounter.increment();

**Error Rate (% of requests that error):**
- What: Percentage of requests returning 4xx/5xx
- How: Divide error requests by total requests
- Alert: > 1% for 5 minutes (warning), > 5% (critical)

# Prometheus
error_rate = (error_requests_total / requests_total) * 100

**Duration (Latency):**
- What: Time from request start to response
- How: Histogram of response times
- Alert: p99 > 1 second (warning), p99 > 5 seconds (critical)

// Prometheus
histogram_quantile(0.99, request_duration_seconds_bucket)

#### USE Metrics (Utilization, Saturation, Errors)

**Utilization (% of resource capacity used):**
- CPU: `(1 - idle%) * 100`
- Memory: `(used / total) * 100`
- Disk: `(used / total) * 100`
- DB connections: `(active / max) * 100`

Alert thresholds:
- CPU > 80% for 5 minutes: Scale up
- Memory > 85% for 5 minutes: Warning
- DB connections > 90% of max: Critical

**Saturation (Queue depth, waiting time):**
- Task queue depth: Number of tasks waiting
- Database connection pool: Waiting threads
- Network: Packet retransmissions

Alert thresholds:
- SQS queue depth > 1000: Scale consumers
- DB pool waiters > 10: Increase pool size
- Retransmit rate > 1%: Network issue

**Errors (for resources):**
- Failed requests
- Timeouts
- Retries

### Metrics Implementation

#### Java (Micrometer + Prometheus)

**Dependency:**
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <version>1.12.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
    <version>3.2.0</version>
</dependency>

**Custom Metrics:**
import io.micrometer.core.instrument.*;

@Configuration
public class MetricsConfig {
    
    @Bean
    public MeterBinder customMetrics() {
        return (registry) -> {
            // Counter: Orders created
            Counter.builder("orders.created.total")
                .description("Total orders created")
                .register(registry);
            
            // Gauge: Current order count by status
            registry.gauge("orders.by_status",
                new AtomicReference<>(getOrdersByStatus()),
                value -> value.get().size()
            );
            
            // Timer: Order creation latency
            Timer.builder("order.creation.duration")
                .description("Time to create an order")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
            
            // Histogram: Payment amounts
            DistributionSummary.builder("payment.amount")
                .description("Payment amounts in USD")
                .baseUnit("cents")
                .register(registry);
        };
    }
}

@Service
public class OrderService {
    
    private final MeterRegistry registry;
    private final Timer orderCreationTimer;
    
    public OrderService(MeterRegistry registry) {
        this.registry = registry;
        this.orderCreationTimer = Timer.builder("order.creation.duration")
            .register(registry);
    }
    
    public Order createOrder(CreateOrderRequest req) {
        return orderCreationTimer.recordCallable(() -> {
            // Business logic
            Order order = persistOrder(req);
            
            // Increment counter
            registry.counter("orders.created.total",
                "status", order.getStatus(),
                "currency", order.getCurrency()
            ).increment();
            
            return order;
        });
    }
}

**Prometheus Scrape Configuration:**
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'order-service'
    static_configs:
      - targets: ['order-service.bookstore.local:8080']
    metrics_path: '/actuator/prometheus'

#### Python (Prometheus Client)

**Dependency:**
prometheus-client==0.19.0

**Custom Metrics:**
from prometheus_client import Counter, Gauge, Histogram, Summary, CollectorRegistry, generate_latest
from prometheus_client import start_http_server

# Create registry
registry = CollectorRegistry()

# Counter: Search queries
search_counter = Counter(
    'search_queries_total',
    'Total search queries',
    ['query_type', 'status'],
    registry=registry
)

# Gauge: Search result cache size
cache_size = Gauge(
    'search_cache_size_bytes',
    'Search cache size in bytes',
    registry=registry
)

# Histogram: Search latency
search_duration = Histogram(
    'search_duration_seconds',
    'Time to execute search',
    buckets=(0.01, 0.05, 0.1, 0.5, 1.0, 2.5, 5.0),
    registry=registry
)

# Summary: Recommendations algorithm performance
recommendations_duration = Summary(
    'recommendations_generation_seconds',
    'Time to generate recommendations',
    registry=registry
)

# Usage
@app.get("/api/v1/search")
async def search(q: str):
    start_time = time.time()
    
    try:
        results = await elasticsearch_client.search(q)
        search_counter.labels(query_type='full_text', status='success').inc()
    except Exception as e:
        search_counter.labels(query_type='full_text', status='error').inc()
        raise
    finally:
        duration = time.time() - start_time
        search_duration.observe(duration)
    
    return results

# Expose metrics on /metrics endpoint
@app.get("/metrics")
async def metrics():
    return generate_latest(registry)

#### TypeScript (prom-client)

**Dependency:**
npm install prom-client

**Custom Metrics:**
import { register, Counter, Gauge, Histogram, Summary, ContentType } from 'prom-client';

// Counter: Payment transactions
const paymentCounter = new Counter({
  name: 'payment_transactions_total',
  help: 'Total payment transactions',
  labelNames: ['status', 'payment_method'],
});

// Gauge: Payment processing queue depth
const queueDepth = new Gauge({
  name: 'payment_queue_depth',
  help: 'Payment processing queue depth',
});

// Histogram: Payment processing time
const paymentDuration = new Histogram({
  name: 'payment_processing_seconds',
  help: 'Time to process payment',
  buckets: [0.01, 0.05, 0.1, 0.5, 1, 2, 5, 10],
});

// Summary: Stripe API latency
const stripeDuration = new Summary({
  name: 'stripe_api_duration_seconds',
  help: 'Stripe API response time',
  percentiles: [0.5, 0.9, 0.99],
});

// Middleware for request timing
app.use((req: Request, res: Response, next: NextFunction) => {
  const start = Date.now();
  
  res.on('finish', () => {
    const duration = (Date.now() - start) / 1000;
    paymentDuration.observe(duration);
  });
  
  next();
});

// Route handler
app.post('/api/v1/payments', async (req: Request, res: Response) => {
  queueDepth.inc();
  const timerEnd = stripeDuration.startTimer();
  
  try {
    const payment = await stripe.charges.create({...});
    paymentCounter.labels(status: 'success', payment_method: req.body.method).inc();
    res.json(payment);
  } catch (error) {
    paymentCounter.labels(status: 'error', payment_method: req.body.method).inc();
    throw error;
  } finally {
    timerEnd();
    queueDepth.dec();
  }
});

// Expose metrics
app.get('/metrics', (req: Request, res: Response) => {
  res.set('Content-Type', register.contentType);
  res.end(register.metrics());
});

---

## Service Level Indicators (SLIs)

### SLI Definition Framework

**SLI = Successful Requests / Total Requests**

For each service, measure:
1. **Availability**: Request succeeds (HTTP 2xx/3xx)
2. **Latency**: Response time within threshold
3. **Error rate**: No client/server errors

### Target SLOs (Service Level Objectives)

| Service | Availability SLO | Latency SLO (p99) | Error Rate SLO |
|---------|-----------------|------------------|----------------|
| **Order Service** | 99.9% | < 1 second | < 0.1% |
| **User Service** | 99.9% | < 500ms | < 0.1% |
| **Catalog Service** | 99.5% | < 500ms | < 0.5% |
| **Payment Service** | 99.95% | < 5 seconds | < 0.05% |
| **Search Service** | 99.0% | < 1 second | < 1.0% |
| **Notifications Service** | 95.0% | N/A (async) | < 5.0% |

### SLI Calculation Queries

**Availability (from logs/metrics):**
# All successful requests (2xx/3xx) in last 5 minutes
successful_requests = sum(rate(http_requests_total{status=~"2.."}[5m]))

# All requests
total_requests = sum(rate(http_requests_total[5m]))

# Availability %
availability = (successful_requests / total_requests) * 100

**Latency (from metrics):**
# P99 latency in last 5 minutes
p99_latency = histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[5m]))

**Error Rate (from logs):**
# Error requests in last 5 minutes
error_requests = sum(rate(http_requests_total{status=~"4..|5.."}[5m]))

# Error rate %
error_rate = (error_requests / total_requests) * 100

### SLI Monitoring Dashboard

**Terraform CloudWatch Dashboard:**

resource "aws_cloudwatch_dashboard" "sli" {
  dashboard_name = "bookstore-sli"

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          title   = "Order Service: Availability (SLO: 99.9%)"
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime",
             { stat = "Average", label = "Availability %" }]
          ]
          period = 300
          yAxis = { left = { min = 95, max = 100 } }
        }
      },
      {
        type = "metric"
        properties = {
          title   = "Order Service: P99 Latency (SLO: < 1s)"
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime",
             { stat = "p99", label = "P99 Latency (ms)" }]
          ]
          yAxis = { left = { min = 0, max = 1000 } }
        }
      },
      {
        type = "log"
        properties = {
          title = "Error Rate by Service"
          query = """
            fields service, @message
            | filter level = "ERROR"
            | stats count() as error_count by service
            | sort error_count desc
          """
        }
      }
    ]
  })
}

---

## Distributed Tracing

### Trace Context Propagation

**Standard Headers (W3C Trace Context):**

traceparent: version-traceid-parentid-traceflags
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
tracestate: vendor1=value1,vendor2=value2

**AWS X-Ray Headers:**

X-Amzn-Trace-Id: Root=1-5e1be6a7-acde48db7f6da47d12345678;Parent=5e1be6a7acde48db;Sampled=1

### Trace Sampling Strategy

**Sampling Rules (for cost control):**

resource "aws_xray_sampling_rule" "bookstore" {
  rule_name      = "bookstore-sampling"
  priority       = 1000
  version        = 1
  reservoir_size = 1
  fixed_rate     = 0.05  # Sample 5% of all requests
  url_path       = "*"
  host           = "*"
  http_method    = "*"
  service_type   = "*"
  service_name   = "*"
  resource_arn   = "*"
}

# Overrides: Sample 100% of errors
resource "aws_xray_sampling_rule" "errors" {
  rule_name      = "bookstore-errors"
  priority       = 100    # Higher priority than default
  version        = 1
  reservoir_size = 10
  fixed_rate     = 1.0    # Sample 100%
  url_path       = "*"
  host           = "*"
  http_method    = "*"
  service_type   = "*"
  service_name   = "*"
  resource_arn   = "*"

  attributes = {
    error = "true"
  }
}

### Trace Instrumentation

#### Java (Spring Cloud Sleuth + X-Ray)

**Dependency:**
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
    <version>3.1.9</version>
</dependency>
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-xray-recorder-sdk-spring</artifactId>
    <version>2.12.0</version>
</dependency>

**Configuration:**
@Configuration
public class TracingConfig {
    
    @Bean
    public AWSXRay configureXRay() {
        AWSXRay.setGlobalRecorder(new AWSXRayRecorderBuilder().build());
        return AWSXRay.getGlobalRecorder();
    }
    
    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory requestFactory =
            new HttpComponentsClientHttpRequestFactory();
        
        return new RestTemplate(
            new AWSXRayClientHttpRequestFactory(requestFactory)
        );
    }
}

**Usage:**
@Service
public class OrderService {
    
    private final AWSXRay xray = AWSXRay.getGlobalRecorder();
    
    public Order createOrder(CreateOrderRequest req) {
        // Create subsegment for database operation
        Segment dbSegment = xray.beginSegment("database-write");
        try {
            Order order = persistOrder(req);
            dbSegment.putMetadata("order_id", order.getId());
            return order;
        } finally {
            xray.endSegment(dbSegment);
        }
    }
    
    public void callCatalogService(UUID productId) {
        // Automatic tracing for HTTP client
        Segment catalogSegment = xray.beginSegment("catalog-service-call");
        try {
            restTemplate.getForObject(
                "http://catalog-service/api/v1/products/{id}",
                Product.class,
                productId
            );
        } finally {
            xray.endSegment(catalogSegment);
        }
    }
}

#### Python (Jaeger Client for local dev, X-Ray for prod)

**Dependency:**
aws-xray-sdk==2.12.0
jaeger-client==4.8.0  # For local development

**Configuration:**
from aws_xray_sdk.core import xray_recorder
from aws_xray_sdk.core import patch_all
from aws_xray_sdk.core.context import Context
from aws_xray_sdk.ext.flask.middleware import XRayMiddleware
import os

# Patch AWS SDK and HTTP libraries
patch_all()

# Configure X-Ray
if os.getenv('ENVIRONMENT') == 'prod':
    xray_recorder.configure(
        context=Context(),
        service='search-service',
        emitter=xray_recorder.emitter,
    )
else:
    xray_recorder.configure(context_missing='LOG_ERROR')

# Add to Flask/FastAPI app
app = FastAPI()
XRayMiddleware(app, xray_recorder)

# Usage
from aws_xray_sdk.core.context import Context

@app.get("/api/v1/search")
async def search(q: str):
    # Automatic subsegment for database queries
    subsegment = xray_recorder.current_subsegment()
    
    # Custom metadata
    subsegment.put_metadata('query', q)
    
    # Call external service (automatically traced)
    results = await elasticsearch_client.search(q)
    
    subsegment.put_metadata('results_count', len(results))
    
    return results

#### TypeScript (X-Ray SDK)

**Dependency:**
npm install aws-xray-sdk-core aws-sdk

**Configuration:**
import * as AWSXRay from 'aws-xray-sdk-core';
import * as AWS from 'aws-sdk';

// Patch AWS SDK
AWSXRay.config([AWSXRay.plugins.EC2Plugin, AWSXRay.plugins.ECSPlugin]);

// Wrap HTTP client
import axios from 'axios';
const http = AWSXRay.captureAxiosClient(axios);

// Usage
@Controller('/api/v1/payments')
export class PaymentController {
    
    @Post()
    async chargeCard(@Body() req: ChargeCardRequest) {
        const segment = AWSXRay.getSegment();
        const subsegment = segment.addNewSubsegment('stripe-charge');
        
        try {
            subsegment.addMetadata('amount', req.amount);
            subsegment.addMetadata('currency', req.currency);
            
            const charge = await stripe.charges.create({
                amount: req.amount,
                currency: req.currency
            });
            
            subsegment.addMetadata('charge_id', charge.id);
            return charge;
        } catch (error) {
            subsegment.addError(error);
            throw error;
        } finally {
            subsegment.close();
        }
    }
}

### Trace Analysis Queries

**Find all spans for a trace ID:**
SELECT * FROM logs
WHERE trace_id = '1-5e1be6a7-acde48db7f6da47d12345678'
ORDER BY timestamp

**Find slow traces (p99 > 1 second):**
SELECT trace_id, duration_ms, service
FROM traces
WHERE duration_ms > 1000
ORDER BY duration_ms DESC
LIMIT 100

**Find traces with errors:**
SELECT trace_id, service, error_code, error_message
FROM traces
WHERE error_code IS NOT NULL
ORDER BY timestamp DESC

---

## Alerting Rules & On-Call

### Alert Classification

| Severity | Response Time | Escalation | Example |
|----------|---------------|------------|---------|
| **P1 (Critical)** | Immediate | Instant page | All orders failing |
| **P2 (High)** | 15 minutes | Page then escalate | Error rate > 10% |
| **P3 (Medium)** | 1 hour | Ticket | P99 latency > 2s |
| **P4 (Low)** | Next business day | Email | CPU > 80% |

### Critical Alerts (SLI-Based)

#### Alert 1: Order Service Availability < 99%

resource "aws_cloudwatch_metric_alarm" "order_availability" {
  alarm_name          = "order-service-availability-critical"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 2
  threshold           = 99.0
  metric_name         = "HTTPSuccessRate"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Average"
  alarm_actions       = [aws_sns_topic.critical_alerts.arn]
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.order_service.arn_suffix
  }
}

**Runbook:** See section "Order Service Availability Critical"

#### Alert 2: Payment Service Error Rate > 1%

resource "aws_cloudwatch_log_group_metric_filter" "payment_errors" {
  name           = "payment-error-rate"
  log_group_name = aws_cloudwatch_log_group.payment_service.name
  filter_pattern = "[level = ERROR]"

  metric_transformation {
    name      = "payment-errors-count"
    namespace = "bookstore/payment"
    value     = "1"
  }
}

resource "aws_cloudwatch_metric_alarm" "payment_error_rate" {
  alarm_name          = "payment-service-error-rate-critical"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  threshold           = 1.0
  metric_name         = "ErrorRate"
  namespace           = "bookstore/payment"
  period              = 300
  statistic           = "Average"
  alarm_actions       = [aws_sns_topic.critical_alerts.arn]
  alarm_description   = "Payment service error rate > 1%"
}

**Runbook:** See section "Payment Service High Error Rate"

#### Alert 3: RDS Database Connection Exhaustion

resource "aws_cloudwatch_metric_alarm" "rds_connections_exhaustion" {
  alarm_name          = "rds-connections-exhaustion"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  threshold           = 240  # Out of 256 max
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Maximum"
  alarm_actions       = [aws_sns_topic.critical_alerts.arn]

  dimensions = {
    DBClusterIdentifier = aws_rds_cluster.bookstore.cluster_identifier
  }
}

**Runbook:** See section "RDS Connection Pool Exhaustion"

#### Alert 4: SQS Dead Letter Queue Messages

resource "aws_cloudwatch_metric_alarm" "dlq_messages" {
  alarm_name          = "fulfillment-dlq-messages"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  threshold           = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300
  statistic           = "Average"
  alarm_actions       = [aws_sns_topic.critical_alerts.arn]

  dimensions = {
    QueueName = aws_sqs_queue.fulfillment_dlq.name
  }
}

**Runbook:** See section "SQS Dead Letter Queue"

### Warning Alerts

#### Alert 5: Slow Database Queries

resource "aws_cloudwatch_log_group_metric_filter" "slow_queries" {
  name           = "slow-database-queries"
  log_group_name = aws_cloudwatch_log_group.rds.name
  filter_pattern = "[log_type = POSTGRESQL, duration > 2000]"

  metric_transformation {
    name      = "slow-query-count"
    namespace = "bookstore/database"
    value     = "1"
  }
}

resource "aws_cloudwatch_metric_alarm" "slow_queries_warning" {
  alarm_name          = "slow-queries-warning"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 5
  threshold           = 10
  metric_name         = "slow-query-count"
  namespace           = "bookstore/database"
  period              = 300
  statistic           = "Sum"
  alarm_actions       = [aws_sns_topic.warnings.arn]
}

#### Alert 6: High CPU Utilization

resource "aws_cloudwatch_metric_alarm" "ecs_cpu_high" {
  alarm_name          = "order-service-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  threshold           = 80
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 300
  statistic           = "Average"
  alarm_actions       = [aws_sns_topic.warnings.arn]

  dimensions = {
    ServiceName = "order-service"
  }
}

### On-Call Escalation

**Escalation Policy:**
P1 Alert (Page)
  ↓
On-Call Engineer (immediate)
  ↓
If not resolved in 15 minutes:
Escalate to Team Lead
  ↓
If not resolved in 30 minutes:
Escalate to Engineering Manager

**PagerDuty Integration:**

resource "pagerduty_escalation_policy" "bookstore" {
  name = "bookstore-escalation"

  escalation_rule {
    escalation_delay_in_minutes = 15
    target {
      type = "user_id"
      id   = pagerduty_user.on_call_engineer.id
    }
  }

  escalation_rule {
    escalation_delay_in_minutes = 15
    target {
      type = "user_id"
      id   = pagerduty_user.team_lead.id
    }
  }
}

resource "pagerduty_service" "bookstore" {
  name                    = "Bookstore Backend"
  escalation_policy       = pagerduty_escalation_policy.bookstore.id
  alert_creation          = "on_demand"
  alert_grouping_disabled = false
}

resource "pagerduty_integration" "aws_sns" {
  type    = "aws_cloudwatch_integration"
  service = pagerduty_service.bookstore.id
  name    = "CloudWatch Integration"
}

---

## Dashboards & Visualization

### Executive Dashboard (Metrics for non-technical stakeholders)

**KPIs:**
- Daily Active Users (DAU)
- Orders per day
- Revenue per day
- System uptime %

### Engineering Dashboard (Detailed operations)

**Sections:**
1. **Service Health:** Availability, latency, error rate per service
2. **Infrastructure:** CPU, memory, disk, network per AZ
3. **Database:** Connections, query latency, replication lag
4. **Events:** Recent deployments, alerts triggered, incidents

### On-Call Dashboard (For incident response)

**Sections:**
1. **Current Alerts:** All firing alerts (red), triggered in last 24h
2. **Error Logs:** Last 100 errors with stack traces
3. **Performance:** Real-time p50/p95/p99 latency
4. **Infrastructure Status:** All service health indicators

**Terraform CloudWatch Dashboard:**

resource "aws_cloudwatch_dashboard" "oncall" {
  dashboard_name = "bookstore-oncall"

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          title   = "🔴 Current Alerts"
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime",
             { stat = "Maximum", label = "Response Time" }]
          ]
        }
      },
      {
        type = "log"
        properties = {
          title = "Last 100 Errors"
          query = """
            fields @timestamp, @message, service, error_code
            | filter level = "ERROR"
            | sort @timestamp desc
            | limit 100
          """
        }
      },
      {
        type = "metric"
        properties = {
          title   = "P50/P95/P99 Latency"
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime",
             { stat = "p50", label = "P50" }],
            ["AWS/ApplicationELB", "TargetResponseTime",
             { stat = "p95", label = "P95" }],
            ["AWS/ApplicationELB", "TargetResponseTime",
             { stat = "p99", label = "P99" }]
          ]
          yAxis = { left = { min = 0, max = 5000 } }
        }
      }
    ]
  })
}

---

## Incident Response

### Incident Severity Classification

**SEV-1:** Loss of service for significant user base
- Example: All orders failing
- Response: Page on-call immediately
- Target Resolution: 1 hour

**SEV-2:** Partial service degradation
- Example: Payment service 10% error rate
- Response: Page on-call within 15 minutes
- Target Resolution: 4 hours

**SEV-3:** Minor issues with workarounds
- Example: Search service slow (p99 > 2s)
- Response: Create ticket, page during business hours
- Target Resolution: 24 hours

### Incident Response Process

#### Phase 1: Detection (0-5 minutes)

1. Alert fires in PagerDuty
2. Page sent to on-call engineer
3. Engineer acknowledges alert
4. War room opened (Slack channel + Zoom call)

#### Phase 2: Triage (5-15 minutes)

1. **Gather information:**
   - When did it start?
   - What changed recently? (deployments, config changes)
   - Affected services/users
   
2. **Assess severity:**
   - Is it SEV-1, 2, or 3?
   - Escalate if needed

3. **Check dashboards:**
   - View system health dashboard
   - Check alert history
   - Look for recent errors/warnings

**Investigation Queries:**

-- Find affected orders in last 30 minutes
SELECT COUNT(*), status FROM orders
WHERE created_at > NOW() - INTERVAL '30 minutes'
GROUP BY status

-- Find payment errors
SELECT COUNT(*), error_code FROM payment_errors
WHERE timestamp > NOW() - INTERVAL '30 minutes'
GROUP BY error_code

-- Find service errors
SELECT service, COUNT(*) as error_count
FROM logs
WHERE level = 'ERROR' AND timestamp > NOW() - INTERVAL '30 minutes'
GROUP BY service

#### Phase 3: Mitigation (15-60 minutes)

**Option 1: Scale Up**
# Scale order service to 10 tasks
aws ecs update-service \
  --cluster bookstore-prod \
  --service order-service \
  --desired-count 10

**Option 2: Circuit Breaker**
Temporarily disable external service calls (payment, search)
Return error quickly instead of timeout

**Option 3: Database Failover**
# Promote read replica to primary
aws rds promote-read-replica \
  --db-instance-identifier bookstore-read-replica-1

**Option 4: Rollback Deployment**
# Rollback to previous version
aws ecs update-service \
  --cluster bookstore-prod \
  --service order-service \
  --force-new-deployment

#### Phase 4: Resolution & Follow-Up

1. **Verify fix:**
   - Confirm alerts resolved
   - Check metrics returning to normal
   - Test core functionality

2. **Post-Incident:**
   - Document what happened (timeline)
   - Identify root cause
   - Create action items (to prevent recurrence)
   - Schedule blameless post-mortem meeting

### Incident Communication Template

**Initial Alert (Slack):**
🚨 SEV-2: Payment Service High Error Rate
- Service: payment-service
- Error Rate: 15%
- Affected: ~5% of users
- Start Time: 2026-01-18 12:35 UTC
- Status: Investigating
- Lead: @Alice (on-call)

**Update (every 15 minutes):**
📊 Update #1 (12:50 UTC)
- Found: Recent deployment to payment-service caused issue
- Action: Rolling back deployment
- ETA: 10 minutes

**Resolution:**
✅ RESOLVED (12:55 UTC)
- Root Cause: Incorrect Stripe API version in deployment
- Fix: Reverted to previous version
- Duration: 20 minutes
- Post-Mortem: Thursday 10 AM UTC

---

## Operational Runbooks

### Runbook: Order Service Availability Critical

**Symptom:** Availability < 99% for 2 minutes

**Investigation (5 minutes):**

# 1. Check service health
aws ecs describe-services \
  --cluster bookstore-prod \
  --services order-service

# 2. Check recent errors
aws logs filter-log-events \
  --log-group-name /aws/ecs/order-service \
  --start-time $(date -d '10 minutes ago' +%s)000 \
  --query 'events[?level==`ERROR`]'

# 3. Check RDS connection pool
aws cloudwatch get-metric-statistics \
  --namespace AWS/RDS \
  --metric-name DatabaseConnections \
  --start-time $(date -u -d '10 minutes ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 60 \
  --statistics Maximum

**Mitigation (based on findings):**

**Case 1: High error rate from RDS**
# Scale RDS Proxy
aws docdb scale-db-cluster \
  --db-cluster-identifier bookstore-postgres \
  --scaling-type horizontal \
  --apply-immediately

# Or: Increase max connections
# Update parameter group: max_connections = 512

**Case 2: Tasks crashing**
# Check if new deployment has issue
aws ecs describe-task-definition \
  --task-definition order-service:1

# Rollback
aws ecs update-service \
  --cluster bookstore-prod \
  --service order-service \
  --force-new-deployment

**Case 3: Downstream service down (Catalog)**
# Implement circuit breaker
# Temporarily return degraded response:
# "Order created (inventory not reserved, will retry)"

# Or: Wait for downstream service recovery
# Monitor: aws ecs describe-services --cluster bookstore-prod --services catalog-service

**Verification:**
# Confirm availability restored
for i in {1..10}; do
  curl -s https://api.bookstore.local/health | jq '.status'
done

# Check metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/ApplicationELB \
  --metric-name HTTPSuccessRate \
  --start-time $(date -u -d '5 minutes ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 60 \
  --statistics Average

### Runbook: RDS Connection Pool Exhaustion

**Symptom:** Database connections > 240/256

**Investigation:**

-- Find which services have most connections
SELECT 
  application_name,
  count(*) as connection_count,
  state
FROM pg_stat_activity
GROUP BY application_name, state
ORDER BY connection_count DESC;

-- Find idle connections
SELECT 
  pid,
  application_name,
  state,
  query_start,
  state_change,
  query
FROM pg_stat_activity
WHERE state != 'active'
ORDER BY state_change DESC;

-- Check query slow queries
SELECT 
  now() - query_start as duration,
  query
FROM pg_stat_activity
WHERE state = 'active'
ORDER BY duration DESC
LIMIT 5;

**Mitigation:**

**Option 1: Increase RDS Proxy pool size**
aws rds modify-db-proxy \
  --db-proxy-name bookstore-proxy \
  --max-connections 200  # from 100

**Option 2: Kill idle connections**
-- Kill idle connections from specific app
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE application_name = 'order-service'
  AND state = 'idle'
  AND state_change < NOW() - INTERVAL '10 minutes';

**Option 3: Scale down non-critical tasks**
# Reduce search service to free connections
aws ecs update-service \
  --cluster bookstore-prod \
  --service search-service \
  --desired-count 1

---

## Observability Testing

### Chaos Engineering Tests

**Test 1: Simulate Service Failure**
# Kill random order service task
aws ecs update-service \
  --cluster bookstore-prod \
  --service order-service \
  --desired-count 2  # From 3

sleep 300  # Wait 5 minutes

# Verify:
# 1. Alerts fired in PagerDuty
# 2. Service still responsive (load balanced to remaining 2)
# 3. Auto-scale restored count to 3

**Test 2: Simulate Network Partition**
# Block traffic to RDS (using security group)
aws ec2 revoke-security-group-ingress \
  --group-id sg-12345 \
  --protocol tcp \
  --port 5432 \
  --cidr 10.0.0.0/16

# Verify:
# 1. Alerts for slow queries
# 2. Circuit breaker activates
# 3. Service returns errors quickly (not timeout)

# Restore
aws ec2 authorize-security-group-ingress \
  --group-id sg-12345 \
  --protocol tcp \
  --port 5432 \
  --cidr 10.0.0.0/16

**Test 3: Load Testing**
# Generate 1000 RPS load
ab -n 100000 -c 1000 https://api.bookstore.local/api/v1/products

# Monitor:
# 1. Latency p99 stays < 1s
# 2. Error rate < 1%
# 3. Auto-scaling triggers at ~60% CPU

### Observability Test Automation

#!/bin/bash
# test-observability.sh

set -e

echo "Running observability tests..."

# Test 1: Verify structured logging
echo "Test 1: Structured logging"
LOGS=$(aws logs filter-log-events \
  --log-group-name /aws/ecs/order-service \
  --start-time $(($(date +%s)*1000-600000)) \
  --query 'events[0].message')

# Verify JSON format
echo "$LOGS" | jq '.' > /dev/null || exit 1
echo "✓ Logs are valid JSON"

# Test 2: Verify correlation ID propagation
echo "Test 2: Correlation ID propagation"
curl -s -H "X-Correlation-ID: test-123" https://api.bookstore.local/api/v1/products \
  -H "Cookie: " > /dev/null

# Check logs contain correlation ID
FOUND=$(aws logs filter-log-events \
  --log-group-name /aws/ecs/user-service \
  --filter-pattern 'correlation_id="test-123"' \
  --query 'events | length(@)' \
  --output text)

if [ "$FOUND" -gt 0 ]; then
  echo "✓ Correlation ID propagated"
else
  echo "✗ Correlation ID NOT found"
  exit 1
fi

# Test 3: Verify metrics are being collected
echo "Test 3: Metrics collection"
METRICS=$(aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --start-time $(date -u -d '5 minutes ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 60 \
  --statistics Average \
  --query 'Datapoints | length(@)' \
  --output text)

if [ "$METRICS" -gt 0 ]; then
  echo "✓ Metrics are being collected"
else
  echo "✗ No metrics found"
  exit 1
fi

# Test 4: Verify alerts are configured
echo "Test 4: Alert configuration"
ALARMS=$(aws cloudwatch describe-alarms \
  --state-value INSUFFICIENT_DATA \
  --query 'MetricAlarms | length(@)' \
  --output text)

echo "✓ Found $ALARMS alarms configured"

echo ""
echo "All observability tests passed! ✓"

---

## Implementation Checklist

### Phase 1: Logging Infrastructure (Week 1)

- [ ] **CloudWatch Log Groups**
  - [ ] Create log groups per service
  - [ ] Configure retention policies
  - [ ] Setup encryption (KMS)
  - [ ] Configure subscription filters (to S3)

- [ ] **Structured Logging Implementation**
  - [ ] Implement JSON logging in Java (Order, User, Catalog services)
  - [ ] Implement JSON logging in Python (Search service)
  - [ ] Implement JSON logging in TypeScript (Payment, Notifications services)
  - [ ] Add correlation ID header middleware
  - [ ] Add structured context to log entries

- [ ] **Log Aggregation**
  - [ ] Setup Kinesis Firehose to S3
  - [ ] Create S3 bucket for log storage
  - [ ] Configure log partitioning (year/month/day)
  - [ ] Setup CloudWatch Logs Insights queries

### Phase 2: Metrics & Instrumentation (Week 1-2)

- [ ] **Prometheus Integration**
  - [ ] Install Micrometer in Java services
  - [ ] Install prometheus-client in Python
  - [ ] Install prom-client in TypeScript
  - [ ] Expose /metrics endpoints

- [ ] **Custom Metrics**
  - [ ] Implement RED metrics (Request Rate, Errors, Duration)
  - [ ] Implement USE metrics (Utilization, Saturation, Errors)
  - [ ] Add business metrics (orders created, revenue)
  - [ ] Configure metric retention (30 days)

- [ ] **CloudWatch Dashboards**
  - [ ] Create SLI dashboard
  - [ ] Create infrastructure dashboard
  - [ ] Create on-call dashboard

### Phase 3: Distributed Tracing (Week 2)

- [ ] **X-Ray Integration**
  - [ ] Install X-Ray SDK in all services
  - [ ] Configure sampling rules
  - [ ] Add custom subsegments
  - [ ] Enable trace propagation headers

- [ ] **Trace Analysis**
  - [ ] Create trace search queries
  - [ ] Setup service map
  - [ ] Configure trace retention (1 week)

### Phase 4: Alerting (Week 2-3)

- [ ] **SLI-Based Alerts**
  - [ ] Configure availability alerts (per service)
  - [ ] Configure latency alerts (per service)
  - [ ] Configure error rate alerts (per service)
  - [ ] Configure infrastructure alerts (CPU, memory, disk, connections)

- [ ] **Alert Actions**
  - [ ] Integrate with PagerDuty
  - [ ] Configure Slack notifications
  - [ ] Setup email alerts (warnings)
  - [ ] Create runbook links for each alert

- [ ] **On-Call Rotation**
  - [ ] Setup PagerDuty escalation policies
  - [ ] Configure on-call rotation
  - [ ] Create schedule (weekly rotation)

### Phase 5: Runbooks & Playbooks (Week 3)

- [ ] **Incident Response Documentation**
  - [ ] Write runbooks for top 5 alerts
  - [ ] Document investigation queries
  - [ ] Document mitigation steps
  - [ ] Document rollback procedures

- [ ] **Runbook Storage**
  - [ ] Create wiki/confluence pages
  - [ ] Link runbooks from PagerDuty alerts
  - [ ] Review and update quarterly

### Phase 6: Testing & Validation (Week 3-4)

- [ ] **Chaos Engineering Tests**
  - [ ] Test service failure scenarios
  - [ ] Test network partition scenarios
  - [ ] Test database failover scenarios
  - [ ] Verify incident response works end-to-end

- [ ] **Load Testing**
  - [ ] Generate 1000 RPS load
  - [ ] Verify auto-scaling
  - [ ] Verify alerts trigger
  - [ ] Verify dashboards update

- [ ] **Observability Audit**
  - [ ] Verify all logs are structured JSON
  - [ ] Verify correlation IDs propagate
  - [ ] Verify metrics are collected
  - [ ] Verify alerts are configured

---
