package com.youdash.dto;

import lombok.Data;

@Data
public class OtpVerifyDTO {
    private String phoneNumber;
    private String otp;
}