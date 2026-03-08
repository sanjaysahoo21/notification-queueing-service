package com.example.notification.worker.consumer;

import com.example.notification.worker.Repository.NotificationRepository;
import com.example.notification.worker.dto.NotificationMessage;
import com.example.notification.worker.entity.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the Worker consumer.
 * Verifies that a message published to RabbitMQ is consumed and the
 * notification status is updated to SENT in PostgreSQL.
 */
@SpringBootTest
@Testcontainers
class NotificationConsumerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("notifications")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
    }

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private Jackson2JsonMessageConverter messageConverter;

    @Test
    void consume_pendingNotification_shouldUpdateStatusToSent() throws InterruptedException {
        // Persist a PENDING notification directly in the DB
        UUID id = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(id);
        notification.setRecipient("worker-test@example.com");
        notification.setSubject("Worker Integration Test");
        notification.setBody("Test body");
        notification.setType("EMAIL");
        notification.setStatus("PENDING");
        notification.setCreatedAt(OffsetDateTime.now());
        notification.setUpdatedAt(OffsetDateTime.now());
        notificationRepository.save(notification);

        // Configure the RabbitTemplate to use JSON converter (same as producer)
        rabbitTemplate.setMessageConverter(messageConverter);

        // Publish the message as the API would
        NotificationMessage message = new NotificationMessage();
        message.setNotificationId(id);
        message.setRecipient("worker-test@example.com");
        message.setType("EMAIL");
        message.setCorrelationId("test-correlation-id");
        rabbitTemplate.convertAndSend("notification.queue", message);

        // Wait for consumer processing (2s Thread.sleep + overhead)
        Thread.sleep(6000);

        Notification updated = notificationRepository.findById(id).orElseThrow();
        assertEquals("SENT", updated.getStatus(), "Status should be updated to SENT after processing");
    }

    @Test
    void consume_alreadyProcessedNotification_shouldBeSkippedIdempotently() throws InterruptedException {
        UUID id = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(id);
        notification.setRecipient("idempotent@example.com");
        notification.setSubject("Idempotency Test");
        notification.setBody("Test body");
        notification.setType("EMAIL");
        notification.setStatus("SENT");  // Already processed
        notification.setCreatedAt(OffsetDateTime.now());
        notification.setUpdatedAt(OffsetDateTime.now());
        notificationRepository.save(notification);

        rabbitTemplate.setMessageConverter(messageConverter);

        NotificationMessage message = new NotificationMessage();
        message.setNotificationId(id);
        message.setRecipient("idempotent@example.com");
        message.setType("EMAIL");
        rabbitTemplate.convertAndSend("notification.queue", message);

        Thread.sleep(3000);

        // Status should remain SENT, not re-processed
        Notification unchanged = notificationRepository.findById(id).orElseThrow();
        assertEquals("SENT", unchanged.getStatus(), "Already-processed notification should not be re-processed");
    }
}
