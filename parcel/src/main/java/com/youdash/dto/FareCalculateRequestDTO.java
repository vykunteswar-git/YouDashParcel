package com.youdash.dto;

import lombok.Data;

@Data
public class FareCalculateRequestDTO {
    /**
     * LOCAL: user selects a vehicle (vehicleId required)
     * OUTSTATION: vehicleId is ignored; backend uses configured outstation rate
     */
    private String serviceType;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private Long vehicleId;
}

