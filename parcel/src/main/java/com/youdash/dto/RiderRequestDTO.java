package com.youdash.dto;

import lombok.Data;

@Data
public class RiderRequestDTO {
    private String name;
    private String phone;
    private String vehicleType;

    private String emergencyPhone;

    private String profileImageUrl;
    private String aadhaarImageUrl;
    private String licenseImageUrl;
}
