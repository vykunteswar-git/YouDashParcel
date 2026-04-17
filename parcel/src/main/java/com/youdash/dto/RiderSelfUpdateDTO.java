package com.youdash.dto;

import lombok.Data;

/**
 * Partial update for the logged-in rider. Only non-null fields are applied.
 * KYC / identity fields (name, phone, vehicle, documents) are not editable via this DTO.
 */
@Data
public class RiderSelfUpdateDTO {
    private Boolean isAvailable;
    private Double currentLat;
    private Double currentLng;
    private String emergencyPhone;
    private String fcmToken;
    private String email;
}
