package com.example.notification.worker.consumer;

import com.example.notification.worker.Repository.NotificationRepository;
import com.example.notification.worker.dto.*;
import com.example.notification.worker.entity.Notification;

import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NotificationConsumer {

    private final NotificationRepository notificationRepository;

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    public NotificationConsumer(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @RabbitListener(queues = "notification.queue")
    public void consume(NotificationMessage message) throws InterruptedException {

        UUID notificationId = message.getNotificationId();

        System.out.println("Processing notification: " + notificationId);

        Optional<Notification> optional = notificationRepository.findById(notificationId);

        if (optional.isEmpty()) {
            System.out.println("Notification not found: " + notificationId);
            return;
        }

        Notification notification = optional.get();

        if (!"PENDING".equals(notification.getStatus())) {
            System.out.println("Notification already processed: " + notificationId);
            return;
        }

        Thread.sleep(2000);

        notification.setStatus("SENT");
        notification.setUpdatedAt(OffsetDateTime.now());

        notificationRepository.save(notification);

        log.info("Notification processed id: {}", notificationId);

    }
}
