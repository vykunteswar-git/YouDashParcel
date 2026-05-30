package com.youdash.dto;

import lombok.Data;

@Data
public class AdminOrderStatusDTO {

    private String status;
    /** Pickup or delivery OTP when admin advances a gated outstation step. */
    private String otp;
    /** Emergency skip of OTP checks (logged on timeline). */
    private Boolean adminOverride;
}
