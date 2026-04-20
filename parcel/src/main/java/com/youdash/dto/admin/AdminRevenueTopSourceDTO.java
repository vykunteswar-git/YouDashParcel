package com.youdash.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminRevenueTopSourceDTO {
    private String metricId;
    private String source;
    private Long volume;
    private Double conversionPercent;
    private String status;
}

