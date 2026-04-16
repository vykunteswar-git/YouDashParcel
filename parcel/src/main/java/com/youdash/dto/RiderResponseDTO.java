package com.youdash.dto;

import lombok.Data;

@Data
public class RiderResponseDTO {
    private Long id;
    private String publicId;
    private String name;
    private String phone;
    private Long vehicleId;
    private String vehicleType;
    private Boolean isAvailable;
    private Boolean isBlocked;
    private Double rating;
    private String approvalStatus;
    private String profileImageUrl;
    private String aadhaarImageUrl;
    private String licenseImageUrl;
    private String rcImageUrl;
}
