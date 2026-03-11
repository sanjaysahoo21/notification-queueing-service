# Notification Queueing Service

A production-grade distributed notification system built with two Spring Boot microservices that communicate asynchronously through RabbitMQ.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [System Architecture](#2-system-architecture)
3. [Tech Stack](#3-tech-stack)
4. [Project Structure](#4-project-structure)
5. [Running the Project](#5-running-the-project)
6. [API Reference](#6-api-reference)
7. [Configuration](#7-configuration)
8. [Testing](#8-testing)

---

## 1) Project Overview

A distributed notification queueing system where an API accepts notification requests and processes them **asynchronously** via a message queue. The API responds instantly with a notification ID — a separate Worker service handles the actual processing in the background.

### How It Works

1. Client sends `POST /api/notifications`
2. API validates input and checks rate limit
3. API saves notification to PostgreSQL with `status = PENDING`
4. API publishes a message to RabbitMQ (only after DB commit)
5. Worker consumes the message, simulates sending, updates `status = SENT`
6. Client polls `GET /api/notifications/{id}` to check the result

### Key Features

- **Asynchronous processing** — API responds in milliseconds, never blocks on delivery
- **Atomic sliding-window rate limiting** — Redis Lua script (ZSET-based, 5 req / 60s per IP)
- **Dead Letter Queue (DLQ)** — failed messages retry 3× with exponential backoff (1s→2s→4s), then route to DLQ
- **Idempotent consumer** — duplicate message delivery is safely skipped
- **Distributed tracing** — `X-Correlation-ID` propagates from API through RabbitMQ into Worker logs
- **JSON structured logging** — all logs are machine-parseable (LogstashEncoder)
- **Full test coverage** — unit tests (Mockito) + integration tests (Testcontainers)
- **Environment-based config** — no hardcoded credentials anywhere

---

## 2) System Architecture

```
Client
  │
  ▼
POST /api/notifications
  │
  ├─► Validate request
  ├─► Rate limit check (Redis)
  ├─► Save to PostgreSQL  ──────────► status = PENDING
  └─► Publish to RabbitMQ (afterCommit)
                 │
                 ▼
          notification.queue
                 │
        ┌── retry on fail (3×) ──┐
        │                        ▼
        │              notification.dlq (DLQ)
        ▼
      Worker consumes
        │
        ├─► Idempotency check
        ├─► Simulate sending (2s)
        └─► Update PostgreSQL ──► status = SENT

Client polls:
GET /api/notifications/{id}  ──►  { "status": "SENT" }
```

### RabbitMQ Topology

```
notification.exchange (DirectExchange)
        │  routing-key: notification.created
        ▼
notification.queue  ──[3 retries exhausted]──►  notification.dlx  ──►  notification.dlq
```

### Services & Ports

| Service | Port | Role |
|---|---|---|
| API | `8080` | REST endpoints, rate limiting, RabbitMQ producer |
| Worker | — | RabbitMQ consumer, DB updater |
| PostgreSQL | `5433` (host) | Notification storage |
| RabbitMQ | `5672` / `15672` | Message broker / Management UI |
| Redis | `6379` | Sliding-window rate limiter state |

---

## 3) Tech Stack

| Category | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Database | PostgreSQL 15 + Spring Data JPA (Hibernate) |
| Messaging | RabbitMQ 3 + Spring AMQP |
| Rate Limiting | Redis 7 + Lua scripting |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Logging | Logback + LogstashEncoder (JSON) |
| Containerization | Docker + Docker Compose |
| Build | Maven |
| Testing | JUnit 5 + Mockito + Testcontainers |

---

## 4) Project Structure

```
notification-queueing-service/
├── docker-compose.yml
├── .env.example
│
├── api/                              # REST API service (port 8080)
│   └── src/main/java/
│       ├── config/
│       │   ├── CorrelationIdFilter.java   # Assigns X-Correlation-ID per request (OncePerRequestFilter)
│       │   ├── RabbitMQConfig.java        # Exchange, queue, DLX, DLQ bean declarations
│       │   └── WebConfig.java            # Registers rate limit MVC interceptor
│       ├── controller/
│       │   ├── NotificationController.java  # POST /api/notifications, GET /api/notifications/{id}
│       │   └── HealthController.java        # GET /health (active check: DB + Redis + RabbitMQ)
│       ├── service/
│       │   ├── NotificationService.java     # Business logic; publishes message afterCommit
│       │   └── NotificationPublisher.java   # Wraps RabbitTemplate.convertAndSend()
│       ├── ratelimiter/
│       │   ├── RatelimiterService.java      # Atomic Lua sliding-window rate limiter
│       │   └── RateLimitInterceptor.java    # Spring MVC interceptor (checks per IP)
│       ├── exception/
│       │   ├── GlobalExceptionHandler.java           # Maps exceptions → HTTP status codes
│       │   ├── NotificationNotFoundException.java    # → 404 Not Found
│       │   └── RateLimitExceededException.java       # → 429 Too Many Requests
│       └── dto/
│           ├── NotificationRequest.java           # Input DTO (Bean Validation annotations)
│           ├── NotificationMessage.java           # RabbitMQ payload (includes correlationId)
│           └── NotificationStatusResponse.java    # GET /api/notifications/{id} response
│
└── worker/                           # Background consumer service
    └── src/main/java/
        ├── consumer/
        │   └── NotificationConsumer.java   # @RabbitListener, retry, idempotency, MDC tracing
        └── config/
            ├── RabbitMQConfig.java         # Mirrors API queue/DLX/DLQ declarations
            └── RabbitListenerConfig.java   # Retry interceptor: 3×, exponential backoff, DLQ recoverer
```

---

## 5) Running the Project

### Prerequisites

- Docker and Docker Compose installed

### Start All Services

```bash
docker compose up --build
```

Docker will build both services and start all 5 containers automatically.

### Stop

```bash
docker compose down

# Stop and delete all data (fresh start)
docker compose down -v
```

### Verify All Containers Are Running

```bash
docker compose ps
# All 5 containers (api, worker, postgres, redis, rabbitmq) should show "Up"
```

### Access Points

| URL | Description |
|---|---|
| `http://localhost:8080/swagger-ui/index.html` | Interactive API documentation |
| `http://localhost:8080/health` | Health check endpoint |
| `http://localhost:15672` | RabbitMQ Management UI (`guest` / `guest`) |

---

## 6) API Reference

### POST /api/notifications

Submit a notification request.

**Request Body:**
```json
{
  "recipient": "user@example.com",
  "subject": "Optional subject line",
  "body": "Notification body text",
  "type": "EMAIL"
}
```

**Success Response — `202 Accepted`:**
```json
{ "notificationId": "bc973b86-b7f3-4f1e-9c6c-0c65392fae78" }
```

**Response Headers:**
```
X-Correlation-ID: 789aeba4-22f6-4fdb-99b2-f42b6d716ccc
```

**Error Responses:**

| Status | Cause |
|---|---|
| `400 Bad Request` | Missing required field or invalid email format |
| `429 Too Many Requests` | Rate limit exceeded (5 requests / 60 seconds per IP) |

---

### GET /api/notifications/{id}

Poll the status of a submitted notification.

**Success Response — `200 OK`:**
```json
{
  "id": "bc973b86-b7f3-4f1e-9c6c-0c65392fae78",
  "recipient": "user@example.com",
  "subject": "Optional subject line",
  "body": "Notification body text",
  "type": "EMAIL",
  "status": "SENT",
  "createdAt": "2026-03-11T14:48:07.457322Z",
  "updatedAt": "2026-03-11T14:48:09.877407Z"
}
```

**Status values:** `PENDING` → `SENT`

| Status | Cause |
|---|---|
| `404 Not Found` | Notification ID does not exist |

---

### GET /health

Active health check that probes PostgreSQL, Redis, and RabbitMQ connectivity.

**Response — `200 OK`:**
```json
{ "status": "UP" }
```

---

## 7) Configuration

All connection details use `${ENV_VAR:default}` syntax — safe defaults for local dev, fully overridable via environment in Docker.

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/notifications` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | DB password |
| `SPRING_RABBITMQ_HOST` | `localhost` | RabbitMQ hostname |
| `SPRING_RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port |
| `SPRING_RABBITMQ_USERNAME` | `guest` | RabbitMQ username |
| `SPRING_RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis hostname |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `RATE_LIMIT_MAX_REQUESTS` | `5` | Max requests per window |
| `RATE_LIMIT_WINDOW_SECONDS` | `60` | Rate limit window (seconds) |

See [`.env.example`](.env.example) for a complete template.

---

## 8) Testing

### Unit Tests

```bash
# API — 3 tests
cd api && ./mvnw test -Dtest="NotificationServiceTest"

# Worker — 1 test
cd worker && ./mvnw test -Dtest="NotificationConsumerTest"
```

### Integration Tests (requires Docker daemon)

```bash
# API — 7 tests (Testcontainers: PostgreSQL + Redis + RabbitMQ)
cd api && ./mvnw test -Dtest="NotificationApiIntegrationTest"

# Worker — 2 tests (Testcontainers: PostgreSQL + RabbitMQ)
cd worker && ./mvnw test -Dtest="NotificationConsumerIntegrationTest"
```

**Integration test coverage:**

| Test | What it verifies |
|---|---|
| `healthCheck_shouldReturnUp` | Health endpoint returns UP with all infra running |
| `submitNotification_validRequest_shouldReturn202` | 202 response + notificationId + X-Correlation-ID header |
| `submitNotification_missingRecipient_shouldReturn400` | Validation: required field |
| `submitNotification_invalidEmail_shouldReturn400` | Validation: email format |
| `getNotification_existingId_shouldReturn200` | GET returns correct notification data |
| `getNotification_unknownId_shouldReturn404` | 404 for non-existent ID |
| `rateLimiting_shouldReturn429AfterExceedingLimit` | Sliding window blocks excess requests |
| `consume_pendingNotification_shouldUpdateStatusToSent` | Full flow: publish → consume → SENT in DB |
| `consume_alreadyProcessed_shouldBeSkippedIdempotently` | Duplicate delivery is a safe no-op |
