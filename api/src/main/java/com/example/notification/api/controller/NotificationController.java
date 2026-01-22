package com.example.notification.api.controller;

import com.example.notification.api.dto.NotificationMessage;
import com.example.notification.api.dto.NotificationStatusResponse;
import com.example.notification.api.service.NotificationPublisher;
import com.example.notification.api.service.NotificationService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import com.example.notification.api.dto.NotificationRequest;

import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    NotificationPublisher publisher;
    private final NotificationService notificationService;

    public NotificationController(NotificationPublisher publisher, NotificationService notificationService) {
        this.publisher = publisher;
        this.notificationService = notificationService;
    }

    @Operation(summary = "Submit a notification asynchronously")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Notification accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping
    public ResponseEntity<Map<String, UUID>> submitNotification(@Valid @RequestBody NotificationRequest request) {

        UUID notificationId = notificationService.createNotification(request);

        return ResponseEntity
                .accepted()
                .body(Map.of("notificationId", notificationId));

    }

    @Operation(summary = "Get notification status by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification found"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<NotificationStatusResponse> getNotificationStatus(@PathVariable  UUID id) {

        NotificationStatusResponse response = notificationService.getNotification(id);

        if(response == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(response);
    }


}
