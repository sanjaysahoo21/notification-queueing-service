package com.example.notification.api.service;

import com.example.notification.api.Repository.NotificationRepository;
import com.example.notification.api.dto.NotificationMessage;
import com.example.notification.api.dto.NotificationRequest;
import com.example.notification.api.dto.NotificationStatusResponse;
import com.example.notification.api.entity.Notification;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

     private final NotificationRepository notificationRepository;
     private final NotificationPublisher publisher;

     private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

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

     public NotificationStatusResponse getNotification(UUID id) {

          Optional<Notification> optional = notificationRepository.findById(id);

          if(optional.isEmpty()) {
               return null;
          }

          Notification notification = optional.get();

          return new NotificationStatusResponse(
                  notification.getId(),
                  notification.getRecipient(),
                  notification.getSubject(),
                  notification.getBody(),
                  notification.getType(),
                  notification.getStatus(),
                  notification.getCreatedAt(),
                  notification.getUpdatedAt()
          );

     }

}
