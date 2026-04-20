package com.youdash.dto.notification;

import lombok.Data;

@Data
public class AdminNotificationCampaignDTO {
    private Long id;
    private String title;
    private String body;
    private String notificationType;
    private String targetType;
    private String city;
    private Long zoneId;
    private String status;
    private Integer totalTargets;
    private Integer successCount;
    private Integer failedCount;
    private Long createdBy;
    private String createdAt;
    private String sentAt;
}

