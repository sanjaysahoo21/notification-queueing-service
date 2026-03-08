package com.example.notification.api.service;

import com.example.notification.api.Repository.NotificationRepository;
import com.example.notification.api.dto.NotificationMessage;
import com.example.notification.api.dto.NotificationRequest;
import com.example.notification.api.dto.NotificationStatusResponse;
import com.example.notification.api.entity.Notification;
import com.example.notification.api.exception.NotificationNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
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
          log.info("Notification created with id: {}", notificationId);

          NotificationMessage message = new NotificationMessage(
                    notificationId,
                    request.getRecipient(),
                    request.getType(),
                    MDC.get("correlationId"));

          // Publish only after the DB transaction commits to avoid ghost messages
          if (TransactionSynchronizationManager.isActualTransactionActive()) {
               TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                         publisher.publish(message);
                    }
               });
          } else {
               publisher.publish(message);
          }

          return notificationId;
     }

     public NotificationStatusResponse getNotification(UUID id) {

          Notification notification = notificationRepository.findById(id)
                    .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + id));

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
