package com.example.notification.worker.dto;

import java.util.UUID;

public class NotificationMessage {

    private UUID notificationId;
    private String recipient;
    private String type;

    public NotificationMessage() {}

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(UUID notificationId) {
        this.notificationId = notificationId;
    }
}
