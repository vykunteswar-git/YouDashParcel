package com.youdash.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Data;

@Data
public class FcmTokenRequestDTO {
    /**
     * FCM registration token. JSON may use {@code "token"} or {@code "fcmToken"} (common on mobile clients).
     */
    @JsonAlias({ "fcmToken", "registrationToken", "deviceToken" })
    private String token;
}

