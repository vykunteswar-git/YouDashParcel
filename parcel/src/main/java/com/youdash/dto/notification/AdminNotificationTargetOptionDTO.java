package com.youdash.dto.notification;

import lombok.Data;

@Data
public class AdminNotificationTargetOptionDTO {
    private Long id;
    private String type;
    private String label;
    private String subLabel;
    private Boolean hasFcmToken;
}

