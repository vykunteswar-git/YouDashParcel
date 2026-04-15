package com.youdash.dto;

import lombok.Data;

@Data
public class RiderRequestDTO {
    private String name;
    private String phone;
    /**
     * Preferred: vehicle selected from admin-created vehicles (dropdown uses GET /public/vehicles).
     * If provided, backend resolves to vehicle name and stores/returns it as {@code vehicleType}.
     */
    private Long vehicleId;

    /** Backward-compatible fallback. Prefer {@code vehicleId}. */
    private String vehicleType;

    private String emergencyPhone;

    private String profileImageUrl;
    private String aadhaarImageUrl;
    private String licenseImageUrl;
}
