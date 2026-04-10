package com.youdash.dto;

import lombok.Data;

@Data
public class AdminPricingConfigDTO {
    // All fields are optional for update; non-null fields will be applied.
    private Double gstPercent;
    private Double platformFee;
    private Double inCityRadiusKm;
}

