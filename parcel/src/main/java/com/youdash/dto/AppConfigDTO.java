package com.youdash.dto;

import lombok.Data;

@Data
public class AppConfigDTO {

    private Long id;
    private Double gstPercent;
    private Double platformFee;
    private Double pickupRatePerKm;
    private Double dropRatePerKm;
    private Double perKgRate;
    private Double defaultRouteRatePerKm;
}
