package com.youdash.dto.admin;

import lombok.Data;

@Data
public class AdminDashboardSummaryDTO {
    private String range;
    private String from;
    private String to;

    private Long totalOrders;
    private Double grossRevenue;
    private Long onlineRiders;
    private Long activeUsers;

    private Double avgAssignmentEtaMinutes;
    private Double cancellationRate;
    private Double completionRate;
    private Double avgOrderValue;
}

