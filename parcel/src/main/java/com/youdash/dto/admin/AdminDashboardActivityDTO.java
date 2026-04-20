package com.youdash.dto.admin;

import lombok.Data;

@Data
public class AdminDashboardActivityDTO {
    private Long orderId;
    private String displayOrderId;
    private String customerName;
    private String riderName;
    private String serviceMode;
    private String status;
    private Double amount;
    private String createdAt;
}

