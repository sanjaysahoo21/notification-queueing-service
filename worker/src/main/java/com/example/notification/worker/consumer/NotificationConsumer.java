package com.example.notification.worker.consumer;

import com.example.notification.worker.Repository.NotificationRepository;
import com.example.notification.worker.config.RabbitMQConfig;
import com.example.notification.worker.dto.NotificationMessage;
import com.example.notification.worker.entity.Notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationConsumer {

    private final NotificationRepository notificationRepository;

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    public NotificationConsumer(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    @Transactional
    public void consume(NotificationMessage message) throws InterruptedException {

        UUID notificationId = message.getNotificationId();

        // Propagate correlationId from API into worker MDC for end-to-end tracing
        String correlationId = message.getCorrelationId();
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }

        try {
            log.info("Processing notification: {}", notificationId);

            Optional<Notification> optional = notificationRepository.findById(notificationId);

            if (optional.isEmpty()) {
                log.warn("Notification not found: {}", notificationId);
                return;
            }

            Notification notification = optional.get();

            if (!"PENDING".equals(notification.getStatus())) {
                log.warn("Notification already processed: {}", notificationId);
                return;
            }

            Thread.sleep(2000);

            notification.setStatus("SENT");
            notification.setUpdatedAt(OffsetDateTime.now());
            notificationRepository.save(notification);

            log.info("Notification processed id: {}", notificationId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Consumer thread interrupted for notification: {}", notificationId, e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to process notification: {} — will retry", notificationId, e);
            throw e; // rethrow so Spring AMQP retry kicks in; exhausted retries → DLQ
        } finally {
            MDC.clear();
        }
    }
}
