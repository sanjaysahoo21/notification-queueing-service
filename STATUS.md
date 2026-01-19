# Notification Queueing Service - Status Report

## Current Status

### ✅ Fixed Issues
1. **Missing Database Configuration** - Both API and Worker services were missing database connection properties
2. **Missing RabbitMQ Configuration** - Message broker settings were not configured
3. **Missing Redis Configuration** - Rate limiting service configuration was absent

### 🔧 Configuration Updates

#### API Service (`api/src/main/resources/application.properties`)
- Server Port: **8080**
- PostgreSQL Database: `notification_db`
- RabbitMQ: localhost:5672
- Redis: localhost:6379

#### Worker Service (`worker/src/main/resources/application.properties`)
- Server Port: **8081**
- PostgreSQL Database: `notification_db`
- RabbitMQ: localhost:5672

---

## Service Dependencies Status

| Service | Port | Status | Notes |
|---------|------|--------|-------|
| PostgreSQL | 5432 | ✅ Running | Database is accessible |
| RabbitMQ | 5672 | ❌ Not Running | **REQUIRED - Must be started** |
| Redis | 6379 | ✅ Running | Cache/Rate limiting service is accessible |

---

## ⚠️ Critical Issue: RabbitMQ Not Running

Both Spring Boot applications **require RabbitMQ** to start successfully. The applications are currently attempting to connect but will fail without RabbitMQ.

### How to Start RabbitMQ

#### Option 1: Using Docker (Recommended)
```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

#### Option 2: Using Windows Service
```bash
# If RabbitMQ is installed as a Windows service
net start RabbitMQ
```

#### Option 3: Using RabbitMQ Server Directly
```bash
# Navigate to RabbitMQ installation directory and run
rabbitmq-server
```

---

## Running the Applications

### Terminal 1 - API Service
```bash
cd c:\Users\LENOVO\Desktop\GppTasks\notification-queueing-service\api
.\mvnw spring-boot:run
```

### Terminal 2 - Worker Service
```bash
cd c:\Users\LENOVO\Desktop\GppTasks\notification-queueing-service\worker
.\mvnw spring-boot:run
```

---

## Next Steps

1. **Start RabbitMQ** using one of the methods above
2. **Restart both Spring Boot applications** (they are currently running but may be in a retry loop)
3. **Verify the applications are running**:
   - API Service: http://localhost:8080
   - Worker Service: http://localhost:8081
   - Swagger UI (API): http://localhost:8080/swagger-ui.html

---

## Database Setup

The applications use `spring.jpa.hibernate.ddl-auto=update`, which means:
- Tables will be **automatically created** on first run
- Schema will be **automatically updated** when entity classes change
- No manual database setup required

---

## Troubleshooting

### If applications fail to start:
1. Check all three services are running (PostgreSQL, RabbitMQ, Redis)
2. Verify database credentials in `application.properties`
3. Check logs for specific error messages
4. Ensure ports 8080 and 8081 are not already in use

### To stop the applications:
- Press `Ctrl+C` in each terminal window

### To view detailed logs:
```bash
# In the respective project directory
.\mvnw spring-boot:run -X
```
