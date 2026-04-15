package com.youdash.dto;

import java.util.Map;

import lombok.Data;

@Data
public class TestPushRequestDTO {
    /** FCM registration token from device (NOT server key). */
    private String token;
    private String title;
    private String body;
    /** Optional FCM data map; values must be strings. */
    private Map<String, String> data;
    /** Optional: {@link com.youdash.notification.NotificationType} name. */
    private String type;
}

