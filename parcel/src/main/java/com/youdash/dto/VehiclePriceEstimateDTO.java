package com.youdash.dto;

import lombok.Data;

@Data
public class VehiclePriceEstimateDTO {
    private Long vehicleId;
    private String vehicleName;
    private Double totalAmount;
}
