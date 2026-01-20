package com.example.notification.api.dto;

import java.util.UUID;

public class NotificationMessage {

    private UUID notificationId;
    private String recipient;
    private String type;

    public NotificationMessage(UUID notificationId, String recipient, String type) {
        this.notificationId = notificationId;
        this.recipient = recipient;
        this.type = type;
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
}
