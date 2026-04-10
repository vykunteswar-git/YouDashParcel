package com.youdash.dto;

import lombok.Data;

@Data
public class FareCalculateResponseDTO {
    private String serviceType;
    private Double distanceKm;
    private Double pricePerKm;
    private Double subTotal;
    private Double platformFee;
    private Double gstBase;
    private Double gstPercent;
    private Double gstAmount;
    private Double totalAmount;
    private Long vehicleId;
}

