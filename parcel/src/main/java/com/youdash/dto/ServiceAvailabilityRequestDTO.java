package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Pickup and drop coordinates for service detection; weight (kg) filters incity vehicles")
public class ServiceAvailabilityRequestDTO {

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double pickupLat;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double pickupLng;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double dropLat;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double dropLng;

    /** Parcel weight in kilograms; used to filter vehicles when service mode is INCITY */
    @NotNull
    @DecimalMin(value = "0.01", message = "weightKg must be greater than 0")
    @DecimalMax(value = "10000.0", message = "weightKg exceeds maximum allowed")
    @Schema(description = "Parcel weight in kg (incity response lists only vehicles that can carry this weight)")
    private Double weightKg;
}
