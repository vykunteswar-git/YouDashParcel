package com.youdash.dto.notification;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationInboxItemDTO {
    private Long id;
    private String title;
    private String body;
    private String notificationType;
    private String dataJson;
    private Boolean isRead;
    private String readAt;
    private String createdAt;
}
