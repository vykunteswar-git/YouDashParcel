package com.youdash.dto;

import lombok.Data;

@Data
public class RiderRequestDTO {
    private String name;
    private String phone;
    private String vehicleType;
    private Double currentLat;
    private Double currentLng;
}
