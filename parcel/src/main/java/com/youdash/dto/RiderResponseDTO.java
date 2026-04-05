package com.youdash.dto;

import lombok.Data;

@Data
public class RiderResponseDTO {
    private Long id;
    private String name;
    private String phone;
    private String vehicleType;
    private Boolean isAvailable;
    private Double rating;
}
