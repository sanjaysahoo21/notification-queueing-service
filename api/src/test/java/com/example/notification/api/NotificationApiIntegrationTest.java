package com.example.notification.api;

import com.example.notification.api.dto.NotificationRequest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the API service.
 * Spins up real PostgreSQL, Redis, and RabbitMQ via Testcontainers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("notifications")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
            .withExposedPorts(6379);

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        // Low rate limit for the rate-limiting test
        registry.add("rate.limit.max-requests", () -> "3");
        registry.add("rate.limit.window-seconds", () -> "60");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    // ---- Health ----

    @Test
    @Order(1)
    void healthCheck_shouldReturnUp() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/health", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }

    // ---- Submit notification ----

    @Test
    @Order(2)
    void submitNotification_validRequest_shouldReturn202WithId() {
        NotificationRequest request = validRequest("submit@example.com");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/notifications", request, Map.class);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("notificationId"), "notificationId must be present");
        assertNotNull(response.getHeaders().getFirst("X-Correlation-ID"), "X-Correlation-ID header must be set");
    }

    @Test
    @Order(3)
    void submitNotification_missingRecipient_shouldReturn400() {
        NotificationRequest request = new NotificationRequest();
        request.setSubject("Test");
        request.setBody("Body");
        request.setType("EMAIL");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/notifications", request, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody().get("recipient"));
    }

    @Test
    @Order(4)
    void submitNotification_invalidEmail_shouldReturn400() {
        NotificationRequest request = validRequest("not-an-email");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/notifications", request, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ---- Get notification ----

    @Test
    @Order(5)
    void getNotification_existingId_shouldReturn200WithPendingStatus() {
        NotificationRequest request = validRequest("get@example.com");
        ResponseEntity<Map> createResponse = restTemplate.postForEntity("/api/notifications", request, Map.class);
        String notificationId = createResponse.getBody().get("notificationId").toString();

        ResponseEntity<Map> getResponse = restTemplate.getForEntity(
                "/api/notifications/" + notificationId, Map.class);

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertEquals(notificationId, getResponse.getBody().get("id").toString());
        assertEquals("PENDING", getResponse.getBody().get("status"));
    }

    @Test
    @Order(6)
    void getNotification_unknownId_shouldReturn404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/notifications/" + UUID.randomUUID(), String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ---- Rate limiting ----

    @Test
    @Order(7)
    void rateLimiting_shouldReturn429AfterExceedingLimit() {
        NotificationRequest request = validRequest("ratelimit@example.com");

        // First 3 requests should succeed (limit configured to 3 for this test)
        for (int i = 0; i < 3; i++) {
            ResponseEntity<Map> response = restTemplate.postForEntity("/api/notifications", request, Map.class);
            assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(),
                    "Request " + (i + 1) + " should be accepted");
        }

        // 4th request should be rate limited
        ResponseEntity<String> blocked = restTemplate.postForEntity("/api/notifications", request, String.class);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatusCode());
        assertNotNull(blocked.getHeaders().getFirst("Retry-After"), "Retry-After header must be present");
    }

    // ---- Helper ----

    private NotificationRequest validRequest(String email) {
        NotificationRequest req = new NotificationRequest();
        req.setRecipient(email);
        req.setSubject("Integration Test Subject");
        req.setBody("Integration test body content");
        req.setType("EMAIL");
        return req;
    }
}
