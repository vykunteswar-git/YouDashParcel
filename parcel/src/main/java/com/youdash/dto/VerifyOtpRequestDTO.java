package com.youdash.dto;

import lombok.Data;

@Data
public class VerifyOtpRequestDTO {
    private String phoneNumber;
    private String otp;
    private String fcmToken;
}
