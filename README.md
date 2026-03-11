# Notification Queueing Service

> **Presentation Guide** — A step-by-step walkthrough for the screen recording demo.
> Follow the sections in order. Each section includes what to show, what to say, and key code to highlight.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [System Architecture](#2-system-architecture)
3. [Project Structure Walkthrough](#3-project-structure-walkthrough)
4. [Key Implementation Highlights](#4-key-implementation-highlights)
5. [Running the Project](#5-running-the-project)
6. [Live Demo — API Endpoints](#6-live-demo--api-endpoints)
7. [Improvements Made (Mentor Report)](#7-improvements-made-mentor-report)
8. [Testing Strategy](#8-testing-strategy)
9. [Git History](#9-git-history)

---

## 1) Project Overview

**What is this?**
A production-grade distributed notification system built with two independent Spring Boot microservices that communicate asynchronously through RabbitMQ.

**The core idea:** The API never blocks waiting for a notification to be sent. It immediately stores the request and hands it off to a message queue. A separate Worker picks it up and processes it in the background. The client can poll for the status at any time.

**Why this architecture?**
- **Fast API responses** — the POST endpoint returns in milliseconds, not waiting for actual sending
- **Resilient** — if the Worker crashes, messages stay in RabbitMQ and are redelivered on restart
- **Scalable** — multiple Worker instances can consume from the same queue in parallel
- **Observable** — every request carries a Correlation ID that traces through both services

### Request Flow

```
Client
  │
  ▼
POST /api/notifications
  │
  ├─► Validate request (email format, required fields)
  ├─► Check rate limit (Redis sliding window)
  ├─► Save to PostgreSQL  ──► status = PENDING
  └─► Publish to RabbitMQ (after DB commit)
                               │
                               ▼
                         Worker consumes
                               │
                         Idempotency check
                               │
                         Simulate sending (2s)
                               │
                         Update PostgreSQL ──► status = SENT
                               │
                         (on failure → retry 3x → DLQ)

Client polls:
GET /api/notifications/{id}  ──►  { status: "SENT" }
```

---

## 2) System Architecture

### Services & Ports

| Service | Port | Purpose |
|---|---|---|
| **API** | `8080` | REST endpoints, rate limiting, RabbitMQ producer |
| **Worker** | `8081` | RabbitMQ consumer, DB updater |
| **PostgreSQL** | `5433` (host) / `5432` (container) | Persistent notification storage |
| **RabbitMQ** | `5672` (AMQP) / `15672` (Management UI) | Message broker |
| **Redis** | `6379` | Sliding window rate limiter state |

### Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Database | PostgreSQL 15 + Spring Data JPA (Hibernate) |
| Messaging | RabbitMQ 3 + Spring AMQP |
| Rate Limiting | Redis 7 + Lua script (atomic sliding window) |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Logging | Logback + LogstashEncoder (JSON structured logs) |
| Containerization | Docker + Docker Compose |
| Build | Maven |
| Testing | JUnit 5 + Mockito + Testcontainers |

### RabbitMQ Topology

```
notification.exchange (DirectExchange)
        │  routing-key: notification.created
        ▼
notification.queue  ──[on failure]──►  notification.dlx  ──►  notification.dlq
```

- Messages failing 3 retry attempts are routed to the **Dead Letter Queue** for inspection
- Retry backoff: 1s → 2s → 4s (exponential)

---

## 3) Project Structure Walkthrough

```
notification-queueing-service/
├── docker-compose.yml          ← spins up all 5 services
├── .env.example                ← documents all env variables
│
├── api/                        ← Spring Boot REST API (port 8080)
│   ├── src/main/java/
│   │   └── config/
│   │       ├── CorrelationIdFilter.java   ← assigns X-Correlation-ID per request
│   │       ├── RabbitMQConfig.java        ← declares exchange, queue, DLX, DLQ
│   │       └── WebConfig.java             ← registers rate limit interceptor
│   │   └── controller/
│   │       ├── NotificationController.java ← POST + GET endpoints
│   │       └── HealthController.java       ← active health check (DB+Redis+RabbitMQ)
│   │   └── service/
│   │       ├── NotificationService.java    ← creates notification, publishes after commit
│   │       └── NotificationPublisher.java  ← wraps RabbitTemplate.convertAndSend()
│   │   └── ratelimiter/
│   │       ├── RatelimiterService.java     ← atomic Lua sliding window
│   │       └── RateLimitInterceptor.java   ← Spring MVC interceptor
│   │   └── exception/
│   │       ├── GlobalExceptionHandler.java        ← maps exceptions to HTTP status codes
│   │       ├── NotificationNotFoundException.java ← throws 404
│   │       └── RateLimitExceededException.java    ← throws 429
│   │   └── dto/
│   │       ├── NotificationRequest.java       ← input (validated)
│   │       ├── NotificationMessage.java       ← queue payload (includes correlationId)
│   │       └── NotificationStatusResponse.java ← GET response
│   └── src/test/
│       ├── service/NotificationServiceTest.java         ← unit tests (3 tests)
│       └── NotificationApiIntegrationTest.java          ← Testcontainers (7 tests)
│
└── worker/                     ← Spring Boot consumer (no exposed port)
    ├── src/main/java/
    │   └── consumer/
    │       └── NotificationConsumer.java  ← @RabbitListener, retry, MDC, idempotency
    │   └── config/
    │       ├── RabbitMQConfig.java        ← mirrors API queue/DLX/DLQ declarations
    │       └── RabbitListenerConfig.java  ← retry interceptor (3x, exponential backoff)
    └── src/test/
        └── consumer/NotificationConsumerIntegrationTest.java ← Testcontainers (2 tests)
```

---

## 4) Key Implementation Highlights

> **Show these files during the recording — these are the most important improvements.**

### 4.1 Atomic Sliding Window Rate Limiter

**File:** `api/src/main/java/.../ratelimiter/RatelimiterService.java`

**The problem with the naive approach:**
```java
// BAD — two separate Redis commands, not atomic
redisTemplate.opsForValue().increment(key);   // step 1
redisTemplate.expire(key, 60, SECONDS);        // step 2 — if crash here, key lives forever!
// Also: fixed window means up to 2x requests at window boundary
```

**The solution — single atomic Lua script + sliding window:**
```lua
redis.call('ZREMRANGEBYSCORE', key, '-inf', clearBefore)  -- remove expired entries
local count = redis.call('ZCARD', key)                     -- count active requests
if count < limit then
  redis.call('ZADD', key, now, requestId)                  -- record this request
  redis.call('EXPIRE', key, ttl)
  return 1   -- allowed
else
  return 0   -- denied
end
```
- **Atomic** — all 4 Redis operations in one script execution, no race condition
- **Sliding window** — uses a Sorted Set (ZSET) with millisecond timestamps as scores
- **Per client IP** — key is `rate_limit:{clientIP}`

---

### 4.2 Correlation ID — End-to-End Tracing

**The flow:**

1. **`CorrelationIdFilter`** (extends `OncePerRequestFilter`) — assigns a UUID to every incoming request and puts it in MDC + response header
2. **`NotificationService`** — reads `MDC.get("correlationId")` and embeds it in the `NotificationMessage` DTO sent to RabbitMQ
3. **`NotificationConsumer`** — reads `message.getCorrelationId()` and puts it back in MDC

**Result:** Every log line in both services shares the same `correlationId` for a single user request — making distributed debugging trivial.

```json
// API log
{"level":"INFO","message":"Notification created","correlationId":"abc-123","service":"api"}

// Worker log (same correlationId)
{"level":"INFO","message":"Processing notification","correlationId":"abc-123","service":"worker"}
```

---

### 4.3 Transactional Publish (afterCommit)

**File:** `api/src/main/java/.../service/NotificationService.java`

**The problem:** If you publish to RabbitMQ inside a `@Transactional` method and then the DB commit fails, you've sent a message for a notification that doesn't exist in the database.

**The fix:**
```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        publisher.publish(message);  // only runs AFTER successful DB commit
    }
});
```

---

### 4.4 Worker Retry + Dead Letter Queue

**File:** `worker/src/main/java/.../config/RabbitListenerConfig.java`

```java
factory.setAdviceChain(
    RetryInterceptorBuilder.stateless()
        .maxAttempts(3)                          // 3 total attempts
        .backOffOptions(1000L, 2.0, 4000L)       // 1s → 2s → 4s exponential backoff
        .recoverer(new RejectAndDontRequeueRecoverer())  // after 3 failures → DLQ
        .build()
);
```

**Flow on failure:**
1. Consumer throws exception
2. Spring AMQP retries up to 3 times with exponential backoff
3. After 3 failures: `nack` with `requeue=false`
4. RabbitMQ routes message to `notification.dlq` via `x-dead-letter-exchange`
5. Dead messages can be inspected in RabbitMQ Management UI at `http://localhost:15672`

---

### 4.5 Idempotency in the Worker

**File:** `worker/src/main/java/.../consumer/NotificationConsumer.java`

```java
if (!"PENDING".equals(notification.getStatus())) {
    log.warn("Notification already processed: {}", notificationId);
    return;  // silently skip — safe to call multiple times
}
```

If the same message is delivered twice (RabbitMQ at-least-once delivery guarantee), the second processing is a no-op.

---

### 4.6 JSON Structured Logging

**Files:** `api/src/main/resources/logback-spring.xml` and `worker/src/main/resources/logback-spring.xml`

Every log line is valid JSON — ready for ingestion by ELK stack, Grafana Loki, or Datadog:

```json
{
  "timestamp": "2026-03-11T14:48:07.457Z",
  "@version": "1",
  "message": "Notification created with id: bc973b86-...",
  "logger": "com.example.notification.api.service.NotificationService",
  "thread": "http-nio-8080-exec-3",
  "level": "INFO",
  "correlationId": "789aeba4-22f6-4fdb-99b2-f42b6d716ccc",
  "service": "api"
}
```

---

## 5) Running the Project

### Prerequisites

- Docker + Docker Compose installed

### Start Everything

```bash
# Clone and enter the project
cd notification-queueing-service

# Start all 5 services (builds JARs inside Docker)
docker compose up --build
```

Docker Compose starts in this order:
1. `postgres` — database
2. `redis` — rate limiter state
3. `rabbitmq` — message broker
4. `api` — REST service (waits for postgres, redis, rabbitmq)
5. `worker` — consumer (waits for postgres, rabbitmq)

### Verify All Services Are Running

```bash
docker compose ps
```

Expected output — all containers showing `Up`:
```
NAME       STATUS
api        Up
postgres   Up
rabbitmq   Up
redis      Up
worker     Up
```

### Stop Everything

```bash
docker compose down

# Stop AND delete all data (fresh start)
docker compose down -v
```

---

## 6) Live Demo — API Endpoints

> **Show these curl commands live during the recording.**

### Health Check

```bash
curl http://localhost:8080/health
```
```json
{ "status": "UP" }
```

---

### Submit a Notification

```bash
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "recipient": "user@example.com",
    "subject": "Welcome!",
    "body": "Hello from the notification service",
    "type": "EMAIL"
  }'
```
```json
{ "notificationId": "bc973b86-b7f3-4f1e-9c6c-0c65392fae78" }
```
- Returns `202 Accepted` immediately
- Response header: `X-Correlation-ID: <uuid>`

---

### Get Status — Immediately After Submit (PENDING)

```bash
curl http://localhost:8080/api/notifications/bc973b86-b7f3-4f1e-9c6c-0c65392fae78
```
```json
{
  "id": "bc973b86-b7f3-4f1e-9c6c-0c65392fae78",
  "recipient": "user@example.com",
  "status": "PENDING",
  "createdAt": "2026-03-11T14:48:07.457322Z",
  "updatedAt": "2026-03-11T14:48:07.457322Z"
}
```

---

### Get Status — ~3 Seconds Later (SENT)

```bash
# Run the same GET after ~3 seconds
curl http://localhost:8080/api/notifications/bc973b86-b7f3-4f1e-9c6c-0c65392fae78
```
```json
{
  "id": "bc973b86-b7f3-4f1e-9c6c-0c65392fae78",
  "recipient": "user@example.com",
  "status": "SENT",
  "createdAt": "2026-03-11T14:48:07.457322Z",
  "updatedAt": "2026-03-11T14:48:09.877407Z"
}
```

---

### Validation Error — Missing Required Field (400)

```bash
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"type":"EMAIL","body":"Hello"}'
# → 400 Bad Request: "Recipient is required"
```

### Validation Error — Invalid Email (400)

```bash
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"recipient":"not-an-email","type":"EMAIL","body":"Hello"}'
# → 400 Bad Request: "Recipient must be a valid email address"
```

### Rate Limit Hit (429)

```bash
# Send 6 requests rapidly (limit is 5 per 60 seconds)
for i in {1..6}; do
  curl -s -o /dev/null -w "Request $i: %{http_code}\n" \
    -X POST http://localhost:8080/api/notifications \
    -H "Content-Type: application/json" \
    -d '{"recipient":"test@example.com","type":"EMAIL","body":"spam"}'
done
# Request 1: 202
# Request 2: 202
# ...
# Request 6: 429
```

### Not Found (404)

```bash
curl http://localhost:8080/api/notifications/00000000-0000-0000-0000-000000000000
# → 404 Not Found
```

---

### Swagger UI

Open in browser: **http://localhost:8080/swagger-ui/index.html**

All endpoints are documented with request/response schemas and can be tested interactively.

---

### RabbitMQ Management UI

Open in browser: **http://localhost:15672**
- Username: `guest` / Password: `guest`
- Shows: `notification.queue`, `notification.dlq` queues, message rates, connections

---

### Check PostgreSQL Directly

```bash
# Connect to the running PostgreSQL container
docker exec -it postgres psql -U postgres -d notifications

# View all notifications
SELECT id, status, created_at, updated_at FROM notification ORDER BY created_at DESC;

# Count by status
SELECT status, COUNT(*) FROM notification GROUP BY status;

# Exit
\q
```

---

## 7) Improvements Made (Mentor Report)

After receiving the initial score, the following improvements were implemented to address all mentor feedback:

| # | Issue | Fix |
|---|---|---|
| 1 | **Non-atomic rate limiter** — INCR + EXPIRE could crash between commands leaving immortal keys; fixed-window allowed 2x burst at boundary | Replaced with atomic Lua script + Redis ZSET sliding window |
| 2 | **Hardcoded credentials** — DB passwords, RabbitMQ credentials hardcoded in `application.properties` | All values use `${ENV_VAR:default}` — configurable via environment, safe defaults for local dev |
| 3 | **No worker error handling** — uncaught exceptions killed the consumer silently | Added try-catch-finally; exceptions rethrown for Spring AMQP retry; 3 attempts with exponential backoff; exhausted → DLQ |
| 4 | **No Dead Letter Queue** — failed messages were lost | Declared `notification.dlx` + `notification.dlq`; queue configured with `x-dead-letter-exchange` |
| 5 | **Correlation ID not propagated** — couldn't trace a request across API + Worker logs | `correlationId` added to `NotificationMessage` DTO; API reads from MDC, Worker writes back to MDC |
| 6 | **Non-JSON logging** — plain text logs not parseable by log aggregators | Both services use LogstashEncoder; all output is structured JSON |
| 7 | **No integration tests** — only unit tests with mocks | Added Testcontainers integration tests: 7 for API, 2 for Worker |
| 8 | **HealthController missing Swagger docs** | Added `@Tag`, `@Operation`, `@ApiResponses` annotations |
| 9 | **Publisher inside @Transactional** — message published before DB commit | Wrapped publish in `TransactionSynchronization.afterCommit()` |
| 10 | **Hardcoded queue name in @RabbitListener** | Changed to `RabbitMQConfig.QUEUE_NAME` constant |

---

## 8) Testing Strategy

### Unit Tests

```bash
# Run API unit tests
cd api && ./mvnw test -Dtest="NotificationServiceTest"
# Tests run: 3, Failures: 0, Errors: 0 ✅

# Run Worker unit tests
cd worker && ./mvnw test -Dtest="NotificationConsumerTest"
# Tests run: 1, Failures: 0, Errors: 0 ✅
```

**What's tested:**
- `testCreateNotification_Success` — happy path, notification saved and message published
- `testGetNotification_NotFound_ThrowsException` — verifies `NotificationNotFoundException` for unknown ID
- `testGetNotification_Found_ReturnsResponse` — verifies correct response mapping

### Integration Tests (Testcontainers)

```bash
# Run API integration tests (requires Docker daemon)
cd api && ./mvnw test -Dtest="NotificationApiIntegrationTest"

# Run Worker integration tests (requires Docker daemon)
cd worker && ./mvnw test -Dtest="NotificationConsumerIntegrationTest"
```

**Testcontainers spins up real Docker containers for each test run:**
- `PostgreSQLContainer("postgres:15")`
- `GenericContainer("redis:7")`
- `RabbitMQContainer("rabbitmq:3-management")`

**API Integration Tests (7 tests):**
| Test | Verifies |
|---|---|
| `healthCheck_shouldReturnUp` | Health endpoint returns UP |
| `submitNotification_validRequest_shouldReturn202WithId` | 202 + notificationId + `X-Correlation-ID` header |
| `submitNotification_missingRecipient_shouldReturn400` | Input validation |
| `submitNotification_invalidEmail_shouldReturn400` | Email format validation |
| `getNotification_existingId_shouldReturn200WithPendingStatus` | GET returns correct notification |
| `getNotification_unknownId_shouldReturn404` | 404 for missing ID |
| `rateLimiting_shouldReturn429AfterExceedingLimit` | Sliding window rate limiter |

**Worker Integration Tests (2 tests):**
| Test | Verifies |
|---|---|
| `consume_pendingNotification_shouldUpdateStatusToSent` | Full flow: publish → consume → SENT |
| `consume_alreadyProcessedNotification_shouldBeSkippedIdempotently` | Idempotency: second delivery is no-op |

---

## 9) Git History

All changes were committed atomically — one commit per concern:

```bash
git log --oneline
```

```
383d17f test: extend NotificationServiceTest with getNotification test cases
8da0779 test: add Worker consumer integration tests using Testcontainers
603315d test: add API integration tests using Testcontainers
2d338c8 chore: add logstash-logback-encoder and Testcontainers to Worker pom.xml
e0a59d0 chore: add logstash-logback-encoder and Testcontainers to API pom.xml
95c41cd feat: switch to JSON structured logging via LogstashEncoder in both services
903a9f9 feat: configure Spring AMQP retry with exponential backoff and DLQ in Worker
2edd880 feat: rewrite Worker consumer with error handling and correlationId MDC
af1e6a6 feat: declare dead-letter exchange and DLQ in API RabbitMQConfig
e9f16f2 feat: add Swagger/OpenAPI annotations to HealthController
15ac1f2 feat: propagate correlationId and use afterCommit transactional publish
3107930 feat: add correlationId field to NotificationMessage DTOs in API and Worker
3142753 feat: implement atomic sliding-window rate limiter using Redis Lua script
23f824c fix: correct postgres port mapping and inject all env vars in docker-compose
8e4432a feat: externalize all credentials to environment variables
2a0d799 fix: simplify getNotificationStatus and add @Repository annotation
b3a46ea feat: add NotificationNotFoundException for proper 404 handling
d5bcd4d fix: return correct HTTP 4xx status codes in GlobalExceptionHandler
5626081 fix: correct CorrelationIdFilter to extend OncePerRequestFilter
```

- Separation of API and Worker services
- Docker-based setup for easy execution

The system is production-ready and follows modern backend design practices.

---

## 9) System Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           CLIENT APPLICATION                              │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │
                                 │ HTTP Request
                                 │ POST /api/notifications
                                 ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                          API SERVICE (Port 8080)                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  REST Controller                                                    │  │
│  │  - Input Validation                                                 │  │
│  │  - Rate Limiting Check (Redis)                                      │  │
│  └─────────────────────────────┬──────────────────────────────────────┘  │
│                                 │                                         │
│  ┌─────────────────────────────▼──────────────────────────────────────┐  │
│  │  Service Layer                                                      │  │
│  │  - Business Logic                                                   │  │
│  │  - Save to Database (status: PENDING)                               │  │
│  │  - Publish to Queue                                                 │  │
│  └─────────────────────────────┬──────────────────────────────────────┘  │
└────────────────────────────────┼─────────────────────────────────────────┘
                                 │
                 ┌───────────────┼───────────────┐
                 │               │               │
                 ▼               ▼               ▼
        ┌────────────┐  ┌────────────┐  ┌────────────┐
        │   Redis    │  │ PostgreSQL │  │ RabbitMQ   │
        │            │  │            │  │            │
        │ Rate       │  │ Notification│ │  Queue     │
        │ Limiting   │  │   Table    │  │ (AMQP)     │
        └────────────┘  └────────────┘  └──────┬─────┘
                                               │
                                               │ Message
                                               │ Consumed
                                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                        WORKER SERVICE (Background)                        │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  Message Listener                                                   │  │
│  │  - Consume from RabbitMQ                                            │  │
│  │  - Deserialize Message                                              │  │
│  └─────────────────────────────┬──────────────────────────────────────┘  │
│                                 │                                         │
│  ┌─────────────────────────────▼──────────────────────────────────────┐  │
│  │  Notification Processor                                             │  │
│  │  - Simulate Sending (Email/SMS/Push)                                │  │
│  │  - Update Database (status: SENT)                                   │  │
│  └─────────────────────────────┬──────────────────────────────────────┘  │
└────────────────────────────────┼─────────────────────────────────────────┘
                                 │
                                 ▼
                        ┌────────────┐
                        │ PostgreSQL │
                        │            │
                        │  Update    │
                        │  Status    │
                        └────────────┘
```

### Architecture Components

#### 1. API Service

- **Technology**: Spring Boot REST API
- **Port**: 8080
- **Responsibilities**:
  - Accept HTTP requests
  - Validate input data
  - Apply rate limiting (Redis)
  - Save notification to database
  - Publish message to RabbitMQ queue
  - Return notification ID to client

#### 2. Worker Service

- **Technology**: Spring Boot Background Service
- **Responsibilities**:
  - Listen to RabbitMQ queue
  - Process notifications asynchronously
  - Simulate sending notifications
  - Update notification status in database

#### 3. PostgreSQL

- **Purpose**: Persistent data storage
- **Stores**:
  - Notification details (id, recipient, subject, body, type)
  - Status (PENDING, SENT, FAILED)
  - Timestamps (createdAt, updatedAt)

#### 4. RabbitMQ

- **Purpose**: Message queue for async processing
- **Benefits**:
  - Decouples API from worker
  - Ensures reliable message delivery
  - Enables horizontal scaling of workers

#### 5. Redis

- **Purpose**: Distributed rate limiting
- **Function**:
  - Tracks API request count per user/IP
  - Prevents API abuse
  - Fast in-memory operations

### Data Flow Summary

1. **Request Phase**: Client → API → Validation → Rate Limit Check → Save to DB (PENDING) → Publish to Queue → Return ID
2. **Processing Phase**: Worker → Consume Message → Process Notification → Update DB (SENT)
3. **Status Check**: Client → API → Query DB → Return Status

---

## 10) How to Run Tests

The project includes unit tests for both the API and Worker services.

### Running API Tests

```bash
cd api
mvn test
```

### Running Worker Tests

```bash
cd worker
mvn test
```

These tests verify:

- **API**: NotificationService creates records correctly and publishes to RabbitMQ.
- **Worker**: NotificationConsumer receives messages and updates the database status.

---

## 11) Key Design Decisions

### 1. Rate Limiting Algorithm

We implemented a **Fixed Window Counter** algorithm using Redis.

- **Why?**: It is simple, atomic (using Redis `INCR`), and highly effective for preventing abuse in distributed systems.
- **Implementation**: Each client key (e.g., recipient email) has a counter in Redis that expires every 60 seconds.

### 2. Message Queue (RabbitMQ)

We used RabbitMQ to decouple the API from the Worker.

- **Why?**:
  - **Reliability**: Ensures messages are not lost if the worker is down.
  - **Scalability**: Allows multiple workers to process notifications in parallel.
  - **Responsiveness**: The API responds immediately (202/200 OK) without waiting for the slow email sending process.

### 3. Error Handling

- **Global Exception Handling**: A `@RestControllerAdvice` manages errors centrally.
  - Returns `400 Bad Request` for validation errors.
  - Returns `429 Too Many Requests` when the rate limit is exceeded.
- **Graceful Failures**: The worker logs errors if a message cannot be processed, preventing the entire consumer from crashing.
