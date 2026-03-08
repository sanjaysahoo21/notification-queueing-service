package com.example.notification.api.dto;

import java.util.UUID;

public class NotificationMessage {

    private UUID notificationId;
    private String recipient;
    private String type;
    private String correlationId;

    public NotificationMessage(UUID notificationId, String recipient, String type, String correlationId) {
        this.notificationId = notificationId;
        this.recipient = recipient;
        this.type = type;
        this.correlationId = correlationId;
    }

    public NotificationMessage() {}

    public UUID getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(UUID notificationId) {
        this.notificationId = notificationId;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
