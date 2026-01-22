# Notification Queueing Service

---

## 1) Project Explanation

This project is a backend notification system.

The API accepts notification requests and processes them asynchronously using a message queue.  
The API does not send notifications directly. Instead, it saves the request in a database and sends it to a queue.  
A separate worker service reads the message from the queue and processes it in the background.

This makes the system fast, scalable, and reliable.

### Flow:
1. Client sends notification request to API
2. API validates input and applies rate limiting
3. API saves notification in PostgreSQL with status PENDING
4. API publishes message to RabbitMQ
5. Worker consumes message from RabbitMQ
6. Worker simulates sending notification
7. Worker updates status to SENT
8. Client checks status using GET API

---

## 2) Frameworks and Tools Used

### Backend
- Java 17
- Spring Boot
- Spring Web
- Spring Data JPA (Hibernate)
- Spring Validation
- Spring AMQP (RabbitMQ)
- Spring Data Redis

### Infrastructure
- PostgreSQL
- RabbitMQ
- Redis
- Docker
- Docker Compose
- Maven

---

## 3) How to Run the Project Locally (Without Docker)

> Local run is optional. Docker is recommended.

### Requirements
- Java 17
- Maven
- PostgreSQL
- Redis
- RabbitMQ

### Run API
```bash
cd api
mvn spring-boot:run
```

### Run Worker
```bash
cd worker
mvn spring-boot:run
```

---

## 4) How to Run Using Docker (Recommended)

### Requirements
- Docker
- Docker Compose

### Steps

#### 1. Build JAR files
```bash
cd api
mvn clean package -DskipTests
cd ../worker
mvn clean package -DskipTests
cd ..
```

#### 2. Start all services
```bash
docker-compose up --build
```

This starts:
- API service
- Worker service
- PostgreSQL
- Redis
- RabbitMQ

---

## 5) Application URLs

- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **Health Check**: http://localhost:8080/health
- **RabbitMQ Management UI**: http://localhost:15672  
  - Username: `guest`  
  - Password: `guest`

---

## 6) Test Data

### Create Notification (POST /api/notifications)

**Request:**
```json
{
  "recipient": "test@example.com",
  "subject": "Test Message",
  "body": "This is a test notification",
  "type": "EMAIL"
}
```

**Response:**
```json
{
  "notification_id": "uuid-value"
}
```

### Get Notification Status (GET /api/notifications/{id})

**Response:**
```json
{
  "id": "uuid-value",
  "recipient": "test@example.com",
  "subject": "Test Message",
  "body": "This is a test notification",
  "type": "EMAIL",
  "status": "SENT",
  "createdAt": "2026-01-19T09:20:00Z",
  "updatedAt": "2026-01-19T09:20:02Z"
}
```

---

## 7) PostgreSQL Commands to Check Status (Docker)

### Enter PostgreSQL container
```bash
docker exec -it postgres psql -U postgres -d notifications
```

### View all notifications
```sql
SELECT id, status FROM notifications;
```

### View only SENT notifications
```sql
SELECT * FROM notifications WHERE status = 'SENT';
```

---

## 8) Conclusion

This project demonstrates:
- Asynchronous processing using RabbitMQ
- Distributed rate limiting using Redis
- Reliable data storage using PostgreSQL
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