package com.youdash.dto;

import lombok.Data;

@Data
public class IncityVehicleEstimateRequestDTO {
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private Double weightKg;
}
