package com.example.notification.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class NotificationStatusResponse {

    private UUID id;
    private String recipient;
    private String subject;
    private String body;
    private String type;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public NotificationStatusResponse(
            UUID id, String recipient, String subject, String body, String type, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.type = type;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
