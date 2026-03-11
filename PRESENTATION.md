# Presentation Script — Screen Recording Guide

> This file is YOUR personal script. Read this while recording.
> Follow the sections in order. Total target: 15–20 minutes.

---

## BEFORE YOU START RECORDING

Run this to make sure everything is up:

```bash
docker compose ps
```

All 5 containers should show `Up`. If not:

```bash
docker compose down -v && docker compose up --build
# Wait ~60 seconds for everything to start
```

Open these tabs in your browser before recording:
- `http://localhost:8080/swagger-ui/index.html`
- `http://localhost:15672` (login: `guest` / `guest`)
- VS Code with the project open

---

## SEGMENT 1 — Introduction (2 min)

**Say:**
> "Hi, I'm going to walk you through my Notification Queueing Service project.
> This is a distributed backend system built with two Spring Boot microservices —
> an API and a Worker — that communicate asynchronously through RabbitMQ."

**Show:** The `README.md` file open in VS Code or browser.

**Say:**
> "The core idea is simple: when a client submits a notification, the API doesn't
> block waiting for it to be sent. It immediately saves it to PostgreSQL and
> drops a message in RabbitMQ. A completely separate Worker service picks it up
> and processes it in the background. The client can poll for the status anytime."

**Point to the flow diagram in README Section 1.**

---

## SEGMENT 2 — Architecture (2 min)

**Show:** README Section 2 (System Architecture).

**Say:**
> "We have 5 Docker containers:
> - The API on port 8080 — handles REST, rate limiting, publishes to RabbitMQ
> - The Worker — listens to RabbitMQ, updates the DB
> - PostgreSQL for persistence
> - Redis for rate limiting state
> - RabbitMQ as the message broker"

**Point to the RabbitMQ topology diagram:**
> "For reliability, we also have a Dead Letter Queue setup. If the Worker fails
> to process a message 3 times, it gets routed to a DLQ — nothing is silently lost."

---

## SEGMENT 3 — Key Code: Rate Limiter (3 min)

**Show:** `api/src/main/java/.../ratelimiter/RatelimiterService.java`

**Say:**
> "Let me show you one of the most important improvements in this project —
> the rate limiter."

**Scroll to the Lua script and say:**
> "The naive approach is to call Redis INCR and then EXPIRE separately.
> The problem? If the app crashes between those two commands, you get a key
> in Redis that never expires — and the user is permanently rate-limited."

> "My solution is an atomic Lua script. Redis executes the entire script
> as a single operation — no race condition is possible."

**Point to the ZSET operations:**
> "I also upgraded from a fixed-window to a sliding-window algorithm.
> Instead of a simple counter, I use a sorted set where each member is a
> unique request ID and the score is the timestamp in milliseconds.
> On every request, I remove all entries older than the window, count what's
> left, and decide allow or deny — all in one atomic script execution."

---

## SEGMENT 4 — Key Code: Correlation ID Tracing (2 min)

**Show:** `api/src/main/java/.../config/CorrelationIdFilter.java`

**Say:**
> "For distributed tracing, every incoming request gets a Correlation ID
> assigned here. It goes into the MDC — that's Mapped Diagnostic Context —
> which means it automatically appears in every log line for this request."

**Show:** `api/src/main/java/.../service/NotificationService.java` (the NotificationMessage constructor line)

**Say:**
> "When we build the RabbitMQ message, we grab the correlation ID from MDC
> and embed it inside the message payload."

**Show:** `worker/src/main/java/.../consumer/NotificationConsumer.java` (the MDC.put line)

**Say:**
> "On the Worker side, we read it back from the message and put it into the
> Worker's MDC. So if I look at logs from both services, every log line for
> the same user request shares the exact same correlation ID — making debugging
> trivial in a distributed system."

---

## SEGMENT 5 — Key Code: Worker Reliability (2 min)

**Show:** `worker/src/main/java/.../config/RabbitListenerConfig.java`

**Say:**
> "The original worker had no error handling — if anything went wrong, the
> exception was swallowed and the message was just lost."

> "Now I've configured Spring AMQP's retry interceptor with 3 max attempts
> and exponential backoff — 1 second, then 2 seconds, then 4 seconds.
> After all 3 attempts fail, the RejectAndDontRequeueRecoverer sends a
> negative acknowledgement to RabbitMQ, which routes the message to the
> Dead Letter Queue."

**Show:** `worker/src/main/java/.../consumer/NotificationConsumer.java` (the try-catch block)

**Say:**
> "And the consumer always rethrows exceptions so the retry interceptor
> actually triggers. The finally block clears the MDC to prevent context
> leakage between messages."

> "There's also an idempotency check here — if the notification is already
> SENT, we skip it silently. RabbitMQ guarantees at-least-once delivery,
> so we need to handle duplicates."

---

## SEGMENT 6 — Live Demo (4 min)

Open a terminal. Run these commands one by one:

