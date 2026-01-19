package com.example.notification.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import com.example.notification.api.dto.NotificationRequest;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    
    @PostMapping
    public ResponseEntity<String> submitNotification(@Valid @RequestBody NotificationRequest request) {

        return ResponseEntity.ok("Validation successful");

    }

}
