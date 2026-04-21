package com.youdash.dto;

import com.youdash.model.PaymentType;
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
    private Boolean codEnabled;
    private Boolean onlineEnabled;
    private PaymentType defaultPaymentType;
}
