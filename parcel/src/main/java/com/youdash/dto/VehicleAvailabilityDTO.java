package com.youdash.dto;

import lombok.Data;

@Data
public class VehicleAvailabilityDTO {

    private Long id;
    private String name;
    private Double maxWeight;
    private Double pricePerKm;
    private Double baseFare;
    private Double minimumKm;
}
