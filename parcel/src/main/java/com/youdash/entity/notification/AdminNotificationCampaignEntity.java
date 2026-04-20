package com.youdash.entity.notification;

import com.youdash.model.notification.AdminNotificationCampaignStatus;
import com.youdash.model.notification.AdminNotificationTargetType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "youdash_admin_notification_campaigns", indexes = {
        @Index(name = "idx_admin_notif_created", columnList = "created_at"),
        @Index(name = "idx_admin_notif_status", columnList = "status"),
        @Index(name = "idx_admin_notif_target", columnList = "target_type")
})
@Data
public class AdminNotificationCampaignEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, length = 1024)
    private String body;

    @Column(name = "notification_type", length = 64)
    private String notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private AdminNotificationTargetType targetType;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "zone_id")
    private Long zoneId;

    @Column(name = "user_ids_json", columnDefinition = "TEXT")
    private String userIdsJson;

    @Column(name = "rider_ids_json", columnDefinition = "TEXT")
    private String riderIdsJson;

    @Column(name = "data_json", columnDefinition = "TEXT")
    private String dataJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AdminNotificationCampaignStatus status;

    @Column(name = "total_targets", nullable = false)
    private Integer totalTargets;

    @Column(name = "success_count", nullable = false)
    private Integer successCount;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = AdminNotificationCampaignStatus.DRAFT;
        }
        if (totalTargets == null) {
            totalTargets = 0;
        }
        if (successCount == null) {
            successCount = 0;
        }
        if (failedCount == null) {
            failedCount = 0;
        }
    }
}