### Health Check
```bash
curl http://localhost:8080/health
```
**Say:** "Health check shows UP — all dependencies are reachable."

---

### Submit a Notification
```bash
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"recipient":"demo@example.com","subject":"Hello","body":"This is a demo notification","type":"EMAIL"}'
```
**Say:**
> "Returns 202 Accepted immediately with a notification ID.
> Notice the X-Correlation-ID header in the response — that's our tracing ID."

Copy the `notificationId` from the response.

---

### Check Status — Immediately
```bash
curl http://localhost:8080/api/notifications/<PASTE_ID_HERE>
```
**Say:** "Status is PENDING — the Worker hasn't processed it yet."

---

### Check Status — After 3 Seconds
```bash
# Wait 3 seconds, then run again
curl http://localhost:8080/api/notifications/<PASTE_ID_HERE>
```
**Say:** "Now it's SENT. The Worker consumed the message from RabbitMQ,
waited 2 seconds to simulate sending, and updated the database."

---

### Validation Error
```bash
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"type":"EMAIL","body":"missing recipient"}'
```
**Say:** "400 Bad Request — recipient is required."

---

### Rate Limiting
```bash
for i in {1..6}; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/notifications \
    -H "Content-Type: application/json" \
    -d '{"recipient":"rate@example.com","type":"EMAIL","body":"test"}')
  echo "Request $i: $STATUS"
done
```
**Say:** "The 6th request gets 429 — rate limit exceeded.
The sliding window allows 5 requests per 60 seconds per IP."

---

### Not Found
```bash
curl http://localhost:8080/api/notifications/00000000-0000-0000-0000-000000000000
```
**Say:** "404 for a non-existent ID."

---

## SEGMENT 7 — Swagger UI (1 min)

**Open browser:** `http://localhost:8080/swagger-ui/index.html`

**Say:**
> "All endpoints are documented with Swagger UI. You can see the request
> schemas, response formats, and even test the API directly from the browser."

**Click on the HealthController and show the @Tag and @Operation annotations in code.**

---

## SEGMENT 8 — RabbitMQ Management UI (1 min)

**Open browser:** `http://localhost:15672`

**Say:**
> "RabbitMQ's management UI shows us the notification.queue and notification.dlq.
> You can see message rates, consumers, and inspect any messages in the DLQ
> if failures occur."

**Click on Queues tab — show `notification.queue` and `notification.dlq`.**

---

## SEGMENT 9 — JSON Structured Logging (1 min)

**Run in terminal:**
```bash
docker compose logs api --tail=5
```

**Say:**
> "All logs are structured JSON — not plain text. Every field is named:
> timestamp, level, logger, message, correlationId, service.
> This makes logs directly ingestible by ELK stack, Grafana Loki, or Datadog
> without any parsing configuration."

---

## SEGMENT 10 — Improvements Summary (1 min)

**Show:** README Section — or just say from memory:

**Say:**
> "To summarise the improvements I made based on mentor feedback:
>
> 1. Atomic sliding-window rate limiter — replaced non-atomic INCR+EXPIRE
> 2. Externalized all credentials — no hardcoded passwords anywhere
> 3. Worker error handling — try-catch, rethrow, retry, DLQ
> 4. Correlation ID propagation — full end-to-end tracing
> 5. JSON structured logging in both services
> 6. Integration tests with Testcontainers — real DB, real RabbitMQ, real Redis
> 7. Swagger docs on health endpoint
> 8. Transactional publish — message only sent after DB commit succeeds"

---

## SEGMENT 11 — Git History (30 sec)

**Run:**
```bash
git log --oneline
```

**Say:**
> "All 19 changes were committed atomically — one commit per concern,
> following conventional commit format: fix:, feat:, chore:, test:.
> This makes the history readable and reviewable."

---

## CLOSING (30 sec)

**Say:**
> "That's the full project. A production-ready distributed notification system
> with asynchronous processing, rate limiting, retry logic, dead letter queues,
> distributed tracing, JSON logging, and full test coverage.
> Thank you for watching."

---

## QUICK REFERENCE — All Demo Commands

```bash
# Health
curl http://localhost:8080/health

# Submit
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"recipient":"demo@example.com","subject":"Hello","body":"Demo","type":"EMAIL"}'

# Get status
curl http://localhost:8080/api/notifications/{ID}

# Validation error
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"type":"EMAIL","body":"no recipient"}'

# Rate limit test
for i in {1..6}; do curl -s -o /dev/null -w "Request $i: %{http_code}\n" -X POST http://localhost:8080/api/notifications -H "Content-Type: application/json" -d '{"recipient":"r@r.com","type":"EMAIL","body":"t"}'; done

# Not found
curl http://localhost:8080/api/notifications/00000000-0000-0000-0000-000000000000

# View logs
docker compose logs api --tail=10
docker compose logs worker --tail=10

# Git history
git log --oneline
```
