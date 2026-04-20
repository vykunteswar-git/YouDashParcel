package com.youdash.dto.notification;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AdminNotificationBroadcastRequestDTO {
    private String targetType;
    private String title;
    private String body;
    /** Optional custom label from UI (e.g. PROMOTIONAL, SYSTEM, CRITICAL). */
    private String notificationType;
    private String city;
    private Long zoneId;
    private List<Long> userIds;
    private List<Long> riderIds;
    private Map<String, String> data;
    /** When true, save as draft and do not push immediately. */
    private Boolean saveDraft;
}

