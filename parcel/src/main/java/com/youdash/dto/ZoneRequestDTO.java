package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Create or replace zone (incity service area)")
public class ZoneRequestDTO {

    @NotBlank
    @Schema(example = "Bengaluru")
    private String city;

    @NotBlank
    @Schema(example = "Koramangala")
    private String name;

    @NotNull
    @DecimalMin(value = "-90.0", inclusive = true)
    @DecimalMax(value = "90.0", inclusive = true)
    @Schema(example = "12.9352")
    private Double centerLat;

    @NotNull
    @DecimalMin(value = "-180.0", inclusive = true)
    @DecimalMax(value = "180.0", inclusive = true)
    @Schema(example = "77.6245")
    private Double centerLng;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "must be greater than 0")
    @Schema(example = "5.0")
    private Double radiusKm;

    @Schema(description = "Defaults to true when omitted on create")
    private Boolean isActive;
}
