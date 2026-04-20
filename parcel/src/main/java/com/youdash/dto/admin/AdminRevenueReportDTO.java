package com.youdash.dto.admin;

import lombok.Data;

import java.util.List;

@Data
public class AdminRevenueReportDTO {
    private String range;
    private String from;
    private String to;

    private Double totalRevenue;
    private Double completionRate;
    private Double rushMultiplier;
    private Double avgAssignmentEtaMinutes;

    private List<AdminRevenueTrendPointDTO> trend;
    private List<AdminRevenueTopSourceDTO> topSources;
}

