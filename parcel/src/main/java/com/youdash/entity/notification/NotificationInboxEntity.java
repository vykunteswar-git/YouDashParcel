package com.youdash.entity.notification;

import com.youdash.model.notification.NotificationRecipientType;
import com.youdash.notification.NotificationType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "youdash_notification_inbox", indexes = {
        @Index(name = "idx_notif_recipient_created", columnList = "recipient_type,recipient_id,created_at"),
        @Index(name = "idx_notif_unread", columnList = "recipient_type,recipient_id,is_read")
})
@Data
public class NotificationInboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 16)
    private NotificationRecipientType recipientType;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, length = 1024)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", length = 64)
    private NotificationType notificationType;

    @Column(name = "data_json", columnDefinition = "TEXT")
    private String dataJson;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (isRead == null) {
            isRead = false;
        }
    }
}
