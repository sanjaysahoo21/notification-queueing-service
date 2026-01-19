package com.example.notification.api.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import com.example.notification.api.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    
}
