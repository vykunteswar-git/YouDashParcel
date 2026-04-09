package com.youdash.dto;

import lombok.Data;

@Data
public class DeliveryTypeRateDTO {
    private String deliveryTypeName; // STANDARD/EXPRESS/SAFE
    private String scope; // IN_CITY/OUT_CITY
    private Double fee;
    private String description;
    private Boolean active;
}

