package com.youdash.dto;

import lombok.Data;

@Data
public class DeliveryTypeOptionDTO {
    private String name;        // STANDARD/EXPRESS/SAFE
    private String scope;       // IN_CITY/OUT_CITY (resolved by backend)
    private Double fee;         // fee for this scope
    private String description; // admin-configured
}

