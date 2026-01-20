package com.example.notification.api.controller;

import com.example.notification.api.dto.NotificationMessage;
import com.example.notification.api.service.NotificationPublisher;
import com.example.notification.api.service.NotificationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import com.example.notification.api.dto.NotificationRequest;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    NotificationPublisher publisher;
    private final NotificationService notificationService;

    public NotificationController(NotificationPublisher publisher, NotificationService notificationService) {
        this.publisher = publisher;
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> submitNotification(@Valid @RequestBody NotificationRequest request) {

        UUID notificationId = notificationService.createNotification(request);

        return ResponseEntity
                .accepted()
                .body(Map.of("notificationId", notificationId));

    }

}
