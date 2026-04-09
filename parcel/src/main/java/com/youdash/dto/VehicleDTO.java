package com.youdash.dto;

import lombok.Data;

@Data
public class VehicleDTO {
    private Long id;
    private String name;
    private Double pricePerKm;
    private Double baseFare;
    private Double minimumKm;
    private Double maxWeight;
    private String imageUrl;
    private Boolean isActive;
}
