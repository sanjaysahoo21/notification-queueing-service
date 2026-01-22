package com.example.notification.worker.consumer;

import com.example.notification.worker.Repository.NotificationRepository;
import com.example.notification.worker.dto.NotificationMessage;
import com.example.notification.worker.entity.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    @Test
    void testConsume_Success() throws InterruptedException {
        // Arrange
        UUID notificationId = UUID.randomUUID();
        NotificationMessage message = new NotificationMessage();
        message.setNotificationId(notificationId);

        Notification notification = new Notification();
        notification.setId(notificationId);
        notification.setStatus("PENDING");

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        // Act
        notificationConsumer.consume(message);

        // Assert
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(notificationRepository, times(1)).findById(notificationId);
    }
}
