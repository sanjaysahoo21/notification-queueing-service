package com.example.notification.worker.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import com.example.notification.worker.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    
}
