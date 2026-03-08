package com.example.notification.api.service;

import com.example.notification.api.dto.NotificationMessage;
import com.example.notification.api.dto.NotificationRequest;
import com.example.notification.api.entity.Notification;
import com.example.notification.api.exception.NotificationNotFoundException;
import com.example.notification.api.Repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private NotificationService notificationService;

    private NotificationRequest request;

    @BeforeEach
    void setUp() {
        request = new NotificationRequest();
        request.setRecipient("test@example.com");
        request.setSubject("Test Subject");
        request.setBody("Test Body");
        request.setType("EMAIL");
    }

    @Test
    void testCreateNotification_Success() {
        // Arrange — no active transaction in unit test, so publish falls through guard directly
        Notification savedNotification = new Notification();
        savedNotification.setId(UUID.randomUUID());
        savedNotification.setStatus("PENDING");

        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
        doNothing().when(notificationPublisher).publish(any(NotificationMessage.class));

        // Act
        UUID responseId = notificationService.createNotification(request);

        // Assert
        assertNotNull(responseId);
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(notificationPublisher, times(1)).publish(any(NotificationMessage.class));
    }

    @Test
    void testGetNotification_NotFound_ThrowsException() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class,
                () -> notificationService.getNotification(id));
    }

    @Test
    void testGetNotification_Found_ReturnsResponse() {
        UUID id = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(id);
        notification.setRecipient("test@example.com");
        notification.setSubject("Subject");
        notification.setBody("Body");
        notification.setType("EMAIL");
        notification.setStatus("PENDING");
        notification.setCreatedAt(OffsetDateTime.now());
        notification.setUpdatedAt(OffsetDateTime.now());

        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        var response = notificationService.getNotification(id);

        assertNotNull(response);
        assertEquals(id, response.getId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("test@example.com", response.getRecipient());
    }
}
