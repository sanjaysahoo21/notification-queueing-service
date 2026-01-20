package com.example.notification.api.service;

import com.example.notification.api.Repository.NotificationRepository;
import com.example.notification.api.dto.NotificationMessage;
import com.example.notification.api.dto.NotificationRequest;
import com.example.notification.api.entity.Notification;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class NotificationService {

     private final NotificationRepository notificationRepository;
     private final NotificationPublisher publisher;

     public NotificationService(NotificationRepository notificationRepository, NotificationPublisher publisher) {
          this.notificationRepository = notificationRepository;
          this.publisher = publisher;
     }

     @Transactional
     public UUID createNotification(NotificationRequest request) {
          UUID notificationId = UUID.randomUUID();

          Notification notification = new Notification();

          notification.setId(notificationId);
          notification.setRecipient(request.getRecipient());
          notification.setSubject(request.getSubject());
          notification.setBody(request.getBody());
          notification.setType(request.getType());
          notification.setStatus("PENDING");
          notification.setCreatedAt(OffsetDateTime.now());
          notification.setUpdatedAt(OffsetDateTime.now());

          notificationRepository.save(notification);

          NotificationMessage message = new NotificationMessage(
                    notificationId,
                    request.getRecipient(),
                    request.getType());

          publisher.publish(message);

          return notificationId;
     }

}
